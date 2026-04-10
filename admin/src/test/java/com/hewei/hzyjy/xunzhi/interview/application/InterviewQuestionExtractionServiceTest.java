package com.hewei.hzyjy.xunzhi.interview.application;

import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewQuestionReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewQuestionRespDTO;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.XingChenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewQuestionExtractionServiceTest {

    @Test
    void shouldFailWhenWorkflowFallsBackToSmallTalkInsteadOfQuestions() throws Exception {
        BusinessAgentResolver businessAgentResolver = mock(BusinessAgentResolver.class);
        XingChenAIClient xingChenAIClient = mock(XingChenAIClient.class);
        InterviewQuestionService interviewQuestionService = mock(InterviewQuestionService.class);
        InterviewQuestionCacheService interviewQuestionCacheService = mock(InterviewQuestionCacheService.class);
        InterviewResponseParser interviewResponseParser = new InterviewResponseParser();
        InterviewQuestionExtractionService service = new InterviewQuestionExtractionService(
                businessAgentResolver,
                xingChenAIClient,
                interviewQuestionService,
                interviewQuestionCacheService,
                interviewResponseParser
        );

        AgentPropertiesDO agent = new AgentPropertiesDO();
        agent.setId(8L);
        agent.setApiKey("key");
        agent.setApiSecret("secret");
        agent.setApiFlowId("flow-id");
        when(businessAgentResolver.resolveRequired(BusinessAgentScene.INTERVIEW_QUESTION_EXTRACTION))
                .thenReturn(agent);
        when(xingChenAIClient.uploadFile(any(), eq("key"), eq("secret")))
                .thenReturn("https://example.com/resume.pdf");
        doAnswer(invocation -> {
            OutputStream outputStream = invocation.getArgument(4);
            String workflowResponse = """
                    {"choices":[{"delta":{"role":"assistant","content":"{\\"questions\\":[],\\"sugest\\":[],\\"type\\":\\"\\",\\"smallTalk\\":\\"当然可以，欢迎回到面试环节\\",\\"resumeScore\\":0}"}}]}
                    """;
            outputStream.write(workflowResponse.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return null;
        }).when(xingChenAIClient).chat(
                any(),
                eq("session-1"),
                eq("{}"),
                eq(false),
                any(OutputStream.class),
                any(),
                eq("key"),
                eq("secret"),
                eq("flow-id"),
                eq("https://example.com/resume.pdf")
        );

        InterviewQuestionReqDTO request = new InterviewQuestionReqDTO();
        request.setSessionId("session-1");
        request.setUserName("tester");
        request.setResumePdf(new MockMultipartFile(
                "resumePdf",
                "resume.pdf",
                "application/pdf",
                "dummy".getBytes(StandardCharsets.UTF_8)
        ));

        InterviewQuestionRespDTO response = service.extractInterviewQuestions(request);

        assertEquals(0, response.getIsSuccess());
        assertTrue(response.getErrorMessage().contains("smallTalk"));
        verify(interviewQuestionService).createFromAIResponse(
                eq(request),
                any(),
                any(),
                eq(null)
        );
        verify(interviewQuestionCacheService, never()).cacheInterviewQuestions(eq("session-1"), any());
        verify(interviewQuestionCacheService, never()).initInterviewFlow(eq("session-1"), any());
    }
}
