import { useState } from "react"
import { NavLink, Outlet, useNavigate } from "react-router-dom"
import {
  LayoutDashboard, Package, Factory, Store, PackagePlus,
  ShoppingCart, Wallet, LogOut, Menu, X, Building2, Users,
} from "lucide-react"
import { useAuth } from "../context/AuthContext"
import SubscriptionBanner from "./SubscriptionBanner"

const COMPANY_NAV_ITEMS = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/products", label: "Products", icon: Package },
  { to: "/factories", label: "Factories", icon: Factory },
  { to: "/shops", label: "Shops", icon: Store },
  { to: "/purchases", label: "Purchases", icon: PackagePlus },
  { to: "/sales", label: "Sales", icon: ShoppingCart },
  { to: "/payments", label: "Payments", icon: Wallet },
]

const COMPANY_ADMIN_NAV_ITEMS = [
  { to: "/company/users", label: "Staff", icon: Users },
]

const SUPER_ADMIN_NAV_ITEMS = [
  { to: "/super-admin/companies", label: "Companies", icon: Building2 },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const isSuperAdmin = user?.user_type === "SUPER_ADMIN"
  const isCompanyAdmin = user?.user_type === "COMPANY_ADMIN"

  const navItems = isSuperAdmin
    ? SUPER_ADMIN_NAV_ITEMS
    : [...COMPANY_NAV_ITEMS, ...(isCompanyAdmin ? COMPANY_ADMIN_NAV_ITEMS : [])]

  async function handleLogout() {
    await logout()
    navigate("/login")
  }

  return (
    <div className="flex min-h-screen bg-paper">
      {drawerOpen && (
        <div className="fixed inset-0 bg-black/40 z-20 md:hidden" onClick={() => setDrawerOpen(false)} />
      )}

      <aside
        className={`no-print fixed inset-y-0 left-0 z-30 w-64 bg-ink text-white flex flex-col transition-transform duration-200 md:static md:translate-x-0 md:w-60 ${
          drawerOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="h-1 bg-amber" />
        <div className="px-5 py-5 border-b border-white/10 flex items-center justify-between">
          <div>
            <h1 className="font-display font-bold text-lg tracking-tight">RetailApp</h1>
            <p className="eyebrow text-white/40 mt-0.5">
              {isSuperAdmin ? "Platform Admin" : "Distributor Ledger"}
            </p>
          </div>
          <button className="md:hidden text-white/60 hover:text-white" onClick={() => setDrawerOpen(false)}>
            <X size={20} />
          </button>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setDrawerOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 md:py-2 rounded-md text-sm font-medium transition-colors border-l-2 ${
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
          <span className="stamp-amber mt-1">{user?.user_type}</span>
          <button
            onClick={handleLogout}
            className="flex items-center gap-2 text-white/50 hover:text-white text-sm mt-3 transition-colors"
          >
            <LogOut size={14} /> Log out
          </button>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <div className="no-print md:hidden flex items-center gap-3 px-4 py-3 bg-ink text-white">
          <button onClick={() => setDrawerOpen(true)}><Menu size={20} /></button>
          <span className="font-display font-bold">RetailApp</span>
        </div>
        {!isSuperAdmin && <SubscriptionBanner />}
        <main className="flex-1 p-4 md:p-8 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
