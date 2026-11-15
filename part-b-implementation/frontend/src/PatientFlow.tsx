import type { Activity, FlowEvent } from './api'

const EVENT: Record<FlowEvent['type'], { icon: string; text: string; tone: string }> = {
  ARRIVED: { icon: '→', text: 'arrived', tone: 'flow-in' },
  TREATMENT_STARTED: { icon: '⏵', text: 'started treatment', tone: 'flow-start' },
  TREATED: { icon: '✓', text: 'treated, went home', tone: 'flow-out' },
  WALKED_OUT: { icon: '✕', text: 'walked out', tone: 'flow-left' },
}

/** Live patient flow: who arrives and who leaves, per department and as a running feed. */
export default function PatientFlow({ activity }: { activity: Activity | null }) {
  if (!activity?.runId) return null
  const { total } = activity

  return (
    <section className="flow" aria-label="Patient flow">
      <div className="card">
        <h2>Patient flow <span className="muted small">last {activity.minutes} simulated minutes</span></h2>
        <table className="table">
          <thead>
            <tr>
              <th>Department</th>
              <th className="num" title="Arrived">→ In</th>
              <th className="num" title="Treated and went home">✓ Treated</th>
              <th className="num" title="Left without treatment">✕ Walked out</th>
            </tr>
          </thead>
          <tbody>
            {activity.departments.map((d) => (
              <tr key={d.departmentId}>
                <td>{d.name}</td>
                <td className="num">{d.counts.arrived}</td>
                <td className="num">{d.counts.treated}</td>
                <td className={`num ${d.counts.walkedOut ? 'flow-left' : ''}`}>{d.counts.walkedOut}</td>
              </tr>
            ))}
            <tr className="total">
              <td>All departments</td>
              <td className="num">{total.arrived}</td>
              <td className="num">{total.treated}</td>
              <td className={`num ${total.walkedOut ? 'flow-left' : ''}`}>{total.walkedOut}</td>
            </tr>
          </tbody>
        </table>
        {total.arrived > 0 && (
          <p className="muted small">
            {Math.round((total.walkedOut / total.arrived) * 100)}% of arrivals in this window walked out.
          </p>
        )}
      </div>

      <div className="card">
        <h2>Live feed <span className="muted small">newest first · {activity.simClock}</span></h2>
        {activity.events.length === 0 ? (
          <p className="muted">Nothing yet in this window.</p>
        ) : (
          <ol className="feed">
            {activity.events.map((e) => {
              const kind = EVENT[e.type]
              return (
                <li key={`${e.patientId}-${e.type}`} className={kind.tone}>
                  <span className="feed-time">{e.simClock}</span>
                  <span className="feed-icon" aria-hidden>{kind.icon}</span>
                  <span>
                    Patient #{e.patientId} <span className={`urgency urgency-${e.urgency.toLowerCase()}`}>{e.urgency}</span>
                    {' '}{kind.text} · {e.department}
                    {e.waitedMinutes != null && <span className="muted"> · waited {Math.round(e.waitedMinutes)} min</span>}
                  </span>
                </li>
              )
            })}
          </ol>
        )}
      </div>
    </section>
  )
}
