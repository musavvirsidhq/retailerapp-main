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

func (h *BillHandler) loadSaleBill(r *http.Request, id int32) (billpdf.BillData, string, error) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	sale, err := h.Queries.GetSaleByID(r.Context(), db.GetSaleByIDParams{ID: id, CompanyID: companyID})
	if err != nil {
		return billpdf.BillData{}, "", err
	}
	items, err := h.Queries.ListSaleItems(r.Context(), sale.ID)
	if err != nil {
		return billpdf.BillData{}, "", err
	}
	company, err := h.Queries.GetCompanyByID(r.Context(), companyID)
	if err != nil {
		return billpdf.BillData{}, "", err
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
	return data, sale.BillNumber, nil
}

func (h *BillHandler) loadPurchaseBill(r *http.Request, id int32) (billpdf.BillData, string, error) {
	companyID, _ := appMiddleware.CompanyIDFromContext(r.Context())
	purchase, err := h.Queries.GetPurchaseByID(r.Context(), db.GetPurchaseByIDParams{ID: id, CompanyID: companyID})
	if err != nil {
		return billpdf.BillData{}, "", err
	}
	items, err := h.Queries.ListPurchaseItems(r.Context(), purchase.ID)
	if err != nil {
		return billpdf.BillData{}, "", err
	}
	company, err := h.Queries.GetCompanyByID(r.Context(), companyID)
	if err != nil {
		return billpdf.BillData{}, "", err
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
	return data, purchase.BillNumber, nil
}

// SaleDetail returns the sale bill as JSON (GET /api/sales/bills/{id}) for the on-screen bill view.
func (h *BillHandler) SaleDetail(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	data, _, err := h.loadSaleBill(r, int32(id))
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, data)
}

// PurchaseDetail returns the purchase bill as JSON (GET /api/purchases/bills/{id}).
func (h *BillHandler) PurchaseDetail(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	data, _, err := h.loadPurchaseBill(r, int32(id))
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	writeJSON(w, data)
}

func (h *BillHandler) SalePDF(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	data, billNumber, err := h.loadSaleBill(r, int32(id))
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	out, err := billpdf.Generate(data)
	if err != nil {
		http.Error(w, "failed to generate PDF: "+err.Error(), http.StatusInternalServerError)
		return
	}
	h.writePDF(w, billNumber, out)
}

func (h *BillHandler) PurchasePDF(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.Atoi(chi.URLParam(r, "id"))
	if err != nil {
		http.Error(w, "invalid id", http.StatusBadRequest)
		return
	}
	data, billNumber, err := h.loadPurchaseBill(r, int32(id))
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	out, err := billpdf.Generate(data)
	if err != nil {
		http.Error(w, "failed to generate PDF: "+err.Error(), http.StatusInternalServerError)
		return
	}
	h.writePDF(w, billNumber, out)
}
