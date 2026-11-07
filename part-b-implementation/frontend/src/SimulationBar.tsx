import { useState } from 'react'
import { api, ApiError, SCENARIO_LABELS, type Scenario, type SimulationStatus } from './api'

const SCENARIOS = Object.keys(SCENARIO_LABELS) as Scenario[]
const SPEEDS = [
  { seconds: 5, label: '12× (1 sim-minute every 5 s)' },
  { seconds: 2, label: '30× (every 2 s)' },
  { seconds: 1, label: '60× (every second)' },
]

const STOP_REASON: Record<NonNullable<SimulationStatus['stopReason']>, string> = {
  STOPPED: 'stopped',
  COMPLETED: 'finished its day',
  INTERRUPTED: 'interrupted by a server restart',
  ERROR: 'stopped after an error',
}

/** Simulation status for everyone; start and stop for the clinic manager. */
export default function SimulationBar({ token, status, isManager, onChange, onUnauthorized }: {
  token: string
  status: SimulationStatus | null
  isManager: boolean
  onChange: () => void
  onUnauthorized: () => void
}) {
  const [scenario, setScenario] = useState<Scenario>('NORMAL')
  const [speed, setSpeed] = useState(5)
  const [seed, setSeed] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      onChange()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onUnauthorized()
      else setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const running = status?.running ?? false
  const progress = status?.maxTicks ? Math.min(100, Math.round((status.tick / status.maxTicks) * 100)) : 0

  return (
    <section className="card simulation" aria-label="Simulation">
      <div className="simulation-status">
        <span className={`dot ${running ? 'dot-live' : ''}`} aria-hidden />
        {status?.runId ? (
          <span>
            <strong>{running ? 'Simulation running' : `Simulation ${STOP_REASON[status.stopReason ?? 'STOPPED']}`}</strong>
            {' · '}Day {status.simDay}, {status.simClock}
            {' · '}{status.scenario ? SCENARIO_LABELS[status.scenario] : ''}
            <span className="muted small"> · seed {status.seed} · run #{status.runId}</span>
          </span>
        ) : (
          <span><strong>No simulation yet.</strong> <span className="muted">The clinic is empty until a run starts.</span></span>
        )}
      </div>
      {status?.runId && status.maxTicks ? (
        <div className="bar thin" title={`${status.tick} of ${status.maxTicks} simulated minutes`}>
          <div className="bar-fill" style={{ width: `${progress}%` }} />
        </div>
      ) : null}

      {isManager && (
        <div className="simulation-controls">
          {running ? (
            <button className="danger" disabled={busy} onClick={() => run(() => api.stopSimulation(token))}>Stop simulation</button>
          ) : (
            <>
              <label>Scenario
                <select value={scenario} onChange={(e) => setScenario(e.target.value as Scenario)}>
                  {SCENARIOS.map((s) => <option key={s} value={s}>{SCENARIO_LABELS[s]}</option>)}
                </select>
              </label>
              <label>Speed
                <select value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>
                  {SPEEDS.map((s) => <option key={s.seconds} value={s.seconds}>{s.label}</option>)}
                </select>
              </label>
              <label>Seed (optional)
                <input inputMode="numeric" placeholder="random" value={seed}
                       onChange={(e) => setSeed(e.target.value.replace(/[^0-9]/g, ''))} />
              </label>
              <button className="primary" disabled={busy} onClick={() => run(() => api.startSimulation(token, {
                scenario, tickIntervalSeconds: speed, ...(seed ? { seed: Number(seed) } : {}),
              }))}>
                Start simulation
              </button>
            </>
          )}
        </div>
      )}
      {isManager && !running && <p className="muted small">Starting a run resets the clinic: patients cleared, rooms, doctors and stock back to their starting values.</p>}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
