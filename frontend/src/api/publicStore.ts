function base(companyCode: string) {
  return `/public/${encodeURIComponent(companyCode)}`
}

export interface StoreInfo {
  company_name: string
  cod_enabled: boolean
  contact_enabled: boolean
  contact_phone?: string
}

export interface StoreProductImage {
  url: string
}

export interface StorePackItem {
  size: string
  quantity: number
}

export interface StoreProduct {
  id: number
  name: string
  description: string
  unit: string
  selling_price: number
  in_stock: boolean
  is_bundle: boolean
  images: StoreProductImage[]
  pack_items?: StorePackItem[]
}

export async function getStoreInfo(companyCode: string): Promise<StoreInfo | null> {
  const res = await fetch(base(companyCode))
  if (!res.ok) return null
  return res.json()
}

export async function listStoreProducts(companyCode: string): Promise<StoreProduct[]> {
  const res = await fetch(`${base(companyCode)}/products`)
  if (!res.ok) throw new Error("Failed to load products")
  return res.json()
}

export async function getStoreProduct(companyCode: string, id: number): Promise<StoreProduct> {
  const res = await fetch(`${base(companyCode)}/products/${id}`)
  if (!res.ok) throw new Error("Product not found")
  return res.json()
}

export interface StoreOrderItemInput {
  product_id: number
  quantity: number
}

export interface StoreOrderInput {
  customer_name: string
  customer_phone: string
  customer_address: string
  fulfillment_method: "COD" | "CONTACT"
  items: StoreOrderItemInput[]
}

export interface StoreOrderResult {
  id: number
  total_amount: number
  fulfillment_method: "COD" | "CONTACT"
  status: string
  contact_phone?: string
}

export async function placeStoreOrder(companyCode: string, input: StoreOrderInput): Promise<StoreOrderResult> {
  const res = await fetch(`${base(companyCode)}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to place order")
  return res.json()
}
