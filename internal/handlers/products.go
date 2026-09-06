package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type ProductHandler struct {
	Queries *db.Queries
}

func NewProductHandler(q *db.Queries) *ProductHandler {
	return &ProductHandler{Queries: q}
}

type productInput struct {
	Name          string  `json:"name"`
	SKU           string  `json:"sku"`
	Unit          string  `json:"unit"`
	CategoryID    int32   `json:"category_id"`
	SubcategoryID *int32  `json:"subcategory_id"`
	SellingPrice  float64 `json:"selling_price"`
}

func numericFromFloat(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	n.Scan(strconv.FormatFloat(f, 'f', 2, 64))
	return n
}

func int4OrNil(v *int32) pgtype.Int4 {
	if v == nil {
		return pgtype.Int4{Valid: false}
	}
	return pgtype.Int4{Int32: *v, Valid: true}
}

func (h *ProductHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	products, err := h.Queries.ListProducts(r.Context(), companyID)
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
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	product, err := h.Queries.GetProduct(r.Context(), db.GetProductParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, product)
}

// validateProductRefs ensures the unit exists and the category/subcategory belong to this
// company (and to each other), so a request can never point a product at another company's
// category or an unknown unit.
func (h *ProductHandler) validateProductRefs(r *http.Request, companyID int32, in productInput) error {
	if _, err := h.Queries.GetUnit(r.Context(), in.Unit); err != nil {
		return errInvalidUnit
	}
	if _, err := h.Queries.GetCategory(r.Context(), db.GetCategoryParams{ID: in.CategoryID, CompanyID: companyID}); err != nil {
		return errInvalidCategory
	}
	if in.SubcategoryID != nil {
		sub, err := h.Queries.GetSubcategory(r.Context(), db.GetSubcategoryParams{ID: *in.SubcategoryID, CompanyID: companyID})
		if err != nil || sub.CategoryID != in.CategoryID {
			return errInvalidSubcategory
		}
	}
	return nil
}

func (h *ProductHandler) Create(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in productInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Name == "" || in.SKU == "" || in.Unit == "" || in.CategoryID == 0 {
		http.Error(w, "name, sku, unit and category are required", http.StatusBadRequest)
		return
	}
	if err := h.validateProductRefs(r, companyID, in); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	product, err := h.Queries.CreateProduct(r.Context(), db.CreateProductParams{
		CompanyID:           companyID,
		Name:                in.Name,
		Sku:                 in.SKU,
		Unit:                in.Unit,
		CategoryID:          in.CategoryID,
		SubcategoryID:       int4OrNil(in.SubcategoryID),
		CurrentSellingPrice: numericFromFloat(in.SellingPrice),
	})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a product with this SKU already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, product)
}

func (h *ProductHandler) Update(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
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
	if err := h.validateProductRefs(r, companyID, in); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	product, err := h.Queries.UpdateProduct(r.Context(), db.UpdateProductParams{
		ID:                  int32(id),
		CompanyID:           companyID,
		Name:                in.Name,
		Sku:                 in.SKU,
		Unit:                in.Unit,
		CategoryID:          in.CategoryID,
		SubcategoryID:       int4OrNil(in.SubcategoryID),
		CurrentSellingPrice: numericFromFloat(in.SellingPrice),
	})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a product with this SKU already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, product)
}

func (h *ProductHandler) Delete(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.Queries.DeleteProduct(r.Context(), db.DeleteProductParams{ID: int32(id), CompanyID: companyID}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// CurrentCost reports the cost of the oldest active cost layer for a product, so the frontend
// can show an inline below-cost warning before submitting a sale. The backend sale-creation
// path is the actual source of truth and re-derives cost via FIFO at submit time.
func (h *ProductHandler) CurrentCost(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	layers, err := h.Queries.ListActiveCostLayersForUpdate(r.Context(), db.ListActiveCostLayersForUpdateParams{
		CompanyID: companyID,
		ProductID: int32(id),
	})
	if err != nil && err != pgx.ErrNoRows {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if len(layers) == 0 {
		writeJSON(w, map[string]interface{}{"cost": nil})
		return
	}
	writeJSON(w, map[string]interface{}{"cost": layers[0].BuyingPrice})
}

func writeJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}
