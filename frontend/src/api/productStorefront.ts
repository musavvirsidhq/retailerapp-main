import type { Product } from "./products"

const API_BASE = "/api/products"

export interface ProductImage {
  ID: number
  CompanyID: number
  ProductID: number
  Url: string
  SortOrder: number
  CreatedAt: string
}

export interface PackItem {
  ID: number
  CompanyID: number
  ProductID: number
  Size: string
  Quantity: number
}

export interface ProductStorefrontDetails {
  product: Product & { Description: string | null; IsBundle: boolean; StorefrontVisible: boolean }
  images: ProductImage[]
  pack_items: PackItem[]
}

export interface StorefrontDetailsInput {
  description: string
  is_bundle: boolean
  storefront_visible: boolean
}

export async function getProductStorefront(id: number): Promise<ProductStorefrontDetails> {
  const res = await fetch(`${API_BASE}/${id}/storefront`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load storefront details")
  return res.json()
}

export async function updateProductStorefront(id: number, input: StorefrontDetailsInput) {
  const res = await fetch(`${API_BASE}/${id}/storefront`, {
    credentials: "include",
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to save storefront details")
  return res.json()
}

export async function setPackItems(id: number, items: { size: string; quantity: number }[]): Promise<PackItem[]> {
  const res = await fetch(`${API_BASE}/${id}/pack-items`, {
    credentials: "include",
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(items),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to save pack sizes")
  return res.json()
}

export async function uploadProductImage(id: number, file: File): Promise<ProductImage> {
  const form = new FormData()
  form.append("image", file)
  const res = await fetch(`${API_BASE}/${id}/images`, {
    credentials: "include",
    method: "POST",
    body: form,
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to upload image")
  return res.json()
}

export async function deleteProductImage(id: number, imageId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/${id}/images/${imageId}`, { credentials: "include", method: "DELETE" })
  if (!res.ok) throw new Error("Failed to delete image")
}
