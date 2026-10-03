package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	"github.com/Sivanandha02/retailapp/internal/testutil"
)

func jsonBody(t *testing.T, v interface{}) *bytes.Buffer {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	return bytes.NewBuffer(b)
}

// TestArchiveCustomer covers Cycle 5 section 3: staff can't archive, a customer with a balance
// is refused with the "settle" message, archiving hides them from lists and dues but keeps the
// row (and ledger, with archived_at), new payments are refused, restore works, and both steps
// are audited.
func TestArchiveCustomer(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()
	companyID := testutil.SeedCompany(t, q, "Archive Co", "ARCH5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "arch_admin")
	staffID := seedStaff(t, q, companyID, "arch_staff", true, true)
	categoryID := testutil.SeedCategory(t, q, companyID, "Cat")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "ARCH-SKU")
	shopID := testutil.SeedShop(t, q, companyID, "Owes Money")

	shops := NewShopHandler(q)
	payments := NewPaymentHandler(q)
	params := map[string]string{"id": strconv.Itoa(int(shopID))}

	// Staff -> 403.
	w := httptest.NewRecorder()
	shops.Delete(w, asUser("DELETE", "/api/shops/x", nil, companyID, staffID, "STAFF", true, true, params))
	if w.Code != http.StatusForbidden {
		t.Fatalf("staff archive: got %d, want 403", w.Code)
	}

	// A pending balance -> refused, nothing archived.
	seedSale(t, q, companyID, shopID, productID, randomBillNumber(), 500, 100)
	w = httptest.NewRecorder()
	shops.Delete(w, asUser("DELETE", "/api/shops/x", nil, companyID, adminID, "COMPANY_ADMIN", true, true, params))
	if w.Code != http.StatusBadRequest || !strings.Contains(w.Body.String(), "Settle the ₹400.00 balance before archiving.") {
		t.Fatalf("archive with balance: got %d %q", w.Code, w.Body.String())
	}

	// Settle it, then archive.
	w = httptest.NewRecorder()
	payments.Create(w, asUser("POST", "/api/payments/", jsonBody(t, map[string]interface{}{
		"party_type": "shop", "party_id": shopID, "amount": 400, "payment_mode": "Cash",
	}), companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	if w.Code != http.StatusCreated {
		t.Fatalf("settling payment: got %d %s", w.Code, w.Body.String())
	}
	w = httptest.NewRecorder()
	shops.Delete(w, asUser("DELETE", "/api/shops/x", nil, companyID, adminID, "COMPANY_ADMIN", true, true, params))
	if w.Code != http.StatusNoContent {
		t.Fatalf("archive settled customer: got %d %s", w.Code, w.Body.String())
	}

	// Hidden from the default list and dues; still there with include_archived; row kept.
	active, _ := q.ListShops(ctx, companyID)
	if len(active) != 0 {
		t.Fatalf("archived customer still in ListShops: %+v", active)
	}
	w = httptest.NewRecorder()
	shops.List(w, asUser("GET", "/api/shops/?include_archived=true", nil, companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	var all []db.Shop
	json.Unmarshal(w.Body.Bytes(), &all)
	if len(all) != 1 || !all[0].ArchivedAt.Valid {
		t.Fatalf("include_archived list: %s", w.Body.String())
	}
	dues, _ := q.CustomerDues(ctx, companyID)
	if len(dues) != 0 {
		t.Fatalf("archived customer still in dues: %+v", dues)
	}

	// The ledger still opens and says it's archived.
	w = httptest.NewRecorder()
	NewLedgerHandler(q).CustomerLedger(w, asUser("GET", "/api/shops/x/ledger", nil, companyID, adminID, "COMPANY_ADMIN", true, true, params))
	var ledger struct {
		Party struct {
			ArchivedAt *string `json:"archived_at"`
		} `json:"party"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &ledger); err != nil || w.Code != http.StatusOK || ledger.Party.ArchivedAt == nil {
		t.Fatalf("ledger of archived customer: %d %s", w.Code, w.Body.String())
	}

	// New payments are refused.
	w = httptest.NewRecorder()
	payments.Create(w, asUser("POST", "/api/payments/", jsonBody(t, map[string]interface{}{
		"party_type": "shop", "party_id": shopID, "amount": 10, "payment_mode": "Cash",
	}), companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	if w.Code != http.StatusBadRequest || !strings.Contains(w.Body.String(), "archived") {
		t.Fatalf("payment to archived customer: got %d %s", w.Code, w.Body.String())
	}

	// Restore (staff refused first).
	w = httptest.NewRecorder()
	shops.Restore(w, asUser("POST", "/api/shops/x/restore", nil, companyID, staffID, "STAFF", true, true, params))
	if w.Code != http.StatusForbidden {
		t.Fatalf("staff restore: got %d", w.Code)
	}
	w = httptest.NewRecorder()
	shops.Restore(w, asUser("POST", "/api/shops/x/restore", nil, companyID, adminID, "COMPANY_ADMIN", true, true, params))
	if w.Code != http.StatusOK {
		t.Fatalf("restore: got %d %s", w.Code, w.Body.String())
	}
	if active, _ := q.ListShops(ctx, companyID); len(active) != 1 {
		t.Fatalf("restored customer missing from ListShops")
	}

	audit, err := q.ListAuditLogByEntity(ctx, db.ListAuditLogByEntityParams{EntityType: "shop", EntityID: shopID})
	if err != nil {
		t.Fatal(err)
	}
	actions := map[string]bool{}
	for _, a := range audit {
		actions[a.Action] = true
	}
	if !actions["SHOP_ARCHIVE"] || !actions["SHOP_RESTORE"] {
		t.Fatalf("audit actions = %v, want SHOP_ARCHIVE and SHOP_RESTORE", actions)
	}
}

// TestArchiveSupplierWithBalanceRefused checks the same balance rule for suppliers.
func TestArchiveSupplierWithBalanceRefused(t *testing.T) {
	q := testutil.OpenTestTx(t)
	companyID := testutil.SeedCompany(t, q, "Supplier Co", "SUPP5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "supp_admin")
	factoryID := testutil.SeedFactory(t, q, companyID, "Mill")
	if _, err := q.CreatePurchase(context.Background(), db.CreatePurchaseParams{
		CompanyID: companyID, BillNumber: randomBillNumber(), FactoryID: factoryID,
		TotalAmount: numericFromFloat(250), AmountPaid: numericFromFloat(0),
	}); err != nil {
		t.Fatal(err)
	}
	w := httptest.NewRecorder()
	NewFactoryHandler(q).Delete(w, asUser("DELETE", "/api/factories/x", nil, companyID, adminID, "COMPANY_ADMIN", true, true,
		map[string]string{"id": strconv.Itoa(int(factoryID))}))
	if w.Code != http.StatusBadRequest || !strings.Contains(w.Body.String(), "Settle the ₹250.00 balance") {
		t.Fatalf("archive supplier with balance: got %d %q", w.Code, w.Body.String())
	}
}

// TestArchiveProductAndSkuClash: a product with stock can be archived (with the stock in the
// audit entry), it drops out of the list, and reusing its SKU gets the "restore it" message.
func TestArchiveProductAndSkuClash(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()
	companyID := testutil.SeedCompany(t, q, "Product Co", "PROD5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "prod_admin")
	categoryID := testutil.SeedCategory(t, q, companyID, "Cat")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "SKU-OLD")
	q.IncrementProductStock(ctx, db.IncrementProductStockParams{ID: productID, CurrentStock: numericFromFloat(7)})

	products := NewProductHandler(q)
	w := httptest.NewRecorder()
	products.Delete(w, asUser("DELETE", "/api/products/x", nil, companyID, adminID, "COMPANY_ADMIN", true, true,
		map[string]string{"id": strconv.Itoa(int(productID))}))
	if w.Code != http.StatusNoContent {
		t.Fatalf("archive product with stock: got %d %s", w.Code, w.Body.String())
	}
	if list, _ := q.ListProducts(ctx, companyID); len(list) != 0 {
		t.Fatalf("archived product still listed")
	}
	audit, _ := q.ListAuditLogByEntity(ctx, db.ListAuditLogByEntityParams{EntityType: "product", EntityID: productID})
	var impact map[string]string
	if len(audit) == 1 {
		json.Unmarshal(audit[0].InventoryImpact, &impact)
	}
	if len(audit) != 1 || audit[0].Action != "PRODUCT_ARCHIVE" || impact["stock_on_hand"] != "7" {
		t.Fatalf("product archive audit: %+v", audit)
	}

	w = httptest.NewRecorder()
	products.Create(w, asUser("POST", "/api/products/", jsonBody(t, map[string]interface{}{
		"name": "New", "sku": "SKU-OLD", "unit": "PIECE", "category_id": categoryID, "selling_price": 5,
	}), companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	if w.Code != http.StatusConflict || !strings.Contains(w.Body.String(), "SKU already used by archived product") {
		t.Fatalf("SKU clash with archived product: got %d %q", w.Code, w.Body.String())
	}
}

// TestSalesListFilters: no params = the old full list with no headers (web unchanged);
// date/search/paging filter correctly; the totals headers cover the whole range and skip
// cancelled bills in the amount.
func TestSalesListFilters(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()
	companyID := testutil.SeedCompany(t, q, "List Co", "LIST5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "list_admin")
	categoryID := testutil.SeedCategory(t, q, companyID, "Cat")
	productID := testutil.SeedProduct(t, q, companyID, categoryID, "LIST-SKU")
	alpha := testutil.SeedShop(t, q, companyID, "Alpha Stores")
	beta := testutil.SeedShop(t, q, companyID, "Beta Traders")

	ids := []int32{
		seedSale(t, q, companyID, alpha, productID, "SB-A1", 100, 0),
		seedSale(t, q, companyID, alpha, productID, "SB-A2", 200, 0),
		seedSale(t, q, companyID, beta, productID, "SB-B1", 300, 0),
	}
	// Cancel the third: it still counts as a bill but not towards the amount.
	if _, err := q.CancelSale(ctx, db.CancelSaleParams{ID: ids[2], CompanyID: companyID, CancelledReason: pgtype.Text{String: "test", Valid: true}}); err != nil {
		t.Fatal(err)
	}

	sales := NewSaleHandler(q, nil)
	get := func(target string) (*httptest.ResponseRecorder, []db.ListSalesRow) {
		w := httptest.NewRecorder()
		sales.List(w, asUser("GET", target, nil, companyID, adminID, "COMPANY_ADMIN", true, true, nil))
		var rows []db.ListSalesRow
		if err := json.Unmarshal(w.Body.Bytes(), &rows); err != nil {
			t.Fatalf("%s: %d %s", target, w.Code, w.Body.String())
		}
		return w, rows
	}

	w, rows := get("/api/sales/")
	if len(rows) != 3 || w.Header().Get("X-Total-Count") != "" {
		t.Fatalf("no params: %d rows, headers %v", len(rows), w.Header())
	}

	saleDay := rows[0].SaleDate.Time
	today := saleDay.Format(dateLayout)
	w, rows = get("/api/sales/?from=" + today + "&to=" + today + "&limit=2")
	if len(rows) != 2 || w.Header().Get("X-Total-Count") != "3" || w.Header().Get("X-Total-Amount") != "300.00" {
		t.Fatalf("today paged: %d rows, count=%s amount=%s", len(rows), w.Header().Get("X-Total-Count"), w.Header().Get("X-Total-Amount"))
	}
	_, page2 := get("/api/sales/?from=" + today + "&to=" + today + "&limit=2&offset=2")
	if len(page2) != 1 || page2[0].ID == rows[0].ID || page2[0].ID == rows[1].ID {
		t.Fatalf("second page: %+v", page2)
	}
	w, rows = get("/api/sales/?from=" + saleDay.AddDate(0, 0, 1).Format(dateLayout) + "&limit=50")
	if len(rows) != 0 || w.Header().Get("X-Total-Count") != "0" {
		t.Fatalf("future range: %d rows, count=%s", len(rows), w.Header().Get("X-Total-Count"))
	}
	if ids[0] == 0 {
		t.Fatal("unreachable")
	}

	_, rows = get("/api/sales/?q=beta")
	if len(rows) != 1 || rows[0].BillNumber != "SB-B1" {
		t.Fatalf("search by customer: %+v", rows)
	}
	_, rows = get("/api/sales/?q=" + "%25")
	if len(rows) != 0 {
		t.Fatalf("%% must match literally, got %d rows", len(rows))
	}

	w = httptest.NewRecorder()
	sales.List(w, asUser("GET", "/api/sales/?from=yesterday", nil, companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("bad date: got %d", w.Code)
	}
}

// TestPaymentsListPartyFilter checks ?party_type= and the totals headers on payments.
func TestPaymentsListPartyFilter(t *testing.T) {
	q := testutil.OpenTestTx(t)
	companyID := testutil.SeedCompany(t, q, "Pay Co", "PAYL5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "pay_admin")
	shopID := testutil.SeedShop(t, q, companyID, "Shop")
	factoryID := testutil.SeedFactory(t, q, companyID, "Factory")
	h := NewPaymentHandler(q)
	for _, p := range []map[string]interface{}{
		{"party_type": "shop", "party_id": shopID, "amount": 50, "payment_mode": "Cash"},
		{"party_type": "shop", "party_id": shopID, "amount": 25, "payment_mode": "UPI"},
		{"party_type": "factory", "party_id": factoryID, "amount": 70, "payment_mode": "Bank"},
	} {
		w := httptest.NewRecorder()
		h.Create(w, asUser("POST", "/api/payments/", jsonBody(t, p), companyID, adminID, "COMPANY_ADMIN", true, true, nil))
		if w.Code != http.StatusCreated {
			t.Fatalf("seed payment: %d %s", w.Code, w.Body.String())
		}
	}
	w := httptest.NewRecorder()
	h.List(w, asUser("GET", "/api/payments/?party_type=shop&limit=50", nil, companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	var rows []db.Payment
	json.Unmarshal(w.Body.Bytes(), &rows)
	if len(rows) != 2 || w.Header().Get("X-Total-Count") != "2" || w.Header().Get("X-Total-Amount") != "75.00" {
		t.Fatalf("party filter: %d rows, headers %v", len(rows), w.Header())
	}
}

// TestCompanySettings: everyone reads, only the admin writes.
func TestCompanySettings(t *testing.T) {
	q := testutil.OpenTestTx(t)
	companyID := testutil.SeedCompany(t, q, "Settings Co", "SETT5")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "set_admin")
	staffID := seedStaff(t, q, companyID, "set_staff", true, false)
	h := NewCompanyHandler(q)

	w := httptest.NewRecorder()
	h.UpdateSettings(w, asUser("PUT", "/api/company/settings", jsonBody(t, map[string]bool{"require_payment_photo": true}),
		companyID, staffID, "STAFF", true, false, nil))
	if w.Code != http.StatusForbidden {
		t.Fatalf("staff update: got %d", w.Code)
	}
	w = httptest.NewRecorder()
	h.UpdateSettings(w, asUser("PUT", "/api/company/settings", jsonBody(t, map[string]bool{"require_payment_photo": true}),
		companyID, adminID, "COMPANY_ADMIN", true, true, nil))
	if w.Code != http.StatusOK {
		t.Fatalf("admin update: got %d %s", w.Code, w.Body.String())
	}
	w = httptest.NewRecorder()
	h.GetSettings(w, asUser("GET", "/api/company/settings", nil, companyID, staffID, "STAFF", true, false, nil))
	if strings.TrimSpace(w.Body.String()) != `{"require_payment_photo":true}` {
		t.Fatalf("staff read: %d %s", w.Code, w.Body.String())
	}
}
