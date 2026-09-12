import { useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { ArrowLeft, PackageSearch, Minus, Plus } from "lucide-react"
import { getStoreInfo, getStoreProduct, type StoreInfo, type StoreProduct } from "../../api/publicStore"
import { useStoreCart } from "../../context/StoreCartContext"
import StoreHeader from "../../components/store/StoreHeader"

export default function StoreProductPage() {
  const { companyCode = "", id = "" } = useParams()
  const navigate = useNavigate()
  const { addItem } = useStoreCart()

  const [info, setInfo] = useState<StoreInfo | null>(null)
  const [product, setProduct] = useState<StoreProduct | null>(null)
  const [activeImage, setActiveImage] = useState(0)
  const [quantity, setQuantity] = useState(1)
  const [loading, setLoading] = useState(true)
  const [added, setAdded] = useState(false)

  useEffect(() => {
    Promise.all([getStoreInfo(companyCode), getStoreProduct(companyCode, parseInt(id))])
      .then(([i, p]) => { setInfo(i); setProduct(p) })
      .catch(() => setProduct(null))
      .finally(() => setLoading(false))
  }, [companyCode, id])

  if (loading) return <div className="min-h-screen bg-paper flex items-center justify-center text-slate">Loading...</div>
  if (!info || !product) {
    return (
      <div className="min-h-screen bg-paper flex items-center justify-center p-6 text-center">
        <div>
          <h1 className="font-display font-bold text-xl mb-2">Product not found</h1>
          <Link to={`/store/${companyCode}`} className="btn-ghost">Back to catalog</Link>
        </div>
      </div>
    )
  }

  function handleAddToCart() {
    if (!product) return
    addItem({ productId: product.id, name: product.name, price: product.selling_price, unit: product.unit, image: product.images[0]?.url }, quantity)
    setAdded(true)
    setTimeout(() => navigate(`/store/${companyCode}/cart`), 400)
  }

  const packTotal = product.pack_items?.reduce((sum, i) => sum + i.quantity, 0) ?? 0

  return (
    <div className="min-h-screen bg-paper">
      <StoreHeader companyName={info.company_name} />

      <main className="max-w-5xl mx-auto px-4 md:px-8 py-6">
        <Link to={`/store/${companyCode}`} className="btn-ghost inline-flex items-center gap-1.5 mb-4">
          <ArrowLeft size={15} /> Back
        </Link>

        <div className="grid md:grid-cols-2 gap-8">
          <div>
            <div className="aspect-square bg-line rounded-lg overflow-hidden">
              {product.images[activeImage] ? (
                <img src={product.images[activeImage].url} alt={product.name} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-slate/50">
                  <PackageSearch size={40} />
                </div>
              )}
            </div>
            {product.images.length > 1 && (
              <div className="flex gap-2 mt-2">
                {product.images.map((img, i) => (
                  <button
                    key={img.url}
                    onClick={() => setActiveImage(i)}
                    className={`w-14 h-14 rounded-md overflow-hidden border-2 ${i === activeImage ? "border-amber" : "border-transparent"}`}
                  >
                    <img src={img.url} alt="" className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
            <h1 className="font-display font-bold text-2xl">{product.name}</h1>
            <p className="mono-num font-semibold text-amber text-xl mt-1">₹{product.selling_price.toLocaleString("en-IN")}</p>
            <span className={product.in_stock ? "stamp-green mt-2" : "stamp-red mt-2"}>
              {product.in_stock ? "In stock" : "Out of stock"}
            </span>

            {product.description && <p className="text-sm text-ink-soft mt-4 whitespace-pre-line">{product.description}</p>}

            {product.is_bundle && product.pack_items && product.pack_items.length > 0 && (
              <div className="card p-4 mt-4">
                <p className="eyebrow mb-2">Pack composition ({packTotal} pcs)</p>
                <div className="flex flex-wrap gap-2">
                  {product.pack_items.map((item) => (
                    <span key={item.size} className="stamp-slate">{item.size}: {item.quantity}</span>
                  ))}
                </div>
              </div>
            )}

            <div className="flex items-center gap-3 mt-6">
              <div className="flex items-center border border-line rounded-md">
                <button className="px-3 py-2" onClick={() => setQuantity((q) => Math.max(1, q - 1))}><Minus size={14} /></button>
                <span className="w-10 text-center mono-num">{quantity}</span>
                <button className="px-3 py-2" onClick={() => setQuantity((q) => q + 1)}><Plus size={14} /></button>
              </div>
              <span className="text-sm text-slate">{product.unit}{product.is_bundle ? "s" : ""}</span>
            </div>

            <button
              onClick={handleAddToCart}
              disabled={!product.in_stock}
              className="btn-accent w-full mt-4 disabled:opacity-50"
            >
              {added ? "Added!" : "Add to Cart"}
            </button>
          </div>
        </div>
      </main>
    </div>
  )
}
