const API_BASE = "/api/company/users"

export interface CompanyUser {
  id: number
  name: string
  username: string
  user_type: string
  purchase_access: boolean
  sales_access: boolean
  sales_below_cost_approve: boolean
  status: string
}

export interface CreateStaffInput {
  name: string
  username: string
  password: string
  purchase_access: boolean
  sales_access: boolean
  sales_below_cost_approve: boolean
}

export interface SubscriptionStatus {
  status: string
  expiry_date?: string
  days_remaining?: number
  warning_level?: "" | "SOON" | "URGENT" | "EXPIRED"
  subscription_type?: string
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(await res.text() || "Request failed")
  return (await res.json()) as T
}

export async function listCompanyUsers(): Promise<CompanyUser[]> {
  const res = await fetch(API_BASE, { credentials: "include" })
  return handle<CompanyUser[]>(res)
}

export async function createStaff(input: CreateStaffInput): Promise<CompanyUser> {
  const res = await fetch(API_BASE, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  return handle<CompanyUser>(res)
}

export async function updatePermissions(id: number, purchaseAccess: boolean, salesAccess: boolean, salesBelowCostApprove: boolean): Promise<CompanyUser> {
  const res = await fetch(`${API_BASE}/${id}/permissions`, {
    credentials: "include",
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ purchaseAccess, salesAccess, salesBelowCostApprove }),
  })
  return handle<CompanyUser>(res)
}

export async function disableStaff(id: number): Promise<CompanyUser> {
  const res = await fetch(`${API_BASE}/${id}/disable`, { credentials: "include", method: "PUT" })
  return handle<CompanyUser>(res)
}

export async function getSubscriptionStatus(): Promise<SubscriptionStatus> {
  const res = await fetch("/api/company/subscription-status", { credentials: "include" })
  return handle<SubscriptionStatus>(res)
}
