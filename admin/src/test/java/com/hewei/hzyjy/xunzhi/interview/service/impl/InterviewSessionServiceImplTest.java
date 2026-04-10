package com.hewei.hzyjy.xunzhi.interview.service.impl;

import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewSessionCreateRespDTO;
import com.hewei.hzyjy.xunzhi.interview.application.InterviewSessionOwnershipService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSession;
import com.hewei.hzyjy.xunzhi.interview.dao.repository.InterviewSessionRepository;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewSessionStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionServiceImplTest {

    @Test
    void shouldCreateIndependentInterviewSessionAndAbandonPreviousActiveOnes() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewSessionOwnershipService ownershipService = mock(InterviewSessionOwnershipService.class);
        BusinessAgentResolver businessAgentResolver = mock(BusinessAgentResolver.class);
        InterviewSessionServiceImpl service = new InterviewSessionServiceImpl(repository, ownershipService, businessAgentResolver);

        AgentPropertiesDO interviewer = new AgentPropertiesDO();
        interviewer.setId(9L);
        when(businessAgentResolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_ASKING)).thenReturn(interviewer);

        InterviewSession oldDraft = new InterviewSession();
        oldDraft.setSessionId("old-draft");
        oldDraft.setStatus(InterviewSessionStatus.DRAFT.name());
        InterviewSession oldReady = new InterviewSession();
        oldReady.setSessionId("old-ready");
        oldReady.setStatus(InterviewSessionStatus.READY.name());
        List<InterviewSession> activeSessions = new ArrayList<>(List.of(oldDraft, oldReady));
        when(repository.findByUserIdAndStatusInAndDelFlagOrderByUpdateTimeDesc(eq(1001L), any(), eq(0)))
                .thenReturn(activeSessions);
        when(repository.save(any(InterviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InterviewSessionCreateRespDTO response = service.createSession(1001L);

        verify(repository).saveAll(activeSessions);
        verify(repository).save(any(InterviewSession.class));
        assertEquals(InterviewSessionStatus.ABANDONED.name(), oldDraft.getStatus());
        assertEquals(InterviewSessionStatus.ABANDONED.name(), oldReady.getStatus());
        assertNotNull(oldDraft.getEndTime());
        assertNotNull(oldReady.getEndTime());
        assertNotNull(response.getSessionId());
        assertEquals(InterviewSessionStatus.DRAFT.name(), response.getStatus());
    }

    @Test
    void shouldFinishInterviewSessionWithTimestamps() {
        InterviewSessionRepository repository = mock(InterviewSessionRepository.class);
        InterviewSessionOwnershipService ownershipService = mock(InterviewSessionOwnershipService.class);
        BusinessAgentResolver businessAgentResolver = mock(BusinessAgentResolver.class);
        InterviewSessionServiceImpl service = new InterviewSessionServiceImpl(repository, ownershipService, businessAgentResolver);

        InterviewSession session = new InterviewSession();
        session.setSessionId("session-1");
        session.setStatus(InterviewSessionStatus.IN_PROGRESS.name());
        session.setCreateTime(new Date(System.currentTimeMillis() - 60_000));
        when(ownershipService.requireOwnedSession("session-1", 2002L)).thenReturn(session);
        when(repository.save(any(InterviewSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.finishSession("session-1", 2002L);

        verify(repository).save(session);
        assertEquals(InterviewSessionStatus.FINISHED.name(), session.getStatus());
        assertNotNull(session.getStartTime());
        assertNotNull(session.getEndTime());
    }
}
