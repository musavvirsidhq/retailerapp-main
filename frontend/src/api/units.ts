export interface Unit {
  Code: string
  Label: string
}

export async function listUnits(): Promise<Unit[]> {
  const res = await fetch("/api/units", { credentials: "include" })
  if (!res.ok) throw new Error("Failed to fetch units")
  return res.json()
}
