import { useEffect, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { getPurchaseBill, purchaseBillPdfUrl, shareBillPdf, type BillData } from "../api/bills"
import { cancelPurchase } from "../api/purchases"
import { useAuth } from "../context/AuthContext"
import BillView from "../components/BillView"

function PurchaseBillPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [bill, setBill] = useState<BillData | null>(null)
  const [error, setError] = useState("")

  async function load() {
    if (!id) return
    try {
      setBill(await getPurchaseBill(parseInt(id)))
    } catch {
      setError("Could not load this bill.")
    }
  }

  useEffect(() => { load() }, [id])

  if (error) return <p className="stamp-red max-w-2xl mx-auto">{error}</p>
  if (!bill) return <p className="text-slate text-sm max-w-2xl mx-auto">Loading...</p>

  const canCancel = user?.user_type === "COMPANY_ADMIN" || user?.purchase_access === true

  return (
    <BillView
      bill={bill}
      pdfUrl={purchaseBillPdfUrl(parseInt(id!))}
      onShare={() => shareBillPdf(purchaseBillPdfUrl(parseInt(id!)), bill.BillNumber)}
      canCancel={canCancel}
      onCancel={async (reason) => {
        await cancelPurchase(parseInt(id!), reason)
        await load()
        navigate(`/purchases/${id}/bill`, { replace: true })
      }}
    />
  )
}

export default PurchaseBillPage
