import { useEffect, useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { getSaleBill, saleBillPdfUrl, shareBillPdf, type BillData } from "../api/bills"
import { cancelSale } from "../api/sales"
import { useAuth } from "../context/AuthContext"
import BillView from "../components/BillView"

function SaleBillPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [bill, setBill] = useState<BillData | null>(null)
  const [error, setError] = useState("")

  async function load() {
    if (!id) return
    try {
      setBill(await getSaleBill(parseInt(id)))
    } catch {
      setError("Could not load this bill.")
    }
  }

  useEffect(() => { load() }, [id])

  if (error) return <p className="stamp-red max-w-2xl mx-auto">{error}</p>
  if (!bill) return <p className="text-slate text-sm max-w-2xl mx-auto">Loading...</p>

  const canCancel = user?.user_type === "COMPANY_ADMIN" || user?.sales_access === true

  return (
    <BillView
      bill={bill}
      pdfUrl={saleBillPdfUrl(parseInt(id!))}
      onShare={() => shareBillPdf(saleBillPdfUrl(parseInt(id!)), bill.BillNumber)}
      canCancel={canCancel}
      onCancel={async (reason) => {
        await cancelSale(parseInt(id!), reason)
        await load()
        navigate(`/sales/${id}/bill`, { replace: true })
      }}
    />
  )
}

export default SaleBillPage
