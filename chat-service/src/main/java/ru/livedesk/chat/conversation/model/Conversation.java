package ru.livedesk.chat.conversation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.livedesk.chat.exception.IllegalStateTransitionException;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(nullable = false, length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Conversation() {}

    public Conversation(UUID clientId, String topic) {
        this.id = UUID.randomUUID();
        this.clientId = clientId;
        this.topic = topic;
        this.status = ConversationStatus.WAITING;
        this.createdAt = Instant.now();
    }

    public void take(UUID operatorId) {
        if (status != ConversationStatus.WAITING) {
            throw new IllegalStateTransitionException("Обращение уже забрано или закрыто");
        }
        this.operatorId = operatorId;
        this.status = ConversationStatus.ACTIVE;
    }

    /** Клиент вправе закрыть обращение, не дождавшись оператора. */
    public void close() {
        if (status == ConversationStatus.CLOSED) {
            throw new IllegalStateTransitionException("Обращение уже закрыто");
        }
        this.status = ConversationStatus.CLOSED;
    }

    public boolean hasParticipant(UUID userId) {
        return clientId.equals(userId) || Objects.equals(operatorId, userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public String getTopic() {
        return topic;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
