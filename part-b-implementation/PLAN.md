# Part B – Implementation Plan

AI Agent for Clinic Operational Management (Braude College, project 26-2-D-13)
Amir Azulay, Yovel Katz · Advisor: Dr. Nataly Levi

This document is the engineering plan for Phase B. It turns the requirements, architecture
and success metrics from the Phase A project book into a concrete stack, data design,
repository layout and build order.

---

## 1. Decisions

| Area | Decision | Reason |
|---|---|---|
| Backend language | Java 21 | Team experience, strong typing, long-term support release |
| Backend framework | Spring Boot 3.x | First-class modules for REST, security, scheduling and JPA |
| Architecture | **One backend** – `clinic-app` owns PostgreSQL and serves everything: clinic state, history, simulation, AI agent, tools. `simulation-service` and `agent-service` are optional later extractions (see §4) | One deployable, one database, one transaction per tick and per tool call; no service-to-service calls to fail |
| Frontend | React + Vite + TypeScript | Matches the project book; fast dev loop |
| Database | **PostgreSQL 16** (see §2 for the MySQL comparison) – both the current state and the history | Matches the project book, JSONB for tool-call payloads and context, full-text search for AI explanations, window functions for trends |
| LLM | Anthropic Claude via the official Java SDK, model `claude-opus-5-5` | Native tool calling with strict JSON schemas; wrapped behind an `LlmClient` interface so OpenAI/Gemini can be added later as the book allows |
| Auth | Spring Security + JWT | Covers the authentication and authorization NFRs |
| Migrations | Flyway | Versioned, reviewable schema changes |
| Local environment | Docker Compose | One command brings up PostgreSQL and the backend; the frontend joins in phase 5 |
| Tests | JUnit 5 + Testcontainers | Real PostgreSQL in integration tests |
| CI | GitHub Actions | Build + tests on every push and pull request |
| IDE | IntelliJ IDEA Community (backend and frontend) | Free; best Spring experience |

### Why no Elasticsearch

An earlier version of this plan stored history in Elasticsearch behind a second service
(`history-service`). It was dropped on 2026-11-07:

- **Data volume is small.** A tick every few seconds across four departments is tens of
  thousands of rows a day. PostgreSQL with a `(department_id, taken_at)` index answers the
  15-minute trend and chart queries in milliseconds.
- **No dual write.** A tick and its snapshot commit in the same transaction, so the history
  can never disagree with the state. The retry, fail-soft and rebuild machinery the second
  service needed is gone.
- **Less to run.** No 1–2 GB Elasticsearch heap on laptops, one service to start, one
  database to back up and test against.
- **What Elasticsearch gave us is covered:** full-text search over explanations by a
  PostgreSQL `tsvector` index, aggregations by SQL, dashboards by the React frontend.

### Changes required in the Phase A book

The Phase A book names **FastAPI (Python)** and **AWS Aurora PostgreSQL**; the architecture
diagram shows the same. Before the first Phase B review:

1. Update the section 6 technology table (backend → Java / Spring Boot; database → PostgreSQL).
2. Update the architecture diagram: replace the FastAPI box with `clinic-app` (Spring Boot) and point the Logging System and AI Action History at PostgreSQL history tables.
3. Inform the advisor of the change and the reason (team expertise, stronger typing and validation for the tool layer).
4. Add one functional requirement for patient walkouts (see §3.4): "The system shall track patients who leave the queue before treatment when their waiting time exceeds a tolerance that depends on urgency, and shall report the walkout rate per department." Add one non-functional requirement for history retention (see §3.5): "Historical data shall be kept for a configurable period and removed automatically afterwards."


---

## 2. PostgreSQL vs MySQL

Both work for this project and both are fully supported by Spring Data JPA, Flyway and
Testcontainers. The differences that matter here:

| Topic | PostgreSQL 16 | MySQL 8.4 |
|---|---|---|
| Matches Phase A book and diagram | Yes | No – one more deviation to justify |
| Storing the raw tool-call JSON and context JSON | `jsonb` column, indexable and queryable | `JSON` column, less mature indexing |
| Full-text search over AI explanations | `tsvector` + GIN index, built in | `FULLTEXT` index, fine for InnoDB |
| Enum-like columns (department status, room status) | Native `CHECK` / enum types | `ENUM` type, fine |
| Transactions, foreign keys, constraints | Excellent | Excellent (InnoDB) |
| Timestamp handling | `timestamptz` is unambiguous | `DATETIME` has no timezone; `TIMESTAMP` is limited |
| Team familiarity | Lower | Higher (Aurora MySQL at work) |
| Hosting later (if needed) | Neon / Supabase / RDS free tiers | PlanetScale / RDS free tiers |

**Recommendation: PostgreSQL.** It keeps the book consistent, and `jsonb` is a real
advantage for storing each AI decision's parameters and context next to the relational row.
The familiarity gap is small because all data access goes through JPA and Flyway SQL that is
nearly identical on both engines.

---

## 3. Data design

### 3.1 One database, two kinds of tables

- **State tables** hold the current clinic: departments, rooms, doctors, patients, medications, alerts, restock requests. Every tool executes a transaction here.
- **History tables** are append-only: what the clinic looked like at every tick, what the agent saw, what it decided, and why. Rows are inserted, never updated, and deleted only by the retention job.

Write rule: the simulation tick updates the state tables and inserts its snapshot rows in
**one transaction**. The agent cycle writes `ai_scans` and `ai_actions` in the same
transaction as the tool it executes.

### 3.2 State tables (Flyway V1)

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, username, password_hash, role (`CLINIC_MANAGER`, `RECEPTION`, `MEDICAL_STAFF`) | Login + role-based access |
| `departments` | id, name, status (`NORMAL`, `WARNING`, `OVERLOADED`, `CRITICAL`), max_queue, warning_threshold, overload_threshold | General medicine, pediatrics, orthopedics, emergency |
| `treatment_rooms` | id, department_id, name, status (`AVAILABLE`, `OCCUPIED`, `INACTIVE`, `MAINTENANCE`) | |
| `doctors` | id, department_id, name, available (bool), max_concurrent_patients | |
| `patients` | id, department_id, urgency (`LOW`, `MEDIUM`, `HIGH`), status (`WAITING`, `IN_TREATMENT`, `DONE`, `LEFT`), arrived_at, patience_min, treatment_started_at, treatment_ended_at, left_at, room_id, doctor_id | Waiting time is computed, not stored. `LEFT` + `left_at` record a walkout (§3.4); added in the phase 2 migration (V4) |
| `medications` | id, name, stock, threshold | |
| `restock_requests` | id, medication_id, quantity, status (`OPEN`, `FULFILLED`), created_by (`AI`/`USER`), created_at | |
| `alerts` | id, type, severity, department_id, message, active (bool), created_by, created_at, resolved_at | |
| `simulation_runs` | id, started_at, stopped_at, seed, scenario_name, tick_interval_sec | |

### 3.3 History tables (Flyway V3)

| Table | One row per | Columns |
|---|---|---|
| `department_snapshots` | department per simulation tick | run_id, tick, taken_at, department_id, status, waiting_count, avg_wait_min, max_wait_min, left_count, left_rate (§3.4, V4), rooms_available, rooms_occupied, rooms_inactive, doctors_available |
| `medication_snapshots` | medication per simulation tick | run_id, tick, taken_at, medication_id, stock, threshold |
| `ai_scans` | agent cycle | started_at, finished_at, trigger_type, summary, model, context_json (`jsonb`, what the model saw), latency_ms, input_tokens, output_tokens |
| `ai_actions` | tool call | scan_id, tool, parameters (`jsonb`), explanation (full-text indexed), validation_result, executed, created_at |
| `system_events` | log event | created_at, level, component, message, details (`jsonb`) |
| `evaluation_results` | evaluation scenario run | created_at, scenario, run, expected_issues, detected_issues, expected_action, actual_action, correct, explained |

Snapshots are stored one row per department rather than as one JSON document per tick, so the
trend query is plain SQL: `avg`/`max` of the wait columns and the change in `waiting_count`
over the last 15 minutes, grouped by department.

### 3.4 Patient walkouts (retention over time)

Real clinics lose patients who give up waiting ("left without being seen"). Modelling this
makes the simulation honest and gives the agent a measurable outcome, not only a status.

- Every simulated patient gets a **patience** in minutes, drawn per urgency (for example
  LOW 45–90, MEDIUM 60–120, HIGH never leaves). Stored as `patience_min` on the patient.
- On each tick, a `WAITING` patient whose waiting time exceeds its patience becomes `LEFT`
  with `left_at` set, and frees its queue slot. The row stays: the walkout rate is a metric,
  so a walkout is a lifecycle timestamp like `resolved_at` on alerts, never a delete.
- Each `department_snapshots` row carries `left_count` (walkouts in that tick) and
  `left_rate` (walkouts ÷ arrivals over the run so far). The Context Builder includes the
  15-minute walkout trend, so the agent sees that waiting is already costing patients, not
  only that the queue is long.
- The dashboard shows walkouts per department next to the queue.
- Evaluation (§7) adds an outcome metric: the same scenario run with the agent off and on,
  comparing walkout rates. This is the project's evidence for "improved clinic efficiency".

### 3.5 History retention (data over time)

The history tables are append-only and grow with every tick, so they need a retention
rule. Retention is a hard delete by age, not a `deleted_at` flag: the point is to keep the
tables small, nobody restores a month-old snapshot, and a soft delete would force a
`deleted_at is null` filter into every trend and chart query.

- A `@Scheduled` job (`HistoryRetentionJob`, phase 7) runs once a day and deletes rows older
  than the configured age: `department_snapshots`, `medication_snapshots` and
  `system_events` after `history.retention-days` (default 30); `ai_actions`, `ai_scans` and
  `evaluation_results` after `history.evidence-retention-days` (default 180), because they
  are the project's evidence.
- Deletes run in batches of a few thousand rows so the job never holds a long lock while
  the simulation is ticking, and in foreign-key order: `ai_actions` before `ai_scans`,
  snapshots before any old `simulation_runs`.
- Every table involved already has the time column the job filters on (`taken_at` or
  `created_at`), and it is indexed.
- State tables keep current state only and need no retention; finished `simulation_runs`
  and their patients can be purged by run id.

---

## 4. Service architecture

One Spring Boot service, `clinic-app`, owns PostgreSQL. Everything that must be
transactional and everything that reads history lives in it.

| Component | Owns | Responsibilities | Port |
|---|---|---|---|
| `clinic-app` | PostgreSQL | Domain (departments, rooms, doctors, patients, medications, alerts, restock), users + JWT login, simulation engine, history (snapshots, trends, AI decisions, events, evaluation results), AI agent (context, prompt, LLM call), the six tools + validation, dashboard REST API | 8080 |
| `frontend` | – | React dashboard; Vite proxy routes `/api/*` to `clinic-app` | 5173 |

### 4.0 Repository layout

```
part-b-implementation/
  pom.xml                     parent (dependency management, shared plugins)
  clinic-app/
    src/main/java/.../clinic/
      model/                  enums: statuses, roles, urgency, tools
      auth/                   JWT login, security config, user roles
      domain/                 entities, repositories, services, REST for the clinic state
      simulation/             SimulationEngine (@Scheduled tick), arrival generator, state rules, start/stop API
      history/                snapshot writer, trend queries, AI decision / event / evaluation queries, retention job
      agent/
        context/              ContextBuilder → ClinicContext JSON (current state + trends, both from PostgreSQL)
        prompt/               system prompt text + tool schemas
        llm/                  LlmClient interface, AnthropicLlmClient, FakeLlmClient
        tools/                one class per tool + ToolValidator + ToolExecutor
        AgentService (the cycle), AgentScheduler (15-minute trigger)
      api/                    DTOs, error handling, OpenAPI config
  frontend/                   React + Vite + TypeScript
  docker-compose.yml          postgres, clinic-app (frontend joins in phase 5)
  clinic-app/Dockerfile       multi-stage build: Maven package, then a JRE image
  docs/                       ERD, API spec, diagrams
```

The `simulation/` and `agent/` packages depend on the rest of the app only through
service interfaces, so extracting them later is moving a package, not a rewrite.

### 4.0.1 Optional extractions (phase 8, only if time allows)

| Order | Service | Why it is a clean cut | What changes |
|---|---|---|---|
| 1 | `simulation-service` | Only sends commands ("patient arrived", "treatment finished") to `clinic-app`; holds no transactions | Commands become REST calls; `clinic-app` exposes `/api/internal/simulation/*` |
| 2 | `agent-service` | Reads state and trends from `clinic-app`, executes one tool endpoint; isolates the LLM dependency and API key | `clinic-app` exposes `/api/internal/tools/execute`; validation stays in `clinic-app` |

Never extract: the tools + validation layer away from PostgreSQL (breaks the one-transaction
guarantee), history into its own service (it would reintroduce the dual write this plan
removed), users/login into an auth service (three roles, pure overhead), or individual
domain entities (they share transactions).

### 4.1 Tools (from the project book, section 4.3.6)

| Tool | Parameters | Validation before execution |
|---|---|---|
| `READ_CLINIC_STATE` | – | Always allowed; returns the context JSON |
| `CREATE_ALERT` | type, severity, department_id, message | Department exists; no identical active alert already open |
| `SET_DEPARTMENT_STATUS` | department_id, status | Department exists; status is a known value; transition is justified by current metrics |
| `OPEN_ROOM` | department_id | Department has at least one `INACTIVE` room; department is `WARNING` or worse |
| `REQUEST_MEDICATION_RESTOCK` | medication_id, quantity | Stock is below threshold; no `OPEN` request already exists |
| `GENERATE_SUMMARY` | – | Always allowed; stored on the `ai_scans` row |

Every tool call is logged with its validation result even when rejected. Rejected calls count
toward the "incorrect action" metric.

### 4.2 Agent cycle

1. `AgentScheduler` fires every 15 minutes, or `POST /api/agent/scan` fires it manually.
2. `ContextBuilder` builds a compact `ClinicContext` JSON: current queues, waiting times, department statuses, room availability, doctor load, medication stock, active alerts, last N AI actions, and 15-minute trends from `department_snapshots` / `medication_snapshots`.
3. `AnthropicLlmClient` sends the system prompt, the context, and the six tools (strict schemas) in one request. The system prompt is marked for prompt caching.
4. The response contains at most one tool call plus an explanation. `ToolValidator` checks it; `ToolExecutor` runs it inside a transaction.
5. `GENERATE_SUMMARY` is called in a second request with the outcome, producing the operational summary.
6. The whole cycle – context, model, tokens, latency, tool call, validation result, summary – is written to `ai_scans` / `ai_actions` in the same transaction as the tool.

Safety boundaries from the book are enforced in code, not only in the prompt: the model
cannot call anything outside the six tools, and the backend decides whether a call executes.

---

## 5. REST API (first version)

All endpoints are served by `clinic-app` on port 8080 and documented with springdoc at
`/swagger-ui.html`. Every endpoint except login, health and the API docs requires the user JWT.

### 5.1 Clinic state, simulation and agent

| Method & path | Purpose |
|---|---|
| `POST /api/auth/login` | Returns a JWT |
| `GET /api/clinic/state` | Full dashboard payload (departments, queues, rooms, doctors, medications, active alerts) |
| `GET /api/departments`, `/rooms`, `/doctors`, `/patients`, `/medications`, `/alerts` | Read endpoints with filters |
| `POST /api/simulation/start`, `/stop`, `GET /api/simulation/status` | Clinic manager only |
| `POST /api/simulation/scenario/{name}` | Seed a predefined scenario (used by evaluation) |
| `POST /api/agent/activate`, `/deactivate`, `/scan` | Control the agent; manual scan |
| `GET /api/agent/actions`, `/scans`, `/summary/latest` | AI recommendations, recent actions and the latest summary |

### 5.2 History

| Method & path | Purpose |
|---|---|
| `GET /api/history/snapshots?from&to&department` | Time series for dashboard charts |
| `GET /api/history/trends?minutes=15` | Per-department aggregations (avg/max wait, queue growth, stock delta); the same query the Context Builder uses |
| `GET /api/history/decisions?from&to&tool&q` | AI action history page; `q` searches explanation text |
| `GET /api/history/events?from&to&level` | System event log |
| `GET /api/history/evaluations/summary` | The four section-8 metrics per scenario |

There are no ingest endpoints: history rows are written inside the backend by the code that
produces them.

---

## 6. Build order

Each phase ends with something runnable and tested. Estimated effort assumes two people
working part-time.

| # | Phase | Deliverables | Done when |
|---|---|---|---|
| 0 | Skeleton (1 week) | Maven layout, `docker-compose.yml`, `clinic-app` boots against PostgreSQL with Flyway V1, health endpoint, GitHub Actions running tests | `docker compose up` starts the database; CI is green |
| 1 | Core domain (1–2 weeks) | All tables, JPA entities, repositories, read endpoints, seed data, JWT login with roles | Login test cases from the book pass; dashboard payload endpoint returns seeded clinic |
| 2 | Simulation engine (2 weeks) | Scheduled tick, patient arrivals by urgency, department assignment, waiting-time updates, room occupancy and release, doctor load, department status rules, patient patience and walkouts (§3.4, Flyway V4), start/stop API; snapshot rows written in the tick's transaction; `/api/history/snapshots` and `/api/history/trends` | Overload, room shortage and medication shortage scenarios appear on their own; walkouts appear when waits exceed patience; snapshots visible in the `department_snapshots` table and via `/api/history/snapshots` |
| 3 | Tools + validation, no LLM (1 week) | Six tool services, `ToolValidator`, `ToolExecutor`, alert and restock generation, unit tests for every validation rule | All tool test cases from the book pass without any model involved |
| 4 | AI agent (2 weeks) | `ContextBuilder` (state + trends), system prompt, `AnthropicLlmClient`, agent cycle, scheduler, manual scan, decisions stored with context, tokens and latency | Agent detects a seeded overload and opens a room with an explanation; every action has an explanation |
| 5 | Dashboard (2 weeks) | Login page, live clinic view, history charts, alerts panel, AI recommendations + history, summary view, simulation and agent controls by role | All UI test cases from the book pass |
| 6 | Evaluation harness (1 week) | Scenario JSON files with expected issues and expected actions, scorer that computes the four section-8 metrics, results stored in `evaluation_results`, evaluation page in the dashboard | Metrics computed for at least 10 scenarios, 3 runs each |
| 7 | Hardening (1 week) | Testcontainers integration tests, history retention job (§3.5), Docker image for the frontend, README with one-command setup, final diagrams updated | Full system test case from the book passes in CI; old snapshot rows are deleted automatically |
| 8 | Optional extractions (only if time allows) | `simulation-service`, then `agent-service`, per §4.0.1 | Evaluation metrics unchanged after the split |

Why this order: tools are built and tested before the LLM so the agent has deterministic
actions to call; the frontend is built after the API is stable so it is not rewritten; the
demo and the evaluation never depend on phase 8.

---

## 7. Evaluation (project book section 8)

Scenarios are JSON files under `clinic-app/src/test/resources/scenarios/`. Each one seeds the
clinic, declares the expected issues and the expected action, runs one agent cycle, and
records the outcome to the `evaluation_results` table. The report numbers come from
`GET /api/history/evaluations/summary` and the evaluation page in the dashboard.

| Metric | Formula | Target |
|---|---|---|
| Detection accuracy | detected ÷ expected issues | ≥ 90 % |
| Action accuracy | correct ÷ executed actions | ≥ 85 % |
| Incorrect action rate | incorrect ÷ executed actions | < 10 % |
| Explanation coverage | actions with explanation ÷ actions | 100 % |
| Walkout reduction (outcome, §3.4) | walkout rate with agent off − with agent on, same scenario and seed | report the number; target: lower with the agent on |

To handle model non-determinism: fixed seed per scenario, three runs each, report the mean.
Include at least two "healthy clinic" scenarios to measure false positives. The walkout
comparison runs each overload scenario twice, once with the agent disabled, so the
difference is attributable to the agent's actions alone.

---

## 8. Local development

```bash
# prerequisites: Docker Desktop, JDK 21, Node 20
cp .env.example .env            # add ANTHROPIC_API_KEY
docker compose up -d --build    # postgres + clinic-app as one unit
cd frontend && npm install && npm run dev      # dashboard dev server

# or, to debug the backend from the IDE:
docker compose up -d postgres
./mvnw -pl clinic-app spring-boot:run
```

- clinic-app: http://localhost:8080 (Swagger at `/swagger-ui.html`)
- Frontend: http://localhost:5173
- PostgreSQL: `localhost:5432`, database, user and password `clinic` (any client: IntelliJ Database tool, `psql`, DBeaver)

From phase 5, `docker compose up` also starts the frontend, so a demo is one command.

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Stack differs from the Phase A book | Update the book and diagrams in the first week; keep this document in the repo as the reference |
| LLM cost | One request per 15 minutes with a compact context; prompt caching on the system prompt; a `FakeLlmClient` for tests and the evaluation harness dry-runs |
| Non-deterministic agent behaviour | Strict tool schemas, backend validation, fixed-seed scenarios, repeated runs |
| History tables grow without bound | Indexed by time; retention job (phase 7) deletes old snapshot and event rows; partition by day only if a measured query gets slow |
| Snapshot inserts slow the tick | One multi-row insert per table per tick (four departments, a handful of medications); measured in phase 2 |
| Scope creep (medical logic) | Tools are the only actions; no patient-clinical fields in the data model |
