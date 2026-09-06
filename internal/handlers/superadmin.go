package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
	"github.com/Sivanandha02/retailapp/internal/subscription"
)

type SuperAdminHandler struct {
	Queries *db.Queries
	Pool    *pgxpool.Pool
}

func NewSuperAdminHandler(q *db.Queries, pool *pgxpool.Pool) *SuperAdminHandler {
	return &SuperAdminHandler{Queries: q, Pool: pool}
}

func dateOf(t time.Time) pgtype.Date {
	return pgtype.Date{Time: t, Valid: true}
}

type createCompanyInput struct {
	CompanyName   string `json:"company_name"`
	CompanyCode   string `json:"company_code"`
	AdminName     string `json:"admin_name"`
	AdminUsername string `json:"admin_username"`
	AdminPassword string `json:"admin_password"`
}

// CreateCompany creates a company together with its first Company Admin user in one
// transaction - a company with no admin has no one who could ever log in and manage it.
func (h *SuperAdminHandler) CreateCompany(w http.ResponseWriter, r *http.Request) {
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	var in createCompanyInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.CompanyName == "" || in.CompanyCode == "" || in.AdminUsername == "" || in.AdminPassword == "" {
		http.Error(w, "company_name, company_code, admin_username and admin_password are required", http.StatusBadRequest)
		return
	}

	ctx := r.Context()
	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)
	qtx := h.Queries.WithTx(tx)

	company, err := qtx.CreateCompany(ctx, db.CreateCompanyParams{
		CompanyName: in.CompanyName,
		CompanyCode: in.CompanyCode,
		JoiningDate: dateOf(time.Now()),
		CreatedBy:   pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a company with this code already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(in.AdminPassword), bcrypt.DefaultCost)
	if err != nil {
		http.Error(w, "failed to hash password", http.StatusInternalServerError)
		return
	}
	adminName := in.AdminName
	if adminName == "" {
		adminName = in.CompanyName + " Admin"
	}
	if _, err := qtx.CreateUser(ctx, db.CreateUserParams{
		CompanyID:    pgtype.Int4{Int32: company.ID, Valid: true},
		Name:         adminName,
		Username:     in.AdminUsername,
		PasswordHash: string(hash),
		UserType:     appMiddleware.UserTypeCompanyAdmin,
	}); err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a user with this username already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, company)
}

func (h *SuperAdminHandler) ListCompanies(w http.ResponseWriter, r *http.Request) {
	companies, err := h.Queries.ListCompanies(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if companies == nil {
		companies = []db.Company{}
	}

	type companyView struct {
		db.Company
		SubscriptionType string  `json:"subscription_type,omitempty"`
		ExpiryDate       *string `json:"expiry_date,omitempty"`
		Status           string  `json:"subscription_status,omitempty"`
	}
	out := make([]companyView, 0, len(companies))
	for _, c := range companies {
		view := companyView{Company: c}
		sub, err := h.Queries.GetLatestSubscriptionByCompany(r.Context(), c.ID)
		if err == nil {
			view.SubscriptionType = sub.SubscriptionType
			expiry := sub.ExpiryDate.Time.Format("2006-01-02")
			view.ExpiryDate = &expiry
			view.Status = subscription.ComputeStatus(sub.Status, sub.ExpiryDate.Time, time.Now())
		}
		out = append(out, view)
	}
	writeJSON(w, out)
}

func (h *SuperAdminHandler) GetCompany(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	company, err := h.Queries.GetCompanyByID(r.Context(), int32(id))
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, company)
}

func (h *SuperAdminHandler) GetSubscription(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	subs, err := h.Queries.ListSubscriptionsByCompany(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if subs == nil {
		subs = []db.Subscription{}
	}
	writeJSON(w, subs)
}

// GrantTrial gives a company a one-month trial starting today.
func (h *SuperAdminHandler) GrantTrial(w http.ResponseWriter, r *http.Request) {
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	now := time.Now()
	expiry := now.AddDate(0, 1, 0)
	sub, err := h.Queries.CreateSubscription(r.Context(), db.CreateSubscriptionParams{
		CompanyID:        int32(id),
		SubscriptionType: "TRIAL",
		StartDate:        dateOf(now),
		ExpiryDate:       dateOf(expiry),
		Amount:           numericFromFloat(0),
		PurchaseDate:     dateOf(now),
		Status:           "TRIAL",
		GrantedBy:        pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, sub)
}

type grantSubscriptionInput struct {
	SubscriptionType string  `json:"subscription_type"` // ANNUAL, EXTENDED
	Amount           float64 `json:"amount"`
	Months           int     `json:"months"`
}

// GrantSubscription activates a paid subscription starting today for the given duration.
func (h *SuperAdminHandler) GrantSubscription(w http.ResponseWriter, r *http.Request) {
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var in grantSubscriptionInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.SubscriptionType == "" {
		in.SubscriptionType = "ANNUAL"
	}
	months := in.Months
	if months == 0 {
		months = 12
	}
	now := time.Now()
	expiry := now.AddDate(0, months, 0)
	sub, err := h.Queries.CreateSubscription(r.Context(), db.CreateSubscriptionParams{
		CompanyID:        int32(id),
		SubscriptionType: in.SubscriptionType,
		StartDate:        dateOf(now),
		ExpiryDate:       dateOf(expiry),
		Amount:           numericFromFloat(in.Amount),
		PurchaseDate:     dateOf(now),
		Status:           "ACTIVE",
		GrantedBy:        pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, sub)
}

type extendSubscriptionInput struct {
	Months int     `json:"months"`
	Days   int     `json:"days"`
	Amount float64 `json:"amount"`
}

// ExtendSubscription adds time on top of the existing expiry if the company is still active,
// or starts fresh from now if it has already expired - no remaining subscription time is lost.
func (h *SuperAdminHandler) ExtendSubscription(w http.ResponseWriter, r *http.Request) {
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var in extendSubscriptionInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	now := time.Now()
	latest, err := h.Queries.GetLatestSubscriptionByCompany(r.Context(), int32(id))
	currentExpiry := now
	if err == nil {
		currentExpiry = latest.ExpiryDate.Time
	}
	newExpiry := subscription.ExtendExpiry(currentExpiry, now, in.Months, in.Days)

	sub, err := h.Queries.CreateSubscription(r.Context(), db.CreateSubscriptionParams{
		CompanyID:        int32(id),
		SubscriptionType: "EXTENDED",
		StartDate:        dateOf(now),
		ExpiryDate:       dateOf(newExpiry),
		Amount:           numericFromFloat(in.Amount),
		PurchaseDate:     dateOf(now),
		Status:           "ACTIVE",
		GrantedBy:        pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, sub)
}
