const API_BASE = "http://localhost:8080/api/factories"

export interface Factory {
  ID: number
  Name: string
  ContactPerson: string | null
  Phone: string | null
  Address: string | null
  CreatedAt: string
}

export interface FactoryInput {
  name: string
  contact_person: string
  phone: string
  address: string
}

export async function listFactories(): Promise<Factory[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch factories")
  return res.json()
}

export async function createFactory(input: FactoryInput): Promise<Factory> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error("Failed to create factory")
  return res.json()
}

export async function deleteFactory(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: "include", method: "DELETE" })
  if (!res.ok) throw new Error("Failed to delete factory")
}
