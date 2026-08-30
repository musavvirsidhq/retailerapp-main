import { NavLink, Outlet, useNavigate } from "react-router-dom"
import {
  LayoutDashboard, Package, Factory, Store, PackagePlus,
  ShoppingCart, Wallet, LogOut,
} from "lucide-react"
import { useAuth } from "../context/AuthContext"

const NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/products", label: "Products", icon: Package },
  { to: "/factories", label: "Factories", icon: Factory },
  { to: "/shops", label: "Shops", icon: Store },
  { to: "/purchases", label: "Purchases", icon: PackagePlus },
  { to: "/sales", label: "Sales", icon: ShoppingCart },
  { to: "/payments", label: "Payments", icon: Wallet },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate("/login")
  }

  return (
    <div className="flex min-h-screen bg-paper">
      <aside className="w-60 bg-ink text-white flex flex-col">
        <div className="h-1 bg-amber" />
        <div className="px-5 py-5 border-b border-white/10">
          <h1 className="font-display font-bold text-lg tracking-tight">RetailApp</h1>
          <p className="eyebrow text-white/40 mt-0.5">Distributor Ledger</p>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1">
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors border-l-2 ${
                  isActive
                    ? "bg-white/10 border-amber text-white"
                    : "border-transparent text-white/60 hover:text-white hover:bg-white/5"
                }`
              }
            >
              <Icon size={17} strokeWidth={2} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="px-5 py-4 border-t border-white/10">
          <p className="text-sm font-medium">{user?.name}</p>
          <span className="stamp-amber mt-1">{user?.role}</span>
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 text-white/50 hover:text-white text-sm mt-3 transition-colors"
          >
            <LogOut size={14} /> Log out
          </button>
        </div>
      </aside>
      <main className="flex-1 p-8 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
