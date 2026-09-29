package handlers

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// QuickItemsHandler serves the Cycle 4 "most-used items" chip strip on the Android sale and
// purchase forms, plus the Company Admin's pinned items that always lead that strip.
type QuickItemsHandler struct {
	Queries *db.Queries
}

func NewQuickItemsHandler(q *db.Queries) *QuickItemsHandler {
	return &QuickItemsHandler{Queries: q}
}

const maxPinnedProducts = 10

type quickItem struct {
	ID                  int32          `json:"id"`
	Name                string         `json:"name"`
	Sku                 string         `json:"sku"`
	Unit                string         `json:"unit"`
	CurrentSellingPrice pgtype.Numeric `json:"current_selling_price"`
	CurrentStock        pgtype.Numeric `json:"current_stock"`
	Pinned              bool           `json:"pinned"`
	Uses                int32          `json:"uses"`
}

func queryLimit(r *http.Request, def, max int) int32 {
	n, err := strconv.Atoi(r.URL.Query().Get("limit"))
	if err != nil || n <= 0 {
		return int32(def)
	}
	if n > max {
		n = max
	}
	return int32(n)
}

// Frequent handles GET /api/products/frequent?type=sale|purchase&limit=10, and the
// customer-specific "usually buys" list with ?type=sale&shop_id=N.
func (h *QuickItemsHandler) Frequent(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	limit := queryLimit(r, 10, 30)
	out := []quickItem{}

	switch r.URL.Query().Get("type") {
	case "purchase":
		rows, err := h.Queries.FrequentPurchaseProducts(ctx, db.FrequentPurchaseProductsParams{CompanyID: companyID, Limit: limit})
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		for _, p := range rows {
			out = append(out, quickItem(p))
		}
	case "sale", "":
		if s := r.URL.Query().Get("shop_id"); s != "" {
			shopID, err := strconv.Atoi(s)
			if err != nil {
				http.Error(w, "invalid shop_id", http.StatusBadRequest)
				return
			}
			rows, err := h.Queries.CustomerUsualProducts(ctx, db.CustomerUsualProductsParams{
				CompanyID: companyID, ShopID: int32(shopID), Limit: queryLimit(r, 5, 30),
			})
			if err != nil {
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}
			for _, p := range rows {
				out = append(out, quickItem(p))
			}
			break
		}
		rows, err := h.Queries.FrequentSaleProducts(ctx, db.FrequentSaleProductsParams{CompanyID: companyID, Limit: limit})
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		for _, p := range rows {
			out = append(out, quickItem(p))
		}
	default:
		http.Error(w, "type must be 'sale' or 'purchase'", http.StatusBadRequest)
		return
	}
	writeJSON(w, out)
}

func (h *QuickItemsHandler) setPinned(w http.ResponseWriter, r *http.Request, pinned bool) {
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if pinned {
		product, err := h.Queries.GetProduct(ctx, db.GetProductParams{ID: int32(id), CompanyID: companyID})
		if err != nil {
			http.Error(w, "product not found", http.StatusNotFound)
			return
		}
		if !product.Pinned {
			count, err := h.Queries.CountPinnedProducts(ctx, companyID)
			if err != nil {
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}
			if count >= maxPinnedProducts {
				http.Error(w, "you can pin at most 10 items - unpin one first", http.StatusBadRequest)
				return
			}
		}
	}
	n, err := h.Queries.SetProductPinned(ctx, db.SetProductPinnedParams{ID: int32(id), CompanyID: companyID, Pinned: pinned})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if n == 0 {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *QuickItemsHandler) Pin(w http.ResponseWriter, r *http.Request)   { h.setPinned(w, r, true) }
func (h *QuickItemsHandler) Unpin(w http.ResponseWriter, r *http.Request) { h.setPinned(w, r, false) }
