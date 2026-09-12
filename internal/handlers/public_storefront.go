package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
)

// PublicStorefrontHandler serves the unauthenticated public catalog/ordering site. Every route
// is scoped by a company's public company_code (e.g. /public/ACME123/...), which keeps the
// door open for every company to eventually run its own storefront even though only one is
// switched on today - nothing here is hardcoded to a single company.
type PublicStorefrontHandler struct {
	Queries *db.Queries
	Pool    *pgxpool.Pool
}

func NewPublicStorefrontHandler(q *db.Queries, pool *pgxpool.Pool) *PublicStorefrontHandler {
	return &PublicStorefrontHandler{Queries: q, Pool: pool}
}

// resolveStorefront looks up the company by its public code and confirms its storefront is
// turned on, writing a 404 either way if not (a disabled storefront and an unknown code look
// identical to a public visitor).
func (h *PublicStorefrontHandler) resolveStorefront(w http.ResponseWriter, r *http.Request) (db.Company, db.CompanyStorefrontSetting, bool) {
	code := chi.URLParam(r, "companyCode")
	company, err := h.Queries.GetCompanyByCode(r.Context(), code)
	if err != nil {
		http.Error(w, "storefront not found", http.StatusNotFound)
		return db.Company{}, db.CompanyStorefrontSetting{}, false
	}
	settings, err := h.Queries.GetStorefrontSettings(r.Context(), company.ID)
	if err != nil || !settings.Enabled {
		http.Error(w, "storefront not found", http.StatusNotFound)
		return db.Company{}, db.CompanyStorefrontSetting{}, false
	}
	return company, settings, true
}

func (h *PublicStorefrontHandler) Info(w http.ResponseWriter, r *http.Request) {
	company, settings, ok := h.resolveStorefront(w, r)
	if !ok {
		return
	}
	resp := map[string]interface{}{
		"company_name": company.CompanyName,
		"cod_enabled":  settings.CodEnabled,
	}
	if settings.ContactEnabled {
		resp["contact_enabled"] = true
		resp["contact_phone"] = settings.ContactPhone.String
	} else {
		resp["contact_enabled"] = false
	}
	writeJSON(w, resp)
}

type publicProductImage struct {
	URL string `json:"url"`
}

type publicPackItem struct {
	Size     string `json:"size"`
	Quantity int32  `json:"quantity"`
}

type publicProduct struct {
	ID           int32                `json:"id"`
	Name         string               `json:"name"`
	Description  string               `json:"description"`
	Unit         string               `json:"unit"`
	SellingPrice float64              `json:"selling_price"`
	InStock      bool                 `json:"in_stock"`
	IsBundle     bool                 `json:"is_bundle"`
	Images       []publicProductImage `json:"images"`
	PackItems    []publicPackItem     `json:"pack_items,omitempty"`
}

func (h *PublicStorefrontHandler) toPublicProduct(r *http.Request, companyID int32, p db.Product) (publicProduct, error) {
	images, err := h.Queries.ListProductImages(r.Context(), db.ListProductImagesParams{ProductID: p.ID, CompanyID: companyID})
	if err != nil {
		return publicProduct{}, err
	}
	out := publicProduct{
		ID:           p.ID,
		Name:         p.Name,
		Description:  p.Description.String,
		Unit:         p.Unit,
		SellingPrice: numericToFloat(p.CurrentSellingPrice),
		InStock:      numericToFloat(p.CurrentStock) > 0,
		IsBundle:     p.IsBundle,
		Images:       []publicProductImage{},
	}
	for _, img := range images {
		out.Images = append(out.Images, publicProductImage{URL: img.Url})
	}
	if p.IsBundle {
		packItems, err := h.Queries.ListProductPackItems(r.Context(), db.ListProductPackItemsParams{ProductID: p.ID, CompanyID: companyID})
		if err != nil {
			return publicProduct{}, err
		}
		for _, item := range packItems {
			out.PackItems = append(out.PackItems, publicPackItem{Size: item.Size, Quantity: item.Quantity})
		}
	}
	return out, nil
}

func (h *PublicStorefrontHandler) ListProducts(w http.ResponseWriter, r *http.Request) {
	company, _, ok := h.resolveStorefront(w, r)
	if !ok {
		return
	}
	products, err := h.Queries.ListStorefrontProducts(r.Context(), company.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	out := make([]publicProduct, 0, len(products))
	for _, p := range products {
		pub, err := h.toPublicProduct(r, company.ID, p)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		out = append(out, pub)
	}
	writeJSON(w, out)
}

func (h *PublicStorefrontHandler) GetProduct(w http.ResponseWriter, r *http.Request) {
	company, _, ok := h.resolveStorefront(w, r)
	if !ok {
		return
	}
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	product, err := h.Queries.GetStorefrontProduct(r.Context(), db.GetStorefrontProductParams{ID: int32(id), CompanyID: company.ID})
	if err != nil {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}
	pub, err := h.toPublicProduct(r, company.ID, product)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, pub)
}

type publicOrderItemInput struct {
	ProductID int32 `json:"product_id"`
	Quantity  int32 `json:"quantity"`
}

type publicOrderInput struct {
	CustomerName      string                 `json:"customer_name"`
	CustomerPhone     string                 `json:"customer_phone"`
	CustomerAddress   string                 `json:"customer_address"`
	FulfillmentMethod string                 `json:"fulfillment_method"` // "COD" or "CONTACT"
	Items             []publicOrderItemInput `json:"items"`
}

// CreateOrder records a no-payment order (cash on delivery or "we'll call you back"). Nothing
// here touches product stock or the existing sales/FIFO-cost machinery - it's a lead for staff
// to follow up on and fulfill manually, exactly like a phone order would be today.
func (h *PublicStorefrontHandler) CreateOrder(w http.ResponseWriter, r *http.Request) {
	company, settings, ok := h.resolveStorefront(w, r)
	if !ok {
		return
	}
	var in publicOrderInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.CustomerName == "" || in.CustomerPhone == "" {
		http.Error(w, "name and phone number are required", http.StatusBadRequest)
		return
	}
	if len(in.Items) == 0 {
		http.Error(w, "order must have at least one item", http.StatusBadRequest)
		return
	}
	switch in.FulfillmentMethod {
	case "COD":
		if !settings.CodEnabled {
			http.Error(w, "cash on delivery is not available right now", http.StatusBadRequest)
			return
		}
	case "CONTACT":
		if !settings.ContactEnabled {
			http.Error(w, "contact-to-purchase is not available right now", http.StatusBadRequest)
			return
		}
	default:
		http.Error(w, "fulfillment_method must be 'COD' or 'CONTACT'", http.StatusBadRequest)
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

	var total float64
	type lineItem struct {
		productID int32
		quantity  int32
		unitPrice float64
	}
	var lines []lineItem
	for _, item := range in.Items {
		if item.Quantity <= 0 {
			http.Error(w, "quantity must be greater than 0", http.StatusBadRequest)
			return
		}
		product, err := qtx.GetStorefrontProduct(ctx, db.GetStorefrontProductParams{ID: item.ProductID, CompanyID: company.ID})
		if err != nil {
			http.Error(w, "one of the selected products is no longer available", http.StatusBadRequest)
			return
		}
		unitPrice := numericToFloat(product.CurrentSellingPrice)
		total += unitPrice * float64(item.Quantity)
		lines = append(lines, lineItem{productID: item.ProductID, quantity: item.Quantity, unitPrice: unitPrice})
	}

	order, err := qtx.CreatePublicOrder(ctx, db.CreatePublicOrderParams{
		CompanyID:         company.ID,
		CustomerName:      in.CustomerName,
		CustomerPhone:     in.CustomerPhone,
		CustomerAddress:   pgTextOrNil(in.CustomerAddress),
		FulfillmentMethod: in.FulfillmentMethod,
		TotalAmount:       numericFromFloat(total),
	})
	if err != nil {
		http.Error(w, "failed to create order: "+err.Error(), http.StatusInternalServerError)
		return
	}
	for _, line := range lines {
		if _, err := qtx.CreatePublicOrderItem(ctx, db.CreatePublicOrderItemParams{
			OrderID:   order.ID,
			ProductID: line.productID,
			Quantity:  line.quantity,
			UnitPrice: numericFromFloat(line.unitPrice),
			LineTotal: numericFromFloat(line.unitPrice * float64(line.quantity)),
		}); err != nil {
			http.Error(w, "failed to create order item: "+err.Error(), http.StatusInternalServerError)
			return
		}
	}
	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	resp := map[string]interface{}{
		"id":                 order.ID,
		"total_amount":       total,
		"fulfillment_method": order.FulfillmentMethod,
		"status":             order.Status,
	}
	if in.FulfillmentMethod == "CONTACT" {
		resp["contact_phone"] = settings.ContactPhone.String
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, resp)
}
