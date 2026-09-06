package handlers

import (
	"context"
	"errors"
	"testing"

	"github.com/jackc/pgx/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/testutil"
)

// TestMultiCompanyIsolation reproduces spec section 29.3: changing an id in a request must
// never allow access to another company's records. It seeds two companies, each with their own
// product, and asserts every company-scoped query only ever returns rows for the company
// passed in - never the other company's, even when asked for the other company's own id.
func TestMultiCompanyIsolation(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()

	companyA := testutil.SeedCompany(t, q, "Company A", "COMPA")
	companyB := testutil.SeedCompany(t, q, "Company B", "COMPB")

	catA := testutil.SeedCategory(t, q, companyA, "Category A")
	catB := testutil.SeedCategory(t, q, companyB, "Category B")

	productA := testutil.SeedProduct(t, q, companyA, catA, "A-SKU-1")
	productB := testutil.SeedProduct(t, q, companyB, catB, "B-SKU-1")

	// Company A's product list must never include Company B's product, and vice versa.
	listA, err := q.ListProducts(ctx, companyA)
	if err != nil {
		t.Fatalf("ListProducts(companyA) error: %v", err)
	}
	for _, p := range listA {
		if p.ID == productB {
			t.Fatalf("company A's product list leaked company B's product %d", productB)
		}
	}

	listB, err := q.ListProducts(ctx, companyB)
	if err != nil {
		t.Fatalf("ListProducts(companyB) error: %v", err)
	}
	for _, p := range listB {
		if p.ID == productA {
			t.Fatalf("company B's product list leaked company A's product %d", productA)
		}
	}

	// The critical case: asking for company B's product id while scoped as company A must
	// behave exactly like "not found", not return the other company's row.
	_, err = q.GetProduct(ctx, db.GetProductParams{ID: productB, CompanyID: companyA})
	if !errors.Is(err, pgx.ErrNoRows) {
		t.Fatalf("expected pgx.ErrNoRows when company A requests company B's product id, got: %v", err)
	}

	// And company A can retrieve its own product fine.
	got, err := q.GetProduct(ctx, db.GetProductParams{ID: productA, CompanyID: companyA})
	if err != nil {
		t.Fatalf("company A failed to fetch its own product: %v", err)
	}
	if got.ID != productA {
		t.Fatalf("expected product %d, got %d", productA, got.ID)
	}
}
