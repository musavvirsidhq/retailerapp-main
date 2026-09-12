import { useEffect, useRef, useState } from "react"
import { X, Trash2, ImagePlus } from "lucide-react"
import type { Product } from "../api/products"
import {
  getProductStorefront, updateProductStorefront, setPackItems,
  uploadProductImage, deleteProductImage, type ProductImage,
} from "../api/productStorefront"

const SIZES = ["S", "M", "L", "XL", "XXL", "XXXL", "FREE"]

export default function ProductStorefrontModal({ product, onClose }: { product: Product; onClose: () => void }) {
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const fileInput = useRef<HTMLInputElement>(null)

  const [description, setDescription] = useState("")
  const [isBundle, setIsBundle] = useState(false)
  const [storefrontVisible, setStorefrontVisible] = useState(false)
  const [quantities, setQuantities] = useState<Record<string, string>>({})
  const [images, setImages] = useState<ProductImage[]>([])
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    getProductStorefront(product.ID)
      .then((data) => {
        setDescription(data.product.Description || "")
        setIsBundle(data.product.IsBundle)
        setStorefrontVisible(data.product.StorefrontVisible)
        setImages(data.images)
        const q: Record<string, string> = {}
        for (const item of data.pack_items) q[item.Size] = String(item.Quantity)
        setQuantities(q)
      })
      .catch(() => setError("Could not load storefront details"))
      .finally(() => setLoading(false))
  }, [product.ID])

  async function handleSave() {
    setSaving(true)
    setError("")
    try {
      await updateProductStorefront(product.ID, { description, is_bundle: isBundle, storefront_visible: storefrontVisible })
      const items = isBundle
        ? SIZES.filter((s) => parseInt(quantities[s]) > 0).map((s) => ({ size: s, quantity: parseInt(quantities[s]) }))
        : []
      await setPackItems(product.ID, items)
      onClose()
    } catch (err: any) {
      setError(err.message || "Failed to save")
    } finally {
      setSaving(false)
    }
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    setError("")
    try {
      const image = await uploadProductImage(product.ID, file)
      setImages((prev) => [...prev, image])
    } catch (err: any) {
      setError(err.message || "Failed to upload image")
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ""
    }
  }

  async function handleDeleteImage(imageId: number) {
    setImages((prev) => prev.filter((img) => img.ID !== imageId))
    try {
      await deleteProductImage(product.ID, imageId)
    } catch {
      setError("Failed to delete image")
    }
  }

  const packTotal = SIZES.reduce((sum, s) => sum + (parseInt(quantities[s]) || 0), 0)

  return (
    <div className="fixed inset-0 bg-black/40 z-40 flex items-center justify-center p-4" onClick={onClose}>
      <div className="card w-full max-w-xl max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
        <div className="card-header">
          <h2 className="font-display font-semibold">Storefront &middot; {product.Name}</h2>
          <button onClick={onClose} className="text-slate hover:text-ink"><X size={18} /></button>
        </div>

        {loading ? (
          <p className="p-6 text-slate text-sm">Loading...</p>
        ) : (
          <div className="p-6 space-y-5">
            <div>
              <label className="eyebrow block mb-1.5">Photos</label>
              <div className="flex flex-wrap gap-3">
                {images.map((img) => (
                  <div key={img.ID} className="relative w-20 h-20 rounded-md overflow-hidden border border-line group">
                    <img src={img.Url} alt="" className="w-full h-full object-cover" />
                    <button
                      onClick={() => handleDeleteImage(img.ID)}
                      className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity"
                    >
                      <Trash2 size={16} className="text-white" />
                    </button>
                  </div>
                ))}
                <button
                  onClick={() => fileInput.current?.click()}
                  disabled={uploading}
                  className="w-20 h-20 rounded-md border border-dashed border-line flex flex-col items-center justify-center text-slate hover:border-amber hover:text-amber transition-colors disabled:opacity-50"
                >
                  <ImagePlus size={18} />
                  <span className="text-[10px] mt-1">{uploading ? "..." : "Add"}</span>
                </button>
                <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={handleFileChange} />
              </div>
            </div>

            <div>
              <label className="eyebrow block mb-1.5">Description (shown on storefront)</label>
              <textarea
                className="input-field w-full"
                rows={3}
                placeholder="Fabric, fit, care instructions..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={isBundle} onChange={(e) => setIsBundle(e.target.checked)} />
              Sold as a fixed size-assortment pack (S/M/L/XL...)
            </label>

            {isBundle && (
              <div>
                <label className="eyebrow block mb-1.5">One pack contains</label>
                <div className="grid grid-cols-4 gap-2">
                  {SIZES.map((size) => (
                    <div key={size}>
                      <span className="text-xs text-slate">{size}</span>
                      <input
                        type="number"
                        min={0}
                        className="input-field w-full mono-num"
                        value={quantities[size] || ""}
                        onChange={(e) => setQuantities((prev) => ({ ...prev, [size]: e.target.value }))}
                      />
                    </div>
                  ))}
                </div>
                <p className="text-xs text-slate mt-1.5">{packTotal} piece{packTotal === 1 ? "" : "s"} per pack</p>
              </div>
            )}

            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={storefrontVisible} onChange={(e) => setStorefrontVisible(e.target.checked)} />
              Show this product on the public storefront
            </label>

            {error && <p className="stamp-red">{error}</p>}
            <button onClick={handleSave} disabled={saving} className="btn-accent w-full">
              {saving ? "Saving..." : "Save"}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
