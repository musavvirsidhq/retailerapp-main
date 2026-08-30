import { useEffect, useState } from "react"
import { Trash2 } from "lucide-react"
import { listProducts, createProduct, deleteProduct, type Product } from "../api/products"

function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  const [name, setName] = useState("")
  const [unit, setUnit] = useState("")
  const [purchasePrice, setPurchasePrice] = useState("")
  const [sellingPrice, setSellingPrice] = useState("")

  async function loadProducts() {
    try {
      setLoading(true)
      setProducts(await listProducts())
    } catch {
      setError("Could not load products. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadProducts() }, [])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name || !unit) return
    await createProduct({
      name, unit,
      purchase_price: parseFloat(purchasePrice) || 0,
      selling_price: parseFloat(sellingPrice) || 0,
    })
    setName(""); setUnit(""); setPurchasePrice(""); setSellingPrice("")
    loadProducts()
  }

  async function handleDelete(id: number) {
    if (!confirm("Delete this product?")) return
    await deleteProduct(id)
    loadProducts()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Products</h1>
      <p className="eyebrow mb-6">Catalog & stock levels</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8 grid grid-cols-2 gap-3">
        <input className="input-field" placeholder="Product name" value={name} onChange={(e) => setName(e.target.value)} />
        <input className="input-field" placeholder="Unit (kg, box, piece...)" value={unit} onChange={(e) => setUnit(e.target.value)} />
        <input className="input-field mono-num" placeholder="Purchase price" type="number" step="0.01" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} />
        <input className="input-field mono-num" placeholder="Selling price" type="number" step="0.01" value={sellingPrice} onChange={(e) => setSellingPrice(e.target.value)} />
        <button type="submit" className="btn-accent col-span-2">Add Product</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden">
          <div className="card-header"><h2 className="font-display font-semibold">All Products</h2></div>
          <table className="table-base">
            <thead>
              <tr>
                <th>Name</th><th>Unit</th>
                <th className="text-right">Purchase</th><th className="text-right">Selling</th>
                <th className="text-right">Stock</th><th></th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.ID}>
                  <td className="font-medium">{p.Name}</td>
                  <td className="text-slate">{p.Unit}</td>
                  <td className="mono-num text-right">₹{p.PurchasePrice}</td>
                  <td className="mono-num text-right">₹{p.SellingPrice}</td>
                  <td className="mono-num text-right">
                    <span className={parseFloat(p.CurrentStock) < 10 ? "stamp-red" : "stamp-green"}>
                      {p.CurrentStock}
                    </span>
                  </td>
                  <td className="text-right">
                    <button onClick={() => handleDelete(p.ID)} className="text-slate hover:text-red transition-colors">
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
              {products.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate">No products yet. Add one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default ProductsPage
