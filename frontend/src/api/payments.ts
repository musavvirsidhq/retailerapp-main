const API_BASE = "http://localhost:8080/api/payments"

export interface Payment {
  ID: number
  PartyType: "shop" | "factory"
  PartyID: number
  Amount: string
  PaymentMode: string
  PaymentDate: string
  Notes: string | null
  CreatedAt: string
}

export interface PaymentInput {
  party_type: "shop" | "factory"
  party_id: number
  amount: number
  payment_mode: string
  notes: string
}

export async function listPayments(): Promise<Payment[]> {
  const res = await fetch(`${API_BASE}/`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch payments")
  return res.json()
}

export async function createPayment(input: PaymentInput): Promise<Payment> {
  const res = await fetch(`${API_BASE}/`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  if (!res.ok) throw new Error("Failed to create payment")
  return res.json()
}

export async function getShopBalance(id: number): Promise<number> {
  const res = await fetch(`http://localhost:8080/api/shops/${id}/balance`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch shop balance")
  const data = await res.json()
  return parseFloat(data.balance)
}

export async function getFactoryBalance(id: number): Promise<number> {
  const res = await fetch(`http://localhost:8080/api/factories/${id}/balance`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch factory balance")
  const data = await res.json()
  return parseFloat(data.balance)
}
