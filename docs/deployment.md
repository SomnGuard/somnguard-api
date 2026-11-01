# Reproducible API deployment

## Local stack

The API Compose file runs a version-pinned PostgreSQL container, MailHog, S3Mock, and the API. PostgreSQL data is kept in the named `postgres-data` volume. Do not use `docker compose down -v` unless intentionally deleting local database data.

From this repository:

```bash
cp .env.example .env
# Replace the local placeholders in .env before starting the stack.
docker compose --env-file .env up -d --build --wait
docker compose --env-file .env ps
```

The API waits for PostgreSQL's `pg_isready` health check. Its own Docker health check calls the public, sanitized `/actuator/health` endpoint, which includes the actual database connection indicator. Production actuator details remain restricted to authorized requests.

To initialize a blank database from the database repository's Liquibase changelog, keep `somnguard-api` and `somnguard-db` as sibling directories, then run:

```bash
docker compose --env-file .env --profile migrations run --build --rm db-migrations update
docker compose --env-file .env up -d --build --wait
```

The migration service mounts the sibling `somnguard-db` changelog read-only and connects to the same Compose PostgreSQL service as the API. The default `SPRING_PROFILES_ACTIVE=dev` also allows Hibernate to create missing namespaces for a quick local boot; production uses `ddl-auto: validate` and must run Liquibase first.

## Production pipeline contract

The production workflow is triggered only after the `Docker Publish` workflow succeeds for `main`. The image is tagged with the full source commit SHA. Before it changes the running API, the workflow:

1. Confirms the VPS has the expected API Compose services and a sibling database changelog checkout.
2. Pulls the image tagged with the workflow SHA.
3. Creates a restricted-permission custom-format PostgreSQL dump, verifies that it is non-empty, reads its archive table of contents, and writes a SHA-256 sidecar.
4. Runs the Liquibase migration service.
5. Recreates the API and waits for its Docker health check, then calls `/actuator/health` over HTTP.

The VPS must have the checked-in API Compose file at `~/somnguard/deploy/api/docker-compose.yml`, production-only values in `~/somnguard/deploy/api/.env`, and a `somnguard-db` checkout at `~/somnguard/deploy/db`. The API and migration service use the same Compose PostgreSQL service. The `.env` file must contain strong production credentials and real secret file mounts for JWT keys; never copy the example placeholders to production. Backups default to `~/somnguard/backups`; ensure that path is on protected storage with the organization's retention and encryption controls.

This repository change does not update the VPS configuration or execute the production workflow. The current VPS file layout, existing database location, storage policy, and availability of the required directories/secrets have not been inspected; confirm those before merging this workflow to `main`. It intentionally stops before backup or migration if its preconditions are absent.

## Rollback

If API health or the HTTP check fails after deployment, the workflow attempts to restart the previously running API image. It then fails the run so the deployment remains visible for investigation. This application rollback does not roll back the database schema.

Keep the verified pre-migration dump and its `.sha256` file. If a schema migration is not compatible with the prior application, stop traffic and follow the approved database recovery plan. Validate the dump by restoring it into an isolated PostgreSQL instance before any production recovery. Do not run destructive Liquibase rollback commands against production as a routine rollback; prefer forward-compatible migrations and application rollback.
