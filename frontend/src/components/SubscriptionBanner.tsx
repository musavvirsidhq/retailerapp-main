import { useEffect, useState } from "react"
import { AlertTriangle } from "lucide-react"
import { getSubscriptionStatus, type SubscriptionStatus } from "../api/companyUsers"

function SubscriptionBanner() {
  const [sub, setSub] = useState<SubscriptionStatus | null>(null)

  useEffect(() => {
    getSubscriptionStatus().then(setSub).catch(() => setSub(null))
  }, [])

  if (!sub || !sub.warning_level) return null

  const expired = sub.warning_level === "EXPIRED"
  const urgent = sub.warning_level === "URGENT"

  return (
    <div className={`no-print px-4 py-2.5 flex items-center gap-2 text-sm font-medium ${expired ? "bg-red text-white" : urgent ? "bg-red-soft text-red" : "bg-amber-soft text-amber"}`}>
      <AlertTriangle size={16} className="shrink-0" />
      {expired ? (
        <span>Your subscription has expired. Please contact the administrator or renew your annual subscription to continue using the service.</span>
      ) : (
        <span>
          Your subscription expires on <span className="mono-num">{sub.expiry_date}</span>
          {" "}({sub.days_remaining} day{sub.days_remaining === 1 ? "" : "s"} left). Please renew soon to avoid interruption.
        </span>
      )}
    </div>
  )
}

export default SubscriptionBanner
