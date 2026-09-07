package ru.livedesk.chat.message.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

    /** Курсор истории — это id: последовательность БД задаёт порядок в пределах обращения. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected Message() {}

    public Message(UUID conversationId, UUID senderId, String text) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.text = text;
        this.sentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
