package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

// StorefrontSettingsHandler lets a company admin turn its public storefront on/off and choose
// which no-payment purchase options (cash on delivery / reveal our contact number) are offered,
// and review the orders that come in through it.
type StorefrontSettingsHandler struct {
	Queries *db.Queries
}

func NewStorefrontSettingsHandler(q *db.Queries) *StorefrontSettingsHandler {
	return &StorefrontSettingsHandler{Queries: q}
}

func settingsResponse(s db.CompanyStorefrontSetting) map[string]interface{} {
	return map[string]interface{}{
		"enabled":         s.Enabled,
		"cod_enabled":     s.CodEnabled,
		"contact_enabled": s.ContactEnabled,
		"contact_phone":   s.ContactPhone.String,
	}
}

func (h *StorefrontSettingsHandler) Get(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	settings, err := h.Queries.GetStorefrontSettings(r.Context(), companyID)
	if err != nil {
		if err == pgx.ErrNoRows {
			// No row yet - a company that has never touched its storefront settings gets the
			// same defaults the column definitions declare, without needing a row pre-created.
			writeJSON(w, map[string]interface{}{
				"enabled": false, "cod_enabled": true, "contact_enabled": true, "contact_phone": "",
			})
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, settingsResponse(settings))
}

type storefrontSettingsInput struct {
	Enabled        bool   `json:"enabled"`
	CodEnabled     bool   `json:"cod_enabled"`
	ContactEnabled bool   `json:"contact_enabled"`
	ContactPhone   string `json:"contact_phone"`
}

func (h *StorefrontSettingsHandler) Update(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in storefrontSettingsInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Enabled && !in.CodEnabled && !in.ContactEnabled {
		http.Error(w, "at least one purchase option (cash on delivery or contact number) must stay on while the storefront is enabled", http.StatusBadRequest)
		return
	}
	if in.ContactEnabled && in.ContactPhone == "" {
		http.Error(w, "a contact number is required to enable the contact-to-purchase option", http.StatusBadRequest)
		return
	}
	settings, err := h.Queries.UpsertStorefrontSettings(r.Context(), db.UpsertStorefrontSettingsParams{
		CompanyID:      companyID,
		Enabled:        in.Enabled,
		CodEnabled:     in.CodEnabled,
		ContactEnabled: in.ContactEnabled,
		ContactPhone:   pgTextOrNil(in.ContactPhone),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, settingsResponse(settings))
}

func (h *StorefrontSettingsHandler) ListOrders(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	orders, err := h.Queries.ListPublicOrders(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if orders == nil {
		orders = []db.PublicOrder{}
	}
	writeJSON(w, orders)
}

func (h *StorefrontSettingsHandler) GetOrderItems(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if _, err := h.Queries.GetPublicOrder(r.Context(), db.GetPublicOrderParams{ID: int32(id), CompanyID: companyID}); err != nil {
		http.Error(w, "order not found", http.StatusNotFound)
		return
	}
	items, err := h.Queries.ListPublicOrderItems(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if items == nil {
		items = []db.ListPublicOrderItemsRow{}
	}
	writeJSON(w, items)
}

type updateOrderStatusInput struct {
	Status string `json:"status"`
}

func (h *StorefrontSettingsHandler) UpdateOrderStatus(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	var in updateOrderStatusInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Status != "NEW" && in.Status != "CONFIRMED" && in.Status != "CANCELLED" {
		http.Error(w, "status must be NEW, CONFIRMED or CANCELLED", http.StatusBadRequest)
		return
	}
	order, err := h.Queries.UpdatePublicOrderStatus(r.Context(), db.UpdatePublicOrderStatusParams{
		ID:        int32(id),
		CompanyID: companyID,
		Status:    in.Status,
	})
	if err != nil {
		http.Error(w, "order not found", http.StatusNotFound)
		return
	}
	writeJSON(w, order)
}
