import { useEffect, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { PackageSearch } from "lucide-react"
import { getStoreInfo, listStoreProducts, type StoreInfo, type StoreProduct } from "../../api/publicStore"
import StoreHeader from "../../components/store/StoreHeader"

export default function StoreCatalogPage() {
  const { companyCode = "" } = useParams()
  const [info, setInfo] = useState<StoreInfo | null>(null)
  const [products, setProducts] = useState<StoreProduct[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([getStoreInfo(companyCode), listStoreProducts(companyCode)])
      .then(([i, p]) => { setInfo(i); setProducts(p) })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false))
  }, [companyCode])

  if (loading) return <div className="min-h-screen bg-paper flex items-center justify-center text-slate">Loading...</div>

  if (!info) {
    return (
      <div className="min-h-screen bg-paper flex items-center justify-center p-6 text-center">
        <div>
          <h1 className="font-display font-bold text-xl mb-2">Storefront not available</h1>
          <p className="text-slate text-sm">This shop isn&apos;t taking online orders right now.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-paper">
      <StoreHeader companyName={info.company_name} />

      <main className="max-w-5xl mx-auto px-4 md:px-8 py-6">
        {info.contact_enabled && info.contact_phone && (
          <div className="stamp-amber mb-6">Bulk enquiries: call {info.contact_phone}</div>
        )}

        {products.length === 0 ? (
          <div className="text-center py-24 text-slate">
            <PackageSearch className="mx-auto mb-3" size={32} />
            <p>No products available yet.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {products.map((p) => {
              const packTotal = p.pack_items?.reduce((sum, i) => sum + i.quantity, 0) ?? 0
              return (
                <Link
                  key={p.id}
                  to={`/store/${companyCode}/products/${p.id}`}
                  className="card overflow-hidden hover:shadow-md transition-shadow group"
                >
                  <div className="aspect-square bg-line overflow-hidden">
                    {p.images[0] ? (
                      <img src={p.images[0].url} alt={p.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-slate/50">
                        <PackageSearch size={28} />
                      </div>
                    )}
                  </div>
                  <div className="p-3">
                    <p className="font-medium text-sm truncate">{p.name}</p>
                    <div className="flex items-center justify-between mt-1">
                      <span className="mono-num font-semibold text-amber">₹{p.selling_price.toLocaleString("en-IN")}</span>
                      {!p.in_stock && <span className="stamp-red">Out of stock</span>}
                    </div>
                    {p.is_bundle && packTotal > 0 && (
                      <p className="text-xs text-slate mt-1">Pack of {packTotal} &middot; {p.unit}</p>
                    )}
                  </div>
                </Link>
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}
