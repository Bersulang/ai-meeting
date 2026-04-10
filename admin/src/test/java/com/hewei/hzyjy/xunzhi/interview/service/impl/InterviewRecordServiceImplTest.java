package com.hewei.hzyjy.xunzhi.interview.service.impl;

import com.hewei.hzyjy.xunzhi.interview.application.InterviewSessionOwnershipService;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewRecordDO;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewSession;
import com.hewei.hzyjy.xunzhi.interview.dao.mapper.InterviewRecordMapper;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewSessionService;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewSessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewRecordServiceImplTest {

    @Test
    void shouldFinishInterviewSessionBeforePersistingRecordFromRedis() {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewSessionOwnershipService ownershipService = mock(InterviewSessionOwnershipService.class);
        InterviewSessionService sessionService = mock(InterviewSessionService.class);
        InterviewRecordMapper mapper = mock(InterviewRecordMapper.class);
        InterviewRecordServiceImpl service = new InterviewRecordServiceImpl(cacheService, ownershipService, sessionService);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        InterviewSession session = new InterviewSession();
        session.setSessionId("interview-session-1");
        session.setUserId(1001L);
        session.setStatus(InterviewSessionStatus.FINISHED.name());
        session.setInterviewerAgentId(9L);
        session.setInterviewType("backend");
        session.setResumeFileUrl("https://example.com/resume.pdf");
        session.setCreateTime(new Date(System.currentTimeMillis() - 120_000));
        session.setStartTime(new Date(System.currentTimeMillis() - 60_000));
        session.setEndTime(new Date());
        when(ownershipService.requireOwnedSession("interview-session-1", 1001L)).thenReturn(session);

        when(cacheService.getSessionTotalScore("interview-session-1")).thenReturn(92);
        when(cacheService.getSessionInterviewSuggestions("interview-session-1")).thenReturn(Map.of("1", "Structured answer"));
        when(cacheService.getSessionResumeScore("interview-session-1")).thenReturn(86);
        when(cacheService.getSessionInterviewQuestions("interview-session-1")).thenReturn(Map.of("1", "Describe JVM tuning"));
        when(cacheService.getSessionInterviewDirection("interview-session-1")).thenReturn("backend");
        when(cacheService.getInterviewTurns("interview-session-1")).thenReturn(List.of(
                InterviewTurnLog.builder()
                        .questionNumber("1")
                        .score(88)
                        .feedback("Answer structure is clear and supported by a concrete project example.")
                        .build()
        ));
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(InterviewRecordDO.class))).thenReturn(1);

        service.saveInterviewRecordFromRedis("interview-session-1", 1001L);

        InOrder inOrder = inOrder(sessionService, ownershipService, mapper);
        inOrder.verify(sessionService).finishSession("interview-session-1", 1001L);
        inOrder.verify(ownershipService).requireOwnedSession("interview-session-1", 1001L);
        inOrder.verify(mapper).selectOne(any());
        ArgumentCaptor<InterviewRecordDO> recordCaptor = ArgumentCaptor.forClass(InterviewRecordDO.class);
        verify(mapper).insert(recordCaptor.capture());

        InterviewRecordDO record = recordCaptor.getValue();
        assertEquals("interview-session-1", record.getSessionId());
        assertEquals(1001L, record.getUserId());
        assertEquals(InterviewSessionStatus.FINISHED.name(), record.getInterviewStatus());
        assertEquals(9L, record.getInterviewerAgentId());
        assertEquals(92, record.getInterviewScore());
        assertEquals(86, record.getResumeScore());
        assertTrue(record.getSessionSnapshotJson().contains("\"sessionStatus\":\"FINISHED\""));
        assertTrue(record.getSessionSnapshotJson().contains("\"reviewFeedback\""));
    }
}
