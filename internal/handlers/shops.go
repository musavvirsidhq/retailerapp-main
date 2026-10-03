package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type ShopHandler struct {
	Queries *db.Queries
}

func NewShopHandler(q *db.Queries) *ShopHandler {
	return &ShopHandler{Queries: q}
}

type shopInput struct {
	Name           string  `json:"name"`
	OwnerName      string  `json:"owner_name"`
	PrimaryPhone   string  `json:"primary_phone"`
	SecondaryPhone string  `json:"secondary_phone"`
	Area           string  `json:"area"`
	OpeningBalance float64 `json:"opening_balance"`
}

func (h *ShopHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var shops []db.Shop
	var err error
	if includeArchived(r) {
		shops, err = h.Queries.ListShopsIncludingArchived(r.Context(), companyID)
	} else {
		shops, err = h.Queries.ListShops(r.Context(), companyID)
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if shops == nil {
		shops = []db.Shop{}
	}
	writeJSON(w, shops)
}

func (h *ShopHandler) Get(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	shop, err := h.Queries.GetShop(r.Context(), db.GetShopParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, shop)
}

func (h *ShopHandler) Create(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in shopInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Name == "" {
		http.Error(w, "name is required", http.StatusBadRequest)
		return
	}
	if err := validatePhones(in.PrimaryPhone, in.SecondaryPhone); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	shop, err := h.Queries.CreateShop(r.Context(), db.CreateShopParams{
		CompanyID:      companyID,
		Name:           in.Name,
		OwnerName:      pgTextOrNil(in.OwnerName),
		PrimaryPhone:   in.PrimaryPhone,
		SecondaryPhone: pgTextOrNil(in.SecondaryPhone),
		Area:           pgTextOrNil(in.Area),
		OpeningBalance: numericFromFloat(in.OpeningBalance),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, shop)
}

func (h *ShopHandler) Update(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}

	var in shopInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if err := validatePhones(in.PrimaryPhone, in.SecondaryPhone); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	shop, err := h.Queries.UpdateShop(r.Context(), db.UpdateShopParams{
		ID:             int32(id),
		CompanyID:      companyID,
		Name:           in.Name,
		OwnerName:      pgTextOrNil(in.OwnerName),
		PrimaryPhone:   in.PrimaryPhone,
		SecondaryPhone: pgTextOrNil(in.SecondaryPhone),
		Area:           pgTextOrNil(in.Area),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, shop)
}

// Delete archives the customer (Cycle 5): hidden from lists, pickers and dues, but kept for
// old bills, payments and ledgers. Company Admin only, and only once nothing is owed either way.
func (h *ShopHandler) Delete(w http.ResponseWriter, r *http.Request) {
	if !requireCompanyAdmin(w, r) {
		return
	}
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	userID, _ := appMiddleware.UserIDFromContext(ctx)
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	shop, err := h.Queries.GetShop(ctx, db.GetShopParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "customer not found", http.StatusNotFound)
		return
	}
	if shop.ArchivedAt.Valid {
		http.Error(w, "customer is already archived", http.StatusConflict)
		return
	}
	balance, err := h.Queries.ShopBalance(ctx, db.ShopBalanceParams{ID: shop.ID, CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if p := toPaise(balance); p != 0 {
		http.Error(w, settleBeforeArchive(p), http.StatusBadRequest)
		return
	}
	if _, err := h.Queries.ArchiveShop(ctx, db.ArchiveShopParams{ID: shop.ID, CompanyID: companyID, ArchivedBy: pgInt4Valid(userID)}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if err := writeArchiveAudit(ctx, h.Queries, "SHOP_ARCHIVE", "shop", shop.ID, nil, map[string]string{"balance": formatPaise(0)}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// Restore un-archives a customer. Company Admin only.
func (h *ShopHandler) Restore(w http.ResponseWriter, r *http.Request) {
	if !requireCompanyAdmin(w, r) {
		return
	}
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	shop, err := h.Queries.RestoreShop(ctx, db.RestoreShopParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "customer not found or not archived", http.StatusNotFound)
		return
	}
	if err := writeArchiveAudit(ctx, h.Queries, "SHOP_RESTORE", "shop", shop.ID, nil, nil); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, shop)
}
