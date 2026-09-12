const SETTINGS_BASE = "/api/company/storefront-settings"
const ORDERS_BASE = "/api/company/storefront-orders"

export interface StorefrontSettings {
  enabled: boolean
  cod_enabled: boolean
  contact_enabled: boolean
  contact_phone: string
}

export async function getStorefrontSettings(): Promise<StorefrontSettings> {
  const res = await fetch(`${SETTINGS_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load storefront settings")
  return res.json()
}

export async function updateStorefrontSettings(input: StorefrontSettings): Promise<StorefrontSettings> {
  const res = await fetch(`${SETTINGS_BASE}/`, {
    credentials: "include",
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to save storefront settings")
  return res.json()
}

export interface PublicOrder {
  ID: number
  CompanyID: number
  CustomerName: string
  CustomerPhone: string
  CustomerAddress: string | null
  FulfillmentMethod: "COD" | "CONTACT"
  Status: "NEW" | "CONFIRMED" | "CANCELLED"
  TotalAmount: string
  CreatedAt: string
}

export interface PublicOrderItem {
  ID: number
  OrderID: number
  ProductID: number
  ProductName: string
  Quantity: number
  UnitPrice: string
  LineTotal: string
}

export async function listStorefrontOrders(): Promise<PublicOrder[]> {
  const res = await fetch(`${ORDERS_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load orders")
  return res.json()
}

export async function getStorefrontOrderItems(id: number): Promise<PublicOrderItem[]> {
  const res = await fetch(`${ORDERS_BASE}/${id}/items`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load order items")
  return res.json()
}

export async function updateStorefrontOrderStatus(id: number, status: PublicOrder["Status"]): Promise<PublicOrder> {
  const res = await fetch(`${ORDERS_BASE}/${id}/status`, {
    credentials: "include",
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  })
  if (!res.ok) throw new Error("Failed to update order status")
  return res.json()
}
