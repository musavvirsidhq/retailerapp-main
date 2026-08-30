import { useEffect, useState } from "react"
import { listPayments, createPayment, type Payment } from "../api/payments"
import { listShops, type Shop } from "../api/shops"
import { listFactories, type Factory } from "../api/factories"

function PaymentsPage() {
  const [payments, setPayments] = useState<Payment[]>([])
  const [shops, setShops] = useState<Shop[]>([])
  const [factories, setFactories] = useState<Factory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const [partyType, setPartyType] = useState<"shop" | "factory">("shop")
  const [partyId, setPartyId] = useState("")
  const [amount, setAmount] = useState("")
  const [paymentMode, setPaymentMode] = useState("cash")
  const [notes, setNotes] = useState("")

  async function loadAll() {
    try {
      setLoading(true)
      const [p, sh, f] = await Promise.all([listPayments(), listShops(), listFactories()])
      setPayments(p); setShops(sh); setFactories(f)
    } catch {
      setError("Could not load data. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAll() }, [])

  function partyName(type: string, id: number): string {
    if (type === "shop") return shops.find((s) => s.ID === id)?.Name || `Shop #${id}`
    return factories.find((f) => f.ID === id)?.Name || `Factory #${id}`
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!partyId || !amount) return
    await createPayment({
      party_type: partyType,
      party_id: parseInt(partyId),
      amount: parseFloat(amount),
      payment_mode: paymentMode,
      notes,
    })
    setPartyId(""); setAmount(""); setNotes("")
    loadAll()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Payments</h1>
      <p className="eyebrow mb-6">Money in from shops, money out to factories</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8 grid grid-cols-2 gap-3">
        <select className="input-field" value={partyType} onChange={(e) => { setPartyType(e.target.value as "shop" | "factory"); setPartyId("") }}>
          <option value="shop">Received from Shop</option>
          <option value="factory">Paid to Factory</option>
        </select>
        <select className="input-field" value={partyId} onChange={(e) => setPartyId(e.target.value)}>
          <option value="">Select {partyType}</option>
          {(partyType === "shop" ? shops : factories).map((item: any) => (
            <option key={item.ID} value={item.ID}>{item.Name}</option>
          ))}
        </select>
        <input className="input-field mono-num" placeholder="Amount" type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
        <select className="input-field" value={paymentMode} onChange={(e) => setPaymentMode(e.target.value)}>
          <option value="cash">Cash</option>
          <option value="upi">UPI</option>
          <option value="cheque">Cheque</option>
        </select>
        <input className="input-field col-span-2" placeholder="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <button type="submit" className="btn-accent col-span-2">Record Payment</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">Payment History</h2></div>
          <table className="table-base">
            <thead>
              <tr><th>Date</th><th>Type</th><th>Party</th><th className="text-right">Amount</th><th>Mode</th><th>Notes</th></tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.ID}>
                  <td className="mono-num text-slate">{p.PaymentDate}</td>
                  <td><span className={p.PartyType === "shop" ? "stamp-green" : "stamp-red"}>{p.PartyType}</span></td>
                  <td className="font-medium">{partyName(p.PartyType, p.PartyID)}</td>
                  <td className="mono-num text-right">₹{p.Amount}</td>
                  <td className="text-slate capitalize">{p.PaymentMode}</td>
                  <td className="text-slate">{p.Notes || "-"}</td>
                </tr>
              ))}
              {payments.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate">No payments yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default PaymentsPage
