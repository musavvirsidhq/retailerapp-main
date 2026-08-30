package handlers

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
)

type SaleHandler struct {
	Queries *db.Queries
	Pool    *pgxpool.Pool
}

func NewSaleHandler(q *db.Queries, pool *pgxpool.Pool) *SaleHandler {
	return &SaleHandler{Queries: q, Pool: pool}
}

type saleItemInput struct {
	ProductID int32   `json:"product_id"`
	Quantity  float64 `json:"quantity"`
	UnitPrice float64 `json:"unit_price"`
}

type saleInput struct {
	ShopID      int32           `json:"shop_id"`
	AmountPaid  float64         `json:"amount_paid"`
	PaymentType string          `json:"payment_type"` // "cash" or "credit"
	Items       []saleItemInput `json:"items"`
}

func (h *SaleHandler) List(w http.ResponseWriter, r *http.Request) {
	sales, err := h.Queries.ListSales(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if sales == nil {
		sales = []db.ListSalesRow{}
	}
	writeJSON(w, sales)
}

func (h *SaleHandler) GetItems(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	items, err := h.Queries.ListSaleItems(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if items == nil {
		items = []db.ListSaleItemsRow{}
	}
	writeJSON(w, items)
}

func (h *SaleHandler) Create(w http.ResponseWriter, r *http.Request) {
	var in saleInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if len(in.Items) == 0 {
		http.Error(w, "sale must have at least one item", http.StatusBadRequest)
		return
	}
	if in.PaymentType != "cash" && in.PaymentType != "credit" {
		http.Error(w, "payment_type must be 'cash' or 'credit'", http.StatusBadRequest)
		return
	}

	ctx := r.Context()

	var total float64
	for _, item := range in.Items {
		total += item.Quantity * item.UnitPrice
	}

	amountPaid := in.AmountPaid
	if in.PaymentType == "cash" {
		amountPaid = total // cash sale = fully paid
	}

	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)

	qtx := h.Queries.WithTx(tx)

	sale, err := qtx.CreateSale(ctx, db.CreateSaleParams{
		ShopID:      in.ShopID,
		TotalAmount: numericFromFloat(total),
		AmountPaid:  numericFromFloat(amountPaid),
		PaymentType: in.PaymentType,
	})
	if err != nil {
		http.Error(w, "failed to create sale: "+err.Error(), http.StatusInternalServerError)
		return
	}

	for _, item := range in.Items {
		lineTotal := item.Quantity * item.UnitPrice

		// Attempt to decrement stock; fails (ErrNoRows) if insufficient stock
		_, err := qtx.DecrementProductStock(ctx, db.DecrementProductStockParams{
			ID:           item.ProductID,
			CurrentStock: numericFromFloat(item.Quantity),
		})
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				http.Error(w, "insufficient stock for one or more products", http.StatusConflict)
				return
			}
			http.Error(w, "failed to update stock: "+err.Error(), http.StatusInternalServerError)
			return
		}

		_, err = qtx.CreateSaleItem(ctx, db.CreateSaleItemParams{
			SaleID:    sale.ID,
			ProductID: item.ProductID,
			Quantity:  numericFromFloat(item.Quantity),
			UnitPrice: numericFromFloat(item.UnitPrice),
			LineTotal: numericFromFloat(lineTotal),
		})
		if err != nil {
			http.Error(w, "failed to create sale item: "+err.Error(), http.StatusInternalServerError)
			return
		}
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
	writeJSON(w, sale)
}
