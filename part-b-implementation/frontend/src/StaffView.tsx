import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, ROLE_LABELS, type Role, type StaffAccount } from './api'

const ROLES = Object.keys(ROLE_LABELS) as Role[]

/** Staff accounts, clinic manager only. Emails are login names only; no mail is ever sent. */
export default function StaffView({ token, currentEmail, onUnauthorized }:
  { token: string; currentEmail: string; onUnauthorized: () => void }) {
  const [accounts, setAccounts] = useState<StaffAccount[]>([])
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)

  const handle = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 401) onUnauthorized()
    else setMessage({ kind: 'error', text: e instanceof Error ? e.message : String(e) })
  }, [onUnauthorized])

  const load = useCallback(async () => {
    try {
      setAccounts(await api.listStaff(token))
    } catch (e) {
      handle(e)
    }
  }, [token, handle])

  useEffect(() => { load() }, [load])

  async function update(account: StaffAccount, change: Parameters<typeof api.updateStaff>[2], done: string) {
    try {
      await api.updateStaff(token, account.id, change)
      setMessage({ kind: 'ok', text: done })
      await load()
    } catch (e) {
      handle(e)
    }
  }

  function resetPassword(account: StaffAccount) {
    const password = window.prompt(`New password for ${account.email} (at least 8 characters):`)
    if (password) update(account, { password }, `Password changed for ${account.email}.`)
  }

  return (
    <>
      {message && <p className={message.kind === 'ok' ? 'notice' : 'error'} role="status">{message.text}</p>}
      <CreateStaffForm token={token} onCreated={(a) => { setMessage({ kind: 'ok', text: `Created ${a.email}.` }); load() }} onError={handle} />
      <section className="card">
        <h2>Staff accounts</h2>
        <table className="table">
          <thead>
            <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {accounts.map((a) => {
              const self = a.email === currentEmail
              return (
                <tr key={a.id} className={a.active ? '' : 'row-muted'}>
                  <td>{a.displayName}{self && <span className="muted small"> (you)</span>}</td>
                  <td>{a.email}</td>
                  <td>
                    <select value={a.role} disabled={self}
                            onChange={(e) => update(a, { role: e.target.value as Role }, `Role changed for ${a.email}.`)}>
                      {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                    </select>
                  </td>
                  <td>{a.active ? 'Active' : 'Deactivated'}</td>
                  <td className="actions">
                    <button onClick={() => resetPassword(a)}>Reset password</button>
                    {!self && (
                      <button onClick={() => update(a, { active: !a.active },
                        `${a.email} ${a.active ? 'deactivated' : 'reactivated'}.`)}>
                        {a.active ? 'Deactivate' : 'Reactivate'}
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </section>
    </>
  )
}

function CreateStaffForm({ token, onCreated, onError }:
  { token: string; onCreated: (a: StaffAccount) => void; onError: (e: unknown) => void }) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('MEDICAL_STAFF')

  async function submit(event: FormEvent) {
    event.preventDefault()
    try {
      const created = await api.createStaff(token, { email, displayName, password, role })
      setEmail(''); setDisplayName(''); setPassword('')
      onCreated(created)
    } catch (e) {
      onError(e)
    }
  }

  return (
    <form className="card create-form" onSubmit={submit}>
      <h2>Add staff member</h2>
      <div className="form-row">
        <label>Name<input value={displayName} required maxLength={128} onChange={(e) => setDisplayName(e.target.value)} /></label>
        <label>Email (login name, not verified)<input type="email" value={email} required onChange={(e) => setEmail(e.target.value)} /></label>
        <label>Password<input type="password" value={password} required minLength={8} autoComplete="new-password" onChange={(e) => setPassword(e.target.value)} /></label>
        <label>Role
          <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
          </select>
        </label>
        <button type="submit" className="primary">Add</button>
      </div>
    </form>
  )
}
