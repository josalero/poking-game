import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backendOrigin = process.env.SCRUM_POKING_BACKEND_URL ?? "http://localhost:8089";
const backendWsOrigin = backendOrigin.replace(/^http/, "ws");

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: backendOrigin,
        changeOrigin: true
      },
      "/healthz": {
        target: backendOrigin,
        changeOrigin: true
      },
      "/avatars": {
        target: backendOrigin,
        changeOrigin: true
      },
      "/ws": {
        target: backendWsOrigin,
        changeOrigin: true,
        ws: true
      }
    }
  }
});
