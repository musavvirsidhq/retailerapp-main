import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { useParams } from "react-router-dom"

export interface CartItem {
  productId: number
  name: string
  price: number
  quantity: number
  unit: string
  image?: string
}

interface StoreCartContextType {
  items: CartItem[]
  addItem: (item: Omit<CartItem, "quantity">, quantity: number) => void
  updateQuantity: (productId: number, quantity: number) => void
  removeItem: (productId: number) => void
  clear: () => void
  totalCount: number
  totalAmount: number
}

const StoreCartContext = createContext<StoreCartContextType | undefined>(undefined)

export function StoreCartProvider({ children }: { children: ReactNode }) {
  const { companyCode } = useParams()
  const storageKey = `bulqbee_cart_${companyCode}`
  const [items, setItems] = useState<CartItem[]>([])

  useEffect(() => {
    try {
      const raw = localStorage.getItem(storageKey)
      setItems(raw ? JSON.parse(raw) : [])
    } catch {
      setItems([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [storageKey])

  function persist(next: CartItem[]) {
    setItems(next)
    try { localStorage.setItem(storageKey, JSON.stringify(next)) } catch { /* private browsing, etc. - cart just won't persist */ }
  }

  function addItem(item: Omit<CartItem, "quantity">, quantity: number) {
    const existing = items.find((i) => i.productId === item.productId)
    if (existing) {
      persist(items.map((i) => (i.productId === item.productId ? { ...i, quantity: i.quantity + quantity } : i)))
    } else {
      persist([...items, { ...item, quantity }])
    }
  }

  function updateQuantity(productId: number, quantity: number) {
    if (quantity <= 0) return removeItem(productId)
    persist(items.map((i) => (i.productId === productId ? { ...i, quantity } : i)))
  }

  function removeItem(productId: number) {
    persist(items.filter((i) => i.productId !== productId))
  }

  function clear() {
    persist([])
  }

  const totalCount = items.reduce((sum, i) => sum + i.quantity, 0)
  const totalAmount = items.reduce((sum, i) => sum + i.quantity * i.price, 0)

  return (
    <StoreCartContext.Provider value={{ items, addItem, updateQuantity, removeItem, clear, totalCount, totalAmount }}>
      {children}
    </StoreCartContext.Provider>
  )
}

export function useStoreCart() {
  const ctx = useContext(StoreCartContext)
  if (!ctx) throw new Error("useStoreCart must be used within StoreCartProvider")
  return ctx
}
