const API_BASE = "http://localhost:8080/api/products"

export interface Product {
  ID: number
  Name: string
  Unit: string
  PurchasePrice: string
  SellingPrice: string
  CurrentStock: string
  CreatedAt: string
}

export interface ProductInput {
  name: string
  unit: string
  purchase_price: number
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
  if (!res.ok) throw new Error("Failed to create product")
  return res.json()
}

export async function deleteProduct(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: "include", method: "DELETE" })
  if (!res.ok) throw new Error("Failed to delete product")
}
