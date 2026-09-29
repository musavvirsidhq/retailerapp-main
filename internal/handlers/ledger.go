package handlers

import (
	"errors"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// LedgerHandler serves the Cycle 4 customer (shop) and supplier (factory) dues lists and
// per-party ledgers. Money is summed in integer paise so a ledger's final running balance
// always matches ShopBalance/FactoryBalance exactly, with no float drift.
type LedgerHandler struct {
	Queries *db.Queries
}

func NewLedgerHandler(q *db.Queries) *LedgerHandler {
	return &LedgerHandler{Queries: q}
}

const dateLayout = "2006-01-02"

func toPaise(n pgtype.Numeric) int64 {
	return int64(math.Round(numericToFloat(n) * 100))
}

func formatPaise(p int64) string {
	sign := ""
	if p < 0 {
		sign = "-"
		p = -p
	}
	return fmt.Sprintf("%s%d.%02d", sign, p/100, p%100)
}

func strPtr(s string) *string { return &s }

func textPtr(t pgtype.Text) *string {
	if !t.Valid {
		return nil
	}
	return &t.String
}

func timePtr(t pgtype.Timestamptz) *time.Time {
	if !t.Valid {
		return nil
	}
	return &t.Time
}

func latest(ts ...*time.Time) *time.Time {
	var out *time.Time
	for _, t := range ts {
		if t != nil && (out == nil || t.After(*out)) {
			out = t
		}
	}
	return out
}

// ---- Dues lists ----

type duesRow struct {
	ID             int32      `json:"id"`
	Name           string     `json:"name"`
	Phone          string     `json:"phone"`
	Area           *string    `json:"area"`
	Balance        string     `json:"balance"`
	LastBillAt     *time.Time `json:"last_bill_at"`
	LastPaymentAt  *time.Time `json:"last_payment_at"`
	LastActivityAt *time.Time `json:"last_activity_at"`

	balance int64
}

type duesResponse struct {
	TotalPending string    `json:"total_pending"`
	PendingCount int       `json:"pending_count"`
	Rows         []duesRow `json:"rows"`
}

// sortAndFilterDues applies the dues-list query options: ?sort=recent|amount|oldest|name
// (default recent), ?include_zero=true to keep settled/advance parties, and ?q= search on
// name or phone. Totals always cover every party with a positive balance, whatever the filter.
func sortAndFilterDues(r *http.Request, rows []duesRow) duesResponse {
	var total int64
	count := 0
	for _, row := range rows {
		if row.balance > 0 {
			total += row.balance
			count++
		}
	}

	includeZero := r.URL.Query().Get("include_zero") == "true"
	search := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("q")))
	filtered := make([]duesRow, 0, len(rows))
	for _, row := range rows {
		if !includeZero && row.balance <= 0 {
			continue
		}
		if search != "" && !strings.Contains(strings.ToLower(row.Name), search) && !strings.Contains(row.Phone, search) {
			continue
		}
		filtered = append(filtered, row)
	}

	// Parties with no activity at all sort last for both "recent" and "oldest".
	timeOr := func(t *time.Time, fallback time.Time) time.Time {
		if t == nil {
			return fallback
		}
		return *t
	}
	var less func(a, b duesRow) bool
	switch r.URL.Query().Get("sort") {
	case "amount":
		less = func(a, b duesRow) bool { return a.balance > b.balance }
	case "oldest":
		// Longest since the last payment first; never-paid parties are the oldest of all.
		less = func(a, b duesRow) bool {
			return timeOr(a.LastPaymentAt, time.Time{}).Before(timeOr(b.LastPaymentAt, time.Time{}))
		}
	case "name":
		less = func(a, b duesRow) bool { return strings.ToLower(a.Name) < strings.ToLower(b.Name) }
	default:
		less = func(a, b duesRow) bool {
			return timeOr(a.LastActivityAt, time.Time{}).After(timeOr(b.LastActivityAt, time.Time{}))
		}
	}
	sort.SliceStable(filtered, func(i, j int) bool { return less(filtered[i], filtered[j]) })

	return duesResponse{TotalPending: formatPaise(total), PendingCount: count, Rows: filtered}
}

func (h *LedgerHandler) CustomerDues(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	dbRows, err := h.Queries.CustomerDues(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	rows := make([]duesRow, 0, len(dbRows))
	for _, d := range dbRows {
		lastBill, lastPay := timePtr(d.LastSaleAt), timePtr(d.LastPaymentAt)
		rows = append(rows, duesRow{
			ID: d.ID, Name: d.Name, Phone: d.PrimaryPhone, Area: textPtr(d.Area),
			Balance: formatPaise(toPaise(d.Balance)), balance: toPaise(d.Balance),
			LastBillAt: lastBill, LastPaymentAt: lastPay, LastActivityAt: latest(lastBill, lastPay),
		})
	}
	writeJSON(w, sortAndFilterDues(r, rows))
}

func (h *LedgerHandler) SupplierDues(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	dbRows, err := h.Queries.SupplierDues(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	rows := make([]duesRow, 0, len(dbRows))
	for _, d := range dbRows {
		lastBill, lastPay := timePtr(d.LastPurchaseAt), timePtr(d.LastPaymentAt)
		rows = append(rows, duesRow{
			ID: d.ID, Name: d.Name, Phone: d.PrimaryPhone, Area: textPtr(d.Area),
			Balance: formatPaise(toPaise(d.Balance)), balance: toPaise(d.Balance),
			LastBillAt: lastBill, LastPaymentAt: lastPay, LastActivityAt: latest(lastBill, lastPay),
		})
	}
	writeJSON(w, sortAndFilterDues(r, rows))
}

// ---- Ledgers ----

type ledgerParty struct {
	ID             int32   `json:"id"`
	Name           string  `json:"name"`
	ContactName    *string `json:"contact_name"`
	Phone          string  `json:"phone"`
	SecondaryPhone *string `json:"secondary_phone"`
	Area           *string `json:"area"`
}

type ledgerEntry struct {
	Type         string  `json:"type"` // opening | sale | purchase | payment
	ID           int32   `json:"id"`
	Date         string  `json:"date"`
	Ref          *string `json:"ref"`
	InvoiceNo    *string `json:"invoice_no"`
	Amount       *string `json:"amount"` // bill total, for sale/purchase/opening
	Paid         *string `json:"paid"`   // paid at bill time, or the payment amount
	Mode         *string `json:"mode"`
	Notes        *string `json:"notes"`
	BalanceAfter string  `json:"balance_after"`
	Cancelled    bool    `json:"cancelled"`
	PhotoCount   int32   `json:"photo_count"`
	ItemCount    int32   `json:"item_count"`

	date      time.Time
	createdAt time.Time
	delta     int64 // effect on the balance; 0 for cancelled bills
	billed    int64
	collected int64
	isBill    bool
}

type ledgerSummary struct {
	TotalBilled       string  `json:"total_billed"`
	TotalCollected    string  `json:"total_collected"`
	Pending           string  `json:"pending"`
	LastPaymentAt     *string `json:"last_payment_at"`
	LastPaymentAmount *string `json:"last_payment_amount"`
}

type rangeSummary struct {
	Billed    string `json:"billed"`
	Collected string `json:"collected"`
}

type ledgerResponse struct {
	Party        ledgerParty   `json:"party"`
	Summary      ledgerSummary `json:"summary"`
	RangeSummary rangeSummary  `json:"range_summary"`
	Entries      []ledgerEntry `json:"entries"`
}

// buildLedger orders every balance-affecting event oldest-first, walks it once to compute the
// running balance and all-time totals, then applies the request's filters
// (?from=&to= as YYYY-MM-DD, ?type=all|bill|payment, ?with_photos=true) and returns newest first.
func buildLedger(r *http.Request, party ledgerParty, entries []ledgerEntry) (ledgerResponse, error) {
	sort.SliceStable(entries, func(i, j int) bool {
		a, b := entries[i], entries[j]
		if a.Type == "opening" || b.Type == "opening" {
			return a.Type == "opening" && b.Type != "opening"
		}
		if !a.date.Equal(b.date) {
			return a.date.Before(b.date)
		}
		return a.createdAt.Before(b.createdAt)
	})

	var balance, billed, collected int64
	var lastPayment *ledgerEntry
	for i := range entries {
		e := &entries[i]
		balance += e.delta
		billed += e.billed
		collected += e.collected
		e.BalanceAfter = formatPaise(balance)
		if e.Type == "payment" {
			lastPayment = e
		}
	}

	summary := ledgerSummary{
		TotalBilled:    formatPaise(billed),
		TotalCollected: formatPaise(collected),
		Pending:        formatPaise(balance),
	}
	if lastPayment != nil {
		summary.LastPaymentAt = strPtr(lastPayment.Date)
		summary.LastPaymentAmount = lastPayment.Paid
	}

	q := r.URL.Query()
	var from, to time.Time
	var err error
	if s := q.Get("from"); s != "" {
		if from, err = time.Parse(dateLayout, s); err != nil {
			return ledgerResponse{}, errors.New("from must be YYYY-MM-DD")
		}
	}
	if s := q.Get("to"); s != "" {
		if to, err = time.Parse(dateLayout, s); err != nil {
			return ledgerResponse{}, errors.New("to must be YYYY-MM-DD")
		}
	}
	typ := q.Get("type")
	withPhotos := q.Get("with_photos") == "true"

	var rangeBilled, rangeCollected int64
	out := make([]ledgerEntry, 0, len(entries))
	for i := len(entries) - 1; i >= 0; i-- {
		e := entries[i]
		if !from.IsZero() && e.date.Before(from) {
			continue
		}
		if !to.IsZero() && e.date.After(to) {
			continue
		}
		rangeBilled += e.billed
		rangeCollected += e.collected
		switch typ {
		case "bill", "sale", "purchase":
			if !e.isBill {
				continue
			}
		case "payment":
			if e.Type != "payment" {
				continue
			}
		}
		if withPhotos && e.PhotoCount == 0 {
			continue
		}
		out = append(out, e)
	}

	return ledgerResponse{
		Party:        party,
		Summary:      summary,
		RangeSummary: rangeSummary{Billed: formatPaise(rangeBilled), Collected: formatPaise(rangeCollected)},
		Entries:      out,
	}, nil
}

func (h *LedgerHandler) photoCounts(r *http.Request, companyID int32, entityType string, ids []int32) (map[int32]int32, error) {
	counts := map[int32]int32{}
	if len(ids) == 0 {
		return counts, nil
	}
	rows, err := h.Queries.AttachmentCountsByEntity(r.Context(), db.AttachmentCountsByEntityParams{
		CompanyID: companyID, EntityType: entityType, EntityIds: ids,
	})
	if err != nil {
		return nil, err
	}
	for _, row := range rows {
		counts[row.EntityID] = row.PhotoCount
	}
	return counts, nil
}

func (h *LedgerHandler) paymentEntries(r *http.Request, companyID int32, partyType string, partyID int32) ([]ledgerEntry, error) {
	payments, err := h.Queries.ListPaymentsByParty(r.Context(), db.ListPaymentsByPartyParams{
		CompanyID: companyID, PartyType: partyType, PartyID: partyID,
	})
	if err != nil {
		return nil, err
	}
	ids := make([]int32, 0, len(payments))
	for _, p := range payments {
		ids = append(ids, p.ID)
	}
	counts, err := h.photoCounts(r, companyID, "payment", ids)
	if err != nil {
		return nil, err
	}
	entries := make([]ledgerEntry, 0, len(payments))
	for _, p := range payments {
		amount := toPaise(p.Amount)
		entries = append(entries, ledgerEntry{
			Type: "payment", ID: p.ID, Date: p.PaymentDate.Time.Format(dateLayout),
			Paid: strPtr(formatPaise(amount)), Mode: strPtr(p.PaymentMode), Notes: textPtr(p.Notes),
			PhotoCount: counts[p.ID],
			date:       p.PaymentDate.Time, createdAt: p.CreatedAt.Time,
			delta: -amount, collected: amount,
		})
	}
	return entries, nil
}

func (h *LedgerHandler) CustomerLedger(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	shop, err := h.Queries.GetShop(r.Context(), db.GetShopParams{ID: int32(id), CompanyID: companyID})
	if errors.Is(err, pgx.ErrNoRows) {
		http.Error(w, "customer not found", http.StatusNotFound)
		return
	} else if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	opening := toPaise(shop.OpeningBalance)
	entries := []ledgerEntry{{
		Type: "opening", ID: shop.ID, Date: shop.CreatedAt.Time.Format(dateLayout),
		Amount: strPtr(formatPaise(opening)),
		date:   shop.CreatedAt.Time, createdAt: shop.CreatedAt.Time,
		delta: opening,
	}}

	sales, err := h.Queries.ListSalesByShop(r.Context(), db.ListSalesByShopParams{CompanyID: companyID, ShopID: shop.ID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	saleIDs := make([]int32, 0, len(sales))
	for _, s := range sales {
		saleIDs = append(saleIDs, s.ID)
	}
	counts, err := h.photoCounts(r, companyID, "sale", saleIDs)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	for _, s := range sales {
		total, paid := toPaise(s.TotalAmount), toPaise(s.AmountPaid)
		e := ledgerEntry{
			Type: "sale", ID: s.ID, Date: s.SaleDate.Time.Format(dateLayout), Ref: strPtr(s.BillNumber),
			Amount: strPtr(formatPaise(total)), Paid: strPtr(formatPaise(paid)),
			Cancelled: s.Status == "CANCELLED", PhotoCount: counts[s.ID], ItemCount: s.ItemCount,
			date: s.SaleDate.Time, createdAt: s.CreatedAt.Time, isBill: true,
		}
		if !e.Cancelled {
			e.delta, e.billed, e.collected = total-paid, total, paid
		}
		entries = append(entries, e)
	}

	payments, err := h.paymentEntries(r, companyID, "shop", shop.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	entries = append(entries, payments...)

	resp, err := buildLedger(r, ledgerParty{
		ID: shop.ID, Name: shop.Name, ContactName: textPtr(shop.OwnerName), Phone: shop.PrimaryPhone,
		SecondaryPhone: textPtr(shop.SecondaryPhone), Area: textPtr(shop.Area),
	}, entries)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	writeJSON(w, resp)
}

func (h *LedgerHandler) SupplierLedger(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	factory, err := h.Queries.GetFactory(r.Context(), db.GetFactoryParams{ID: int32(id), CompanyID: companyID})
	if errors.Is(err, pgx.ErrNoRows) {
		http.Error(w, "supplier not found", http.StatusNotFound)
		return
	} else if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	purchases, err := h.Queries.ListPurchasesByFactory(r.Context(), db.ListPurchasesByFactoryParams{CompanyID: companyID, FactoryID: factory.ID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	purchaseIDs := make([]int32, 0, len(purchases))
	for _, p := range purchases {
		purchaseIDs = append(purchaseIDs, p.ID)
	}
	counts, err := h.photoCounts(r, companyID, "purchase", purchaseIDs)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	entries := make([]ledgerEntry, 0, len(purchases))
	for _, p := range purchases {
		total, paid := toPaise(p.TotalAmount), toPaise(p.AmountPaid)
		e := ledgerEntry{
			Type: "purchase", ID: p.ID, Date: p.PurchaseDate.Time.Format(dateLayout), Ref: strPtr(p.BillNumber),
			InvoiceNo: textPtr(p.InvoiceNo),
			Amount:    strPtr(formatPaise(total)), Paid: strPtr(formatPaise(paid)),
			Cancelled: p.Status == "CANCELLED", PhotoCount: counts[p.ID], ItemCount: p.ItemCount,
			date: p.PurchaseDate.Time, createdAt: p.CreatedAt.Time, isBill: true,
		}
		if !e.Cancelled {
			e.delta, e.billed, e.collected = total-paid, total, paid
		}
		entries = append(entries, e)
	}

	payments, err := h.paymentEntries(r, companyID, "factory", factory.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	entries = append(entries, payments...)

	resp, err := buildLedger(r, ledgerParty{
		ID: factory.ID, Name: factory.Name, ContactName: textPtr(factory.ContactPerson), Phone: factory.PrimaryPhone,
		SecondaryPhone: textPtr(factory.SecondaryPhone), Area: textPtr(factory.Address),
	}, entries)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	writeJSON(w, resp)
}
