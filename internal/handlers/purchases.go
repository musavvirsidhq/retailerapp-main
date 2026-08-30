package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
)

type PurchaseHandler struct {
	Queries *db.Queries
	Pool    *pgxpool.Pool
}

func NewPurchaseHandler(q *db.Queries, pool *pgxpool.Pool) *PurchaseHandler {
	return &PurchaseHandler{Queries: q, Pool: pool}
}

type purchaseItemInput struct {
	ProductID int32   `json:"product_id"`
	Quantity  float64 `json:"quantity"`
	UnitPrice float64 `json:"unit_price"`
}

type purchaseInput struct {
	FactoryID    int32               `json:"factory_id"`
	InvoiceNo    string              `json:"invoice_no"`
	AmountPaid   float64             `json:"amount_paid"`
	Items        []purchaseItemInput `json:"items"`
}

func (h *PurchaseHandler) List(w http.ResponseWriter, r *http.Request) {
	purchases, err := h.Queries.ListPurchases(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if purchases == nil {
		purchases = []db.ListPurchasesRow{}
	}
	writeJSON(w, purchases)
}

func (h *PurchaseHandler) GetItems(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	items, err := h.Queries.ListPurchaseItems(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if items == nil {
		items = []db.ListPurchaseItemsRow{}
	}
	writeJSON(w, items)
}

func (h *PurchaseHandler) Create(w http.ResponseWriter, r *http.Request) {
	var in purchaseInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if len(in.Items) == 0 {
		http.Error(w, "purchase must have at least one item", http.StatusBadRequest)
		return
	}

	ctx := r.Context()

	// Calculate total from items (never trust frontend-sent totals for money)
	var total float64
	for _, item := range in.Items {
		total += item.Quantity * item.UnitPrice
	}

	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx) // no-op if already committed

	qtx := h.Queries.WithTx(tx)

	purchase, err := qtx.CreatePurchase(ctx, db.CreatePurchaseParams{
		FactoryID:   in.FactoryID,
		InvoiceNo:   pgTextOrNil(in.InvoiceNo),
		TotalAmount: numericFromFloat(total),
		AmountPaid:  numericFromFloat(in.AmountPaid),
	})
	if err != nil {
		http.Error(w, "failed to create purchase: "+err.Error(), http.StatusInternalServerError)
		return
	}

	for _, item := range in.Items {
		lineTotal := item.Quantity * item.UnitPrice
		_, err := qtx.CreatePurchaseItem(ctx, db.CreatePurchaseItemParams{
			PurchaseID: purchase.ID,
			ProductID:  item.ProductID,
			Quantity:   numericFromFloat(item.Quantity),
			UnitPrice:  numericFromFloat(item.UnitPrice),
			LineTotal:  numericFromFloat(lineTotal),
		})
		if err != nil {
			http.Error(w, "failed to create purchase item: "+err.Error(), http.StatusInternalServerError)
			return
		}

		err = qtx.IncrementProductStock(ctx, db.IncrementProductStockParams{
			ID:           item.ProductID,
			CurrentStock: numericFromFloat(item.Quantity),
		})
		if err != nil {
			http.Error(w, "failed to update stock: "+err.Error(), http.StatusInternalServerError)
			return
		}
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
	writeJSON(w, purchase)
}
