package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"image"
	"image/color"
	"image/png"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgtype"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
	"github.com/Sivanandha02/retailapp/internal/testutil"
)

// asUser builds a request carrying the context values RequireAuth would set.
func asUser(method, target string, body *bytes.Buffer, companyID, userID int32, userType string, sales, purchase bool, params map[string]string) *http.Request {
	if body == nil {
		body = &bytes.Buffer{}
	}
	r := httptest.NewRequest(method, target, body)
	ctx := context.WithValue(r.Context(), appMiddleware.CompanyIDKey, companyID)
	ctx = context.WithValue(ctx, appMiddleware.UserIDKey, userID)
	ctx = context.WithValue(ctx, appMiddleware.UserTypeKey, userType)
	ctx = context.WithValue(ctx, appMiddleware.SalesAccessKey, sales)
	ctx = context.WithValue(ctx, appMiddleware.PurchaseAccessKey, purchase)
	rctx := chi.NewRouteContext()
	for k, v := range params {
		rctx.URLParams.Add(k, v)
	}
	ctx = context.WithValue(ctx, chi.RouteCtxKey, rctx)
	return r.WithContext(ctx)
}

func seedStaff(t *testing.T, q *db.Queries, companyID int32, username string, sales, purchase bool) int32 {
	t.Helper()
	u, err := q.CreateUser(context.Background(), db.CreateUserParams{
		CompanyID:      pgtype.Int4{Int32: companyID, Valid: true},
		Name:           username,
		Username:       username,
		PasswordHash:   "test-hash",
		UserType:       "STAFF",
		SalesAccess:    sales,
		PurchaseAccess: purchase,
	})
	if err != nil {
		t.Fatalf("failed to seed staff: %v", err)
	}
	return u.ID
}

func seedSale(t *testing.T, q *db.Queries, companyID, shopID, productID int32, bill string, total, paid float64) int32 {
	t.Helper()
	ctx := context.Background()
	s, err := q.CreateSale(ctx, db.CreateSaleParams{
		CompanyID: companyID, BillNumber: bill, ShopID: shopID,
		TotalAmount: numericFromFloat(total), AmountPaid: numericFromFloat(paid), PaymentType: "credit",
	})
	if err != nil {
		t.Fatalf("failed to seed sale: %v", err)
	}
	if _, err := q.CreateSaleItem(ctx, db.CreateSaleItemParams{
		SaleID: s.ID, ProductID: productID, Unit: "PIECE",
		Quantity: numericFromFloat(1), UnitPrice: numericFromFloat(total), LineTotal: numericFromFloat(total),
	}); err != nil {
		t.Fatalf("failed to seed sale item: %v", err)
	}
	return s.ID
}

func pngBytes(t *testing.T) []byte {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, 4, 4))
	img.Set(1, 1, color.RGBA{R: 255, A: 255})
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func multipartBody(t *testing.T, content []byte) (*bytes.Buffer, string) {
	t.Helper()
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	fw, err := w.CreateFormFile("file", "proof.png")
	if err != nil {
		t.Fatal(err)
	}
	fw.Write(content)
	w.Close()
	return &buf, w.FormDataContentType()
}

// TestCustomerLedgerMatchesBalance checks the Cycle 4 acceptance rule that a ledger's final
// running balance equals the existing /api/shops/{id}/balance figure, including an opening
// balance, a part-paid sale, a cancelled sale and a payment.
func TestCustomerLedgerMatchesBalance(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()
	companyID := testutil.SeedCompany(t, q, "Ledger Co", "LEDG4")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "ledger_admin")
	catID := testutil.SeedCategory(t, q, companyID, "Cat")
	productID := testutil.SeedProduct(t, q, companyID, catID, "L-1")

	shop, err := q.CreateShop(ctx, db.CreateShopParams{
		CompanyID: companyID, Name: "Sri Ganesh Traders", PrimaryPhone: "9000000009", OpeningBalance: numericFromFloat(500),
	})
	if err != nil {
		t.Fatal(err)
	}
	seedSale(t, q, companyID, shop.ID, productID, "S-1", 5000, 0)
	seedSale(t, q, companyID, shop.ID, productID, "S-2", 6000, 1000)
	cancelled := seedSale(t, q, companyID, shop.ID, productID, "S-3", 9999, 0)
	if _, err := q.CancelSale(ctx, db.CancelSaleParams{ID: cancelled, CompanyID: companyID, CancelledReason: pgTextOrNil("typo")}); err != nil {
		t.Fatal(err)
	}
	if _, err := q.CreatePayment(ctx, db.CreatePaymentParams{
		CompanyID: companyID, PartyType: "shop", PartyID: shop.ID, Amount: numericFromFloat(2000.5), PaymentMode: "UPI",
	}); err != nil {
		t.Fatal(err)
	}

	h := NewLedgerHandler(q)
	w := httptest.NewRecorder()
	h.CustomerLedger(w, asUser("GET", "/", nil, companyID, adminID, "COMPANY_ADMIN", false, false, map[string]string{"id": strconv.Itoa(int(shop.ID))}))
	if w.Code != http.StatusOK {
		t.Fatalf("ledger: %d %s", w.Code, w.Body.String())
	}
	var ledger ledgerResponse
	if err := json.Unmarshal(w.Body.Bytes(), &ledger); err != nil {
		t.Fatal(err)
	}

	balance, err := q.ShopBalance(ctx, db.ShopBalanceParams{ID: shop.ID, CompanyID: companyID})
	if err != nil {
		t.Fatal(err)
	}
	if want := formatPaise(toPaise(balance)); ledger.Summary.Pending != want || ledger.Entries[0].BalanceAfter != want {
		t.Fatalf("ledger pending %s / newest balance %s, want ShopBalance %s", ledger.Summary.Pending, ledger.Entries[0].BalanceAfter, want)
	}
	if ledger.Summary.Pending != "8499.50" {
		t.Fatalf("expected 500 + 5000 + 5000 - 2000.50 = 8499.50, got %s", ledger.Summary.Pending)
	}
	if len(ledger.Entries) != 5 { // opening + 3 sales + 1 payment
		t.Fatalf("expected 5 entries, got %d", len(ledger.Entries))
	}

	// The dues list agrees, and the other company can't see this customer's ledger.
	w = httptest.NewRecorder()
	h.CustomerDues(w, asUser("GET", "/", nil, companyID, adminID, "COMPANY_ADMIN", false, false, nil))
	var dues duesResponse
	json.Unmarshal(w.Body.Bytes(), &dues)
	if len(dues.Rows) != 1 || dues.Rows[0].Balance != "8499.50" || dues.Rows[0].LastActivityAt == nil {
		t.Fatalf("unexpected dues: %s", w.Body.String())
	}

	otherCompany := testutil.SeedCompany(t, q, "Other Co", "OTHR4")
	w = httptest.NewRecorder()
	h.CustomerLedger(w, asUser("GET", "/", nil, otherCompany, adminID, "COMPANY_ADMIN", false, false, map[string]string{"id": strconv.Itoa(int(shop.ID))}))
	if w.Code != http.StatusNotFound {
		t.Fatalf("other company should get 404 for this customer's ledger, got %d", w.Code)
	}
}

// TestAttachmentIsolationAndPermissions covers the Cycle 4 photo rules: uploads land on the
// record, another company can neither list nor download them, staff without sales access are
// refused, only images are accepted, and staff can't delete.
func TestAttachmentIsolationAndPermissions(t *testing.T) {
	q := testutil.OpenTestTx(t)
	companyA := testutil.SeedCompany(t, q, "Photos A", "PHOA4")
	companyB := testutil.SeedCompany(t, q, "Photos B", "PHOB4")
	adminA := testutil.SeedCompanyAdmin(t, q, companyA, "photos_admin_a")
	adminB := testutil.SeedCompanyAdmin(t, q, companyB, "photos_admin_b")
	purchaseOnlyStaff := seedStaff(t, q, companyA, "photos_buyer", false, true)
	catID := testutil.SeedCategory(t, q, companyA, "Cat")
	productID := testutil.SeedProduct(t, q, companyA, catID, "P-1")
	shopID := testutil.SeedShop(t, q, companyA, "Customer A")
	saleID := seedSale(t, q, companyA, shopID, productID, "S-100", 100, 0)
	saleParam := map[string]string{"id": strconv.Itoa(int(saleID))}

	h := NewAttachmentHandler(q, nil, t.TempDir())
	list, upload, del := h.ForEntity("sale")

	// Company A admin uploads a photo.
	body, contentType := multipartBody(t, pngBytes(t))
	r := asUser("POST", "/", body, companyA, adminA, "COMPANY_ADMIN", false, false, saleParam)
	r.Header.Set("Content-Type", contentType)
	w := httptest.NewRecorder()
	upload(w, r)
	if w.Code != http.StatusCreated {
		t.Fatalf("upload: %d %s", w.Code, w.Body.String())
	}
	var created attachmentResponse
	json.Unmarshal(w.Body.Bytes(), &created)
	if created.ContentType != "image/png" || created.UploadedByName != "photos_admin_a" {
		t.Fatalf("unexpected upload response: %s", w.Body.String())
	}

	// A non-image is rejected even with an image filename.
	body, contentType = multipartBody(t, []byte("%PDF-1.4 not an image"))
	r = asUser("POST", "/", body, companyA, adminA, "COMPANY_ADMIN", false, false, saleParam)
	r.Header.Set("Content-Type", contentType)
	w = httptest.NewRecorder()
	upload(w, r)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("non-image upload should be 400, got %d", w.Code)
	}

	// Company A lists it; company B gets 404 for both the list and the file.
	w = httptest.NewRecorder()
	list(w, asUser("GET", "/", nil, companyA, adminA, "COMPANY_ADMIN", false, false, saleParam))
	var listed []attachmentResponse
	json.Unmarshal(w.Body.Bytes(), &listed)
	if len(listed) != 1 || listed[0].ID != created.ID {
		t.Fatalf("company A list: %s", w.Body.String())
	}
	w = httptest.NewRecorder()
	list(w, asUser("GET", "/", nil, companyB, adminB, "COMPANY_ADMIN", false, false, saleParam))
	if w.Code != http.StatusNotFound {
		t.Fatalf("company B listing company A's sale photos: want 404, got %d", w.Code)
	}
	fileParam := map[string]string{"id": strconv.Itoa(int(created.ID))}
	w = httptest.NewRecorder()
	h.ServeFile(w, asUser("GET", "/", nil, companyB, adminB, "COMPANY_ADMIN", false, false, fileParam))
	if w.Code != http.StatusNotFound {
		t.Fatalf("company B downloading company A's photo: want 404, got %d", w.Code)
	}
	w = httptest.NewRecorder()
	h.ServeFile(w, asUser("GET", "/", nil, companyA, adminA, "COMPANY_ADMIN", false, false, fileParam))
	if w.Code != http.StatusOK || !bytes.Equal(w.Body.Bytes(), pngBytes(t)) {
		t.Fatalf("company A download: %d", w.Code)
	}

	// Staff with only purchase access can't see sale photos or delete anything.
	w = httptest.NewRecorder()
	list(w, asUser("GET", "/", nil, companyA, purchaseOnlyStaff, "STAFF", false, true, saleParam))
	if w.Code != http.StatusForbidden {
		t.Fatalf("purchase-only staff listing sale photos: want 403, got %d", w.Code)
	}
	w = httptest.NewRecorder()
	del(w, asUser("DELETE", "/", bytes.NewBufferString(`{"reason":"x"}`), companyA, purchaseOnlyStaff, "STAFF", true, true,
		map[string]string{"id": strconv.Itoa(int(saleID)), "attachmentId": strconv.Itoa(int(created.ID))}))
	if w.Code != http.StatusForbidden {
		t.Fatalf("staff delete: want 403, got %d", w.Code)
	}

	// A soft-deleted photo disappears from the list and can no longer be served.
	if n, err := q.SoftDeleteAttachment(context.Background(), db.SoftDeleteAttachmentParams{
		ID: created.ID, CompanyID: companyA, DeletedBy: pgInt4Valid(adminA), DeleteReason: pgTextOrNil("wrong bill"),
	}); err != nil || n != 1 {
		t.Fatalf("soft delete: n=%d err=%v", n, err)
	}
	w = httptest.NewRecorder()
	list(w, asUser("GET", "/", nil, companyA, adminA, "COMPANY_ADMIN", false, false, saleParam))
	if w.Body.String() != "[]\n" {
		t.Fatalf("deleted photo still listed: %s", w.Body.String())
	}
}

// TestFrequentItemsAndPins checks pinned items lead the strip, cancelled bills don't count,
// and the "usually buys" list is scoped to one customer.
func TestFrequentItemsAndPins(t *testing.T) {
	q := testutil.OpenTestTx(t)
	ctx := context.Background()
	companyID := testutil.SeedCompany(t, q, "Quick Co", "QUIK4")
	adminID := testutil.SeedCompanyAdmin(t, q, companyID, "quick_admin")
	catID := testutil.SeedCategory(t, q, companyID, "Cat")
	often := testutil.SeedProduct(t, q, companyID, catID, "Q-OFTEN")
	once := testutil.SeedProduct(t, q, companyID, catID, "Q-ONCE")
	pinned := testutil.SeedProduct(t, q, companyID, catID, "Q-PIN")
	onlyCancelled := testutil.SeedProduct(t, q, companyID, catID, "Q-CANC")
	shop1 := testutil.SeedShop(t, q, companyID, "Shop 1")
	shop2 := testutil.SeedShop(t, q, companyID, "Shop 2")

	seedSale(t, q, companyID, shop1, often, "Q-1", 10, 10)
	seedSale(t, q, companyID, shop1, often, "Q-2", 10, 10)
	seedSale(t, q, companyID, shop2, once, "Q-3", 10, 10)
	c := seedSale(t, q, companyID, shop2, onlyCancelled, "Q-4", 10, 10)
	q.CancelSale(ctx, db.CancelSaleParams{ID: c, CompanyID: companyID, CancelledReason: pgTextOrNil("x")})

	h := NewQuickItemsHandler(q)
	w := httptest.NewRecorder()
	h.Pin(w, asUser("PUT", "/", nil, companyID, adminID, "COMPANY_ADMIN", false, false, map[string]string{"id": strconv.Itoa(int(pinned))}))
	if w.Code != http.StatusNoContent {
		t.Fatalf("pin: %d %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	h.Frequent(w, asUser("GET", "/?type=sale", nil, companyID, adminID, "COMPANY_ADMIN", false, false, nil))
	var items []quickItem
	json.Unmarshal(w.Body.Bytes(), &items)
	var got []int32
	for _, it := range items {
		got = append(got, it.ID)
	}
	if len(got) != 3 || got[0] != pinned || got[1] != often || got[2] != once {
		t.Fatalf("want [pinned %d, often %d, once %d], got %v", pinned, often, once, got)
	}

	w = httptest.NewRecorder()
	h.Frequent(w, asUser("GET", "/?type=sale&shop_id="+strconv.Itoa(int(shop2)), nil, companyID, adminID, "COMPANY_ADMIN", false, false, nil))
	items = nil
	json.Unmarshal(w.Body.Bytes(), &items)
	if len(items) != 1 || items[0].ID != once {
		t.Fatalf("usually buys for shop 2: %s", w.Body.String())
	}
}
