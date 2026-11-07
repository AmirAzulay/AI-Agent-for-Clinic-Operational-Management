import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-server proxy so the dashboard talks to the backend from the same origin, exactly as in the
// Docker image, where clinic-app serves the built dashboard itself.
// clinic-app -> /api/* and /actuator/* (port 8080)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
