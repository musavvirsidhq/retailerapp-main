import { useEffect, useState } from "react"
import { getDashboard, type DashboardData } from "../api/reports"

function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    getDashboard().then(setData).catch(() => setError("Could not load dashboard. Is the backend running?")).finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="text-slate text-sm max-w-5xl mx-auto">Loading...</p>
  if (error) return <p className="stamp-red max-w-5xl mx-auto">{error}</p>
  if (!data) return null

  const totalDues = data.shop_dues.filter((s) => parseFloat(s.Balance) > 0).reduce((sum, s) => sum + parseFloat(s.Balance), 0)
  const totalPayables = data.factory_payables.filter((f) => parseFloat(f.Balance) > 0).reduce((sum, f) => sum + parseFloat(f.Balance), 0)

  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Dashboard</h1>
      <p className="eyebrow mb-6">Today's snapshot</p>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="card p-5">
          <p className="eyebrow mb-2">Today's Sales</p>
          <p className="font-display font-bold text-2xl mono-num">₹{data.today_sales.Total}</p>
          <p className="text-xs text-slate mt-1">{data.today_sales.Count} transactions</p>
        </div>
        <div className="card p-5">
          <p className="eyebrow mb-2">Today's Purchases</p>
          <p className="font-display font-bold text-2xl mono-num">₹{data.today_purchases.Total}</p>
          <p className="text-xs text-slate mt-1">{data.today_purchases.Count} transactions</p>
        </div>
        <div className="card p-5">
          <p className="eyebrow mb-2">Total Dues</p>
          <p className="font-display font-bold text-2xl mono-num text-amber">₹{totalDues.toFixed(2)}</p>
          <p className="text-xs text-slate mt-1">from shops</p>
        </div>
        <div className="card p-5">
          <p className="eyebrow mb-2">Total Payables</p>
          <p className="font-display font-bold text-2xl mono-num text-red">₹{totalPayables.toFixed(2)}</p>
          <p className="text-xs text-slate mt-1">to factories</p>
        </div>
      </div>

      <div className="card p-6 mb-6 flex items-center justify-between">
        <div>
          <p className="eyebrow mb-1">Total Profit</p>
          <p className="text-xs text-slate">all-time, calculated from every sale</p>
        </div>
        <p className="font-display font-bold text-3xl mono-num text-green">₹{data.total_profit}</p>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">Shop Dues</h2></div>
          <table className="table-base">
            <tbody>
              {data.shop_dues.map((s) => (
                <tr key={s.ID}>
                  <td className="font-medium">{s.Name}</td>
                  <td className="text-right">
                    <span className={parseFloat(s.Balance) > 0 ? "stamp-amber" : "stamp-green"}>₹{s.Balance}</span>
                  </td>
                </tr>
              ))}
              {data.shop_dues.length === 0 && <tr><td className="px-4 py-6 text-center text-slate">No shops yet.</td></tr>}
            </tbody>
          </table>
        </div>

        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">Factory Payables</h2></div>
          <table className="table-base">
            <tbody>
              {data.factory_payables.map((f) => (
                <tr key={f.ID}>
                  <td className="font-medium">{f.Name}</td>
                  <td className="text-right">
                    <span className={parseFloat(f.Balance) > 0 ? "stamp-red" : "stamp-green"}>₹{f.Balance}</span>
                  </td>
                </tr>
              ))}
              {data.factory_payables.length === 0 && <tr><td className="px-4 py-6 text-center text-slate">No factories yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card overflow-hidden mt-6">
        <div className="card-header"><h2 className="font-display font-semibold">Low Stock Alert</h2></div>
        <table className="table-base">
          <tbody>
            {data.low_stock.map((p) => (
              <tr key={p.ID}>
                <td className="font-medium">{p.Name}</td>
                <td className="text-right"><span className="stamp-red">{p.CurrentStock} {p.Unit}</span></td>
              </tr>
            ))}
            {data.low_stock.length === 0 && <tr><td className="px-4 py-6 text-center text-slate">All products well stocked.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default DashboardPage
