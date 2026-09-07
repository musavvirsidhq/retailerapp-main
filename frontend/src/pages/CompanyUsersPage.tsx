import { useEffect, useState } from "react"
import { UserX } from "lucide-react"
import {
  listCompanyUsers, createStaff, updatePermissions, disableStaff, type CompanyUser,
} from "../api/companyUsers"

function CompanyUsersPage() {
  const [users, setUsers] = useState<CompanyUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [formError, setFormError] = useState("")

  const [name, setName] = useState("")
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [purchaseAccess, setPurchaseAccess] = useState(false)
  const [salesAccess, setSalesAccess] = useState(false)
  const [belowCostApprove, setBelowCostApprove] = useState(false)

  async function load() {
    try {
      setLoading(true)
      setUsers(await listCompanyUsers())
    } catch {
      setError("Could not load staff. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError("")
    if (!name || !username || !password) { setFormError("Name, username and password are required"); return }
    try {
      await createStaff({ name, username, password, purchase_access: purchaseAccess, sales_access: salesAccess, sales_below_cost_approve: belowCostApprove })
      setName(""); setUsername(""); setPassword(""); setPurchaseAccess(false); setSalesAccess(false); setBelowCostApprove(false)
      load()
    } catch (err: any) {
      setFormError(err.message || "Failed to create staff user")
    }
  }

  async function togglePermission(u: CompanyUser, field: "purchase_access" | "sales_access" | "sales_below_cost_approve") {
    await updatePermissions(
      u.id,
      field === "purchase_access" ? !u.purchase_access : u.purchase_access,
      field === "sales_access" ? !u.sales_access : u.sales_access,
      field === "sales_below_cost_approve" ? !u.sales_below_cost_approve : u.sales_below_cost_approve,
    )
    load()
  }

  async function handleDisable(u: CompanyUser) {
    if (!confirm(`Disable staff user "${u.name}"?`)) return
    await disableStaff(u.id)
    load()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Staff</h1>
      <p className="eyebrow mb-6">Company users &amp; their purchase/sales permissions</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4">
          <input className="input-field" placeholder="Full name" value={name} onChange={(e) => setName(e.target.value)} />
          <input className="input-field" placeholder="Username" value={username} onChange={(e) => setUsername(e.target.value)} />
          <input className="input-field" placeholder="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <div className="flex flex-wrap gap-4 mb-4">
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={purchaseAccess} onChange={(e) => setPurchaseAccess(e.target.checked)} /> Purchase Access</label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={salesAccess} onChange={(e) => setSalesAccess(e.target.checked)} /> Sales Access</label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={belowCostApprove} onChange={(e) => setBelowCostApprove(e.target.checked)} /> Can Approve Below-Cost Sales</label>
        </div>
        {formError && <p className="stamp-red mb-3">{formError}</p>}
        <button type="submit" className="btn-accent w-full">Add Staff</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden overflow-x-auto">
          <div className="card-header"><h2 className="font-display font-semibold">Company Users</h2></div>
          <table className="table-base">
            <thead>
              <tr><th>Name</th><th>Username</th><th>Purchase</th><th>Sales</th><th>Below-Cost Approve</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {users.filter((u) => u.user_type === "STAFF").map((u) => (
                <tr key={u.id}>
                  <td className="font-medium">{u.name}</td>
                  <td className="text-slate">{u.username}</td>
                  <td><input type="checkbox" checked={u.purchase_access} onChange={() => togglePermission(u, "purchase_access")} /></td>
                  <td><input type="checkbox" checked={u.sales_access} onChange={() => togglePermission(u, "sales_access")} /></td>
                  <td><input type="checkbox" checked={u.sales_below_cost_approve} onChange={() => togglePermission(u, "sales_below_cost_approve")} /></td>
                  <td><span className={u.status === "ACTIVE" ? "stamp-green" : "stamp-red"}>{u.status}</span></td>
                  <td className="text-right">
                    {u.status === "ACTIVE" && (
                      <button onClick={() => handleDisable(u)} className="text-slate hover:text-red transition-colors">
                        <UserX size={15} />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {users.filter((u) => u.user_type === "STAFF").length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-slate">No staff yet. Add one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default CompanyUsersPage
