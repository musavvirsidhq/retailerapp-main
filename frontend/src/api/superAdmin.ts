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
  return res.json()
}

export function listCompanies(): Promise<Company[]> {
  return fetch(API_BASE, { credentials: "include" }).then(handle)
}

export function getCompany(id: number): Promise<Company> {
  return fetch(`${API_BASE}/${id}`, { credentials: "include" }).then(handle)
}

export function createCompany(input: CreateCompanyInput): Promise<Company> {
  return fetch(API_BASE, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  }).then(handle)
}

export function getSubscriptionHistory(companyId: number): Promise<Subscription[]> {
  return fetch(`${API_BASE}/${companyId}/subscription`, { credentials: "include" }).then(handle)
}

export function grantTrial(companyId: number): Promise<Subscription> {
  return fetch(`${API_BASE}/${companyId}/trial`, { credentials: "include", method: "POST" }).then(handle)
}

export function grantSubscription(companyId: number, subscriptionType: string, amount: number, months: number): Promise<Subscription> {
  return fetch(`${API_BASE}/${companyId}/subscription`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subscription_type: subscriptionType, amount, months }),
  }).then(handle)
}

export function extendSubscription(companyId: number, months: number, days: number, amount: number): Promise<Subscription> {
  return fetch(`${API_BASE}/${companyId}/extend`, {
    credentials: "include",
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ months, days, amount }),
  }).then(handle)
}
