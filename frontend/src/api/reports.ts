const API_BASE = "http://localhost:8080/api"

export interface DashboardData {
  today_sales: { Count: number; Total: string }
  today_purchases: { Count: number; Total: string }
  total_profit: string
  low_stock: Array<{ ID: number; Name: string; Unit: string; CurrentStock: string }>
  shop_dues: Array<{ ID: number; Name: string; Balance: string }>
  factory_payables: Array<{ ID: number; Name: string; Balance: string }>
}

export async function getDashboard(): Promise<DashboardData> {
  const res = await fetch(`${API_BASE}/reports/dashboard`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch dashboard")
  return res.json()
}
