import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { host: '0.0.0.0' },
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('/react-dom/') || id.includes('/react/')) return 'vendor-react';
          if (id.includes('/@supabase/')) return 'vendor-supabase';
          if (id.includes('/react-router/')) return 'vendor-router';
          if (id.includes('/lucide-react/')) return 'vendor-icons';
          if (id.includes('/@capacitor/')) return 'vendor-capacitor';
        }
      }
    }
  }
});
