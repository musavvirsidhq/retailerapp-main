import { useEffect, useState } from "react"
import { Trash2 } from "lucide-react"
import { listShops, createShop, deleteShop, type Shop } from "../api/shops"

function ShopsPage() {
  const [shops, setShops] = useState<Shop[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const [name, setName] = useState("")
  const [ownerName, setOwnerName] = useState("")
  const [phone, setPhone] = useState("")
  const [area, setArea] = useState("")
  const [openingBalance, setOpeningBalance] = useState("")

  async function loadShops() {
    try {
      setLoading(true)
      setShops(await listShops())
    } catch {
      setError("Could not load shops. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadShops() }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name) return
    await createShop({
      name, owner_name: ownerName, phone, area,
      opening_balance: parseFloat(openingBalance) || 0,
    })
    setName(""); setOwnerName(""); setPhone(""); setArea(""); setOpeningBalance("")
    loadShops()
  }

  async function handleDelete(id: number) {
    if (!confirm("Delete this shop?")) return
    await deleteShop(id)
    loadShops()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Shops</h1>
      <p className="eyebrow mb-6">Retail customers you supply</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8 grid grid-cols-2 gap-3">
        <input className="input-field" placeholder="Shop name" value={name} onChange={(e) => setName(e.target.value)} />
        <input className="input-field" placeholder="Owner name" value={ownerName} onChange={(e) => setOwnerName(e.target.value)} />
        <input className="input-field" placeholder="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <input className="input-field" placeholder="Area" value={area} onChange={(e) => setArea(e.target.value)} />
        <input className="input-field mono-num col-span-2" placeholder="Opening balance (if any)" type="number" step="0.01" value={openingBalance} onChange={(e) => setOpeningBalance(e.target.value)} />
        <button type="submit" className="btn-accent col-span-2">Add Shop</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">All Shops</h2></div>
          <table className="table-base">
            <thead>
              <tr><th>Name</th><th>Owner</th><th>Phone</th><th>Area</th><th className="text-right">Opening Bal.</th><th></th></tr>
            </thead>
            <tbody>
              {shops.map((s) => (
                <tr key={s.ID}>
                  <td className="font-medium">{s.Name}</td>
                  <td className="text-slate">{s.OwnerName || "-"}</td>
                  <td className="mono-num text-slate">{s.Phone || "-"}</td>
                  <td className="text-slate">{s.Area || "-"}</td>
                  <td className="mono-num text-right">₹{s.OpeningBalance}</td>
                  <td className="text-right">
                    <button onClick={() => handleDelete(s.ID)} className="text-slate hover:text-red transition-colors">
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
              {shops.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate">No shops yet. Add one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default ShopsPage
