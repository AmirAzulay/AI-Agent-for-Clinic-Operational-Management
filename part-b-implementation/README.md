# Part B – Implementation

AI Agent for Clinic Operational Management. The engineering plan is in [PLAN.md](PLAN.md).

## Layout

| Path | What it is |
|---|---|
| `pom.xml` | Maven parent (Spring Boot 3.5, Java 21) |
| `clinic-app/` | The backend. Owns PostgreSQL. Domain, history, simulation engine, AI agent, tools + validation. Port 8080 |
| `frontend/` | React + Vite + TypeScript dashboard. Port 5173 |
| `docker-compose.yml` | PostgreSQL + the backend as one unit. `clinic-app/Dockerfile` builds the backend image |
| `.env.example` | Environment variables. Copy to `.env` |

## Prerequisites

- JDK 21
- Docker Desktop
- Node 20 or newer
- IntelliJ IDEA (Community is enough). See "Running from IntelliJ" below.

No Maven install is needed; `./mvnw` downloads it.

## Running from IntelliJ

One-time setup after cloning:

1. Open the **repository root** folder in IntelliJ (not `part-b-implementation`).
2. In the Project tree, right-click `part-b-implementation/pom.xml` and choose **Add as Maven Project**. IntelliJ imports the `clinic-app` module and downloads dependencies (a minute or two the first time).
3. If asked for a JDK, pick any Java 21 (File > Project Structure > SDK).

After that, the run configurations committed in `.run/` appear in the toolbar drop-down:

| Run configuration | What it starts | Logs |
|---|---|---|
| `clinic-app` | clinic-app on 8080 | Run tool window |
| `frontend (npm run dev)` | Vite dev server on 5173 | Run tool window |

Start the database first (`docker compose up -d postgres`; a plain `docker compose up` would also
start the backend in Docker on the same port 8080), then `clinic-app`.
Each one opens its own tab in the Run tool window at the bottom of IntelliJ; that tab is the
live log. The green bug icon runs the same configuration under the debugger.

No environment variables are needed locally: the defaults in each `application.yml` match
`docker-compose.yml`. To override something (for example `JWT_SECRET`), edit the run
configuration and add it under Environment variables.

## Run as one unit (Docker)

```bash
cd part-b-implementation
cp .env.example .env
docker compose up -d --build     # postgres + clinic-app
```

The first build takes a few minutes (Maven downloads); later builds reuse the cached
dependencies. The backend waits for PostgreSQL to be healthy, applies Flyway migrations and
listens on http://localhost:8080. `docker compose logs -f clinic-app` follows its log.
After a code change, `docker compose up -d --build clinic-app` rebuilds and restarts just the
backend.

## Run locally for development

Use this when you want to debug or hot-restart the backend from the IDE.

```bash
cd part-b-implementation
cp .env.example .env

# 1. database only
docker compose up -d postgres

# 2. backend (a terminal, or the IntelliJ run configuration)
./mvnw -pl clinic-app spring-boot:run

# 3. frontend
cd frontend && npm install && npm run dev
```

| URL | What you get |
|---|---|
| http://localhost:5173 | Status page (phase 0); the dashboard from phase 5 |
| http://localhost:8080/actuator/health | clinic-app health, including PostgreSQL |
| http://localhost:8080/swagger-ui.html | clinic-app API docs |
| `localhost:5432` | PostgreSQL. Database, user and password `clinic`. Connect with any client (IntelliJ Database tool, `psql`, DBeaver) |

Stop everything with `docker compose down`. Add `-v` to also delete the database data.

## Logging in

Three development users are seeded, one per role from the use-case diagram. The password is
the username followed by `123`.

| Username | Password | Role |
|---|---|---|
| `manager` | `manager123` | CLINIC_MANAGER |
| `reception` | `reception123` | RECEPTION |
| `staff` | `staff123` | MEDICAL_STAFF |

```bash
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"manager","password":"manager123"}'
# → {"token":"...","expiresAt":"...","username":"manager","role":"CLINIC_MANAGER"}

curl -s localhost:8080/api/clinic/state -H "Authorization: Bearer <token>"
```

In Swagger UI, call `POST /api/auth/login`, copy the token, click **Authorize** and paste it.
Every endpoint except login, health and the API docs requires a token.

## Tests

```bash
./mvnw verify
```

Tests start a real PostgreSQL container through Testcontainers, so Docker
must be running. CI (`.github/workflows/ci.yml`) runs the same command on every pull
request, plus the frontend build. No API keys are needed for tests.

## Phase status

| Phase | Status |
|---|---|
| 0 Skeleton | done – backend boots, Flyway V1 applied, CI green |
| 1 Core domain | done – JPA entities for all tables, seed clinic (V2), read API, JWT login with roles |
| Single backend | done – Elasticsearch and `history-service` removed; history tables in PostgreSQL (V3) |
| Docker image | done – `clinic-app/Dockerfile`; `docker compose up` runs PostgreSQL + backend together |
| Walkouts + retention | done – patient patience and walkouts (V4, `WalkoutService`), daily history retention job and `POST /api/history/retention/run` |
| 2 Simulation engine | next |
| 3 Tools + validation | |
| 4 AI agent | |
| 5 Dashboard | |
| 6 Evaluation harness | |
| 7 Hardening | |
| 8 Optional extractions | |
