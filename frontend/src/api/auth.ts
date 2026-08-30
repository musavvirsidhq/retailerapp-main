const API_BASE = "http://localhost:8080/api/auth"

export interface User {
  id: number
  name: string
  role: string
}

export async function login(username: string, password: string): Promise<User> {
  const res = await fetch(`${API_BASE}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // send/receive cookies
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || "Login failed")
  }
  return res.json()
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE}/logout`, {
    method: "POST",
    credentials: "include",
  })
}

export async function getCurrentUser(): Promise<User | null> {
  const res = await fetch(`${API_BASE}/me`, { credentials: "include" })
  if (!res.ok) return null
  return res.json()
}
