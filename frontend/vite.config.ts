import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  define: {
    // sockjs-client references the Node.js `global` object, which doesn't
    // exist in the browser. Vite replaces this at build time.
    global: "globalThis",
  },
  server: {
    port: 5173,
    proxy: {
      // WebSocket + SockJS handshake — proxied so the browser sees
      // everything as same-origin during local dev.
      "/ws": {
        target: "http://localhost:8080",
        ws: true,
        changeOrigin: true,
      },
      // REST order API — used starting Day 4, proxied here now so the
      // config doesn't need to change later.
      "/orders": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      // Register/login
      "/auth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
