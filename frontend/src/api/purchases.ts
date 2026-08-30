const API_BASE = "http://localhost:8080/api/purchases"

export interface Purchase {
  ID: number
  FactoryID: number
  FactoryName: string
  InvoiceNo: string | null
  PurchaseDate: string
  TotalAmount: string
  AmountPaid: string
  CreatedAt: string
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
}

export async function listPurchases(): Promise<Purchase[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch purchases")
  return res.json()
}

export async function createPurchase(input: PurchaseInput): Promise<Purchase> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error("Failed to create purchase")
  return res.json()
}
