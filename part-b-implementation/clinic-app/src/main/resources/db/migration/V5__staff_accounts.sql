-- V5: staff accounts log in with an email address instead of a username.
-- The email is only a login name: it is never verified and the app never sends mail to it.
-- Only clinic staff have accounts (manager, reception, medical staff); patients never do.

alter table users
    add column email        varchar(254),
    add column display_name varchar(128),
    add column active       boolean not null default true;

-- The three development accounts keep their passwords ("<old username>123").
update users set email = username || '@clinic.local',
                 display_name = case username
                     when 'manager'   then 'Clinic Manager'
                     when 'reception' then 'Reception Desk'
                     when 'staff'     then 'Medical Staff'
                     else username end;

alter table users
    alter column email set not null,
    alter column display_name set not null,
    add constraint users_email_format check (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    add constraint users_email_lowercase check (email = lower(email)),
    drop column username;

create unique index users_email_key on users (email);
