import { useState } from "react"
import { Printer, Download, Share2, Ban } from "lucide-react"
import type { BillData } from "../api/bills"

interface Props {
  bill: BillData
  pdfUrl: string
  onShare: () => void
  onCancel?: (reason: string) => Promise<void>
  canCancel: boolean
}

function money(v: number) {
  return `₹${v.toFixed(2)}`
}

function BillView({ bill, pdfUrl, onShare, onCancel, canCancel }: Props) {
  const [showCancelForm, setShowCancelForm] = useState(false)
  const [reason, setReason] = useState("")
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState("")

  const balance = bill.TotalAmount - bill.AmountPaid
  const paymentStatus = bill.AmountPaid >= bill.TotalAmount ? "PAID" : bill.AmountPaid > 0 ? "PARTIAL" : "UNPAID"

  async function handleCancel(e: React.FormEvent) {
    e.preventDefault()
    if (!reason.trim() || !onCancel) return
    setCancelling(true)
    setCancelError("")
    try {
      await onCancel(reason.trim())
      setShowCancelForm(false)
    } catch (err: any) {
      setCancelError(err.message || "Failed to cancel")
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="no-print flex flex-wrap gap-2 mb-4">
        <button onClick={() => window.print()} className="btn-primary flex items-center gap-2">
          <Printer size={15} /> Print
        </button>
        <a href={pdfUrl} download className="btn-primary flex items-center gap-2">
          <Download size={15} /> Download PDF
        </a>
        <button onClick={onShare} className="btn-accent flex items-center gap-2">
          <Share2 size={15} /> Share
        </button>
        {canCancel && bill.Status === "COMPLETED" && (
          <button onClick={() => setShowCancelForm((v) => !v)} className="flex items-center gap-2 text-red border border-red rounded-md px-4 py-2 text-sm font-medium hover:bg-red-soft transition-colors ml-auto">
            <Ban size={15} /> Cancel Bill
          </button>
        )}
      </div>

      {showCancelForm && (
        <form onSubmit={handleCancel} className="no-print card p-4 mb-4 border-red">
          <label className="eyebrow block mb-1.5">Cancellation reason (required)</label>
          <textarea className="input-field w-full mb-2" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Wrong quantity entered" />
          {cancelError && <p className="stamp-red mb-2">{cancelError}</p>}
          <button type="submit" disabled={cancelling || !reason.trim()} className="btn-accent">
            {cancelling ? "Cancelling..." : "Confirm Cancellation"}
          </button>
        </form>
      )}

      <div className="card p-8">
        <div className="flex justify-between items-start mb-6">
          <div>
            <h1 className="font-display font-bold text-xl">{bill.CompanyName}</h1>
            <p className="eyebrow mt-0.5">{bill.CompanyCode}</p>
          </div>
          <div className="text-right">
            <p className="font-display font-bold text-lg">{bill.DocumentTitle}</p>
            <p className="mono-num text-sm text-slate">{bill.BillNumber}</p>
            <p className="mono-num text-sm text-slate">{bill.BillDate}</p>
          </div>
        </div>

        {bill.Status === "CANCELLED" && (
          <div className="stamp-red mb-4 w-full justify-center py-2 text-sm">
            CANCELLED{bill.CancelledReason ? ` — ${bill.CancelledReason}` : ""}
          </div>
        )}

        <div className="mb-6">
          <p className="eyebrow mb-1">Bill To</p>
          <p className="font-medium">{bill.CounterpartyName}</p>
          {bill.CounterpartyPhone && <p className="mono-num text-sm text-slate">{bill.CounterpartyPhone}</p>}
          {bill.CounterpartyArea && <p className="text-sm text-slate">{bill.CounterpartyArea}</p>}
        </div>

        <div className="overflow-x-auto -mx-2">
          <table className="table-base min-w-full">
            <thead>
              <tr>
                <th>Product</th><th>Code</th><th>Unit</th>
                <th className="text-right">Qty</th><th className="text-right">Price</th><th className="text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {bill.Items.map((item, i) => (
                <tr key={i}>
                  <td className="font-medium">
                    {item.ProductName}
                    {item.BelowCost && <span className="stamp-red ml-2">Below Cost</span>}
                  </td>
                  <td className="mono-num text-slate">{item.ProductSKU}</td>
                  <td className="text-slate">{item.Unit}</td>
                  <td className="mono-num text-right">{item.Quantity}</td>
                  <td className="mono-num text-right">{money(item.UnitPrice)}</td>
                  <td className="mono-num text-right">{money(item.LineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="border-t border-line mt-4 pt-4 flex flex-col items-end gap-1">
          <p>Total Amount: <span className="mono-num font-medium">{money(bill.TotalAmount)}</span></p>
          <p>Amount Paid: <span className="mono-num font-medium text-green">{money(bill.AmountPaid)}</span></p>
          <p>Balance Due: <span className="mono-num font-medium">{money(balance)}</span></p>
          <p className="mt-1">
            <span className={paymentStatus === "PAID" ? "stamp-green" : paymentStatus === "PARTIAL" ? "stamp-amber" : "stamp-red"}>
              {paymentStatus}
            </span>
          </p>
        </div>
      </div>
    </div>
  )
}

export default BillView
