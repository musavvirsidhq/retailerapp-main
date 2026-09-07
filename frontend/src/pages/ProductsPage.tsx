import { useEffect, useState } from "react"
import { Trash2, Plus } from "lucide-react"
import { listProducts, createProduct, deleteProduct, type Product } from "../api/products"
import { listUnits, type Unit } from "../api/units"
import { listCategories, createCategory, listSubcategories, createSubcategory, type Category, type Subcategory } from "../api/categories"

function ProductsPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [units, setUnits] = useState<Unit[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [subcategories, setSubcategories] = useState<Subcategory[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [formError, setFormError] = useState("")

  const [name, setName] = useState("")
  const [sku, setSku] = useState("")
  const [unit, setUnit] = useState("")
  const [categoryId, setCategoryId] = useState("")
  const [subcategoryId, setSubcategoryId] = useState("")
  const [sellingPrice, setSellingPrice] = useState("")
  const [newCategory, setNewCategory] = useState("")
  const [newSubcategory, setNewSubcategory] = useState("")

  async function loadAll() {
    try {
      setLoading(true)
      const [p, u, c] = await Promise.all([listProducts(), listUnits(), listCategories()])
      setProducts(p); setUnits(u); setCategories(c)
    } catch {
      setError("Could not load products. Is the backend running?")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadAll() }, [])

  useEffect(() => {
    if (!categoryId) { setSubcategories([]); return }
    listSubcategories(parseInt(categoryId)).then(setSubcategories).catch(() => setSubcategories([]))
  }, [categoryId])

  function categoryName(id: number) {
    return categories.find((c) => c.ID === id)?.Name || "-"
  }

  async function handleAddCategory() {
    if (!newCategory.trim()) return
    const c = await createCategory(newCategory.trim())
    setCategories([...categories, c])
    setCategoryId(String(c.ID))
    setNewCategory("")
  }

  async function handleAddSubcategory() {
    if (!newSubcategory.trim() || !categoryId) return
    const s = await createSubcategory(parseInt(categoryId), newSubcategory.trim())
    setSubcategories([...subcategories, s])
    setSubcategoryId(String(s.ID))
    setNewSubcategory("")
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError("")
    if (!name || !sku || !unit || !categoryId) {
      setFormError("Name, SKU, unit and category are required")
      return
    }
    try {
      await createProduct({
        name, sku, unit,
        category_id: parseInt(categoryId),
        subcategory_id: subcategoryId ? parseInt(subcategoryId) : null,
        selling_price: parseFloat(sellingPrice) || 0,
      })
      setName(""); setSku(""); setUnit(""); setCategoryId(""); setSubcategoryId(""); setSellingPrice("")
      loadAll()
    } catch (err: any) {
      setFormError(err.message || "Failed to create product")
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("Delete this product?")) return
    await deleteProduct(id)
    loadAll()
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-2xl mb-1">Products</h1>
      <p className="eyebrow mb-6">Catalog &amp; stock levels</p>

      <form onSubmit={handleSubmit} className="card p-6 mb-8">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <input className="input-field" placeholder="Product name" value={name} onChange={(e) => setName(e.target.value)} />
          <input className="input-field" placeholder="Product code / SKU" value={sku} onChange={(e) => setSku(e.target.value.toUpperCase())} />

          <select className="input-field" value={unit} onChange={(e) => setUnit(e.target.value)}>
            <option value="">Select unit</option>
            {units.map((u) => <option key={u.Code} value={u.Code}>{u.Label}</option>)}
          </select>

          <input className="input-field mono-num" placeholder="Selling price" type="number" step="0.01" value={sellingPrice} onChange={(e) => setSellingPrice(e.target.value)} />

          <div className="flex gap-2">
            <select className="input-field flex-1" value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setSubcategoryId("") }}>
              <option value="">Select category</option>
              {categories.map((c) => <option key={c.ID} value={c.ID}>{c.Name}</option>)}
            </select>
          </div>
          <div className="flex gap-2">
            <input className="input-field flex-1" placeholder="+ New category" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} />
            <button type="button" onClick={handleAddCategory} className="btn-ghost px-2"><Plus size={16} /></button>
          </div>

          <select className="input-field" value={subcategoryId} onChange={(e) => setSubcategoryId(e.target.value)} disabled={!categoryId}>
            <option value="">No subcategory</option>
            {subcategories.map((s) => <option key={s.ID} value={s.ID}>{s.Name}</option>)}
          </select>
          <div className="flex gap-2">
            <input className="input-field flex-1" placeholder="+ New subcategory" value={newSubcategory} onChange={(e) => setNewSubcategory(e.target.value)} disabled={!categoryId} />
            <button type="button" onClick={handleAddSubcategory} disabled={!categoryId} className="btn-ghost px-2 disabled:opacity-30"><Plus size={16} /></button>
          </div>
        </div>

        {formError && <p className="stamp-red mt-3">{formError}</p>}
        <button type="submit" className="btn-accent w-full mt-4">Add Product</button>
      </form>

      {loading && <p className="text-slate text-sm">Loading...</p>}
      {error && <p className="stamp-red">{error}</p>}

      {!loading && !error && (
        <div className="card overflow-hidden overflow-x-auto">
          <div className="card-header"><h2 className="font-display font-semibold">All Products</h2></div>
          <table className="table-base">
            <thead>
              <tr>
                <th>Name</th><th>SKU</th><th>Unit</th><th>Category</th>
                <th className="text-right">Selling</th>
                <th className="text-right">Stock</th><th></th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.ID}>
                  <td className="font-medium">{p.Name}</td>
                  <td className="mono-num text-slate">{p.Sku}</td>
                  <td className="text-slate">{p.Unit}</td>
                  <td className="text-slate">{categoryName(p.CategoryID)}</td>
                  <td className="mono-num text-right">₹{p.CurrentSellingPrice}</td>
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
                <tr><td colSpan={7} className="px-4 py-8 text-center text-slate">No products yet. Add one above.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default ProductsPage
