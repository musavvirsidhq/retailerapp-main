package handlers

import (
	"context"
	"testing"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/testutil"
)

// seedCostLayer creates a purchase item + cost layer pair directly (bypassing the HTTP purchase
// flow, which manages its own top-level transaction and so can't be driven from inside a test's
// already-open transaction) so FIFO consumption can be tested against known layers.
func seedCostLayer(t *testing.T, q *db.Queries, companyID, productID int32, buyingPrice, quantity float64) {
	t.Helper()
	ctx := context.Background()

	userID := testutil.SeedCompanyAdmin(t, q, companyID, randomBillNumber())

	purchase, err := q.CreatePurchase(ctx, db.CreatePurchaseParams{
		CompanyID:   companyID,
		BillNumber:  randomBillNumber(),
		FactoryID:   testutil.SeedFactory(t, q, companyID, "Test Factory"),
		TotalAmount: numericFromFloat(buyingPrice * quantity),
		AmountPaid:  numericFromFloat(buyingPrice * quantity),
	})
	if err != nil {
		t.Fatalf("failed to seed purchase: %v", err)
	}
	item, err := q.CreatePurchaseItem(ctx, db.CreatePurchaseItemParams{
		PurchaseID: purchase.ID,
		ProductID:  productID,
		Unit:       "PIECE",
		Quantity:   numericFromFloat(quantity),
		UnitPrice:  numericFromFloat(buyingPrice),
		LineTotal:  numericFromFloat(buyingPrice * quantity),
	})
	if err != nil {
		t.Fatalf("failed to seed purchase item: %v", err)
	}
	if err := createCostLayerForPurchaseItem(ctx, q, companyID, productID, item.ID, buyingPrice, quantity, userID); err != nil {
		t.Fatalf("failed to seed cost layer: %v", err)
	}
	if err := q.IncrementProductStock(ctx, db.IncrementProductStockParams{ID: productID, CurrentStock: numericFromFloat(quantity)}); err != nil {
		t.Fatalf("failed to seed stock: %v", err)
	}
}

var billNumberCounter int

func randomBillNumber() string {
	billNumberCounter++
	return "TEST-PB-" + string(rune('A'+billNumberCounter%26)) + string(rune('0'+billNumberCounter%10))
}

// TestConsumeFIFO reproduces the spec's worked FIFO example (section 6.5): 100 units @ Rs.20
// then 100 units @ Rs.22, selling 120 units should cost 100*20 + 20*22 = 2440, draining the
// first layer completely and leaving 80 units remaining in the second.
func TestConsumeFIFO(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyID := testutil.SeedCompany(t, q, "FIFO Co", "FIFOCO")
	categoryID := testutil.SeedCategory(t, q, companyID, "Beverages")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "COKE-500")

	seedCostLayer(t, q, companyID, productID, 20, 100)
	seedCostLayer(t, q, companyID, productID, 22, 100)

	totalCost, consumptions, err := consumeFIFO(ctx, q, companyID, productID, 120)
	if err != nil {
		t.Fatalf("consumeFIFO error: %v", err)
	}
	if totalCost != 2440 {
		t.Errorf("expected total cost 2440, got %v", totalCost)
	}
	if len(consumptions) != 2 {
		t.Fatalf("expected 2 layers consumed, got %d", len(consumptions))
	}
	if consumptions[0].Quantity != 100 || consumptions[0].UnitCost != 20 {
		t.Errorf("expected first consumption 100@20, got %+v", consumptions[0])
	}
	if consumptions[1].Quantity != 20 || consumptions[1].UnitCost != 22 {
		t.Errorf("expected second consumption 20@22, got %+v", consumptions[1])
	}

	layers, err := q.ListActiveCostLayersForUpdate(ctx, db.ListActiveCostLayersForUpdateParams{CompanyID: companyID, ProductID: productID})
	if err != nil {
		t.Fatalf("ListActiveCostLayersForUpdate error: %v", err)
	}
	// The first layer is now fully depleted (status flips to DEPLETED, excluded from "active"
	// listing); only the second layer's remaining 80 units should still be active.
	if len(layers) != 1 {
		t.Fatalf("expected exactly 1 still-active layer, got %d", len(layers))
	}
	remaining, _ := layers[0].QuantityRemaining.Float64Value()
	if remaining.Float64 != 80 {
		t.Errorf("expected 80 units remaining in the second layer, got %v", remaining.Float64)
	}
}

// TestConsumeFIFOInsufficientStock asserts that requesting more than is available across all
// active layers fails clearly instead of silently under-costing the sale.
func TestConsumeFIFOInsufficientStock(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyID := testutil.SeedCompany(t, q, "FIFO Co 2", "FIFOCO2")
	categoryID := testutil.SeedCategory(t, q, companyID, "Beverages")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "COKE-500")
	seedCostLayer(t, q, companyID, productID, 20, 50)

	_, _, err := consumeFIFO(ctx, q, companyID, productID, 51)
	if err == nil {
		t.Fatal("expected an error when requesting more quantity than is available in active cost layers")
	}
}
