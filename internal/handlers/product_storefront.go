package handlers

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// ProductStorefrontHandler manages the storefront-facing side of a product: photos, the
// description/visibility flags, and (for bundle products) the fixed size assortment that makes
// up one pack. Kept separate from ProductHandler so the existing product create/update flow
// that the admin UI and Android app already depend on doesn't change shape.
type ProductStorefrontHandler struct {
	Queries    *db.Queries
	Pool       *pgxpool.Pool
	UploadsDir string
}

func NewProductStorefrontHandler(q *db.Queries, pool *pgxpool.Pool, uploadsDir string) *ProductStorefrontHandler {
	return &ProductStorefrontHandler{Queries: q, Pool: pool, UploadsDir: uploadsDir}
}

var allowedImageExt = map[string]bool{
	".jpg": true, ".jpeg": true, ".png": true, ".webp": true,
}

const maxImageUpload = 5 << 20 // 5MB

// UploadImage saves one product photo to disk and records it. Multiple photos per product are
// supported by calling this repeatedly - the storefront shows them in sort_order.
func (h *ProductStorefrontHandler) UploadImage(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if _, err := h.Queries.GetProduct(r.Context(), db.GetProductParams{ID: int32(productID), CompanyID: companyID}); err != nil {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxImageUpload)
	if err := r.ParseMultipartForm(maxImageUpload); err != nil {
		http.Error(w, "image must be under 5MB", http.StatusBadRequest)
		return
	}
	file, header, err := r.FormFile("image")
	if err != nil {
		http.Error(w, "missing image file", http.StatusBadRequest)
		return
	}
	defer file.Close()

	ext := strings.ToLower(filepath.Ext(header.Filename))
	if !allowedImageExt[ext] {
		http.Error(w, "image must be a jpg, png or webp file", http.StatusBadRequest)
		return
	}

	nameBytes := make([]byte, 16)
	if _, err := rand.Read(nameBytes); err != nil {
		http.Error(w, "failed to generate file name", http.StatusInternalServerError)
		return
	}
	fileName := hex.EncodeToString(nameBytes) + ext

	dir := filepath.Join(h.UploadsDir, "products", strconv.Itoa(int(companyID)), strconv.Itoa(productID))
	if err := os.MkdirAll(dir, 0o755); err != nil {
		http.Error(w, "failed to store image", http.StatusInternalServerError)
		return
	}
	dest, err := os.Create(filepath.Join(dir, fileName))
	if err != nil {
		http.Error(w, "failed to store image", http.StatusInternalServerError)
		return
	}
	defer dest.Close()
	if _, err := io.Copy(dest, file); err != nil {
		http.Error(w, "failed to store image", http.StatusInternalServerError)
		return
	}

	url := fmt.Sprintf("/uploads/products/%d/%d/%s", companyID, productID, fileName)
	image, err := h.Queries.CreateProductImage(r.Context(), db.CreateProductImageParams{
		CompanyID: companyID,
		ProductID: int32(productID),
		Url:       url,
	})
	if err != nil {
		http.Error(w, "failed to save image record: "+err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, image)
}

func (h *ProductStorefrontHandler) ListImages(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	images, err := h.Queries.ListProductImages(r.Context(), db.ListProductImagesParams{ProductID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if images == nil {
		images = []db.ProductImage{}
	}
	writeJSON(w, images)
}

func (h *ProductStorefrontHandler) DeleteImage(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	imageID, err := strconv.Atoi(chi.URLParam(r, "imageId"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	image, err := h.Queries.GetProductImage(r.Context(), db.GetProductImageParams{ID: int32(imageID), CompanyID: companyID})
	if err != nil {
		http.Error(w, "image not found", http.StatusNotFound)
		return
	}
	if err := h.Queries.DeleteProductImage(r.Context(), db.DeleteProductImageParams{ID: int32(imageID), CompanyID: companyID}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	// Best-effort: the DB row is the source of truth, so a leftover file on disk after a
	// failed remove isn't worth failing the request over.
	_ = os.Remove(filepath.Join(h.UploadsDir, filepath.FromSlash(strings.TrimPrefix(image.Url, "/uploads/"))))
	w.WriteHeader(http.StatusNoContent)
}

var validPackSizes = map[string]bool{
	"S": true, "M": true, "L": true, "XL": true, "XXL": true, "XXXL": true, "FREE": true,
}

type packItemInput struct {
	Size     string `json:"size"`
	Quantity int32  `json:"quantity"`
}

// SetPackItems replaces a bundle product's whole size composition in one call, e.g.
// [{"size":"S","quantity":2},{"size":"M","quantity":3}] means one pack = 2 S + 3 M.
func (h *ProductStorefrontHandler) SetPackItems(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var items []packItemInput
	if err := json.NewDecoder(r.Body).Decode(&items); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	for _, item := range items {
		if !validPackSizes[item.Size] {
			http.Error(w, "size must be one of S, M, L, XL, XXL, XXXL, FREE", http.StatusBadRequest)
			return
		}
		if item.Quantity <= 0 {
			http.Error(w, "quantity must be greater than 0", http.StatusBadRequest)
			return
		}
	}

	ctx := r.Context()
	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, "failed to start transaction", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)
	qtx := h.Queries.WithTx(tx)

	if _, err := qtx.GetProduct(ctx, db.GetProductParams{ID: int32(productID), CompanyID: companyID}); err != nil {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}
	if err := qtx.DeleteProductPackItems(ctx, db.DeleteProductPackItemsParams{ProductID: int32(productID), CompanyID: companyID}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	for _, item := range items {
		if _, err := qtx.CreateProductPackItem(ctx, db.CreateProductPackItemParams{
			CompanyID: companyID,
			ProductID: int32(productID),
			Size:      item.Size,
			Quantity:  item.Quantity,
		}); err != nil {
			if isUniqueViolation(err) {
				http.Error(w, "duplicate size in pack composition", http.StatusBadRequest)
				return
			}
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
	if err := tx.Commit(ctx); err != nil {
		http.Error(w, "failed to commit transaction", http.StatusInternalServerError)
		return
	}

	packItems, err := h.Queries.ListProductPackItems(ctx, db.ListProductPackItemsParams{ProductID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if packItems == nil {
		packItems = []db.ProductPackItem{}
	}
	writeJSON(w, packItems)
}

func (h *ProductStorefrontHandler) ListPackItems(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	packItems, err := h.Queries.ListProductPackItems(r.Context(), db.ListProductPackItemsParams{ProductID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if packItems == nil {
		packItems = []db.ProductPackItem{}
	}
	writeJSON(w, packItems)
}

type storefrontDetailsInput struct {
	Description       string `json:"description"`
	IsBundle          bool   `json:"is_bundle"`
	StorefrontVisible bool   `json:"storefront_visible"`
}

// UpdateDetails sets a product's storefront-facing description/visibility. Left as its own
// endpoint (rather than folding into ProductHandler.Update) so turning on the storefront for a
// product can't accidentally overwrite its name/price/sku if the two forms ever drift.
func (h *ProductStorefrontHandler) UpdateDetails(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var in storefrontDetailsInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	product, err := h.Queries.UpdateProductStorefront(r.Context(), db.UpdateProductStorefrontParams{
		ID:                int32(productID),
		CompanyID:         companyID,
		Description:       pgTextOrNil(in.Description),
		IsBundle:          in.IsBundle,
		StorefrontVisible: in.StorefrontVisible,
	})
	if err != nil {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}
	writeJSON(w, product)
}

// Details returns everything the admin edit form needs in one call: the product row, its
// photos, and its pack composition.
func (h *ProductStorefrontHandler) Details(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	productID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	product, err := h.Queries.GetProduct(r.Context(), db.GetProductParams{ID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, "product not found", http.StatusNotFound)
		return
	}
	images, err := h.Queries.ListProductImages(r.Context(), db.ListProductImagesParams{ProductID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if images == nil {
		images = []db.ProductImage{}
	}
	packItems, err := h.Queries.ListProductPackItems(r.Context(), db.ListProductPackItemsParams{ProductID: int32(productID), CompanyID: companyID})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if packItems == nil {
		packItems = []db.ProductPackItem{}
	}
	writeJSON(w, map[string]interface{}{
		"product":    product,
		"images":     images,
		"pack_items": packItems,
	})
}
