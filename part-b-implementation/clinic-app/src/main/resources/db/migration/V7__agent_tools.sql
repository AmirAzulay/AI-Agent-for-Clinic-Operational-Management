-- V7: the agent's tools (PLAN.md phase 3, §4.1). Times written by tools are simulated time.

-- escalate_department_status: the agent may raise a department's status for a while; the
-- simulation then shows the higher of its own status and the escalation until it expires.
alter table departments
    add column status_override       varchar(16) check (status_override in ('WARNING', 'OVERLOADED', 'CRITICAL')),
    add column status_override_until timestamptz,
    add constraint departments_override_pair check ((status_override is null) = (status_override_until is null));

-- request_medication_restock: a request belongs to a run and is delivered after a delay.
alter table restock_requests
    add column run_id     bigint references simulation_runs (id) on delete cascade,
    add column deliver_at timestamptz;
create index idx_restock_requests_open on restock_requests (medication_id) where status = 'OPEN';

-- Alerts raised during a run belong to it, so a new run can tell them apart.
alter table alerts add column run_id bigint references simulation_runs (id) on delete cascade;

-- An agent run (one skill execution). Tool calls are logged as ai_actions under it.
alter table ai_scans
    add column run_id      bigint references simulation_runs (id) on delete cascade,
    add column skill       varchar(64),
    add column sim_started_at timestamptz,
    add column cost_usd    numeric(10, 4) check (cost_usd >= 0);
alter table ai_scans drop constraint ai_scans_trigger_type_check;
alter table ai_scans add constraint ai_scans_trigger_type_check
    check (trigger_type in ('SCHEDULED', 'MANUAL', 'AUTOPILOT'));

alter table ai_actions add column sim_time timestamptz;
