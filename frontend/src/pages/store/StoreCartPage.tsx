import { useEffect, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { ArrowLeft, Minus, Plus, Trash2, ShoppingBag, PhoneCall } from "lucide-react"
import { getStoreInfo, placeStoreOrder, type StoreInfo, type StoreOrderResult } from "../../api/publicStore"
import { useStoreCart } from "../../context/StoreCartContext"
import StoreHeader from "../../components/store/StoreHeader"

export default function StoreCartPage() {
  const { companyCode = "" } = useParams()
  const { items, updateQuantity, removeItem, totalAmount, clear } = useStoreCart()

  const [info, setInfo] = useState<StoreInfo | null>(null)
  const [name, setName] = useState("")
  const [phone, setPhone] = useState("")
  const [address, setAddress] = useState("")
  const [method, setMethod] = useState<"COD" | "CONTACT" | "">("")
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState("")
  const [result, setResult] = useState<StoreOrderResult | null>(null)

  useEffect(() => {
    getStoreInfo(companyCode).then((i) => {
      setInfo(i)
      if (i?.cod_enabled) setMethod("COD")
      else if (i?.contact_enabled) setMethod("CONTACT")
    })
  }, [companyCode])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    if (!name || !phone) { setError("Name and phone number are required"); return }
    if (!method) { setError("Choose how you'd like to purchase"); return }
    setSubmitting(true)
    try {
      const res = await placeStoreOrder(companyCode, {
        customer_name: name,
        customer_phone: phone,
        customer_address: address,
        fulfillment_method: method,
        items: items.map((i) => ({ product_id: i.productId, quantity: i.quantity })),
      })
      setResult(res)
      clear()
    } catch (err: any) {
      setError(err.message || "Failed to place order")
    } finally {
      setSubmitting(false)
    }
  }

  if (!info) return <div className="min-h-screen bg-paper flex items-center justify-center text-slate">Loading...</div>

  if (result) {
    return (
      <div className="min-h-screen bg-paper">
        <StoreHeader companyName={info.company_name} />
        <main className="max-w-md mx-auto px-4 py-16 text-center">
          <div className="card p-8">
            <h1 className="font-display font-bold text-xl mb-2">Order received!</h1>
            <p className="text-slate text-sm mb-4">Order #{result.id} &middot; ₹{result.total_amount.toLocaleString("en-IN")}</p>
            {result.fulfillment_method === "COD" ? (
              <p className="text-sm">We&apos;ll get in touch to confirm delivery. Pay in cash when it arrives.</p>
            ) : (
              <div className="stamp-amber inline-flex items-center gap-1.5">
                <PhoneCall size={13} /> Call us at {result.contact_phone} to complete your purchase
              </div>
            )}
            <Link to={`/store/${companyCode}`} className="btn-accent w-full mt-6 inline-block">Continue shopping</Link>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-paper">
      <StoreHeader companyName={info.company_name} />
      <main className="max-w-2xl mx-auto px-4 md:px-8 py-6">
        <Link to={`/store/${companyCode}`} className="btn-ghost inline-flex items-center gap-1.5 mb-4">
          <ArrowLeft size={15} /> Continue shopping
        </Link>

        <h1 className="font-display font-bold text-2xl mb-4">Your Cart</h1>

        {items.length === 0 ? (
          <div className="text-center py-16 text-slate">
            <ShoppingBag className="mx-auto mb-3" size={32} />
            <p>Your cart is empty.</p>
          </div>
        ) : (
          <>
            <div className="card divide-y divide-line mb-6">
              {items.map((item) => (
                <div key={item.productId} className="flex items-center gap-3 p-3">
                  <div className="w-14 h-14 rounded-md bg-line overflow-hidden shrink-0">
                    {item.image && <img src={item.image} alt="" className="w-full h-full object-cover" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm truncate">{item.name}</p>
                    <p className="mono-num text-xs text-slate">₹{item.price.toLocaleString("en-IN")} / {item.unit}</p>
                  </div>
                  <div className="flex items-center border border-line rounded-md">
                    <button className="px-2 py-1" onClick={() => updateQuantity(item.productId, item.quantity - 1)}><Minus size={12} /></button>
                    <span className="w-8 text-center mono-num text-sm">{item.quantity}</span>
                    <button className="px-2 py-1" onClick={() => updateQuantity(item.productId, item.quantity + 1)}><Plus size={12} /></button>
                  </div>
                  <button onClick={() => removeItem(item.productId)} className="text-slate hover:text-red transition-colors">
                    <Trash2 size={15} />
                  </button>
                </div>
              ))}
              <div className="flex justify-between p-3 font-semibold">
                <span>Total</span>
                <span className="mono-num">₹{totalAmount.toLocaleString("en-IN")}</span>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="card p-6 space-y-3">
              <p className="eyebrow">Your details</p>
              <input className="input-field w-full" placeholder="Full name" value={name} onChange={(e) => setName(e.target.value)} />
              <input className="input-field w-full" placeholder="Phone number" value={phone} onChange={(e) => setPhone(e.target.value)} />
              <textarea className="input-field w-full" placeholder="Delivery address (optional)" rows={2} value={address} onChange={(e) => setAddress(e.target.value)} />

              <p className="eyebrow pt-2">How would you like to purchase?</p>
              {!info.cod_enabled && !info.contact_enabled && (
                <p className="stamp-red">Online ordering is temporarily unavailable. Please contact the shop directly.</p>
              )}
              {info.cod_enabled && (
                <label className="flex items-center gap-2 text-sm">
                  <input type="radio" name="method" checked={method === "COD"} onChange={() => setMethod("COD")} />
                  Cash on delivery
                </label>
              )}
              {info.contact_enabled && (
                <label className="flex items-center gap-2 text-sm">
                  <input type="radio" name="method" checked={method === "CONTACT"} onChange={() => setMethod("CONTACT")} />
                  Contact me to purchase{info.contact_phone ? ` (or call ${info.contact_phone})` : ""}
                </label>
              )}

              {error && <p className="stamp-red">{error}</p>}
              <button type="submit" disabled={submitting || (!info.cod_enabled && !info.contact_enabled)} className="btn-accent w-full">
                {submitting ? "Placing order..." : "Place Order"}
              </button>
            </form>
          </>
        )}
      </main>
    </div>
  )
}
