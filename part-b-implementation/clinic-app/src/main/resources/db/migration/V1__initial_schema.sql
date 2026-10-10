-- V1: initial schema for clinic-app (PLAN.md section 3.2).
-- PostgreSQL is the source of truth for the current clinic state. History lives in Elasticsearch.

create table users (
    id            bigserial primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(255) not null,
    role          varchar(32)  not null check (role in ('CLINIC_MANAGER', 'RECEPTION', 'MEDICAL_STAFF')),
    created_at    timestamptz  not null default now()
);

create table departments (
    id                 bigserial primary key,
    name               varchar(64) not null unique,
    status             varchar(16) not null default 'NORMAL'
                       check (status in ('NORMAL', 'WARNING', 'OVERLOADED', 'CRITICAL')),
    max_queue          int not null check (max_queue > 0),
    warning_threshold  int not null check (warning_threshold >= 0),
    overload_threshold int not null check (overload_threshold >= warning_threshold)
);

create table treatment_rooms (
    id            bigserial primary key,
    department_id bigint      not null references departments (id),
    name          varchar(64) not null,
    status        varchar(16) not null default 'AVAILABLE'
                  check (status in ('AVAILABLE', 'OCCUPIED', 'INACTIVE', 'MAINTENANCE')),
    unique (department_id, name)
);

create table doctors (
    id                      bigserial primary key,
    department_id           bigint       not null references departments (id),
    name                    varchar(128) not null,
    available               boolean      not null default true,
    max_concurrent_patients int          not null default 1 check (max_concurrent_patients > 0)
);

create table patients (
    id                   bigserial primary key,
    department_id        bigint      not null references departments (id),
    urgency              varchar(16) not null check (urgency in ('LOW', 'MEDIUM', 'HIGH')),
    status               varchar(16) not null default 'WAITING'
                         check (status in ('WAITING', 'IN_TREATMENT', 'DONE')),
    arrived_at           timestamptz not null,
    treatment_started_at timestamptz,
    treatment_ended_at   timestamptz,
    room_id              bigint references treatment_rooms (id),
    doctor_id            bigint references doctors (id)
);
create index idx_patients_department_status on patients (department_id, status);

create table medications (
    id        bigserial primary key,
    name      varchar(128) not null unique,
    stock     int          not null check (stock >= 0),
    threshold int          not null check (threshold >= 0)
);

create table restock_requests (
    id            bigserial primary key,
    medication_id bigint      not null references medications (id),
    quantity      int         not null check (quantity > 0),
    status        varchar(16) not null default 'OPEN' check (status in ('OPEN', 'FULFILLED')),
    created_by    varchar(8)  not null check (created_by in ('AI', 'USER')),
    created_at    timestamptz not null default now(),
    fulfilled_at  timestamptz
);

create table alerts (
    id            bigserial primary key,
    type          varchar(32) not null,
    severity      varchar(16) not null check (severity in ('INFO', 'WARNING', 'CRITICAL')),
    department_id bigint references departments (id),
    message       text        not null,
    active        boolean     not null default true,
    created_by    varchar(8)  not null check (created_by in ('AI', 'USER', 'SYSTEM')),
    created_at    timestamptz not null default now(),
    resolved_at   timestamptz
);
create index idx_alerts_active on alerts (active) where active;

create table simulation_runs (
    id                bigserial primary key,
    started_at        timestamptz not null default now(),
    stopped_at        timestamptz,
    seed              bigint      not null,
    scenario_name     varchar(64),
    tick_interval_sec int         not null check (tick_interval_sec > 0)
);

create table ai_scans (
    id           bigserial primary key,
    started_at   timestamptz not null default now(),
    finished_at  timestamptz,
    trigger_type varchar(16) not null check (trigger_type in ('SCHEDULED', 'MANUAL')),
    summary      text
);

create table ai_actions (
    id                bigserial primary key,
    scan_id           bigint       not null references ai_scans (id),
    tool              varchar(32)  not null check (tool in (
                          'READ_CLINIC_STATE', 'CREATE_ALERT', 'SET_DEPARTMENT_STATUS',
                          'OPEN_ROOM', 'REQUEST_MEDICATION_RESTOCK', 'GENERATE_SUMMARY')),
    parameters        jsonb        not null default '{}'::jsonb,
    explanation       text         not null,
    validation_result varchar(255) not null,
    executed          boolean      not null default false,
    created_at        timestamptz  not null default now()
);
create index idx_ai_actions_scan on ai_actions (scan_id);
