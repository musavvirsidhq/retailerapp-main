const API_BASE = "/api/purchases"

export interface Purchase {
  ID: number
  BillNumber: string
  FactoryID: number
  FactoryName: string
  InvoiceNo: string | null
  PurchaseDate: string
  TotalAmount: string
  AmountPaid: string
  Status: "COMPLETED" | "CANCELLED"
  CreatedAt: string
}

export interface PurchaseItem {
  ID: number
  PurchaseID: number
  ProductID: number
  ProductName: string
  ProductSku: string
  Unit: string
  Quantity: string
  UnitPrice: string
  LineTotal: string
}

export interface PurchaseItemInput {
  product_id: number
  quantity: number
  unit_price: number
}

export interface PurchaseInput {
  factory_id: number
  invoice_no: string
  amount_paid: number
  items: PurchaseItemInput[]
  new_selling_price?: Record<number, number>
}

export async function listPurchases(): Promise<Purchase[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch purchases")
  return res.json()
}

export async function getPurchaseItems(id: number): Promise<PurchaseItem[]> {
  const res = await fetch(`${API_BASE}/${id}/items`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch purchase items")
  return res.json()
}

export async function createPurchase(input: PurchaseInput): Promise<Purchase> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to create purchase")
  return res.json()
}

export async function cancelPurchase(id: number, reason: string): Promise<Purchase> {
  const res = await fetch(`${API_BASE}/bills/${id}/cancel`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to cancel purchase")
  return res.json()
}
