import { useCallback, useState } from 'react'
import { ROLE_LABELS, type Session } from './api'
import ClinicView from './ClinicView'
import Login from './Login'
import StaffView from './StaffView'

const SESSION_KEY = 'clinic.session'

function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    return new Date(session.expiresAt) > new Date() ? session : null
  } catch {
    return null
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // storage unavailable (private mode); the session just won't survive a reload
  }
}

type Tab = 'clinic' | 'staff'

export default function App() {
  const [session, setSession] = useState<Session | null>(loadSession)
  const [tab, setTab] = useState<Tab>('clinic')

  const login = (s: Session) => { saveSession(s); setSession(s); setTab('clinic') }
  const logout = useCallback(() => { saveSession(null); setSession(null) }, [])

  if (!session) return <Login onLogin={login} />

  const isManager = session.role === 'CLINIC_MANAGER'
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">Clinic Operations</div>
        <nav className="tabs">
          <button className={tab === 'clinic' ? 'tab active' : 'tab'} onClick={() => setTab('clinic')}>Clinic</button>
          {isManager && (
            <button className={tab === 'staff' ? 'tab active' : 'tab'} onClick={() => setTab('staff')}>Staff</button>
          )}
        </nav>
        <div className="user">
          <span>{session.displayName}</span>
          <span className="muted small">{ROLE_LABELS[session.role]}</span>
          <button onClick={logout}>Sign out</button>
        </div>
      </header>
      <main className="content">
        {tab === 'clinic' && <ClinicView token={session.token} isManager={isManager} onUnauthorized={logout} />}
        {tab === 'staff' && isManager && (
          <StaffView token={session.token} currentEmail={session.email} onUnauthorized={logout} />
        )}
      </main>
    </div>
  )
}
