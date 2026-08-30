package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
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
	Phone          string `json:"phone"`
	Address        string `json:"address"`
}

func (h *FactoryHandler) List(w http.ResponseWriter, r *http.Request) {
	factories, err := h.Queries.ListFactories(r.Context())
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
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	factory, err := h.Queries.GetFactory(r.Context(), int32(id))
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	writeJSON(w, factory)
}

func (h *FactoryHandler) Create(w http.ResponseWriter, r *http.Request) {
	var in factoryInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil {
		http.Error(w, "invalid body", http.StatusBadRequest)
		return
	}

	factory, err := h.Queries.CreateFactory(r.Context(), db.CreateFactoryParams{
		Name:          in.Name,
		ContactPerson: pgTextOrNil(in.ContactPerson),
		Phone:         pgTextOrNil(in.Phone),
		Address:       pgTextOrNil(in.Address),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, factory)
}

func (h *FactoryHandler) Update(w http.ResponseWriter, r *http.Request) {
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

	factory, err := h.Queries.UpdateFactory(r.Context(), db.UpdateFactoryParams{
		ID:            int32(id),
		Name:          in.Name,
		ContactPerson: pgTextOrNil(in.ContactPerson),
		Phone:         pgTextOrNil(in.Phone),
		Address:       pgTextOrNil(in.Address),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, factory)
}

func (h *FactoryHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if err := h.Queries.DeleteFactory(r.Context(), int32(id)); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
