package handlers

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/Sivanandha02/retailapp/internal/db"
	appMiddleware "github.com/Sivanandha02/retailapp/internal/middleware"
	billpdf "github.com/Sivanandha02/retailapp/internal/pdf"
)

type BillHandler struct {
	Queries *db.Queries
}

func NewBillHandler(q *db.Queries) *BillHandler {
	return &BillHandler{Queries: q}
}

func (h *BillHandler) writePDF(w http.ResponseWriter, filename string, bytes_ []byte) {
	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", `inline; filename="`+filename+`.pdf"`)
	w.Write(bytes_)
}

func (h *BillHandler) SalePDF(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	sale, err := h.Queries.GetSaleByID(r.Context(), db.GetSaleByIDParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	items, err := h.Queries.ListSaleItems(r.Context(), sale.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	company, err := h.Queries.GetCompanyByID(r.Context(), companyID)
	if err != nil {
		http.Error(w, "company not found", http.StatusInternalServerError)
		return
	}

	data := billpdf.BillData{
		DocumentTitle:     "SALES BILL",
		BillNumber:        sale.BillNumber,
		BillDate:          sale.SaleDate.Time.Format("02-Jan-2006"),
		CompanyName:       company.CompanyName,
		CompanyCode:       company.CompanyCode,
		CounterpartyName:  sale.ShopName,
		CounterpartyPhone: sale.ShopPhone,
		TotalAmount:       numericToFloat(sale.TotalAmount),
		AmountPaid:        numericToFloat(sale.AmountPaid),
		Status:            sale.Status,
		CancelledReason:   sale.CancelledReason.String,
	}
	for _, item := range items {
		data.Items = append(data.Items, billpdf.BillItem{
			ProductName: item.ProductName,
			ProductSKU:  item.ProductSku,
			Unit:        item.Unit,
			Quantity:    numericToFloat(item.Quantity),
			UnitPrice:   numericToFloat(item.UnitPrice),
			LineTotal:   numericToFloat(item.LineTotal),
			BelowCost:   item.BelowCost,
		})
	}

	out, err := billpdf.Generate(data)
	if err != nil {
		http.Error(w, "failed to generate PDF: "+err.Error(), http.StatusInternalServerError)
		return
	}
	h.writePDF(w, sale.BillNumber, out)
}

func (h *BillHandler) PurchasePDF(w http.ResponseWriter, r *http.Request) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	purchase, err := h.Queries.GetPurchaseByID(r.Context(), db.GetPurchaseByIDParams{ID: int32(id), CompanyID: companyID})
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	items, err := h.Queries.ListPurchaseItems(r.Context(), purchase.ID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	company, err := h.Queries.GetCompanyByID(r.Context(), companyID)
	if err != nil {
		http.Error(w, "company not found", http.StatusInternalServerError)
		return
	}

	data := billpdf.BillData{
		DocumentTitle:     "PURCHASE BILL",
		BillNumber:        purchase.BillNumber,
		BillDate:          purchase.PurchaseDate.Time.Format("02-Jan-2006"),
		CompanyName:       company.CompanyName,
		CompanyCode:       company.CompanyCode,
		CounterpartyName:  purchase.FactoryName,
		CounterpartyPhone: purchase.FactoryPhone,
		TotalAmount:       numericToFloat(purchase.TotalAmount),
		AmountPaid:        numericToFloat(purchase.AmountPaid),
		Status:            purchase.Status,
		CancelledReason:   purchase.CancelledReason.String,
	}
	for _, item := range items {
		data.Items = append(data.Items, billpdf.BillItem{
			ProductName: item.ProductName,
			ProductSKU:  item.ProductSku,
			Unit:        item.Unit,
			Quantity:    numericToFloat(item.Quantity),
			UnitPrice:   numericToFloat(item.UnitPrice),
			LineTotal:   numericToFloat(item.LineTotal),
		})
	}

	out, err := billpdf.Generate(data)
	if err != nil {
		http.Error(w, "failed to generate PDF: "+err.Error(), http.StatusInternalServerError)
		return
	}
	h.writePDF(w, purchase.BillNumber, out)
}
