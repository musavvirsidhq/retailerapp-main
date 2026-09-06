package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type PaymentHandler struct {
	Queries *db.Queries
}

func NewPaymentHandler(q *db.Queries) *PaymentHandler {
	return &PaymentHandler{Queries: q}
}

type paymentInput struct {
	PartyType   string  `json:"party_type"` // "shop" or "factory"
	PartyID     int32   `json:"party_id"`
	Amount      float64 `json:"amount"`
	PaymentMode string  `json:"payment_mode"` // cash, upi, cheque
	Notes       string  `json:"notes"`
}

func (h *PaymentHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	payments, err := h.Queries.ListPayments(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if payments == nil {
		payments = []db.Payment{}
	}
	writeJSON(w, payments)
}

func (h *PaymentHandler) Create(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	var in paymentInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.PartyType != "shop" && in.PartyType != "factory" {
		http.Error(w, "party_type must be 'shop' or 'factory'", http.StatusBadRequest)
		return
	}

	payment, err := h.Queries.CreatePayment(r.Context(), db.CreatePaymentParams{
		CompanyID:   companyID,
		PartyType:   in.PartyType,
		PartyID:     in.PartyID,
		Amount:      numericFromFloat(in.Amount),
		PaymentMode: in.PaymentMode,
		Notes:       pgTextOrNil(in.Notes),
		CreatedBy:   pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, payment)
}

func (h *PaymentHandler) ShopBalance(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	balance, err := h.Queries.ShopBalance(r.Context(), db.ShopBalanceParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, map[string]interface{}{"shop_id": id, "balance": balance})
}

func (h *PaymentHandler) FactoryBalance(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	balance, err := h.Queries.FactoryBalance(r.Context(), db.FactoryBalanceParams{FactoryID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, map[string]interface{}{"factory_id": id, "balance": balance})
}
