import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Forward API calls to Spring Boot, so the browser sees a single origin (no CORS needed in dev).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
