export interface Category {
  ID: number
  CompanyID: number
  Name: string
  CreatedAt: string
}

export interface Subcategory {
  ID: number
  CompanyID: number
  CategoryID: number
  Name: string
  CreatedAt: string
}

export async function listCategories(): Promise<Category[]> {
  const res = await fetch("/api/categories", { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch categories")
  return res.json()
}

export async function createCategory(name: string): Promise<Category> {
  const res = await fetch("/api/categories/", {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to create category")
  return res.json()
}

export async function listSubcategories(categoryId: number): Promise<Subcategory[]> {
  const res = await fetch(`/api/categories/${categoryId}/subcategories`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch subcategories")
  return res.json()
}

export async function createSubcategory(categoryId: number, name: string): Promise<Subcategory> {
  const res = await fetch(`/api/categories/${categoryId}/subcategories`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error(await res.text() || "Failed to create subcategory")
  return res.json()
}
