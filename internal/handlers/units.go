package handlers

import (
	"net/http"

	"github.com/Sivanandha02/retailapp/internal/db"
)

type UnitHandler struct {
	Queries *db.Queries
}

func NewUnitHandler(q *db.Queries) *UnitHandler {
	return &UnitHandler{Queries: q}
}

func (h *UnitHandler) List(w http.ResponseWriter, r *http.Request) {
	units, err := h.Queries.ListUnits(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if units == nil {
		units = []db.Unit{}
	}
	writeJSON(w, units)
}
