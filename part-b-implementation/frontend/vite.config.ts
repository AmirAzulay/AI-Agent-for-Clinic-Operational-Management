import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-server proxy so the dashboard talks to the backend from the same origin.
// clinic-app -> /api/*        (port 8080)
// /status/clinic -> the backend's actuator health, used by the status page.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/status/clinic': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: () => '/actuator/health',
      },
    },
  },
})
