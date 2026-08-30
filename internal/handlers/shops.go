package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
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
	Phone          string  `json:"phone"`
	Area           string  `json:"area"`
	OpeningBalance float64 `json:"opening_balance"`
}

func (h *ShopHandler) List(w http.ResponseWriter, r *http.Request) {
	shops, err := h.Queries.ListShops(r.Context())
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
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	shop, err := h.Queries.GetShop(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	writeJSON(w, shop)
}

func (h *ShopHandler) Create(w http.ResponseWriter, r *http.Request) {
	var in shopInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	shop, err := h.Queries.CreateShop(r.Context(), db.CreateShopParams{
		Name:           in.Name,
		OwnerName:      pgTextOrNil(in.OwnerName),
		Phone:          pgTextOrNil(in.Phone),
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

	shop, err := h.Queries.UpdateShop(r.Context(), db.UpdateShopParams{
		ID:        int32(id),
		Name:      in.Name,
		OwnerName: pgTextOrNil(in.OwnerName),
		Phone:     pgTextOrNil(in.Phone),
		Area:      pgTextOrNil(in.Area),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, shop)
}

func (h *ShopHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.Queries.DeleteShop(r.Context(), int32(id)); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
