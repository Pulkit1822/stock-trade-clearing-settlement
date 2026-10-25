import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Dev proxy: route all API calls to the api-gateway so we don't hit CORS.
    // In production, deploy the built bundle behind the same origin as the gateway.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path,
      },
      '/instruments': { target: 'http://localhost:8080', changeOrigin: true },
      '/participants':  { target: 'http://localhost:8080', changeOrigin: true },
      '/accounts':      { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
