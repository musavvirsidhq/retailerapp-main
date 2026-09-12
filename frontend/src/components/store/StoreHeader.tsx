import { Link, useParams } from "react-router-dom"
import { ShoppingBag } from "lucide-react"
import { useStoreCart } from "../../context/StoreCartContext"

export default function StoreHeader({ companyName }: { companyName: string }) {
  const { companyCode } = useParams()
  const { totalCount } = useStoreCart()

  return (
    <header className="sticky top-0 z-10 bg-ink text-white">
      <div className="max-w-5xl mx-auto px-4 md:px-8 py-4 flex items-center justify-between">
        <Link to={`/store/${companyCode}`} className="min-w-0">
          <h1 className="font-display font-bold text-lg tracking-tight truncate">{companyName}</h1>
          <p className="eyebrow text-white/40">Wholesale Catalog</p>
        </Link>
        <Link to={`/store/${companyCode}/cart`} className="relative flex items-center gap-2 text-white/80 hover:text-white transition-colors shrink-0">
          <ShoppingBag size={22} />
          {totalCount > 0 && (
            <span className="absolute -top-2 -right-2 bg-amber text-white text-[10px] font-bold rounded-full w-5 h-5 flex items-center justify-center">
              {totalCount}
            </span>
          )}
        </Link>
      </div>
    </header>
  )
}
