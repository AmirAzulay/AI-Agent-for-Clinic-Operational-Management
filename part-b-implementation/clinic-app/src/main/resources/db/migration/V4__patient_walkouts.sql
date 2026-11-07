-- V4: patient walkouts (PLAN.md section 3.4).
-- A simulated patient has a patience in minutes. When a WAITING patient has waited longer than
-- that, they leave: status LEFT, left_at set. The row stays, because the walkout rate is a metric.

alter table patients
    -- null = never leaves (HIGH urgency)
    add column patience_min int check (patience_min > 0),
    add column left_at      timestamptz;

alter table patients drop constraint patients_status_check;
alter table patients add constraint patients_status_check
    check (status in ('WAITING', 'IN_TREATMENT', 'DONE', 'LEFT'));

-- LEFT and left_at always go together.
alter table patients add constraint patients_left_at_check
    check ((status = 'LEFT') = (left_at is not null));

-- Walkouts per department over a time window (snapshots, agent trends, evaluation).
create index idx_patients_walkouts on patients (department_id, left_at) where status = 'LEFT';

-- Per tick: walkouts in that tick, and walkouts / arrivals over the run so far.
alter table department_snapshots
    add column left_count int              not null default 0 check (left_count >= 0),
    add column left_rate  double precision not null default 0 check (left_rate between 0 and 1);
