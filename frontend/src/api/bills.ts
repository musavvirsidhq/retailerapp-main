export interface BillItem {
  ProductName: string
  ProductSKU: string
  Unit: string
  Quantity: number
  UnitPrice: number
  LineTotal: number
  BelowCost: boolean
}

export interface BillData {
  DocumentTitle: string
  BillNumber: string
  BillDate: string
  CompanyName: string
  CompanyCode: string
  CounterpartyName: string
  CounterpartyPhone: string
  CounterpartyArea: string
  Items: BillItem[]
  TotalAmount: number
  AmountPaid: number
  Status: "COMPLETED" | "CANCELLED"
  CancelledReason: string
}

export async function getSaleBill(id: number): Promise<BillData> {
  const res = await fetch(`/api/sales/bills/${id}`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load bill")
  return res.json()
}

export async function getPurchaseBill(id: number): Promise<BillData> {
  const res = await fetch(`/api/purchases/bills/${id}`, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to load bill")
  return res.json()
}

export function saleBillPdfUrl(id: number): string {
  return `/api/sales/bills/${id}/pdf`
}

export function purchaseBillPdfUrl(id: number): string {
  return `/api/purchases/bills/${id}/pdf`
}

/** Fetches a bill PDF and shares it via the Web Share API where possible, falling back to a
 *  WhatsApp web-share link (no in-browser file attach) and finally a plain download. */
export async function shareBillPdf(pdfUrl: string, billNumber: string) {
  const res = await fetch(pdfUrl, { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch bill PDF")
  const blob = await res.blob()
  const file = new File([blob], `${billNumber}.pdf`, { type: "application/pdf" })

  const nav = navigator as Navigator & { canShare?: (data?: ShareData) => boolean }
  if (nav.share && nav.canShare?.({ files: [file] })) {
    await nav.share({ files: [file], title: billNumber })
    return
  }

  // Fallback: open the PDF in a new tab so the user can save/share it manually (e.g. via
  // WhatsApp Web's own attach-file flow), since wa.me links cannot carry a file attachment.
  const blobUrl = URL.createObjectURL(blob)
  window.open(blobUrl, "_blank")
}
