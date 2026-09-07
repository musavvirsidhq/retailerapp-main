import { useEffect, useState } from "react"
import { ChevronDown, ChevronUp } from "lucide-react"
import {
  listCompanies, createCompany, grantTrial, grantSubscription, extendSubscription,
  type Company,
} from "../api/superAdmin"

function statusStamp(status?: string) {
  if (!status) return "stamp-slate"
  if (status === "EXPIRED" || status === "SUSPENDED") return "stamp-red"
  if (status === "TRIAL") return "stamp-amber"
  return "stamp-green"
}

function CompanyRow({ company, onChanged }: { company: Company; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState("")
  const [months, setMonths] = useState("12")
  const [extendMonths, setExtendMonths] = useState("")
  const [extendDays, setExtendDays] = useState("")
  const [extendAmount, setExtendAmount] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  async function run(fn: () => Promise<unknown>) {
    setBusy(true); setError("")
    try {
      await fn()
      onChanged()
    } catch (err: any) {
      setError(err.message || "Action failed")
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <tr>
        <td className="font-medium">{company.CompanyName}</td>
        <td className="mono-num text-slate">{company.CompanyCode}</td>
        <td className="mono-num text-slate">{company.JoiningDate}</td>
        <td className="mono-num text-slate">{company.expiry_date || "-"}</td>
        <td>{company.subscription_type || "-"}</td>
        <td><span className={statusStamp(company.subscription_status)}>{company.subscription_status || "NONE"}</span></td>
        <td className="text-right">
          <button onClick={() => setOpen((v) => !v)} className="btn-ghost flex items-center gap-1 ml-auto">
            Manage {open ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={7} className="bg-paper px-4 py-4">
            <div className="grid md:grid-cols-3 gap-4">
              <div className="card p-3">
                <p className="eyebrow mb-2">One-Month Trial</p>
                <button disabled={busy} className="btn-primary w-full" onClick={() => run(() => grantTrial(company.ID))}>
                  Start Trial
                </button>
              </div>
              <div className="card p-3">
                <p className="eyebrow mb-2">Grant Annual Subscription</p>
                <input className="input-field w-full mb-2 mono-num" placeholder="Amount paid" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} />
                <input className="input-field w-full mb-2 mono-num" placeholder="Months (default 12)" type="number" value={months} onChange={(e) => setMonths(e.target.value)} />
                <button disabled={busy} className="btn-primary w-full" onClick={() => run(() => grantSubscription(company.ID, "ANNUAL", parseFloat(amount) || 0, parseInt(months) || 12))}>
                  Activate Subscription
                </button>
              </div>
              <div className="card p-3">
                <p className="eyebrow mb-2">Extend Subscription</p>
                <div className="flex gap-2 mb-2">
                  <input className="input-field w-full mono-num" placeholder="Months" type="number" value={extendMonths} onChange={(e) => setExtendMonths(e.target.value)} />
                  <input className="input-field w-full mono-num" placeholder="Days" type="number" value={extendDays} onChange={(e) => setExtendDays(e.target.value)} />
                </div>
                <input className="input-field w-full mb-2 mono-num" placeholder="Amount paid (if any)" type="number" value={extendAmount} onChange={(e) => setExtendAmount(e.target.value)} />
                <button disabled={busy} className="btn-primary w-full" onClick={() => run(() => extendSubscription(company.ID, parseInt(extendMonths) || 0, parseInt(extendDays) || 0, parseFloat(extendAmount) || 0))}>
                  Extend
                </button>
              </div>
            </div>
            {error && <p className="stamp-red mt-3">{error}</p>}
          </td>
        </tr>
      )}
    </>
  )
}

function SuperAdminCompaniesPage() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [formError, setFormError] = useState("")

  const [companyName, setCompanyName] = useState("")
  const [companyCode, setCompanyCode] = useState("")
  const [adminName, setAdminName] = useState("")
  const [adminUsername, setAdminUsername] = useState("")
  const [adminPassword, setAdminPassword] = useState("")

  async function load() {
    try {
      setLoading(true)
      setCompanies(await listCompanies())
    } catch {
      setError("Could not load companies. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError("")
    if (!companyName || !companyCode || !adminUsername || !adminPassword) {
      setFormError("Company name, code, admin username and password are required")
      return
    }
    try {
      await createCompany({ company_name: companyName, company_code: companyCode, admin_name: adminName, admin_username: adminUsername, admin_password: adminPassword })
      setCompanyName(""); setCompanyCode(""); setAdminName(""); setAdminUsername(""); setAdminPassword("")
      load()
    } catch (err: any) {
      setFormError(err.message || "Failed to create company")
    }
  }

  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Companies</h1>
      <p className="eyebrow mb-6">Platform-level company &amp; subscription management</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8 grid grid-cols-1 md:grid-cols-3 gap-3">
        <input className="input-field" placeholder="Company name" value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
        <input className="input-field" placeholder="Company code" value={companyCode} onChange={(e) => setCompanyCode(e.target.value.toUpperCase())} />
        <div />
        <input className="input-field" placeholder="Company admin name" value={adminName} onChange={(e) => setAdminName(e.target.value)} />
        <input className="input-field" placeholder="Admin username" value={adminUsername} onChange={(e) => setAdminUsername(e.target.value)} />
        <input className="input-field" placeholder="Admin password" type="password" value={adminPassword} onChange={(e) => setAdminPassword(e.target.value)} />
        {formError && <p className="stamp-red md:col-span-3">{formError}</p>}
        <button type="submit" className="btn-accent md:col-span-3">Create Company</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden overflow-x-auto">
          <div className="card-header"><h2 className="font-display font-semibold">All Companies</h2></div>
          <table className="table-base">
            <thead>
              <tr>
                <th>Company</th><th>Code</th><th>Joining Date</th><th>Expiry Date</th><th>Plan</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              {companies.map((c) => <CompanyRow key={c.ID} company={c} onChanged={load} />)}
              {companies.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-slate">No companies yet. Create one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default SuperAdminCompaniesPage
