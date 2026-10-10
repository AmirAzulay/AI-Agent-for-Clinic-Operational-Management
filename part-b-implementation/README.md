# Part B – Implementation

AI Agent for Clinic Operational Management. The engineering plan is in [PLAN.md](PLAN.md).

## Layout

| Path | What it is |
|---|---|
| `pom.xml` | Maven parent (Spring Boot 3.5, Java 21) |
| `common/` | Enums and event payloads shared by both services |
| `clinic-app/` | Owns PostgreSQL. Domain, simulation engine, AI agent, tools + validation. Port 8080 |
| `history-service/` | Owns Elasticsearch. Snapshots, AI decisions, events, evaluation results. Port 8081 |
| `frontend/` | React + Vite + TypeScript dashboard. Port 5173 |
| `docker-compose.yml` | PostgreSQL, Elasticsearch, Kibana for local development |
| `.env.example` | Environment variables. Copy to `.env` |

## Prerequisites

- JDK 21
- Docker Desktop
- Node 20 or newer
- IntelliJ IDEA (Community is enough). Open the repository root and import `part-b-implementation/pom.xml` as a Maven project when prompted.

No Maven install is needed; `./mvnw` downloads it.

## Run locally

```bash
cd part-b-implementation
cp .env.example .env

# 1. databases
docker compose up -d

# 2. services (two terminals, or two IntelliJ run configurations)
./mvnw -pl history-service spring-boot:run
./mvnw -pl clinic-app spring-boot:run

# 3. frontend
cd frontend && npm install && npm run dev
```

| URL | What you get |
|---|---|
| http://localhost:5173 | Status page (phase 0); the dashboard from phase 5 |
| http://localhost:8080/actuator/health | clinic-app health, including PostgreSQL |
| http://localhost:8080/swagger-ui.html | clinic-app API docs |
| http://localhost:8081/actuator/health | history-service health, including Elasticsearch |
| http://localhost:8081/swagger-ui.html | history-service API docs |
| http://localhost:5601 | Kibana (indices, index templates, dashboards) |

Stop the databases with `docker compose down`. Add `-v` to also delete the data.

## Tests

```bash
./mvnw verify
```

Tests start real PostgreSQL and Elasticsearch containers through Testcontainers, so Docker
must be running. CI (`.github/workflows/ci.yml`) runs the same command on every pull
request, plus the frontend build. No API keys are needed for tests.

## Phase status

| Phase | Status |
|---|---|
| 0 Skeleton | done – both services boot, Flyway V1 applied, index templates installed, CI green |
| 1 Core domain | next |
| 2 Simulation engine | |
| 3 Tools + validation | |
| 4 AI agent | |
| 5 Dashboard | |
| 6 Evaluation harness | |
| 7 Hardening | |
| 8 Optional extractions | |
