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
| Backend framework | Spring Boot 3.x (Maven) | First-class modules for REST, security, scheduling, JPA and Elasticsearch |
| Frontend | React + Vite + TypeScript | Matches the project book; fast dev loop |
| Relational database | **PostgreSQL 16** (see §2 for the MySQL comparison) | Matches the project book, JSONB for tool-call payloads, identical Spring/Flyway/Testcontainers support |
| Non-relational database | Elasticsearch 8.x + Kibana | Append-only history, time-series aggregations, full-text search over AI explanations, free dashboards for the evaluation chapter |
| LLM | Anthropic Claude via the official Java SDK, model `claude-opus-5-5` | Native tool calling with strict JSON schemas; wrapped behind an `LlmClient` interface so OpenAI/Gemini can be added later as the book allows |
| Auth | Spring Security + JWT | Covers the authentication and authorization NFRs |
| Migrations | Flyway | Versioned, reviewable schema changes |
| Local environment | Docker Compose | One command brings up DB, Elasticsearch, Kibana, backend, frontend |
| Tests | JUnit 5 + Testcontainers | Real PostgreSQL and Elasticsearch in integration tests |
| CI | GitHub Actions | Build + tests on every push and pull request |
| IDE | IntelliJ IDEA Community (backend and frontend) | Free; best Spring experience |

### Changes required in the Phase A book

The Phase A book names **FastAPI (Python)** and **AWS Aurora PostgreSQL**; the architecture
diagram shows the same. Before the first Phase B review:

1. Update the section 6 technology table (backend → Java / Spring Boot; database → PostgreSQL + Elasticsearch).
2. Update the architecture diagram: replace the FastAPI box, add an Elasticsearch box in the Database Layer, and point the Logging System and AI Action History at it.
3. Inform the advisor of the change and the reason (team expertise, stronger typing and validation for the tool layer, two-store design that matches the logging and history requirements).

---

## 2. PostgreSQL vs MySQL

Both work for this project and both are fully supported by Spring Data JPA, Flyway and
Testcontainers. The differences that matter here:

| Topic | PostgreSQL 16 | MySQL 8.4 |
|---|---|---|
| Matches Phase A book and diagram | Yes | No – one more deviation to justify |
| Storing the raw tool-call JSON and context JSON | `jsonb` column, indexable and queryable | `JSON` column, less mature indexing |
| Enum-like columns (department status, room status) | Native `CHECK` / enum types | `ENUM` type, fine |
| Transactions, foreign keys, constraints | Excellent | Excellent (InnoDB) |
| Timestamp handling | `timestamptz` is unambiguous | `DATETIME` has no timezone; `TIMESTAMP` is limited |
| Team familiarity | Lower | Higher (Aurora MySQL at work) |
| Hosting later (if needed) | Neon / Supabase / RDS free tiers | PlanetScale / RDS free tiers |

**Recommendation: PostgreSQL.** It keeps the book consistent, and `jsonb` is a real
advantage for storing each AI decision's parameters next to the relational row. The
familiarity gap is small because all data access goes through JPA and Flyway SQL that is
nearly identical on both engines.

**If the team still prefers MySQL:** the plan does not change. Swap the Docker image, the
JDBC driver dependency, the Flyway dialect and the Testcontainers module. Store JSON payloads
in a `JSON` column instead of `jsonb`. Everything else in this document stays the same.

---

## 3. Data design

### 3.1 Why two databases

- **PostgreSQL is the source of truth for the current state** of the clinic. Every tool executes a transaction here.
- **Elasticsearch is the append-only history**: what the clinic looked like at every tick, what the agent saw, what it decided, and why.

Write order rule: commit to PostgreSQL first, then publish a Spring `ApplicationEvent` from a
`@TransactionalEventListener(AFTER_COMMIT)` that indexes the document into Elasticsearch.
If Elasticsearch is unavailable, clinic state is still correct and the index can be rebuilt.

### 3.2 PostgreSQL tables

| Table | Key columns | Notes |
|---|---|---|
| `users` | id, username, password_hash, role (`CLINIC_MANAGER`, `RECEPTION`, `MEDICAL_STAFF`) | Login + role-based access |
| `departments` | id, name, status (`NORMAL`, `WARNING`, `OVERLOADED`, `CRITICAL`), max_queue, warning_threshold, overload_threshold | General medicine, pediatrics, orthopedics, emergency |
| `treatment_rooms` | id, department_id, name, status (`AVAILABLE`, `OCCUPIED`, `INACTIVE`, `MAINTENANCE`) | |
| `doctors` | id, department_id, name, available (bool), max_concurrent_patients | |
| `patients` | id, department_id, urgency (`LOW`, `MEDIUM`, `HIGH`), status (`WAITING`, `IN_TREATMENT`, `DONE`), arrived_at, treatment_started_at, treatment_ended_at, room_id, doctor_id | Waiting time is computed, not stored |
| `medications` | id, name, stock, threshold | |
| `restock_requests` | id, medication_id, quantity, status (`OPEN`, `FULFILLED`), created_by (`AI`/`USER`), created_at | |
| `alerts` | id, type, severity, department_id, message, active (bool), created_by, created_at, resolved_at | |
| `ai_actions` | id, scan_id, tool, parameters (`jsonb`), explanation, validation_result, executed (bool), created_at | Mirror of what the dashboard shows as AI history |
| `ai_scans` | id, started_at, finished_at, trigger (`SCHEDULED`/`MANUAL`), summary | One row per agent cycle; tracks the last-scan time |
| `simulation_runs` | id, started_at, stopped_at, seed, scenario_name, tick_interval_sec | |

### 3.3 Elasticsearch indices

| Index | One document per | Fields |
|---|---|---|
| `clinic-state-snapshots` | simulation tick | `@timestamp`, run_id, tick, per-department: waiting_count, avg_wait_min, max_wait_min, status, rooms_available, rooms_occupied, doctors_available; medications: name, stock |
| `ai-decisions` | agent cycle | `@timestamp`, scan_id, context_json (what the model saw), model, tool, parameters, explanation, validation_result, executed, latency_ms, input_tokens, output_tokens |
| `system-events` | log event | `@timestamp`, level, component, message, details |
| `evaluation-results` | evaluation scenario run | `@timestamp`, scenario, expected_issues, detected_issues, expected_action, actual_action, correct (bool), explained (bool) |

Index templates live in `docs/elasticsearch/` and are applied at startup.

---

## 4. Backend architecture

```
backend/src/main/java/.../clinic/
  auth/         JWT login, security config, user roles
  clinic/       departments, rooms, doctors, patients, medications, alerts, restock (entities, repos, services, REST)
  simulation/   SimulationEngine (@Scheduled tick), arrival generator, state rules, start/stop API
  agent/
    context/    ContextBuilder → ClinicContext JSON (state from PostgreSQL + trends from Elasticsearch)
    prompt/     system prompt text + tool schemas
    llm/        LlmClient interface, AnthropicLlmClient implementation
    tools/      one class per tool + ToolValidator + ToolExecutor
    AgentService (the cycle), AgentScheduler (15-minute trigger)
  events/       ApplicationEvents + Elasticsearch indexers
  api/          DTOs, error handling, OpenAPI config
```

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
2. `ContextBuilder` builds a compact `ClinicContext` JSON: current queues, waiting times, department statuses, room availability, doctor load, medication stock, active alerts, last N AI actions, and 15-minute trends from Elasticsearch.
3. `AnthropicLlmClient` sends the system prompt, the context, and the six tools (strict schemas) in one request. The system prompt is marked for prompt caching.
4. The response contains at most one tool call plus an explanation. `ToolValidator` checks it; `ToolExecutor` runs it inside a transaction.
5. `GENERATE_SUMMARY` is called in a second request with the outcome, producing the operational summary.
6. The whole cycle is written to `ai_scans` / `ai_actions` (PostgreSQL) and `ai-decisions` (Elasticsearch).

Safety boundaries from the book are enforced in code, not only in the prompt: the model
cannot call anything outside the six tools, and the backend decides whether a call executes.

---

## 5. REST API (first version)

| Method & path | Purpose |
|---|---|
| `POST /api/auth/login` | Returns a JWT |
| `GET /api/clinic/state` | Full dashboard payload (departments, queues, rooms, doctors, medications, active alerts) |
| `GET /api/departments`, `/rooms`, `/doctors`, `/patients`, `/medications`, `/alerts` | Read endpoints with filters |
| `POST /api/simulation/start`, `/stop`, `GET /api/simulation/status` | Clinic manager only |
| `POST /api/simulation/scenario/{name}` | Seed a predefined scenario (used by evaluation) |
| `POST /api/agent/activate`, `/deactivate`, `/scan` | Control the agent; manual scan |
| `GET /api/agent/actions`, `/scans`, `/summary/latest` | AI recommendations, history and the latest summary |
| `GET /api/history/snapshots?from&to` | Time series for charts (from Elasticsearch) |

OpenAPI documentation is generated with springdoc and served at `/swagger-ui.html`.

---

## 6. Build order

Each phase ends with something runnable and tested. Estimated effort assumes two people
working part-time.

| # | Phase | Deliverables | Done when |
|---|---|---|---|
| 0 | Skeleton (1 week) | Repo layout, `docker-compose.yml`, Spring Boot app boots against PostgreSQL + Elasticsearch, Flyway V1, Elasticsearch index templates, GitHub Actions running tests | `docker compose up` works; CI is green |
| 1 | Core domain (1–2 weeks) | All tables, JPA entities, repositories, read endpoints, seed data, JWT login with roles | Login test cases from the book pass; dashboard payload endpoint returns seeded clinic |
| 2 | Simulation engine (2 weeks) | Scheduled tick, patient arrivals by urgency, department assignment, waiting-time updates, room occupancy and release, doctor load, department status rules, start/stop API, snapshot indexing | Overload, room shortage and medication shortage scenarios appear on their own; snapshots visible in Kibana |
| 3 | Tools + validation, no LLM (1 week) | Six tool services, `ToolValidator`, `ToolExecutor`, alert and restock generation, unit tests for every validation rule | All tool test cases from the book pass without any model involved |
| 4 | AI agent (2 weeks) | `ContextBuilder`, system prompt, `AnthropicLlmClient`, agent cycle, scheduler, manual scan, decision logging | Agent detects a seeded overload and opens a room with an explanation; every action has an explanation |
| 5 | Dashboard (2 weeks) | Login page, live clinic view, alerts panel, AI recommendations + history, summary view, simulation and agent controls by role | All UI test cases from the book pass |
| 6 | Evaluation harness (1 week) | Scenario JSON files with expected issues and expected actions, scorer that computes the four section-8 metrics, results indexed to Elasticsearch, Kibana dashboard | Metrics computed for at least 10 scenarios, 3 runs each |
| 7 | Hardening (1 week) | Testcontainers integration tests, Docker images for backend and frontend, README with one-command setup, final diagrams updated | Full system test case from the book passes in CI |

Why this order: tools are built and tested before the LLM so the agent has deterministic
actions to call; the frontend is built after the API is stable so it is not rewritten.

---

## 7. Evaluation (project book section 8)

Scenarios are JSON files under `backend/src/test/resources/scenarios/`. Each one seeds the
clinic, declares the expected issues and the expected action, runs one agent cycle, and
records the outcome to `evaluation-results`.

| Metric | Formula | Target |
|---|---|---|
| Detection accuracy | detected ÷ expected issues | ≥ 90 % |
| Action accuracy | correct ÷ executed actions | ≥ 85 % |
| Incorrect action rate | incorrect ÷ executed actions | < 10 % |
| Explanation coverage | actions with explanation ÷ actions | 100 % |

To handle model non-determinism: fixed seed per scenario, three runs each, report the mean.
Include at least two "healthy clinic" scenarios to measure false positives.

---

## 8. Local development

```bash
# prerequisites: Docker Desktop, JDK 21, Node 20
cp .env.example .env            # add ANTHROPIC_API_KEY
docker compose up -d postgres elasticsearch kibana
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

- Backend: http://localhost:8080 (Swagger at `/swagger-ui.html`)
- Frontend: http://localhost:5173
- Kibana: http://localhost:5601

Elasticsearch runs single-node with a 1 GB heap and security disabled for local use.

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Stack differs from the Phase A book | Update the book and diagrams in the first week; keep this document in the repo as the reference |
| LLM cost | One request per 15 minutes with a compact context; prompt caching on the system prompt; a `FakeLlmClient` for tests and the evaluation harness dry-runs |
| Non-deterministic agent behaviour | Strict tool schemas, backend validation, fixed-seed scenarios, repeated runs |
| Elasticsearch memory on laptops | 1 GB heap cap in Compose; backend works without Elasticsearch (history features degrade gracefully) |
| Dual-write inconsistency | PostgreSQL first, index after commit, rebuild-index endpoint for recovery |
| Scope creep (medical logic) | Tools are the only actions; no patient-clinical fields in the data model |
