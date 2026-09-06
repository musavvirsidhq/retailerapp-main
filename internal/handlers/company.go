package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
	"github.com/Sivanandha02/retailapp/internal/subscription"
)

type CompanyHandler struct {
	Queries *db.Queries
}

func NewCompanyHandler(q *db.Queries) *CompanyHandler {
	return &CompanyHandler{Queries: q}
}

func (h *CompanyHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	users, err := h.Queries.ListCompanyUsers(r.Context(), pgInt4Valid(companyID))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if users == nil {
		users = []db.User{}
	}
	writeJSON(w, redactUsers(users))
}

type createStaffInput struct {
	Name                  string `json:"name"`
	Username              string `json:"username"`
	Password              string `json:"password"`
	PurchaseAccess        bool   `json:"purchase_access"`
	SalesAccess           bool   `json:"sales_access"`
	SalesBelowCostApprove bool   `json:"sales_below_cost_approve"`
}

func (h *CompanyHandler) CreateUser(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in createStaffInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Name == "" || in.Username == "" || in.Password == "" {
		http.Error(w, "name, username and password are required", http.StatusBadRequest)
		return
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(in.Password), bcrypt.DefaultCost)
	if err != nil {
		http.Error(w, "failed to hash password", http.StatusInternalServerError)
		return
	}
	user, err := h.Queries.CreateUser(r.Context(), db.CreateUserParams{
		CompanyID:             pgInt4Valid(companyID),
		Name:                  in.Name,
		Username:              in.Username,
		PasswordHash:          string(hash),
		UserType:              appMiddleware.UserTypeStaff,
		PurchaseAccess:        in.PurchaseAccess,
		SalesAccess:           in.SalesAccess,
		SalesBelowCostApprove: in.SalesBelowCostApprove,
	})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a user with this username already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, redactUser(user))
}

type updatePermissionsInput struct {
	PurchaseAccess        bool `json:"purchaseAccess"`
	SalesAccess           bool `json:"salesAccess"`
	SalesBelowCostApprove bool `json:"salesBelowCostApprove"`
}

func (h *CompanyHandler) UpdatePermissions(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var in updatePermissionsInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	user, err := h.Queries.UpdateUserPermissions(r.Context(), db.UpdateUserPermissionsParams{
		ID:                    int32(id),
		CompanyID:             pgInt4Valid(companyID),
		PurchaseAccess:        in.PurchaseAccess,
		SalesAccess:           in.SalesAccess,
		SalesBelowCostApprove: in.SalesBelowCostApprove,
	})
	if err != nil {
		http.Error(w, "user not found", http.StatusNotFound)
		return
	}
	writeJSON(w, redactUser(user))
}

func (h *CompanyHandler) DisableUser(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	user, err := h.Queries.UpdateUserStatus(r.Context(), db.UpdateUserStatusParams{
		ID:        int32(id),
		CompanyID: pgInt4Valid(companyID),
		Status:    "DISABLED",
	})
	if err != nil {
		http.Error(w, "user not found", http.StatusNotFound)
		return
	}
	writeJSON(w, redactUser(user))
}

// SubscriptionStatus reports the calling company's current subscription status/expiry so the
// frontend can render the renewal warning banner. Backed by ComputeCompanySubscriptionStatus,
// the same logic RequireActiveSubscription uses to gate writes.
func (h *CompanyHandler) SubscriptionStatus(w http.ResponseWriter, r *http.Request) {
	companyID, ok := appMiddleware.CompanyIDFromContext(r.Context())
	if !ok {
		http.Error(w, "no company associated with this user", http.StatusForbidden)
		return
	}
	sub, err := h.Queries.GetLatestSubscriptionByCompany(r.Context(), companyID)
	if err != nil {
		writeJSON(w, map[string]interface{}{"status": "NONE"})
		return
	}
	now := time.Now()
	status := subscription.ComputeStatus(sub.Status, sub.ExpiryDate.Time, now)
	days := subscription.DaysRemaining(sub.ExpiryDate.Time, now)
	writeJSON(w, map[string]interface{}{
		"status":            status,
		"expiry_date":       sub.ExpiryDate.Time.Format("2006-01-02"),
		"days_remaining":    days,
		"warning_level":     subscription.WarningLevel(status, days),
		"subscription_type": sub.SubscriptionType,
	})
}

func redactUser(u db.User) map[string]interface{} {
	return map[string]interface{}{
		"id":                       u.ID,
		"name":                     u.Name,
		"username":                 u.Username,
		"user_type":                u.UserType,
		"purchase_access":          u.PurchaseAccess,
		"sales_access":             u.SalesAccess,
		"sales_below_cost_approve": u.SalesBelowCostApprove,
		"status":                   u.Status,
	}
}

func redactUsers(users []db.User) []map[string]interface{} {
	out := make([]map[string]interface{}, len(users))
	for i, u := range users {
		out[i] = redactUser(u)
	}
	return out
}
