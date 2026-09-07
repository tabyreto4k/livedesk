create table messages (
    id              bigserial     primary key,
    conversation_id uuid          not null references conversations (id),
    sender_id       uuid          not null references users (id),
    text            varchar(4000) not null,
    sent_at         timestamptz   not null default now()
);

-- Keyset-пагинация: одним индексом закрыты и фильтр по обращению, и порядок с курсором.
create index ix_messages_conversation_id_id on messages (conversation_id, id desc);
