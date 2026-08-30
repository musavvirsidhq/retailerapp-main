const API_BASE = "http://localhost:8080/api/sales"

export interface Sale {
  ID: number
  ShopID: number
  ShopName: string
  SaleDate: string
  TotalAmount: string
  AmountPaid: string
  PaymentType: string
  CreatedAt: string
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
