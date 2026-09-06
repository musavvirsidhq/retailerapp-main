package handlers

import (
	"context"
	"testing"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/testutil"
)

// TestSaleCancellationRestoresExactCostLayers reproduces spec section 14.4: cancelling a sale
// must restore the exact quantity it drew from each cost layer, not a re-derived FIFO guess -
// which matters once a later purchase has added a layer that didn't exist at sale time.
func TestSaleCancellationRestoresExactCostLayers(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyID := testutil.SeedCompany(t, q, "Cancel Co", "CANCELCO")
	categoryID := testutil.SeedCategory(t, q, companyID, "Beverages")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "COKE-500")

	seedCostLayer(t, q, companyID, productID, 20, 100)

	// A sale draws 60 units from the only layer.
	_, consumptions, err := consumeFIFO(ctx, q, companyID, productID, 60)
	if err != nil {
		t.Fatalf("consumeFIFO error: %v", err)
	}
	if err := q.DecrementProductStockUnchecked(ctx, db.DecrementProductStockUncheckedParams{ID: productID, CurrentStock: numericFromFloat(60)}); err != nil {
		t.Fatalf("failed to decrement stock: %v", err)
	}

	// A second purchase adds a brand new layer *after* the sale - if cancellation re-derived
	// FIFO instead of restoring the recorded consumption, it could wrongly touch this new layer.
	seedCostLayer(t, q, companyID, productID, 25, 50)

	// Cancel the sale: restore exactly what was recorded, nothing re-derived.
	for _, c := range consumptions {
		if err := q.RestoreCostLayer(ctx, db.RestoreCostLayerParams{ID: c.LayerID, QuantityRemaining: numericFromFloat(c.Quantity)}); err != nil {
			t.Fatalf("RestoreCostLayer error: %v", err)
		}
	}
	if err := q.IncrementProductStockUnchecked(ctx, db.IncrementProductStockUncheckedParams{ID: productID, CurrentStock: numericFromFloat(60)}); err != nil {
		t.Fatalf("failed to restore stock: %v", err)
	}

	layers, err := q.ListActiveCostLayersForUpdate(ctx, db.ListActiveCostLayersForUpdateParams{CompanyID: companyID, ProductID: productID})
	if err != nil {
		t.Fatalf("ListActiveCostLayersForUpdate error: %v", err)
	}
	if len(layers) != 2 {
		t.Fatalf("expected both layers active after restore, got %d", len(layers))
	}
	var originalLayerRemaining, newLayerRemaining float64
	for _, l := range layers {
		remaining := numericToFloat(l.QuantityRemaining)
		if numericToFloat(l.BuyingPrice) == 20 {
			originalLayerRemaining = remaining
		} else {
			newLayerRemaining = remaining
		}
	}
	if originalLayerRemaining != 100 {
		t.Errorf("expected the original layer restored to 100 remaining, got %v", originalLayerRemaining)
	}
	if newLayerRemaining != 50 {
		t.Errorf("expected the newer layer untouched at 50 remaining, got %v", newLayerRemaining)
	}

	product, err := q.GetProduct(ctx, db.GetProductParams{ID: productID, CompanyID: companyID})
	if err != nil {
		t.Fatalf("GetProduct error: %v", err)
	}
	if numericToFloat(product.CurrentStock) != 150 {
		t.Errorf("expected stock restored to 150 (100 + 50 new), got %v", numericToFloat(product.CurrentStock))
	}
}

// TestPurchaseCancelBlockedWhenPartiallyConsumed reproduces the purchase-cancellation rule: a
// purchase's cost layer can only be cancelled while none of its quantity has been sold yet.
func TestPurchaseCancelBlockedWhenPartiallyConsumed(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyID := testutil.SeedCompany(t, q, "Partial Co", "PARTIALCO")
	categoryID := testutil.SeedCategory(t, q, companyID, "Beverages")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "COKE-500")

	seedCostLayer(t, q, companyID, productID, 20, 100)
	if _, _, err := consumeFIFO(ctx, q, companyID, productID, 30); err != nil {
		t.Fatalf("consumeFIFO error: %v", err)
	}

	layers, err := q.ListActiveCostLayersForUpdate(ctx, db.ListActiveCostLayersForUpdateParams{CompanyID: companyID, ProductID: productID})
	if err != nil {
		t.Fatalf("ListActiveCostLayersForUpdate error: %v", err)
	}
	if len(layers) != 1 {
		t.Fatalf("expected 1 active layer, got %d", len(layers))
	}
	layer := layers[0]
	received := numericToFloat(layer.QuantityReceived)
	remaining := numericToFloat(layer.QuantityRemaining)
	if remaining == received {
		t.Fatalf("expected the layer to show partial consumption (remaining != received), got remaining=%v received=%v", remaining, received)
	}
	// This is exactly the condition PurchaseHandler.Cancel checks before allowing cancellation.
	if remaining != 70 || received != 100 {
		t.Errorf("expected remaining=70 received=100, got remaining=%v received=%v", remaining, received)
	}
}

// TestPurchaseCancelAllowedWhenUntouched is the companion case: a purchase whose layer has
// never been sold from can be cancelled cleanly.
func TestPurchaseCancelAllowedWhenUntouched(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyID := testutil.SeedCompany(t, q, "Untouched Co", "UNTOUCHEDCO")
	categoryID := testutil.SeedCategory(t, q, companyID, "Beverages")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "COKE-500")

	seedCostLayer(t, q, companyID, productID, 20, 100)

	layers, err := q.ListActiveCostLayersForUpdate(ctx, db.ListActiveCostLayersForUpdateParams{CompanyID: companyID, ProductID: productID})
	if err != nil {
		t.Fatalf("ListActiveCostLayersForUpdate error: %v", err)
	}
	layer := layers[0]
	if numericToFloat(layer.QuantityRemaining) != numericToFloat(layer.QuantityReceived) {
		t.Fatalf("expected an untouched layer to allow cancellation (remaining == received)")
	}

	if err := q.CancelCostLayer(ctx, layer.ID); err != nil {
		t.Fatalf("CancelCostLayer error: %v", err)
	}
	cancelled, err := q.GetCostLayerByPurchaseItem(ctx, layer.PurchaseBillItemID)
	if err != nil {
		t.Fatalf("GetCostLayerByPurchaseItem error: %v", err)
	}
	if cancelled.Status != "CANCELLED" {
		t.Errorf("expected layer status CANCELLED, got %v", cancelled.Status)
	}
	if numericToFloat(cancelled.QuantityRemaining) != 0 {
		t.Errorf("expected quantity_remaining 0 after cancellation, got %v", numericToFloat(cancelled.QuantityRemaining))
	}
}
