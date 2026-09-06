package handlers

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
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
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	sales, err := h.Queries.ListSales(r.Context(), companyID)
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
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	canApproveBelowCost := appMiddleware.SalesBelowCostApproveFromContext(r.Context()) ||
		appMiddleware.UserTypeFromContext(r.Context()) == appMiddleware.UserTypeCompanyAdmin

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
		amountPaid = total
	}

	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)

	qtx := h.Queries.WithTx(tx)

	billNumber, err := qtx.NextBillNumber(ctx, db.NextBillNumberParams{CompanyID: companyID, BillType: "SALE"})
	if err != nil {
		http.Error(w, "failed to allocate bill number: "+err.Error(), http.StatusInternalServerError)
		return
	}

	sale, err := qtx.CreateSale(ctx, db.CreateSaleParams{
		CompanyID:   companyID,
		BillNumber:  fmt.Sprintf("SB-%d-%d", companyID, billNumber),
		ShopID:      in.ShopID,
		TotalAmount: numericFromFloat(total),
		AmountPaid:  numericFromFloat(amountPaid),
		PaymentType: in.PaymentType,
		CreatedBy:   pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, "failed to create sale: "+err.Error(), http.StatusInternalServerError)
		return
	}

	for _, item := range in.Items {
		product, err := qtx.GetProduct(ctx, db.GetProductParams{ID: item.ProductID, CompanyID: companyID})
		if err != nil {
			http.Error(w, "product not found", http.StatusBadRequest)
			return
		}

		// Cost is derived from FIFO cost layers, not a static purchase_price field, so the
		// below-cost comparison always reflects what this specific quantity actually cost.
		costTotal, consumptions, err := consumeFIFO(ctx, qtx, companyID, item.ProductID, item.Quantity)
		if err != nil {
			if errors.Is(err, errInsufficientStock) {
				http.Error(w, errInsufficientStock.Error(), http.StatusConflict)
				return
			}
			http.Error(w, "failed to cost item: "+err.Error(), http.StatusInternalServerError)
			return
		}
		unitCost := costTotal / item.Quantity
		belowCost := item.UnitPrice < unitCost
		if belowCost && !canApproveBelowCost {
			http.Error(w, fmt.Sprintf(
				"WARNING: selling price ₹%.2f for %s is below its cost ₹%.2f. This sale requires SALES_BELOW_COST_APPROVE permission.",
				item.UnitPrice, product.Name, unitCost,
			), http.StatusForbidden)
			return
		}

		_, err = qtx.DecrementProductStock(ctx, db.DecrementProductStockParams{
			ID:           item.ProductID,
			CurrentStock: numericFromFloat(item.Quantity),
		})
		if err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				http.Error(w, errInsufficientStock.Error(), http.StatusConflict)
				return
			}
			http.Error(w, "failed to update stock: "+err.Error(), http.StatusInternalServerError)
			return
		}

		lineTotal := item.Quantity * item.UnitPrice
		saleItem, err := qtx.CreateSaleItem(ctx, db.CreateSaleItemParams{
			SaleID:    sale.ID,
			ProductID: item.ProductID,
			Unit:      product.Unit,
			Quantity:  numericFromFloat(item.Quantity),
			UnitPrice: numericFromFloat(item.UnitPrice),
			LineTotal: numericFromFloat(lineTotal),
			BelowCost: belowCost,
		})
		if err != nil {
			http.Error(w, "failed to create sale item: "+err.Error(), http.StatusInternalServerError)
			return
		}

		for _, c := range consumptions {
			if _, err := qtx.CreateSaleItemCostConsumption(ctx, db.CreateSaleItemCostConsumptionParams{
				SaleItemID:         saleItem.ID,
				ProductCostLayerID: c.LayerID,
				Quantity:           numericFromFloat(c.Quantity),
				UnitCost:           numericFromFloat(c.UnitCost),
			}); err != nil {
				http.Error(w, "failed to record cost consumption: "+err.Error(), http.StatusInternalServerError)
				return
			}
		}
	}

	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusCreated)
	writeJSON(w, sale)
}

// Cancel reverses a sale bill: every unit consumed from a cost layer is restored to that exact
// layer (not re-derived via FIFO, which could be wrong if more purchases happened since), and
// current_stock is restored per item.
func (h *SaleHandler) Cancel(w http.ResponseWriter, r *http.Request) {
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

	items, err := qtx.ListSaleItems(ctx, int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if len(items) == 0 {
		http.Error(w, "sale not found", http.StatusNotFound)
		return
	}

	consumptions, err := qtx.ListCostConsumptionsBySale(ctx, int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	for _, c := range consumptions {
		if err := qtx.RestoreCostLayer(ctx, db.RestoreCostLayerParams{ID: c.ProductCostLayerID, QuantityRemaining: c.Quantity}); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	type impact struct {
		ProductID int32   `json:"product_id"`
		Quantity  float64 `json:"quantity"`
	}
	var inventoryImpact []impact
	for _, item := range items {
		if err := qtx.IncrementProductStockUnchecked(ctx, db.IncrementProductStockUncheckedParams{
			ID:           item.ProductID,
			CurrentStock: item.Quantity,
		}); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		inventoryImpact = append(inventoryImpact, impact{ProductID: item.ProductID, Quantity: numericToFloat(item.Quantity)})
	}

	sale, err := qtx.CancelSale(ctx, db.CancelSaleParams{
		ID:              int32(id),
		CompanyID:       companyID,
		CancelledReason: pgTextOrNil(in.Reason),
		CancelledBy:     pgtype.Int4{Int32: userID, Valid: true},
	})
	if err != nil {
		http.Error(w, "sale not found or already cancelled", http.StatusConflict)
		return
	}

	impactJSON, _ := json.Marshal(inventoryImpact)
	if _, err := qtx.CreateAuditLogEntry(ctx, db.CreateAuditLogEntryParams{
		CompanyID:       pgtype.Int4{Int32: companyID, Valid: true},
		ActorUserID:     pgtype.Int4{Int32: userID, Valid: true},
		Action:          "SALE_CANCEL",
		EntityType:      "sale",
		EntityID:        sale.ID,
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
	writeJSON(w, sale)
}
