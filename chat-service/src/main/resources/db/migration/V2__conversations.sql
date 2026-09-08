create table conversations (
    id          uuid         primary key,
    client_id   uuid         not null references users (id),
    operator_id uuid         references users (id),
    topic       varchar(200) not null,
    status      varchar(16)  not null,
    created_at  timestamptz  not null default now()
);

-- Очередь оператора — это выборка по статусу в порядке поступления.
create index ix_conversations_status_created_at on conversations (status, created_at);
create index ix_conversations_client_id on conversations (client_id);
create index ix_conversations_operator_id on conversations (operator_id) where operator_id is not null;
