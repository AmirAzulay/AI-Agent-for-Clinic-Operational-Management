import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-server proxy so the dashboard talks to both services from one origin.
// clinic-app  -> /api/*       (port 8080)
// history-svc -> /history/*   (port 8081)
// /status/*   -> each service's actuator health, used by the phase 0 status page.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/history': { target: 'http://localhost:8081', changeOrigin: true },
      '/status/clinic': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: () => '/actuator/health',
      },
      '/status/history': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: () => '/actuator/health',
      },
    },
  },
})
