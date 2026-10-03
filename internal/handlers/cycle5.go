package handlers

// Shared pieces for Cycle 5: archive/restore (admin only, audited) and the optional
// from/to/q/limit/offset filters on the sales, purchases and payments lists.

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// requireCompanyAdmin repeats the route's RequireCompanyAdmin check inside the handler, so
// archive/restore stay admin-only however they are wired (section 15: "the backend must enforce").
func requireCompanyAdmin(w http.ResponseWriter, r *http.Request) bool {
	if !appMiddleware.IsCompanyAdmin(r.Context()) {
		http.Error(w, "forbidden: company admin only", http.StatusForbidden)
		return false
	}
	return true
}

// includeArchived reports ?include_archived=true on the customer/supplier/product lists.
func includeArchived(r *http.Request) bool {
	return r.URL.Query().Get("include_archived") == "true"
}

// writeArchiveAudit records an archive or restore in the audit log (section 3.3 rule 5).
// [impact] is stored as the entry's inventory or payment impact JSON (whichever applies).
func writeArchiveAudit(ctx context.Context, q *db.Queries, action, entityType string, entityID int32, inventoryImpact, paymentImpact map[string]string) error {
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	userID, _ := appMiddleware.UserIDFromContext(ctx)
	params := db.CreateAuditLogEntryParams{
		CompanyID:   pgtype.Int4{Int32: companyID, Valid: true},
		ActorUserID: pgtype.Int4{Int32: userID, Valid: true},
		Action:      action,
		EntityType:  entityType,
		EntityID:    entityID,
	}
	if inventoryImpact != nil {
		params.InventoryImpact, _ = json.Marshal(inventoryImpact)
	}
	if paymentImpact != nil {
		params.PaymentImpact, _ = json.Marshal(paymentImpact)
	}
	_, err := q.CreateAuditLogEntry(ctx, params)
	return err
}

// settleBeforeArchive is the section 3.3 rule 1 message for a party that still has a balance.
func settleBeforeArchive(balancePaise int64) string {
	if balancePaise < 0 {
		balancePaise = -balancePaise
	}
	return "Settle the ₹" + formatPaise(balancePaise) + " balance before archiving."
}

// ---- list filters (section 6.3) ----

type listFilters struct {
	From   pgtype.Date
	To     pgtype.Date
	Q      pgtype.Text // escaped for ILIKE
	Limit  pgtype.Int4 // NULL = no limit
	Offset int32
	// Any is false when the request has none of the new params: the list endpoints then answer
	// exactly as before, so the web frontend is unaffected.
	Any bool
}

const maxListLimit = 200

// parseListFilters reads ?from=&to= (YYYY-MM-DD, inclusive), ?q=, ?limit= (1-200) and ?offset=.
func parseListFilters(r *http.Request) (listFilters, error) {
	var f listFilters
	q := r.URL.Query()
	parseDate := func(name string) (pgtype.Date, error) {
		s := strings.TrimSpace(q.Get(name))
		if s == "" {
			return pgtype.Date{}, nil
		}
		f.Any = true
		t, err := time.Parse(dateLayout, s)
		if err != nil {
			return pgtype.Date{}, errors.New(name + " must be a date like 2026-09-28")
		}
		return pgtype.Date{Time: t, Valid: true}, nil
	}
	var err error
	if f.From, err = parseDate("from"); err != nil {
		return f, err
	}
	if f.To, err = parseDate("to"); err != nil {
		return f, err
	}
	if s := strings.TrimSpace(q.Get("q")); s != "" {
		f.Any = true
		f.Q = pgtype.Text{String: escapeLike(s), Valid: true}
	}
	if s := q.Get("limit"); s != "" {
		f.Any = true
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 || n > maxListLimit {
			return f, errors.New("limit must be between 1 and 200")
		}
		f.Limit = pgtype.Int4{Int32: int32(n), Valid: true}
	}
	if s := q.Get("offset"); s != "" {
		f.Any = true
		n, err := strconv.Atoi(s)
		if err != nil || n < 0 {
			return f, errors.New("offset must be 0 or more")
		}
		f.Offset = int32(n)
	}
	return f, nil
}

// escapeLike makes user input match literally inside ILIKE '%' || q || '%'.
func escapeLike(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}

// writeListTotals adds the summary headers the Android lists show ("23 bills · ₹1,42,300").
// Only sent for paged requests (?limit=), per section 6.3.
func writeListTotals(w http.ResponseWriter, count int32, amount pgtype.Numeric) {
	w.Header().Set("X-Total-Count", strconv.Itoa(int(count)))
	w.Header().Set("X-Total-Amount", formatPaise(toPaise(amount)))
}
