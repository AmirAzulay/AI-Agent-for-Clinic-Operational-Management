import { useEffect, useState } from 'react'

type ServiceStatus = 'checking' | 'up' | 'down'

const services = [
  { name: 'clinic-app', url: '/status/clinic', description: 'PostgreSQL · domain · simulation · AI agent' },
  { name: 'history-service', url: '/status/history', description: 'Elasticsearch · snapshots · AI decisions' },
]

function useServiceStatus(url: string): ServiceStatus {
  const [status, setStatus] = useState<ServiceStatus>('checking')

  useEffect(() => {
    let cancelled = false

    async function check() {
      try {
        const response = await fetch(url)
        const body = await response.json()
        if (!cancelled) setStatus(response.ok && body.status === 'UP' ? 'up' : 'down')
      } catch {
        if (!cancelled) setStatus('down')
      }
    }

    check()
    const timer = setInterval(check, 5000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [url])

  return status
}

function ServiceCard({ name, url, description }: (typeof services)[number]) {
  const status = useServiceStatus(url)
  const color = status === 'up' ? '#1a7f37' : status === 'down' ? '#cf222e' : '#9a6700'
  const label = status === 'up' ? 'UP' : status === 'down' ? 'DOWN' : 'CHECKING'

  return (
    <div style={{ border: '1px solid #d0d7de', borderRadius: 8, padding: 16, minWidth: 260 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <strong>{name}</strong>
        <span style={{ color, fontWeight: 600 }}>{label}</span>
      </div>
      <p style={{ margin: '8px 0 0', color: '#57606a', fontSize: 14 }}>{description}</p>
    </div>
  )
}

export default function App() {
  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 720, margin: '48px auto', padding: '0 16px' }}>
      <h1 style={{ marginBottom: 4 }}>Clinic Operations</h1>
      <p style={{ color: '#57606a', marginTop: 0 }}>Phase 0 status page. The dashboard arrives in phase 5.</p>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 24 }}>
        {services.map((service) => (
          <ServiceCard key={service.name} {...service} />
        ))}
      </div>
    </main>
  )
}
