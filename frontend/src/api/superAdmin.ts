const API_BASE = "/api/super-admin/companies"

export interface Company {
  ID: number
  CompanyName: string
  CompanyCode: string
  Status: string
  JoiningDate: string
  CreatedOn: string
  subscription_type?: string
  expiry_date?: string
  subscription_status?: string
}

export interface Subscription {
  ID: number
  CompanyID: number
  SubscriptionType: string
  StartDate: string
  ExpiryDate: string
  Amount: string
  PurchaseDate: string
  Status: string
}

export interface CreateCompanyInput {
  company_name: string
  company_code: string
  admin_name: string
  admin_username: string
  admin_password: string
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(await res.text() || "Request failed")
  return (await res.json()) as T
}

export async function listCompanies(): Promise<Company[]> {
  const res = await fetch(API_BASE, { credentials: "include" })
  return handle<Company[]>(res)
}

export async function getCompany(id: number): Promise<Company> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: "include" })
  return handle<Company>(res)
}

export async function createCompany(input: CreateCompanyInput): Promise<Company> {
  const res = await fetch(API_BASE, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
  return handle<Company>(res)
}

export async function getSubscriptionHistory(companyId: number): Promise<Subscription[]> {
  const res = await fetch(`${API_BASE}/${companyId}/subscription`, { credentials: "include" })
  return handle<Subscription[]>(res)
}

export async function grantTrial(companyId: number): Promise<Subscription> {
  const res = await fetch(`${API_BASE}/${companyId}/trial`, { credentials: "include", method: "POST" })
  return handle<Subscription>(res)
}

export async function grantSubscription(companyId: number, subscriptionType: string, amount: number, months: number): Promise<Subscription> {
  const res = await fetch(`${API_BASE}/${companyId}/subscription`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subscription_type: subscriptionType, amount, months }),
  })
  return handle<Subscription>(res)
}

export async function extendSubscription(companyId: number, months: number, days: number, amount: number): Promise<Subscription> {
  const res = await fetch(`${API_BASE}/${companyId}/extend`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ months, days, amount }),
  })
  return handle<Subscription>(res)
}
