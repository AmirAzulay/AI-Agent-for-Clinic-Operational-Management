import { useState, type FormEvent } from 'react'
import { api, ApiError, type Session } from './api'

export default function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onLogin(await api.login(email, password))
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401
        ? 'Wrong email or password.'
        : 'Cannot reach the server. If it was asleep, wait a minute and try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <form className="card login-card" onSubmit={submit}>
        <h1>Clinic Operations</h1>
        <p className="muted">Staff sign-in</p>
        <label>
          Email
          <input type="email" autoComplete="username" value={email} required autoFocus
                 onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password} required
                 onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="muted small">For clinic staff only. Accounts are created by the clinic manager.</p>
      </form>
    </main>
  )
}
