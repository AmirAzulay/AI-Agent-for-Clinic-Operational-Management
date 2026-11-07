-- V3: history tables (PLAN.md section 3.3).
-- Elasticsearch is gone; history now lives next to the current state in PostgreSQL.
-- These tables are append-only: rows are inserted, never updated, and old rows are deleted
-- by the retention job (phase 7). Each simulation tick writes its snapshot rows in the same
-- transaction as the tick itself, so state and history can never disagree.

-- One row per department per simulation tick. Replaces the clinic-state-snapshots index.
create table department_snapshots (
    id                bigserial primary key,
    run_id            bigint      not null references simulation_runs (id),
    tick              bigint      not null check (tick >= 0),
    taken_at          timestamptz not null,
    department_id     bigint      not null references departments (id),
    status            varchar(16) not null
                      check (status in ('NORMAL', 'WARNING', 'OVERLOADED', 'CRITICAL')),
    waiting_count     int         not null check (waiting_count >= 0),
    avg_wait_min      double precision not null check (avg_wait_min >= 0),
    max_wait_min      double precision not null check (max_wait_min >= 0),
    rooms_available   int         not null check (rooms_available >= 0),
    rooms_occupied    int         not null check (rooms_occupied >= 0),
    rooms_inactive    int         not null check (rooms_inactive >= 0),
    doctors_available int         not null check (doctors_available >= 0),
    unique (run_id, tick, department_id)
);
-- The agent's 15-minute trends and the dashboard charts read by department and time.
create index idx_department_snapshots_dept_time on department_snapshots (department_id, taken_at);
create index idx_department_snapshots_time on department_snapshots (taken_at);

-- One row per medication per simulation tick.
create table medication_snapshots (
    id            bigserial primary key,
    run_id        bigint      not null references simulation_runs (id),
    tick          bigint      not null check (tick >= 0),
    taken_at      timestamptz not null,
    medication_id bigint      not null references medications (id),
    stock         int         not null check (stock >= 0),
    threshold     int         not null check (threshold >= 0),
    unique (run_id, tick, medication_id)
);
create index idx_medication_snapshots_med_time on medication_snapshots (medication_id, taken_at);
create index idx_medication_snapshots_time on medication_snapshots (taken_at);

-- What the model saw and what the call cost, per agent cycle. Replaces the ai-decisions index
-- together with the existing ai_actions table (one row per tool call).
alter table ai_scans
    add column model         varchar(64),
    add column context_json  jsonb,
    add column latency_ms    bigint check (latency_ms >= 0),
    add column input_tokens  int check (input_tokens >= 0),
    add column output_tokens int check (output_tokens >= 0);

-- Full-text search over AI explanations for the AI action history page.
create index idx_ai_actions_explanation_fts on ai_actions
    using gin (to_tsvector('english', explanation));
create index idx_ai_actions_created_at on ai_actions (created_at);

-- Audit and error log. Replaces the system-events index.
create table system_events (
    id         bigserial primary key,
    created_at timestamptz not null default now(),
    level      varchar(8)  not null check (level in ('DEBUG', 'INFO', 'WARN', 'ERROR')),
    component  varchar(64) not null,
    message    text        not null,
    details    jsonb       not null default '{}'::jsonb
);
create index idx_system_events_time on system_events (created_at);

-- One row per evaluation scenario run (project book section 8). Replaces the
-- evaluation-results index.
create table evaluation_results (
    id              bigserial primary key,
    created_at      timestamptz not null default now(),
    scenario        varchar(128) not null,
    run             int          not null check (run > 0),
    expected_issues text[]       not null default '{}',
    detected_issues text[]       not null default '{}',
    expected_action varchar(32),
    actual_action   varchar(32),
    correct         boolean      not null,
    explained       boolean      not null,
    unique (scenario, run, created_at)
);
create index idx_evaluation_results_scenario on evaluation_results (scenario);
