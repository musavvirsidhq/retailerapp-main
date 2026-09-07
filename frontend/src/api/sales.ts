const API_BASE = "/api/sales"

export interface Sale {
  ID: number
  BillNumber: string
  ShopID: number
  ShopName: string
  SaleDate: string
  TotalAmount: string
  AmountPaid: string
  PaymentType: string
  Status: "COMPLETED" | "CANCELLED"
  CreatedAt: string
}

export interface SaleItem {
  ID: number
  SaleID: number
  ProductID: number
  ProductName: string
  ProductSku: string
  Unit: string
  Quantity: string
  UnitPrice: string
  LineTotal: string
  BelowCost: boolean
}

export interface SaleItemInput {
  product_id: number
  quantity: number
  unit_price: number
}

export interface SaleInput {
  shop_id: number
  amount_paid: number
  payment_type: "cash" | "credit"
  items: SaleItemInput[]
}

export async function listSales(): Promise<Sale[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch sales")
  return res.json()
}

export async function getSaleItems(id: number): Promise<SaleItem[]> {
  const res = await fetch(`${API_BASE}/${id}/items`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch sale items")
  return res.json()
}

export async function createSale(input: SaleInput): Promise<Sale> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || "Failed to create sale")
  }
  return res.json()
}

export async function cancelSale(id: number, reason: string): Promise<Sale> {
  const res = await fetch(`${API_BASE}/bills/${id}/cancel`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to cancel sale")
  return res.json()
}
