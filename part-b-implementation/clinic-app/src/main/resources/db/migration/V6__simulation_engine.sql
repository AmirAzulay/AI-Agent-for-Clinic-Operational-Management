-- V6: simulation engine (PLAN.md phase 2).
-- Simulated time is tick-based: tick N of a run happens at sim_started_at + N * minutes_per_tick.
-- That keeps a run with a fixed seed reproducible no matter how the scheduler's timing jitters.

alter table simulation_runs
    add column minutes_per_tick int         not null default 1 check (minutes_per_tick between 1 and 60),
    add column max_ticks        int         check (max_ticks > 0),
    add column current_tick     bigint      not null default 0 check (current_tick >= 0),
    add column sim_started_at   timestamptz not null default now(),
    add column sim_now          timestamptz not null default now(),
    add column stop_reason      varchar(16) check (stop_reason in ('STOPPED', 'COMPLETED', 'INTERRUPTED', 'ERROR'));

-- Patients belong to the run that created them; a new run starts from an empty clinic.
alter table patients
    add column run_id           bigint references simulation_runs (id),
    add column treatment_due_at timestamptz;
create index idx_patients_run_status on patients (run_id, status);

-- What a new run resets the clinic to. Tools (phase 3) change the live values during a run.
alter table medications add column initial_stock int;
update medications set initial_stock = stock;
alter table medications alter column initial_stock set not null,
    add constraint medications_initial_stock_check check (initial_stock >= 0);

alter table treatment_rooms add column initial_status varchar(16);
-- A room occupied right now starts a new run free.
update treatment_rooms set initial_status = case status when 'OCCUPIED' then 'AVAILABLE' else status end;
alter table treatment_rooms alter column initial_status set not null,
    add constraint treatment_rooms_initial_status_check
        check (initial_status in ('AVAILABLE', 'INACTIVE', 'MAINTENANCE'));

alter table doctors add column initially_available boolean;
update doctors set initially_available = available;
alter table doctors alter column initially_available set not null;
