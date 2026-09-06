package handlers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type CategoryHandler struct {
	Queries *db.Queries
}

func NewCategoryHandler(q *db.Queries) *CategoryHandler {
	return &CategoryHandler{Queries: q}
}

type categoryInput struct {
	Name string `json:"name"`
}

func (h *CategoryHandler) List(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	categories, err := h.Queries.ListCategories(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if categories == nil {
		categories = []db.Category{}
	}
	writeJSON(w, categories)
}

func (h *CategoryHandler) Create(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	var in categoryInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil || in.Name == "" {
		http.Error(w, "name is required", http.StatusBadRequest)
		return
	}
	category, err := h.Queries.CreateCategory(r.Context(), db.CreateCategoryParams{CompanyID: companyID, Name: in.Name})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a category with this name already exists", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, category)
}

func (h *CategoryHandler) ListSubcategories(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	categoryID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	subcategories, err := h.Queries.ListSubcategories(r.Context(), db.ListSubcategoriesParams{
		CompanyID:  companyID,
		CategoryID: int32(categoryID),
	})
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if subcategories == nil {
		subcategories = []db.Subcategory{}
	}
	writeJSON(w, subcategories)
}

func (h *CategoryHandler) CreateSubcategory(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	categoryID, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	if _, err := h.Queries.GetCategory(r.Context(), db.GetCategoryParams{ID: int32(categoryID), CompanyID: companyID}); err != nil {
		http.Error(w, "category not found", http.StatusNotFound)
		return
	}
	var in categoryInput
	if err := json.NewDecoder(r.Body).Decode(&in); err != nil || in.Name == "" {
		http.Error(w, "name is required", http.StatusBadRequest)
		return
	}
	subcategory, err := h.Queries.CreateSubcategory(r.Context(), db.CreateSubcategoryParams{
		CompanyID:  companyID,
		CategoryID: int32(categoryID),
		Name:       in.Name,
	})
	if err != nil {
		if isUniqueViolation(err) {
			http.Error(w, "a subcategory with this name already exists in this category", http.StatusConflict)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
	writeJSON(w, subcategory)
}
