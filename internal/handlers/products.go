package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
)

type ProductHandler struct {
	Queries *db.Queries
}

func NewProductHandler(q *db.Queries) *ProductHandler {
	return &ProductHandler{Queries: q}
}

type productInput struct {
	Name          string  `json:"name"`
	Unit          string  `json:"unit"`
	PurchasePrice float64 `json:"purchase_price"`
	SellingPrice  float64 `json:"selling_price"`
}

func numericFromFloat(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	n.Scan(strconv.FormatFloat(f, 'f', 2, 64))
	return n
}

func (h *ProductHandler) List(w http.ResponseWriter, r *http.Request) {
	products, err := h.Queries.ListProducts(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if products == nil {
		products = []db.Product{}
	}
	writeJSON(w, products)
}

func (h *ProductHandler) Get(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	product, err := h.Queries.GetProduct(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	writeJSON(w, product)
}

func (h *ProductHandler) Create(w http.ResponseWriter, r *http.Request) {
	var in productInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	product, err := h.Queries.CreateProduct(r.Context(), db.CreateProductParams{
		Name:          in.Name,
		Unit:          in.Unit,
		PurchasePrice: numericFromFloat(in.PurchasePrice),
		SellingPrice:  numericFromFloat(in.SellingPrice),
		CurrentStock:  numericFromFloat(0),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, product)
}

func (h *ProductHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}

	var in productInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	product, err := h.Queries.UpdateProduct(r.Context(), db.UpdateProductParams{
		ID:            int32(id),
		Name:          in.Name,
		Unit:          in.Unit,
		PurchasePrice: numericFromFloat(in.PurchasePrice),
		SellingPrice:  numericFromFloat(in.SellingPrice),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, product)
}

func (h *ProductHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.Queries.DeleteProduct(r.Context(), int32(id)); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func writeJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}
