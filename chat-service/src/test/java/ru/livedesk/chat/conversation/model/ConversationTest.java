package ru.livedesk.chat.conversation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.livedesk.chat.exception.IllegalStateTransitionException;

class ConversationTest {

    private final UUID clientId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();

    @Test
    void newConversationWaitsForOperator() {
        Conversation conversation = new Conversation(clientId, "не приходит счёт");

        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WAITING);
        assertThat(conversation.getOperatorId()).isNull();
        assertThat(conversation.getId()).isNotNull();
    }

    @Test
    void takeMovesWaitingToActive() {
        Conversation conversation = new Conversation(clientId, "тема");

        conversation.take(operatorId);

        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.getOperatorId()).isEqualTo(operatorId);
    }

    @Test
    void secondTakeIsRejected() {
        Conversation conversation = new Conversation(clientId, "тема");
        conversation.take(operatorId);

        assertThatThrownBy(() -> conversation.take(UUID.randomUUID()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void closedConversationCannotBeTaken() {
        Conversation conversation = new Conversation(clientId, "тема");
        conversation.close();

        assertThatThrownBy(() -> conversation.take(operatorId)).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void clientMayCloseWithoutWaitingForOperator() {
        Conversation conversation = new Conversation(clientId, "тема");

        conversation.close();

        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.CLOSED);
    }

    @Test
    void activeConversationCloses() {
        Conversation conversation = new Conversation(clientId, "тема");
        conversation.take(operatorId);

        conversation.close();

        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.CLOSED);
    }

    @Test
    void secondCloseIsRejected() {
        Conversation conversation = new Conversation(clientId, "тема");
        conversation.close();

        assertThatThrownBy(conversation::close).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void participantsAreClientAndAssignedOperator() {
        Conversation conversation = new Conversation(clientId, "тема");

        assertThat(conversation.hasParticipant(clientId)).isTrue();
        assertThat(conversation.hasParticipant(operatorId)).isFalse();

        conversation.take(operatorId);

        assertThat(conversation.hasParticipant(operatorId)).isTrue();
        assertThat(conversation.hasParticipant(UUID.randomUUID())).isFalse();
    }
}
