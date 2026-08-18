# Infrastructure

Local Docker Compose baseline for PostgreSQL, API, and Web.

## Commands

Run from the repository root:

```bash
docker compose --env-file .env.example -f infra/compose.yaml config --quiet
docker compose --env-file .env.example -f infra/compose.yaml up --build --wait
docker compose --env-file .env.example -f infra/compose.yaml down
```

The Compose project is `dev-form-dock`. Web, API, and PostgreSQL ports bind only to `127.0.0.1`; PostgreSQL data is stored in a development-only named volume. `down` preserves that volume.

This is a local development baseline, not the production Compose contract.
