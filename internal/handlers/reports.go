package handlers

import (
	"net/http"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
)

type ReportHandler struct {
	Queries *db.Queries
}

func NewReportHandler(q *db.Queries) *ReportHandler {
	return &ReportHandler{Queries: q}
}

func (h *ReportHandler) ShopDues(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	rows, err := h.Queries.ShopDues(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if rows == nil {
		rows = []db.ShopDuesRow{}
	}
	writeJSON(w, rows)
}

func (h *ReportHandler) FactoryPayables(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	rows, err := h.Queries.FactoryPayables(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if rows == nil {
		rows = []db.FactoryPayablesRow{}
	}
	writeJSON(w, rows)
}

func (h *ReportHandler) LowStock(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	rows, err := h.Queries.LowStockProducts(r.Context(), companyID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if rows == nil {
		rows = []db.Product{}
	}
	writeJSON(w, rows)
}

func (h *ReportHandler) Dashboard(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	companyID, _ := appMiddleware.CompanyIDFromContext(ctx)

	todaySales, err := h.Queries.TodaySalesSummary(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get today's sales: "+err.Error(), http.StatusInternalServerError)
		return
	}

	todayPurchases, err := h.Queries.TodayPurchasesSummary(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get today's purchases: "+err.Error(), http.StatusInternalServerError)
		return
	}

	profit, err := h.Queries.ProfitSummary(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get profit summary: "+err.Error(), http.StatusInternalServerError)
		return
	}

	lowStock, err := h.Queries.LowStockProducts(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get low stock: "+err.Error(), http.StatusInternalServerError)
		return
	}
	if lowStock == nil {
		lowStock = []db.Product{}
	}

	shopDues, err := h.Queries.ShopDues(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get shop dues: "+err.Error(), http.StatusInternalServerError)
		return
	}
	if shopDues == nil {
		shopDues = []db.ShopDuesRow{}
	}

	factoryPayables, err := h.Queries.FactoryPayables(ctx, companyID)
	if err != nil {
		http.Error(w, "failed to get factory payables: "+err.Error(), http.StatusInternalServerError)
		return
	}
	if factoryPayables == nil {
		factoryPayables = []db.FactoryPayablesRow{}
	}

	writeJSON(w, map[string]interface{}{
		"today_sales":      todaySales,
		"today_purchases":  todayPurchases,
		"total_profit":     profit,
		"low_stock":        lowStock,
		"shop_dues":        shopDues,
		"factory_payables": factoryPayables,
	})
}
