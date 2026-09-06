// Package testutil provides shared test-database setup for handler tests: each test runs
// inside its own transaction against the real dev Postgres, which is always rolled back at the
// end of the test so the dev database is never polluted and no separate test database is needed.
package testutil

import (
	"context"
	"os"
	"testing"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
)

// OpenTestTx connects to the database named by DATABASE_URL (falling back to the same local
// dev connection string cmd/server/main.go uses), begins a transaction, and registers a
// t.Cleanup that always rolls it back. Skips the test if Postgres isn't reachable, so a plain
// `go test ./...` doesn't hard-fail without Docker running.
func OpenTestTx(t *testing.T) *db.Queries {
	t.Helper()
	ctx := context.Background()

	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://retailapp:retailapp_dev@localhost:55432/retailapp?sslmode=disable"
	}

	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		t.Skipf("skipping: cannot connect to test database: %v", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		t.Skipf("skipping: test database not reachable: %v", err)
	}

	tx, err := pool.Begin(ctx)
	if err != nil {
		pool.Close()
		t.Fatalf("failed to begin test transaction: %v", err)
	}
	t.Cleanup(func() {
		tx.Rollback(ctx)
		pool.Close()
	})

	return db.New(pool).WithTx(tx)
}

// SeedCompany creates a company for a test and returns its id.
func SeedCompany(t *testing.T, q *db.Queries, name, code string) int32 {
	t.Helper()
	ctx := context.Background()
	c, err := q.CreateCompany(ctx, db.CreateCompanyParams{
		CompanyName: name,
		CompanyCode: code,
		JoiningDate: pgtype.Date{Valid: true},
	})
	if err != nil {
		t.Fatalf("failed to seed company: %v", err)
	}
	return c.ID
}

// SeedCompanyAdmin creates a COMPANY_ADMIN user for the given company and returns its id.
func SeedCompanyAdmin(t *testing.T, q *db.Queries, companyID int32, username string) int32 {
	t.Helper()
	ctx := context.Background()
	u, err := q.CreateUser(ctx, db.CreateUserParams{
		CompanyID:    pgtype.Int4{Int32: companyID, Valid: true},
		Name:         username,
		Username:     username,
		PasswordHash: "test-hash",
		UserType:     "COMPANY_ADMIN",
	})
	if err != nil {
		t.Fatalf("failed to seed company admin: %v", err)
	}
	return u.ID
}

// SeedCategory creates a category for a test and returns its id.
func SeedCategory(t *testing.T, q *db.Queries, companyID int32, name string) int32 {
	t.Helper()
	c, err := q.CreateCategory(context.Background(), db.CreateCategoryParams{CompanyID: companyID, Name: name})
	if err != nil {
		t.Fatalf("failed to seed category: %v", err)
	}
	return c.ID
}

// SeedProduct creates a product for a test and returns its id.
func SeedProduct(t *testing.T, q *db.Queries, companyID, categoryID int32, sku string) int32 {
	t.Helper()
	var zero pgtype.Numeric
	zero.Scan("0")
	p, err := q.CreateProduct(context.Background(), db.CreateProductParams{
		CompanyID:           companyID,
		Name:                sku,
		Sku:                 sku,
		Unit:                "PIECE",
		CategoryID:          categoryID,
		CurrentSellingPrice: zero,
	})
	if err != nil {
		t.Fatalf("failed to seed product: %v", err)
	}
	return p.ID
}

// SeedFactory creates a factory for a test and returns its id.
func SeedFactory(t *testing.T, q *db.Queries, companyID int32, name string) int32 {
	t.Helper()
	f, err := q.CreateFactory(context.Background(), db.CreateFactoryParams{
		CompanyID:    companyID,
		Name:         name,
		PrimaryPhone: "9000000000",
	})
	if err != nil {
		t.Fatalf("failed to seed factory: %v", err)
	}
	return f.ID
}

// SeedShop creates a shop for a test and returns its id.
func SeedShop(t *testing.T, q *db.Queries, companyID int32, name string) int32 {
	t.Helper()
	s, err := q.CreateShop(context.Background(), db.CreateShopParams{
		CompanyID:    companyID,
		Name:         name,
		PrimaryPhone: "9000000001",
	})
	if err != nil {
		t.Fatalf("failed to seed shop: %v", err)
	}
	return s.ID
}
