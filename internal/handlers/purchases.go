package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
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
	FactoryID       int32               `json:"factory_id"`
	InvoiceNo       string              `json:"invoice_no"`
	AmountPaid      float64             `json:"amount_paid"`
	Items           []purchaseItemInput `json:"items"`
	NewSellingPrice map[int32]float64   `json:"new_selling_price"` // optional: product_id -> new current_selling_price
}

func (h *PurchaseHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	purchases, err := h.Queries.ListPurchases(r.Context(), companyID)
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
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	userID, _ := appMiddleware.UserIDFromContext(r.Context())

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

	var total float64
	for _, item := range in.Items {
		total += item.Quantity * item.UnitPrice
	}

	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)

	qtx := h.Queries.WithTx(tx)

	billNumber, err := qtx.NextBillNumber(ctx, db.NextBillNumberParams{CompanyID: companyID, BillType: "PURCHASE"})
	if err != nil {
		http.Error(w, "failed to allocate bill number: "+err.Error(), http.StatusInternalServerError)
		return
	}

	purchase, err := qtx.CreatePurchase(ctx, db.CreatePurchaseParams{
		CompanyID:   companyID,
		BillNumber:  fmt.Sprintf("PB-%d-%d", companyID, billNumber),
		FactoryID:   in.FactoryID,
		InvoiceNo:   pgTextOrNil(in.InvoiceNo),
		TotalAmount: numericFromFloat(total),
		AmountPaid:  numericFromFloat(in.AmountPaid),
		CreatedBy:   pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, "failed to create purchase: "+err.Error(), http.StatusInternalServerError)
		return
	}

	for _, item := range in.Items {
		product, err := qtx.GetProduct(ctx, db.GetProductParams{ID: item.ProductID, CompanyID: companyID})
		if err != nil {
			http.Error(w, "product not found", http.StatusBadRequest)
			return
		}

		lineTotal := item.Quantity * item.UnitPrice
		purchaseItem, err := qtx.CreatePurchaseItem(ctx, db.CreatePurchaseItemParams{
			PurchaseID: purchase.ID,
			ProductID:  item.ProductID,
			Unit:       product.Unit,
			Quantity:   numericFromFloat(item.Quantity),
			UnitPrice:  numericFromFloat(item.UnitPrice),
			LineTotal:  numericFromFloat(lineTotal),
		})
		if err != nil {
			http.Error(w, "failed to create purchase item: "+err.Error(), http.StatusInternalServerError)
			return
		}

		if err := createCostLayerForPurchaseItem(ctx, qtx, companyID, item.ProductID, purchaseItem.ID, item.UnitPrice, item.Quantity, userID); err != nil {
			http.Error(w, "failed to record cost layer: "+err.Error(), http.StatusInternalServerError)
			return
		}

		if err := qtx.IncrementProductStock(ctx, db.IncrementProductStockParams{
			ID:           item.ProductID,
			CurrentStock: numericFromFloat(item.Quantity),
		}); err != nil {
			http.Error(w, "failed to update stock: "+err.Error(), http.StatusInternalServerError)
			return
		}

		// A new purchase may set a new default selling price. This only affects future sales -
		// past sale_items already carry the price actually charged at the time.
		if newPrice, ok := in.NewSellingPrice[item.ProductID]; ok {
			if _, err := qtx.UpdateProductSellingPrice(ctx, db.UpdateProductSellingPriceParams{
				ID:                  item.ProductID,
				CompanyID:           companyID,
				CurrentSellingPrice: numericFromFloat(newPrice),
			}); err != nil {
				http.Error(w, "failed to update selling price: "+err.Error(), http.StatusInternalServerError)
				return
			}
		}
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
	writeJSON(w, purchase)
}

func createCostLayerForPurchaseItem(ctx context.Context, qtx *db.Queries, companyID, productID, purchaseItemID int32, buyingPrice, quantity float64, userID int32) error {
	_, err := qtx.CreateCostLayer(ctx, db.CreateCostLayerParams{
		CompanyID:          companyID,
		ProductID:          productID,
		PurchaseBillItemID: purchaseItemID,
		BuyingPrice:        numericFromFloat(buyingPrice),
		QuantityReceived:   numericFromFloat(quantity),
		CreatedBy:          pgtype.Int4{Int32: userID, Valid: true},
	})
	return err
}

type cancelInput struct {
	Reason string `json:"reason"`
}

// Cancel reverses a purchase bill. It is only permitted while none of the purchase's cost-layer
// quantity has yet been consumed by a sale - if any of it has, the purchase cannot be safely
// un-received without also touching the sales that already drew from it, so the caller is asked
// to cancel those first.
func (h *PurchaseHandler) Cancel(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}

	var in cancelInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil || in.Reason == "" {
		http.Error(w, "a cancellation reason is required", http.StatusBadRequest)
		return
	}

	ctx := r.Context()
	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)
	qtx := h.Queries.WithTx(tx)

	items, err := qtx.ListPurchaseItems(ctx, int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if len(items) == 0 {
		http.Error(w, "purchase not found", http.StatusNotFound)
		return
	}

	type impact struct {
		ProductID int32   `json:"product_id"`
		Quantity  float64 `json:"quantity"`
	}
	var inventoryImpact []impact

	for _, item := range items {
		layer, err := qtx.GetCostLayerByPurchaseItem(ctx, item.ID)
		if err != nil {
			http.Error(w, "failed to load cost layer: "+err.Error(), http.StatusInternalServerError)
			return
		}
		received := numericToFloat(layer.QuantityReceived)
		remaining := numericToFloat(layer.QuantityRemaining)
		if remaining != received {
			http.Error(w, fmt.Sprintf("cannot cancel: %.3f units of %s have already been sold; cancel those sales first", received-remaining, item.ProductName), http.StatusConflict)
			return
		}
		if err := qtx.CancelCostLayer(ctx, layer.ID); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		if err := qtx.DecrementProductStockUnchecked(ctx, db.DecrementProductStockUncheckedParams{
			ID:           item.ProductID,
			CurrentStock: item.Quantity,
		}); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		inventoryImpact = append(inventoryImpact, impact{ProductID: item.ProductID, Quantity: -numericToFloat(item.Quantity)})
	}

	purchase, err := qtx.CancelPurchase(ctx, db.CancelPurchaseParams{
		ID:              int32(id),
		CompanyID:       companyID,
		CancelledReason: pgTextOrNil(in.Reason),
		CancelledBy:     pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, "purchase not found or already cancelled", http.StatusConflict)
		return
	}

	impactJSON, _ := json.Marshal(inventoryImpact)
	if _, err := qtx.CreateAuditLogEntry(ctx, db.CreateAuditLogEntryParams{
		CompanyID:       pgtype.Int4{Int32: companyID, Valid: true},
		ActorUserID:     pgtype.Int4{Int32: userID, Valid: true},
		Action:          "PURCHASE_CANCEL",
		EntityType:      "purchase",
		EntityID:        purchase.ID,
		Reason:          pgTextOrNil(in.Reason),
		InventoryImpact: impactJSON,
	}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}
	writeJSON(w, purchase)
}
