package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type FactoryHandler struct {
	Queries *db.Queries
}

func NewFactoryHandler(q *db.Queries) *FactoryHandler {
	return &FactoryHandler{Queries: q}
}

type factoryInput struct {
	Name           string `json:"name"`
	ContactPerson  string `json:"contact_person"`
	PrimaryPhone   string `json:"primary_phone"`
	SecondaryPhone string `json:"secondary_phone"`
	Address        string `json:"address"`
}

func (h *FactoryHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	factories, err := h.Queries.ListFactories(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if factories == nil {
		factories = []db.Factory{}
	}
	writeJSON(w, factories)
}

func (h *FactoryHandler) Get(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	factory, err := h.Queries.GetFactory(r.Context(), db.GetFactoryParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, factory)
}

func validatePhones(primary, secondary string) error {
	if !phoneRegex.MatchString(primary) {
		return errInvalidPhone
	}
	if secondary != "" {
		if !phoneRegex.MatchString(secondary) {
			return errInvalidPhone
		}
		if secondary == primary {
			return errSamePhone
		}
	}
	return nil
}

func (h *FactoryHandler) Create(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in factoryInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if in.Name == "" {
		http.Error(w, "name is required", http.StatusBadRequest)
		return
	}
	if err := validatePhones(in.PrimaryPhone, in.SecondaryPhone); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	factory, err := h.Queries.CreateFactory(r.Context(), db.CreateFactoryParams{
		CompanyID:      companyID,
		Name:           in.Name,
		ContactPerson:  pgTextOrNil(in.ContactPerson),
		PrimaryPhone:   in.PrimaryPhone,
		SecondaryPhone: pgTextOrNil(in.SecondaryPhone),
		Address:        pgTextOrNil(in.Address),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, factory)
}

func (h *FactoryHandler) Update(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}

	var in factoryInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}
	if err := validatePhones(in.PrimaryPhone, in.SecondaryPhone); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	factory, err := h.Queries.UpdateFactory(r.Context(), db.UpdateFactoryParams{
		ID:             int32(id),
		CompanyID:      companyID,
		Name:           in.Name,
		ContactPerson:  pgTextOrNil(in.ContactPerson),
		PrimaryPhone:   in.PrimaryPhone,
		SecondaryPhone: pgTextOrNil(in.SecondaryPhone),
		Address:        pgTextOrNil(in.Address),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, factory)
}

func (h *FactoryHandler) Delete(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.Queries.DeleteFactory(r.Context(), db.DeleteFactoryParams{ID: int32(id), CompanyID: companyID}); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
