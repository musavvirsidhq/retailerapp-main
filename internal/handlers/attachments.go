package handlers

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// AttachmentHandler stores proof photos on payments and sale/purchase bills (Cycle 4). Unlike
// product photos these are private financial records: they live outside the public /uploads
// directory and are only served through ServeFile, after a same-company + permission check.
type AttachmentHandler struct {
	Queries *db.Queries
	Pool    *pgxpool.Pool
	Dir     string
}

func NewAttachmentHandler(q *db.Queries, pool *pgxpool.Pool, dir string) *AttachmentHandler {
	return &AttachmentHandler{Queries: q, Pool: pool, Dir: dir}
}

const (
	maxAttachmentUpload     = 5 << 20 // 5MB, same limit as product photos
	maxAttachmentsPerEntity = 5
)

var attachmentExtByType = map[string]string{
	"image/jpeg": ".jpg",
	"image/png":  ".png",
	"image/webp": ".webp",
}

var errAttachmentForbidden = errors.New("forbidden")

type attachmentResponse struct {
	ID             int32     `json:"id"`
	EntityType     string    `json:"entity_type"`
	EntityID       int32     `json:"entity_id"`
	URL            string    `json:"url"`
	ContentType    string    `json:"content_type"`
	SizeBytes      int32     `json:"size_bytes"`
	CreatedAt      time.Time `json:"created_at"`
	UploadedByName string    `json:"uploaded_by_name"`
}

func attachmentURL(id int32) string {
	return fmt.Sprintf("/api/attachments/%d/file", id)
}

// checkEntityAccess confirms the sale/purchase/payment exists in the caller's company and that
// the caller holds the matching permission: sales access for sales and customer payments,
// purchase access for purchases and supplier payments.
func (h *AttachmentHandler) checkEntityAccess(r *http.Request, companyID int32, entityType string, entityID int32) error {
	ctx := r.Context()
	switch entityType {
	case "sale":
		if _, err := h.Queries.GetSaleByID(ctx, db.GetSaleByIDParams{ID: entityID, CompanyID: companyID}); err != nil {
			return err
		}
		if !appMiddleware.HasSalesAccess(ctx) {
			return errAttachmentForbidden
		}
	case "purchase":
		if _, err := h.Queries.GetPurchaseByID(ctx, db.GetPurchaseByIDParams{ID: entityID, CompanyID: companyID}); err != nil {
			return err
		}
		if !appMiddleware.HasPurchaseAccess(ctx) {
			return errAttachmentForbidden
		}
	case "payment":
		p, err := h.Queries.GetPayment(ctx, db.GetPaymentParams{ID: entityID, CompanyID: companyID})
		if err != nil {
			return err
		}
		if (p.PartyType == "shop" && !appMiddleware.HasSalesAccess(ctx)) ||
			(p.PartyType == "factory" && !appMiddleware.HasPurchaseAccess(ctx)) {
			return errAttachmentForbidden
		}
	default:
		return pgx.ErrNoRows
	}
	return nil
}

func writeAccessError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, pgx.ErrNoRows):
		http.Error(w, "not found", http.StatusNotFound)
	case errors.Is(err, errAttachmentForbidden):
		http.Error(w, "forbidden: you don't have access to this record", http.StatusForbidden)
	default:
		http.Error(w, err.Error(), http.StatusInternalServerError)
	}
}

// ForEntity returns the list/upload/delete handlers for one entity type, so routes can be
// mounted as /api/sales/{id}/attachments, /api/purchases/{id}/attachments, and so on.
func (h *AttachmentHandler) ForEntity(entityType string) (list, upload, del http.HandlerFunc) {
	list = func(w http.ResponseWriter, r *http.Request) { h.list(w, r, entityType) }
	upload = func(w http.ResponseWriter, r *http.Request) { h.upload(w, r, entityType) }
	del = func(w http.ResponseWriter, r *http.Request) { h.delete(w, r, entityType) }
	return
}

func entityIDParam(r *http.Request) (int32, error) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	return int32(id), err
}

func (h *AttachmentHandler) list(w http.ResponseWriter, r *http.Request, entityType string) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	entityID, err := entityIDParam(r)
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.checkEntityAccess(r, companyID, entityType, entityID); err != nil {
		writeAccessError(w, err)
		return
	}
	rows, err := h.Queries.ListAttachments(r.Context(), db.ListAttachmentsParams{
		CompanyID: companyID, EntityType: entityType, EntityID: entityID,
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	out := make([]attachmentResponse, 0, len(rows))
	for _, a := range rows {
		out = append(out, attachmentResponse{
			ID: a.ID, EntityType: a.EntityType, EntityID: a.EntityID, URL: attachmentURL(a.ID),
			ContentType: a.ContentType, SizeBytes: a.SizeBytes, CreatedAt: a.CreatedAt.Time,
			UploadedByName: a.UploadedByName,
		})
	}
	writeJSON(w, out)
}

func (h *AttachmentHandler) upload(w http.ResponseWriter, r *http.Request, entityType string) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	userID, _ := appMiddleware.UserIDFromContext(r.Context())
	entityID, err := entityIDParam(r)
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.checkEntityAccess(r, companyID, entityType, entityID); err != nil {
		writeAccessError(w, err)
		return
	}
	count, err := h.Queries.CountAttachments(r.Context(), db.CountAttachmentsParams{
		CompanyID: companyID, EntityType: entityType, EntityID: entityID,
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if count >= maxAttachmentsPerEntity {
		http.Error(w, fmt.Sprintf("a record can have at most %d photos", maxAttachmentsPerEntity), http.StatusBadRequest)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxAttachmentUpload+(64<<10))
	if err := r.ParseMultipartForm(maxAttachmentUpload); err != nil {
		http.Error(w, "photo must be under 5MB", http.StatusBadRequest)
		return
	}
	file, _, err := r.FormFile("file")
	if err != nil {
		http.Error(w, "missing file", http.StatusBadRequest)
		return
	}
	defer file.Close()

	// Trust the bytes, not the client's filename or Content-Type header.
	head := make([]byte, 512)
	n, _ := io.ReadFull(file, head)
	contentType := http.DetectContentType(head[:n])
	ext, ok := attachmentExtByType[contentType]
	if !ok {
		http.Error(w, "photo must be a jpg, png or webp image", http.StatusBadRequest)
		return
	}
	if _, err := file.Seek(0, io.SeekStart); err != nil {
		http.Error(w, "failed to read photo", http.StatusInternalServerError)
		return
	}

	nameBytes := make([]byte, 16)
	if _, err := rand.Read(nameBytes); err != nil {
		http.Error(w, "failed to generate file name", http.StatusInternalServerError)
		return
	}
	relPath := filepath.ToSlash(filepath.Join(
		strconv.Itoa(int(companyID)), entityType, strconv.Itoa(int(entityID)), hex.EncodeToString(nameBytes)+ext,
	))
	fullPath := filepath.Join(h.Dir, filepath.FromSlash(relPath))
	if err := os.MkdirAll(filepath.Dir(fullPath), 0o750); err != nil {
		http.Error(w, "failed to store photo", http.StatusInternalServerError)
		return
	}
	dest, err := os.Create(fullPath)
	if err != nil {
		http.Error(w, "failed to store photo", http.StatusInternalServerError)
		return
	}
	size, copyErr := io.Copy(dest, file)
	closeErr := dest.Close()
	if copyErr != nil || closeErr != nil {
		_ = os.Remove(fullPath)
		http.Error(w, "failed to store photo", http.StatusInternalServerError)
		return
	}

	a, err := h.Queries.CreateAttachment(r.Context(), db.CreateAttachmentParams{
		CompanyID: companyID, EntityType: entityType, EntityID: entityID, FilePath: relPath,
		ContentType: contentType, SizeBytes: int32(size), UploadedBy: pgInt4Valid(userID),
	})
	if err != nil {
		_ = os.Remove(fullPath)
		http.Error(w, "failed to save photo record: "+err.Error(), http.StatusInternalServerError)
		return
	}
	uploader := ""
	if u, err := h.Queries.GetUser(r.Context(), userID); err == nil {
		uploader = u.Name
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, attachmentResponse{
		ID: a.ID, EntityType: a.EntityType, EntityID: a.EntityID, URL: attachmentURL(a.ID),
		ContentType: a.ContentType, SizeBytes: a.SizeBytes, CreatedAt: a.CreatedAt.Time, UploadedByName: uploader,
	})
}

type deleteAttachmentInput struct {
	Reason string `json:"reason"`
}

// delete soft-deletes a photo (Company Admin only) and writes an audit_log row. The file stays
// on disk: a deleted proof photo may still be needed if a dispute is escalated.
func (h *AttachmentHandler) delete(w http.ResponseWriter, r *http.Request, entityType string) {
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)
	userID, _ := appMiddleware.UserIDFromContext(ctx)
	if !appMiddleware.IsCompanyAdmin(ctx) {
		http.Error(w, "forbidden: only a company admin can delete photos", http.StatusForbidden)
		return
	}
	entityID, err := entityIDParam(r)
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	attachmentID, err := strconv.Atoi(chi.URLParam(r, "attachmentId"))
	if err != nil {
		http.Error(w, "invalid attachment id", http.StatusBadRequest)
		return
	}
	var in deleteAttachmentInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil || strings.TrimSpace(in.Reason) == "" {
		http.Error(w, "a reason is required to delete a photo", http.StatusBadRequest)
		return
	}

	a, err := h.Queries.GetAttachment(ctx, db.GetAttachmentParams{ID: int32(attachmentID), CompanyID: companyID})
	if err != nil || a.EntityType != entityType || a.EntityID != entityID {
		http.Error(w, "photo not found", http.StatusNotFound)
		return
	}

	tx, err := h.Pool.Begin(ctx)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer tx.Rollback(ctx)
	qtx := h.Queries.WithTx(tx)

	if _, err := qtx.SoftDeleteAttachment(ctx, db.SoftDeleteAttachmentParams{
		ID: a.ID, CompanyID: companyID, DeletedBy: pgInt4Valid(userID), DeleteReason: pgTextOrNil(strings.TrimSpace(in.Reason)),
	}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	impact, _ := json.Marshal(map[string]interface{}{"entity_type": a.EntityType, "entity_id": a.EntityID})
	if _, err := qtx.CreateAuditLogEntry(ctx, db.CreateAuditLogEntryParams{
		CompanyID:     pgInt4Valid(companyID),
		ActorUserID:   pgInt4Valid(userID),
		Action:        "DELETE_ATTACHMENT",
		EntityType:    "attachment",
		EntityID:      a.ID,
		Reason:        pgTextOrNil(strings.TrimSpace(in.Reason)),
		PaymentImpact: impact,
	}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if err := tx.Commit(ctx); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// ServeFile streams one photo after the same company and permission checks as listing it.
func (h *AttachmentHandler) ServeFile(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	a, err := h.Queries.GetAttachment(r.Context(), db.GetAttachmentParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "photo not found", http.StatusNotFound)
		return
	}
	if err := h.checkEntityAccess(r, companyID, a.EntityType, a.EntityID); err != nil {
		writeAccessError(w, err)
		return
	}
	w.Header().Set("Content-Type", a.ContentType)
	w.Header().Set("Cache-Control", "private, max-age=86400")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	http.ServeFile(w, r, filepath.Join(h.Dir, filepath.FromSlash(a.FilePath)))
}
