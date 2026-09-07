create table users (
    id            uuid         primary key,
    email         varchar(320) not null,
    password_hash varchar(72)  not null,
    role          varchar(16)  not null,
    created_at    timestamptz  not null default now()
);

create unique index ux_users_email on users (email);

-- Оператора некому зарегистрировать: через API выдаётся только роль CLIENT. Хеш приходит
-- плейсхолдером Flyway из окружения, поэтому в репозитории его нет.
insert into users (id, email, password_hash, role)
values (
    '00000000-0000-0000-0000-000000000001',
    '${operator_email}',
    '${operator_password_hash}',
    'OPERATOR')
on conflict do nothing;
