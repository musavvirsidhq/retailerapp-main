import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import './index.css'
import { AuthProvider } from './context/AuthContext.tsx'
import ProtectedRoute from './components/ProtectedRoute.tsx'
import Layout from './components/Layout.tsx'
import LoginPage from './pages/LoginPage.tsx'
import DashboardPage from './pages/DashboardPage.tsx'
import ProductsPage from './pages/ProductsPage.tsx'
import FactoriesPage from './pages/FactoriesPage.tsx'
import ShopsPage from './pages/ShopsPage.tsx'
import PurchasesPage from './pages/PurchasesPage.tsx'
import SalesPage from './pages/SalesPage.tsx'
import PaymentsPage from './pages/PaymentsPage.tsx'
import SaleBillPage from './pages/SaleBillPage.tsx'
import PurchaseBillPage from './pages/PurchaseBillPage.tsx'
import SuperAdminCompaniesPage from './pages/SuperAdminCompaniesPage.tsx'
import CompanyUsersPage from './pages/CompanyUsersPage.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="factories" element={<FactoriesPage />} />
            <Route path="shops" element={<ShopsPage />} />
            <Route path="purchases" element={<PurchasesPage />} />
            <Route path="sales" element={<SalesPage />} />
            <Route path="sales/:id/bill" element={<SaleBillPage />} />
            <Route path="purchases/:id/bill" element={<PurchaseBillPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="company/users" element={<CompanyUsersPage />} />
            <Route path="super-admin/companies" element={<SuperAdminCompaniesPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
