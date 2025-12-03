import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  
  // ✅ Sin proxy - el frontend accede directamente al túnel del backend
  // Las cookies funcionarán porque ambos usan el mismo dominio base
  
  server: {
    port: 5173,
    host: true, // Permite acceso desde la red local
  },
})