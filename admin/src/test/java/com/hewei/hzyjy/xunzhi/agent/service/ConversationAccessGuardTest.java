package com.hewei.hzyjy.xunzhi.agent.service;

import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentConversation;
import com.hewei.hzyjy.xunzhi.agent.dao.repository.AgentConversationRepository;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationAccessGuardTest {

    @Mock
    private AgentConversationRepository agentConversationRepository;

    private ConversationAccessGuard conversationAccessGuard;

    @BeforeEach
    void setUp() {
        conversationAccessGuard = new ConversationAccessGuard(agentConversationRepository);
    }

    @Test
    void requireOwnedConversation_ShouldThrow_WhenUserIsNotOwner() {
        AgentConversation conversation = new AgentConversation();
        conversation.setSessionId("s-001");
        conversation.setUserId(100L);

        when(agentConversationRepository.findBySessionIdAndDelFlag("s-001", 0))
                .thenReturn(Optional.of(conversation));

        assertThrows(
                ClientException.class,
                () -> conversationAccessGuard.requireOwnedConversation("s-001", 200L)
        );
    }

    @Test
    void requireOwnedConversation_ShouldPass_WhenUserIsOwner() {
        AgentConversation conversation = new AgentConversation();
        conversation.setSessionId("s-002");
        conversation.setUserId(100L);

        when(agentConversationRepository.findBySessionIdAndDelFlag("s-002", 0))
                .thenReturn(Optional.of(conversation));

        AgentConversation result = conversationAccessGuard.requireOwnedConversation("s-002", 100L);
        assertEquals("s-002", result.getSessionId());
        assertEquals(100L, result.getUserId());
    }
}
