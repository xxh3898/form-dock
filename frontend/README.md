# Frontend

React 19 / TypeScript / Vite 8 frontend scaffold.

## Commands

```bash
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

`npm run dev` serves the neutral scaffold shell and proxies `/api` to `http://127.0.0.1:18081` by default. Override the proxy target with `FORMDOCK_API_PROXY_TARGET` when necessary.

Router, server-state, form, styling framework, and business UI dependencies are intentionally absent.
