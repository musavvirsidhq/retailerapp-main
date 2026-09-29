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
	// Collecting from a customer is a sales action and paying a supplier a purchase action,
	// so staff need the matching permission (Cycle 4 section 14).
	if in.PartyType == "shop" && !appMiddleware.HasSalesAccess(r.Context()) {
		http.Error(w, "forbidden: sales access required", http.StatusForbidden)
		return
	}
	if in.PartyType == "factory" && !appMiddleware.HasPurchaseAccess(r.Context()) {
		http.Error(w, "forbidden: purchase access required", http.StatusForbidden)
		return
	}
	if in.Amount <= 0 {
		http.Error(w, "amount must be greater than zero", http.StatusBadRequest)
		return
	}
	// party_id has no foreign key (it points at shops or factories), so check it here to keep
	// a payment from being recorded against another company's party.
	if in.PartyType == "shop" {
		if _, err := h.Queries.GetShop(r.Context(), db.GetShopParams{ID: in.PartyID, CompanyID: companyID}); err != nil {
			http.Error(w, "customer not found", http.StatusBadRequest)
			return
		}
	} else if _, err := h.Queries.GetFactory(r.Context(), db.GetFactoryParams{ID: in.PartyID, CompanyID: companyID}); err != nil {
		http.Error(w, "supplier not found", http.StatusBadRequest)
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

type paymentDetail struct {
	db.Payment
	PartyName string
}

// Get returns one payment plus its party's name, for the Android payment detail screen.
func (h *PaymentHandler) Get(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	p, err := h.Queries.GetPayment(ctx, db.GetPaymentParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "payment not found", http.StatusNotFound)
		return
	}
	if (p.PartyType == "shop" && !appMiddleware.HasSalesAccess(ctx)) ||
		(p.PartyType == "factory" && !appMiddleware.HasPurchaseAccess(ctx)) {
		http.Error(w, "forbidden", http.StatusForbidden)
		return
	}
	name := ""
	if p.PartyType == "shop" {
		if s, err := h.Queries.GetShop(ctx, db.GetShopParams{ID: p.PartyID, CompanyID: companyID}); err == nil {
			name = s.Name
		}
	} else if f, err := h.Queries.GetFactory(ctx, db.GetFactoryParams{ID: p.PartyID, CompanyID: companyID}); err == nil {
		name = f.Name
	}
	writeJSON(w, paymentDetail{Payment: p, PartyName: name})
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
