import { useEffect, useState } from "react"
import { Trash2 } from "lucide-react"
import { listFactories, createFactory, deleteFactory, type Factory } from "../api/factories"

function FactoriesPage() {
  const [factories, setFactories] = useState<Factory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [formError, setFormError] = useState("")

  const [name, setName] = useState("")
  const [contactPerson, setContactPerson] = useState("")
  const [primaryPhone, setPrimaryPhone] = useState("")
  const [secondaryPhone, setSecondaryPhone] = useState("")
  const [address, setAddress] = useState("")

  async function loadFactories() {
    try {
      setLoading(true)
      setFactories(await listFactories())
    } catch {
      setError("Could not load factories. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadFactories() }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError("")
    if (!name || !primaryPhone) { setFormError("Name and primary contact number are required"); return }
    try {
      await createFactory({ name, contact_person: contactPerson, primary_phone: primaryPhone, secondary_phone: secondaryPhone, address })
      setName(""); setContactPerson(""); setPrimaryPhone(""); setSecondaryPhone(""); setAddress("")
      loadFactories()
    } catch (err: any) {
      setFormError(err.message || "Failed to create factory")
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("Delete this factory?")) return
    await deleteFactory(id)
    loadFactories()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Factories</h1>
      <p className="eyebrow mb-6">Suppliers you purchase from</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8 grid grid-cols-1 md:grid-cols-2 gap-3">
        <input className="input-field" placeholder="Factory name" value={name} onChange={(e) => setName(e.target.value)} />
        <input className="input-field" placeholder="Contact person" value={contactPerson} onChange={(e) => setContactPerson(e.target.value)} />
        <input className="input-field" placeholder="Primary contact number" value={primaryPhone} onChange={(e) => setPrimaryPhone(e.target.value)} />
        <input className="input-field" placeholder="Secondary contact number (optional)" value={secondaryPhone} onChange={(e) => setSecondaryPhone(e.target.value)} />
        <input className="input-field md:col-span-2" placeholder="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
        {formError && <p className="stamp-red md:col-span-2">{formError}</p>}
        <button type="submit" className="btn-accent md:col-span-2">Add Factory</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden overflow-x-auto">
          <div className="card-header"><h2 className="font-display font-semibold">All Factories</h2></div>
          <table className="table-base">
            <thead>
              <tr><th>Name</th><th>Contact Person</th><th>Primary</th><th>Secondary</th><th>Address</th><th></th></tr>
            </thead>
            <tbody>
              {factories.map((f) => (
                <tr key={f.ID}>
                  <td className="font-medium">{f.Name}</td>
                  <td className="text-slate">{f.ContactPerson || "-"}</td>
                  <td className="mono-num text-slate">{f.PrimaryPhone}</td>
                  <td className="mono-num text-slate">{f.SecondaryPhone || "-"}</td>
                  <td className="text-slate">{f.Address || "-"}</td>
                  <td className="text-right">
                    <button onClick={() => handleDelete(f.ID)} className="text-slate hover:text-red transition-colors">
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
              {factories.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate">No factories yet. Add one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default FactoriesPage
