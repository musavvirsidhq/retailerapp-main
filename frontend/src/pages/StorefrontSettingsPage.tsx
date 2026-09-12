import { useEffect, useState } from "react"
import { Copy, ChevronDown, ChevronUp } from "lucide-react"
import { useAuth } from "../context/AuthContext"
import {
  getStorefrontSettings, updateStorefrontSettings, listStorefrontOrders,
  getStorefrontOrderItems, updateStorefrontOrderStatus,
  type StorefrontSettings, type PublicOrder, type PublicOrderItem,
} from "../api/storefrontSettings"

const STATUS_STAMP: Record<PublicOrder["Status"], string> = {
  NEW: "stamp-amber",
  CONFIRMED: "stamp-green",
  CANCELLED: "stamp-red",
}

function OrderRow({ order, onStatusChange }: { order: PublicOrder; onStatusChange: (id: number, status: PublicOrder["Status"]) => void }) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<PublicOrderItem[] | null>(null)

  async function toggle() {
    if (!open && items === null) {
      try { setItems(await getStorefrontOrderItems(order.ID)) } catch { setItems([]) }
    }
    setOpen(!open)
  }

  return (
    <>
      <tr className="cursor-pointer" onClick={toggle}>
        <td>{open ? <ChevronUp size={14} /> : <ChevronDown size={14} />}</td>
        <td className="font-medium">{order.CustomerName}</td>
        <td className="text-slate">{order.CustomerPhone}</td>
        <td className="text-slate">{order.FulfillmentMethod === "COD" ? "Cash on delivery" : "Contact to purchase"}</td>
        <td className="mono-num text-right">₹{order.TotalAmount}</td>
        <td onClick={(e) => e.stopPropagation()}>
          <select
            className={`input-field text-xs py-1 ${STATUS_STAMP[order.Status]}`}
            value={order.Status}
            onChange={(e) => onStatusChange(order.ID, e.target.value as PublicOrder["Status"])}
          >
            <option value="NEW">New</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={6} className="bg-paper px-4 py-3">
            {items === null ? (
              <span className="text-slate text-sm">Loading...</span>
            ) : (
              <div className="space-y-1">
                {items.map((item) => (
                  <div key={item.ID} className="flex justify-between text-sm">
                    <span>{item.ProductName} &times; {item.Quantity}</span>
                    <span className="mono-num text-slate">₹{item.LineTotal}</span>
                  </div>
                ))}
                {order.CustomerAddress && <p className="text-sm text-slate mt-2">Deliver to: {order.CustomerAddress}</p>}
              </div>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

export default function StorefrontSettingsPage() {
  const { user } = useAuth()
  const [settings, setSettings] = useState<StorefrontSettings | null>(null)
  const [orders, setOrders] = useState<PublicOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    Promise.all([getStorefrontSettings(), listStorefrontOrders()])
      .then(([s, o]) => { setSettings(s); setOrders(o) })
      .catch(() => setError("Could not load storefront settings"))
      .finally(() => setLoading(false))
  }, [])

  async function handleSave() {
    if (!settings) return
    setSaving(true)
    setError("")
    try {
      setSettings(await updateStorefrontSettings(settings))
    } catch (err: any) {
      setError(err.message || "Failed to save settings")
    } finally {
      setSaving(false)
    }
  }

  async function handleStatusChange(id: number, status: PublicOrder["Status"]) {
    const updated = await updateStorefrontOrderStatus(id, status)
    setOrders((prev) => prev.map((o) => (o.ID === id ? updated : o)))
  }

  const storeUrl = user?.company_code ? `${window.location.origin}/store/${user.company_code}` : ""

  function copyLink() {
    navigator.clipboard.writeText(storeUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  if (loading) return <p className="text-slate text-sm">Loading...</p>
  if (!settings) return <p className="stamp-red">{error || "Could not load settings"}</p>

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Storefront</h1>
      <p className="eyebrow mb-6">Public catalog &amp; no-payment ordering</p>

      <div className="card p-6 mb-8 space-y-4">
        <label className="flex items-center justify-between">
          <span>
            <span className="block font-medium text-sm">Enable public storefront</span>
            <span className="block text-xs text-slate">Turns your product catalog into a public site anyone can browse and order from.</span>
          </span>
          <input
            type="checkbox"
            checked={settings.enabled}
            onChange={(e) => setSettings({ ...settings, enabled: e.target.checked })}
          />
        </label>

        {settings.enabled && storeUrl && (
          <div className="flex items-center gap-2 bg-paper border border-line rounded-md px-3 py-2">
            <code className="text-xs flex-1 truncate">{storeUrl}</code>
            <button onClick={copyLink} className="btn-ghost flex items-center gap-1 text-xs">
              <Copy size={13} /> {copied ? "Copied" : "Copy"}
            </button>
          </div>
        )}

        <hr className="border-line" />

        <label className="flex items-center justify-between">
          <span>
            <span className="block font-medium text-sm">Cash on delivery</span>
            <span className="block text-xs text-slate">Buyer pays when the order is delivered.</span>
          </span>
          <input
            type="checkbox"
            checked={settings.cod_enabled}
            onChange={(e) => setSettings({ ...settings, cod_enabled: e.target.checked })}
          />
        </label>

        <label className="flex items-center justify-between">
          <span>
            <span className="block font-medium text-sm">Contact to purchase</span>
            <span className="block text-xs text-slate">Reveals your contact number so the buyer can call you to arrange the purchase.</span>
          </span>
          <input
            type="checkbox"
            checked={settings.contact_enabled}
            onChange={(e) => setSettings({ ...settings, contact_enabled: e.target.checked })}
          />
        </label>

        {settings.contact_enabled && (
          <input
            className="input-field w-full"
            placeholder="Contact number shown to buyers"
            value={settings.contact_phone}
            onChange={(e) => setSettings({ ...settings, contact_phone: e.target.value })}
          />
        )}

        <p className="text-xs text-slate">No online payment yet - orders are fulfilled as cash-on-delivery or by calling the buyer back. Card/UPI payment can be added later without changing this setup.</p>

        {error && <p className="stamp-red">{error}</p>}
        <button onClick={handleSave} disabled={saving} className="btn-accent w-full">
          {saving ? "Saving..." : "Save Settings"}
        </button>
      </div>

      <div className="card overflow-hidden overflow-x-auto">
        <div className="card-header"><h2 className="font-display font-semibold">Storefront Orders</h2></div>
        <table className="table-base">
          <thead>
            <tr>
              <th></th><th>Customer</th><th>Phone</th><th>Method</th>
              <th className="text-right">Total</th><th>Status</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => <OrderRow key={o.ID} order={o} onStatusChange={handleStatusChange} />)}
            {orders.length === 0 && (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-slate">No storefront orders yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
