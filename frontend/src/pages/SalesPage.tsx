import { useEffect, useState } from "react"
import { Plus, X } from "lucide-react"
import { listSales, createSale, type Sale } from "../api/sales"
import { listShops, type Shop } from "../api/shops"
import { listProducts, type Product } from "../api/products"

interface LineItem {
  product_id: number
  quantity: string
  unit_price: string
}

function SalesPage() {
  const [sales, setSales] = useState<Sale[]>([])
  const [shops, setShops] = useState<Shop[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [formError, setFormError] = useState("")

  const [shopId, setShopId] = useState("")
  const [paymentType, setPaymentType] = useState<"cash" | "credit">("cash")
  const [amountPaid, setAmountPaid] = useState("")
  const [items, setItems] = useState<LineItem[]>([{ product_id: 0, quantity: "", unit_price: "" }])

  async function loadAll() {
    try {
      setLoading(true)
      const [s, sh, pr] = await Promise.all([listSales(), listShops(), listProducts()])
      setSales(s); setShops(sh); setProducts(pr)
    } catch {
      setError("Could not load data. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAll() }, [])

  function addItemRow() { setItems([...items, { product_id: 0, quantity: "", unit_price: "" }]) }
  function removeItemRow(index: number) { setItems(items.filter((_, i) => i !== index)) }
  function updateItem(index: number, field: keyof LineItem, value: string | number) {
    const updated = [...items]
    updated[index] = { ...updated[index], [field]: value }
    if (field === "product_id") {
      const product = products.find((p) => p.ID === value)
      if (product) updated[index].unit_price = product.SellingPrice
    }
    setItems(updated)
  }

  const total = items.reduce((sum, item) => {
    const qty = parseFloat(item.quantity) || 0
    const price = parseFloat(item.unit_price) || 0
    return sum + qty * price
  }, 0)

  function stockFor(productId: number): number {
    const p = products.find((p) => p.ID === productId)
    return p ? parseFloat(p.CurrentStock) : 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError("")
    if (!shopId) { setFormError("Please select a shop"); return }
    const validItems = items.filter((i) => i.product_id && i.quantity && i.unit_price)
    if (validItems.length === 0) { setFormError("Add at least one valid product line"); return }

    try {
      await createSale({
        shop_id: parseInt(shopId),
        amount_paid: paymentType === "credit" ? parseFloat(amountPaid) || 0 : 0,
        payment_type: paymentType,
        items: validItems.map((i) => ({
          product_id: i.product_id,
          quantity: parseFloat(i.quantity),
          unit_price: parseFloat(i.unit_price),
        })),
      })
      setShopId(""); setPaymentType("cash"); setAmountPaid("")
      setItems([{ product_id: 0, quantity: "", unit_price: "" }])
      loadAll()
    } catch (err: any) {
      setFormError(err.message || "Failed to create sale")
    }
  }

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Sales</h1>
      <p className="eyebrow mb-6">Stock out to shops</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8">
        <div className="grid grid-cols-3 gap-3 mb-5">
          <select className="input-field" value={shopId} onChange={(e) => setShopId(e.target.value)}>
            <option value="">Select shop</option>
            {shops.map((s) => <option key={s.ID} value={s.ID}>{s.Name}</option>)}
          </select>
          <select className="input-field" value={paymentType} onChange={(e) => setPaymentType(e.target.value as "cash" | "credit")}>
            <option value="cash">Cash</option>
            <option value="credit">Credit (Udhaar)</option>
          </select>
          {paymentType === "credit" && (
            <input className="input-field mono-num" placeholder="Amount paid now (0 if none)" type="number" step="0.01" value={amountPaid} onChange={(e) => setAmountPaid(e.target.value)} />
          )}
        </div>

        <div className="border-t border-line pt-4">
          <h3 className="eyebrow mb-3">Products</h3>
          {items.map((item, index) => {
            const available = stockFor(item.product_id)
            const requested = parseFloat(item.quantity) || 0
            const exceedsStock = item.product_id !== 0 && requested > available
            return (
              <div key={index} className="mb-2">
                <div className="grid grid-cols-4 gap-2">
                  <select className="input-field col-span-2" value={item.product_id} onChange={(e) => updateItem(index, "product_id", parseInt(e.target.value))}>
                    <option value={0}>Select product</option>
                    {products.map((p) => <option key={p.ID} value={p.ID}>{p.Name} (stock: {p.CurrentStock})</option>)}
                  </select>
                  <input className={`input-field mono-num ${exceedsStock ? "border-red" : ""}`} placeholder="Quantity" type="number" step="0.001" value={item.quantity} onChange={(e) => updateItem(index, "quantity", e.target.value)} />
                  <div className="flex gap-2">
                    <input className="input-field mono-num flex-1" placeholder="Unit price" type="number" step="0.01" value={item.unit_price} onChange={(e) => updateItem(index, "unit_price", e.target.value)} />
                    {items.length > 1 && (
                      <button type="button" onClick={() => removeItemRow(index)} className="text-red hover:opacity-70 px-1">
                        <X size={16} />
                      </button>
                    )}
                  </div>
                </div>
                {exceedsStock && <p className="stamp-red mt-1">Only {available} in stock</p>}
              </div>
            )
          })}
          <button type="button" onClick={addItemRow} className="flex items-center gap-1 text-amber text-sm font-medium hover:opacity-80 mt-1">
            <Plus size={14} /> Add another product
          </button>
        </div>

        {formError && <p className="stamp-red mt-3">{formError}</p>}

        <div className="flex justify-between items-center mt-6 pt-5 border-t border-line">
          <p className="font-display font-semibold text-lg">Total: <span className="mono-num">₹{total.toFixed(2)}</span></p>
          <button type="submit" className="btn-accent">Save Sale</button>
        </div>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">Sales History</h2></div>
          <table className="table-base">
            <thead>
              <tr><th>Date</th><th>Shop</th><th>Type</th><th className="text-right">Total</th><th className="text-right">Paid</th></tr>
            </thead>
            <tbody>
              {sales.map((s) => (
                <tr key={s.ID}>
                  <td className="mono-num text-slate">{s.SaleDate}</td>
                  <td className="font-medium">{s.ShopName}</td>
                  <td>
                    <span className={s.PaymentType === "cash" ? "stamp-green" : "stamp-amber"}>{s.PaymentType}</span>
                  </td>
                  <td className="mono-num text-right">₹{s.TotalAmount}</td>
                  <td className="mono-num text-right text-green">₹{s.AmountPaid}</td>
                </tr>
              ))}
              {sales.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-slate">No sales yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default SalesPage
