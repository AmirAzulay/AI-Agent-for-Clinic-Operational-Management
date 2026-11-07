import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, type Alert, type ClinicState, type DepartmentState, type Medication } from './api'

const REFRESH_MS = 5000

const STATUS_LABEL: Record<DepartmentState['status'], string> = {
  NORMAL: 'Normal',
  WARNING: 'Warning',
  OVERLOADED: 'Overloaded',
  CRITICAL: 'Critical',
}

/** Live clinic view: polls the clinic state every few seconds. */
export default function ClinicView({ token, onUnauthorized }: { token: string; onUnauthorized: () => void }) {
  const [state, setState] = useState<ClinicState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)

  const load = useCallback(async () => {
    try {
      setState(await api.clinicState(token))
      setUpdatedAt(new Date())
      setError(null)
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        onUnauthorized()
        return
      }
      setError('Lost connection to the server; retrying…')
    }
  }, [token, onUnauthorized])

  useEffect(() => {
    load()
    const timer = setInterval(load, REFRESH_MS)
    return () => clearInterval(timer)
  }, [load])

  if (!state) {
    return <p className="muted center">{error ?? 'Loading clinic state…'}</p>
  }

  const totalWaiting = state.departments.reduce((sum, d) => sum + d.waitingCount, 0)
  const totalInTreatment = state.departments.reduce((sum, d) => sum + d.inTreatmentCount, 0)
  const roomsFree = state.departments.reduce((sum, d) => sum + d.roomsAvailable, 0)
  const roomsTotal = state.departments.reduce((sum, d) => sum + d.rooms.length, 0)
  const departmentsUnderPressure = state.departments.filter((d) => d.status !== 'NORMAL').length
  const lowMedications = state.medications.filter((m) => m.belowThreshold).length

  return (
    <>
      <div className="toolbar">
        <span className="muted small">
          {error ? <span className="error">{error}</span> : <>Live · updated {updatedAt?.toLocaleTimeString()}</>}
        </span>
      </div>

      <section className="summary" aria-label="Clinic summary">
        <Stat label="Waiting" value={totalWaiting} />
        <Stat label="In treatment" value={totalInTreatment} />
        <Stat label="Rooms free" value={`${roomsFree} / ${roomsTotal}`} />
        <Stat label="Departments under pressure" value={departmentsUnderPressure} tone={departmentsUnderPressure ? 'warn' : undefined} />
        <Stat label="Medications low" value={lowMedications} tone={lowMedications ? 'warn' : undefined} />
        <Stat label="Active alerts" value={state.activeAlerts.length} tone={state.activeAlerts.length ? 'warn' : undefined} />
      </section>

      <section className="departments" aria-label="Departments">
        {state.departments.map((d) => <DepartmentCard key={d.id} department={d} />)}
      </section>

      <div className="columns">
        <MedicationsPanel medications={state.medications} />
        <AlertsPanel alerts={state.activeAlerts} departments={state.departments} />
      </div>

      {totalWaiting === 0 && totalInTreatment === 0 && (
        <p className="muted small center">
          No patients yet. Patients arrive once the simulation runs (phase 2).
        </p>
      )}
    </>
  )
}

function Stat({ label, value, tone }: { label: string; value: number | string; tone?: 'warn' }) {
  return (
    <div className={`card stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="muted small">{label}</div>
    </div>
  )
}

function DepartmentCard({ department: d }: { department: DepartmentState }) {
  const queuePercent = Math.min(100, Math.round((d.waitingCount / d.maxQueue) * 100))
  return (
    <article className={`card department status-${d.status.toLowerCase()}`}>
      <header className="department-header">
        <h2>{d.name}</h2>
        <span className={`badge status-${d.status.toLowerCase()}`}>{STATUS_LABEL[d.status]}</span>
      </header>

      <div className="queue">
        <div className="queue-label">
          <span><strong>{d.waitingCount}</strong> waiting</span>
          <span className="muted small">max {d.maxQueue}</span>
        </div>
        <div className="bar" role="progressbar" aria-valuenow={d.waitingCount} aria-valuemin={0} aria-valuemax={d.maxQueue}>
          <div className="bar-fill" style={{ width: `${queuePercent}%` }} />
        </div>
      </div>

      <dl className="metrics">
        <div><dt>In treatment</dt><dd>{d.inTreatmentCount}</dd></div>
        <div><dt>Avg wait</dt><dd>{d.avgWaitMinutes} min</dd></div>
        <div><dt>Max wait</dt><dd>{d.maxWaitMinutes} min</dd></div>
        <div><dt>Doctors</dt><dd>{d.doctorsAvailable} / {d.doctorsTotal}</dd></div>
      </dl>

      <div className="rooms" aria-label="Rooms">
        {d.rooms.map((room) => (
          <span key={room.id} className={`room room-${room.status.toLowerCase()}`} title={`${room.name}: ${room.status.toLowerCase()}`}>
            {room.name}
          </span>
        ))}
      </div>
    </article>
  )
}

function MedicationsPanel({ medications }: { medications: Medication[] }) {
  return (
    <section className="card">
      <h2>Medications</h2>
      <table className="table">
        <thead>
          <tr><th>Name</th><th className="num">Stock</th><th className="num">Threshold</th><th /></tr>
        </thead>
        <tbody>
          {medications.map((m) => (
            <tr key={m.id} className={m.belowThreshold ? 'row-warn' : ''}>
              <td>{m.name}</td>
              <td className="num">{m.stock}</td>
              <td className="num">{m.threshold}</td>
              <td>{m.belowThreshold && <span className="badge status-warning">Low</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

function AlertsPanel({ alerts, departments }: { alerts: Alert[]; departments: DepartmentState[] }) {
  const departmentName = (id: number | null) => departments.find((d) => d.id === id)?.name ?? 'Clinic'
  return (
    <section className="card">
      <h2>Active alerts</h2>
      {alerts.length === 0 ? (
        <p className="muted">No active alerts.</p>
      ) : (
        <ul className="alerts">
          {alerts.map((a) => (
            <li key={a.id} className={`alert severity-${a.severity.toLowerCase()}`}>
              <div className="alert-head">
                <span className={`badge severity-${a.severity.toLowerCase()}`}>{a.severity}</span>
                <span className="muted small">{departmentName(a.departmentId)} · {a.createdBy} · {new Date(a.createdAt).toLocaleTimeString()}</span>
              </div>
              <p>{a.message}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
