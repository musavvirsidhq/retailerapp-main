package handlers

import (
	"encoding/json"
	"net/http"

	"github.com/gorilla/sessions"
	"golang.org/x/crypto/bcrypt"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type AuthHandler struct {
	Queries *db.Queries
	Store   *sessions.CookieStore
}

func NewAuthHandler(q *db.Queries, store *sessions.CookieStore) *AuthHandler {
	return &AuthHandler{Queries: q, Store: store}
}

type loginInput struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var in loginInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	user, err := h.Queries.GetUserByUsername(r.Context(), in.Username)
	if err != nil {
		http.Error(w, "invalid username or password", http.StatusUnauthorized)
		return
	}

	if user.Status != "ACTIVE" {
		http.Error(w, "this account has been disabled", http.StatusForbidden)
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(in.Password)); err != nil {
		http.Error(w, "invalid username or password", http.StatusUnauthorized)
		return
	}

	session, _ := h.Store.Get(r, "retailapp_session")
	session.Values["user_id"] = user.ID
	session.Values["name"] = user.Name
	session.Values["user_type"] = user.UserType
	session.Values["purchase_access"] = user.PurchaseAccess
	session.Values["sales_access"] = user.SalesAccess
	session.Values["sales_below_cost_approve"] = user.SalesBelowCostApprove
	if user.CompanyID.Valid {
		session.Values["company_id"] = user.CompanyID.Int32
	} else {
		delete(session.Values, "company_id")
	}
	if err := session.Save(r, w); err != nil {
		http.Error(w, "failed to create session", http.StatusInternalServerError)
		return
	}

	writeJSON(w, h.userResponse(user))
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	session, _ := h.Store.Get(r, "retailapp_session")
	session.Options.MaxAge = -1 // deletes the cookie
	if err := session.Save(r, w); err != nil {
		http.Error(w, "failed to logout", http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusOK)
}

func (h *AuthHandler) Me(w http.ResponseWriter, r *http.Request) {
	userID, ok := appMiddleware.UserIDFromContext(r.Context())
	if !ok {
		http.Error(w, "not logged in", http.StatusUnauthorized)
		return
	}

	user, err := h.Queries.GetUser(r.Context(), userID)
	if err != nil {
		http.Error(w, "not logged in", http.StatusUnauthorized)
		return
	}

	writeJSON(w, h.userResponse(user))
}

func (h *AuthHandler) userResponse(user db.User) map[string]interface{} {
	resp := map[string]interface{}{
		"id":                       user.ID,
		"name":                     user.Name,
		"username":                 user.Username,
		"user_type":                user.UserType,
		"purchase_access":          user.PurchaseAccess,
		"sales_access":             user.SalesAccess,
		"sales_below_cost_approve": user.SalesBelowCostApprove,
		"company_id":               nil,
	}
	if user.CompanyID.Valid {
		resp["company_id"] = user.CompanyID.Int32
	}
	return resp
}
