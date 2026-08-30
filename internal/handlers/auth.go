package handlers

import (
	"encoding/json"
	"net/http"

	"github.com/gorilla/sessions"
	"golang.org/x/crypto/bcrypt"

	"github.com/Sivanandha02/retailapp/internal/db"
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

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(in.Password)); err != nil {
		http.Error(w, "invalid username or password", http.StatusUnauthorized)
		return
	}

	session, _ := h.Store.Get(r, "retailapp_session")
	session.Values["user_id"] = user.ID
	session.Values["role"] = user.Role
	session.Values["name"] = user.Name
	if err := session.Save(r, w); err != nil {
		http.Error(w, "failed to create session", http.StatusInternalServerError)
		return
	}

	writeJSON(w, map[string]interface{}{
		"id":       user.ID,
		"name":     user.Name,
		"username": user.Username,
		"role":     user.Role,
	})
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
	session, _ := h.Store.Get(r, "retailapp_session")
	userID, ok := session.Values["user_id"]
	if !ok {
		http.Error(w, "not logged in", http.StatusUnauthorized)
		return
	}

	writeJSON(w, map[string]interface{}{
		"id":   userID,
		"name": session.Values["name"],
		"role": session.Values["role"],
	})
}
