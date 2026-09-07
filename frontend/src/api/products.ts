const API_BASE = "/api/products"

export interface Product {
  ID: number
  CompanyID: number
  Name: string
  Sku: string
  Unit: string
  CategoryID: number
  SubcategoryID: number | null
  CurrentSellingPrice: string
  CurrentStock: string
  CreatedAt: string
}

export interface ProductInput {
  name: string
  sku: string
  unit: string
  category_id: number
  subcategory_id: number | null
  selling_price: number
}

export async function listProducts(): Promise<Product[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch products")
  return res.json()
}

export async function createProduct(input: ProductInput): Promise<Product> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to create product")
  return res.json()
}

export async function deleteProduct(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: "include", method: "DELETE" })
  if (!res.ok) throw new Error("Failed to delete product")
}

export async function getProductCost(id: number): Promise<number | null> {
  const res = await fetch(`${API_BASE}/${id}/cost`, { credentials: "include" })
  if (!res.ok) return null
  const data = await res.json()
  return data.cost === null || data.cost === undefined ? null : parseFloat(data.cost)
}
