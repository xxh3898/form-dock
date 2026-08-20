# Frontend

React 19 / TypeScript / Vite 8 frontend with React Router 8 Declarative Mode.

## Commands

```bash
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

`npm run dev` serves `/login` and the protected `/admin` Creator shell, and proxies `/api` to `http://127.0.0.1:18081` by default. `/` deterministically redirects to `/admin`; the session guard redirects anonymous users to `/login`. Override the proxy target with `FORMDOCK_API_PROXY_TARGET` when necessary.

The auth client uses the browser-managed HttpOnly session and memory-only CSRF state. Dedicated server-state, form, and styling framework dependencies remain intentionally absent, as does Survey UI.
