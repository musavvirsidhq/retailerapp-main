import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "../context/AuthContext"

function LoginPage() {
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [submitting, setSubmitting] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    setSubmitting(true)
    try {
      await login(username, password)
      navigate("/dashboard")
    } catch (err: any) {
      setError(err.message || "Login failed")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen bg-ink flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-6">
          <div className="h-1 w-10 bg-amber mx-auto mb-4" />
          <h1 className="font-display font-bold text-2xl text-white">RetailApp</h1>
          <p className="eyebrow text-white/40 mt-1">Distributor Ledger</p>
        </div>

        <form onSubmit={handleSubmit} className="card p-7">
          <div className="mb-4">
            <label className="eyebrow block mb-1.5">Username</label>
            <input
              className="input-field w-full"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
            />
          </div>

          <div className="mb-5">
            <label className="eyebrow block mb-1.5">Password</label>
            <input
              className="input-field w-full"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && <p className="stamp-red mb-4">{error}</p>}

          <button type="submit" disabled={submitting} className="btn-accent w-full">
            {submitting ? "Logging in..." : "Log In"}
          </button>
        </form>
      </div>
    </div>
  )
}

export default LoginPage
