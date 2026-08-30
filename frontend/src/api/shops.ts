const API_BASE = "http://localhost:8080/api/shops"

export interface Shop {
  ID: number
  Name: string
  OwnerName: string | null
  Phone: string | null
  Area: string | null
  OpeningBalance: string
  CreatedAt: string
}

export interface ShopInput {
  name: string
  owner_name: string
  phone: string
  area: string
  opening_balance: number
}

export async function listShops(): Promise<Shop[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch shops")
  return res.json()
}

export async function createShop(input: ShopInput): Promise<Shop> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error("Failed to create shop")
  return res.json()
}

export async function deleteShop(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: "include", method: "DELETE" })
  if (!res.ok) throw new Error("Failed to delete shop")
}
