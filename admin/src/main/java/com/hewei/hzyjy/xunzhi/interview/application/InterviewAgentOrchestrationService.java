package com.hewei.hzyjy.xunzhi.interview.application;

import cn.hutool.core.util.StrUtil;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentResolver;
import com.hewei.hzyjy.xunzhi.agent.application.BusinessAgentScene;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.DemeanorEvaluationReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewAnswerReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.InterviewQuestionReqDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewAnswerRespDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.InterviewQuestionRespDTO;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewFlowState;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 面试主编排服务（纯主问题流程，不包含语音转写和追问分支）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewAgentOrchestrationService implements InterviewWorkflowService {

    private final BusinessAgentResolver businessAgentResolver;
    private final InterviewQuestionCacheService interviewQuestionCacheService;
    private final InterviewQuestionExtractionService interviewQuestionExtractionService;
    private final InterviewEvaluationService interviewEvaluationService;
    private final InterviewDemeanorService interviewDemeanorService;
    private final InterviewResponseParser interviewResponseParser;

    public InterviewQuestionRespDTO extractInterviewQuestions(InterviewQuestionReqDTO reqDTO) {
        return interviewQuestionExtractionService.extractInterviewQuestions(reqDTO);
    }

    /**
     * 用户提交回答后的核心链路：
     * 1) 读取当前主问题
     * 2) 调评分官工作流评分
     * 3) 推进到下一道主问题或结束
     */
    public InterviewAnswerRespDTO answerInterviewQuestion(String sessionId, InterviewAnswerReqDTO requestParam) {
        InterviewAnswerRespDTO response = InterviewAnswerRespDTO.init();

        if (StrUtil.isBlank(sessionId)) {
            return response.fail("sessionId cannot be empty");
        }
        if (requestParam == null) {
            return response.fail("request body cannot be empty");
        }

        try {
            String requestId = requestParam.getRequestId();

            // 幂等保护：同一个 requestId 重复提交时，只返回当前状态，不重复计分。
            if (!interviewQuestionCacheService.markAnswerRequestProcessed(sessionId, requestId)) {
                response.setTotalScore(interviewQuestionCacheService.getSessionTotalScore(sessionId));
                fillNextQuestionFromFlow(sessionId, response);
                if (StrUtil.isBlank(response.getErrorMessage())) {
                    response.success();
                }
                return response;
            }

            InterviewFlowState flowState = ensureInterviewFlow(sessionId);
            if (flowState == null) {
                return response.fail("interview flow not initialized");
            }
            if (flowState.isCompleted()) {
                response.setTotalScore(interviewQuestionCacheService.getSessionTotalScore(sessionId));
                return response.finish().success();
            }

            int currentIndex = flowState.getCurrentIndex() == null ? 0 : flowState.getCurrentIndex();
            String currentQuestionNumber = String.valueOf(currentIndex + 1);

            String questionContent = interviewQuestionCacheService.getQuestionByNumber(sessionId, currentQuestionNumber);
            if (StrUtil.isBlank(questionContent)) {
                interviewQuestionCacheService.loadInterviewQuestionsFromDatabase(sessionId);
                questionContent = interviewQuestionCacheService.getQuestionByNumber(sessionId, currentQuestionNumber);
            }
            if (StrUtil.isBlank(questionContent)) {
                return response.fail("question does not exist or expired");
            }
            response.withCurrentQuestion(currentQuestionNumber, questionContent);

            String answerContent = requestParam.getAnswerContent();
            if (StrUtil.isBlank(answerContent)) {
                return response.fail("answer content cannot be empty");
            }

            interviewQuestionCacheService.updateInterviewFlowStatus(sessionId, "EVALUATING");

            AgentPropertiesDO agentProperties = businessAgentResolver.resolveRequired(
                    BusinessAgentScene.INTERVIEW_ANSWER_EVALUATION);
            if (agentProperties == null) {
                return response.fail("agent configuration does not exist");
            }

            Map<String, Object> evaluationResult = interviewEvaluationService.evaluateAnswer(
                    sessionId,
                    requestId,
                    currentQuestionNumber,
                    questionContent,
                    answerContent,
                    agentProperties
            );
            if (evaluationResult == null) {
                return response.fail("failed to parse evaluation result");
            }

            Integer score = interviewResponseParser.parseScoreFromResponse(evaluationResult, "score");
            if (score == null) {
                return response.fail("score missing in evaluation result");
            }

            Integer totalScore = interviewQuestionCacheService.addSessionScore(sessionId, score);
            response.withEvaluation(score, interviewResponseParser.asString(evaluationResult.get("feedback")), totalScore);

            // 无追问分支：评分后直接推进主问题序列。
            InterviewFlowState nextFlow = interviewQuestionCacheService.advanceToNextQuestion(sessionId);
            if (nextFlow == null || nextFlow.isCompleted()) {
                interviewQuestionCacheService.markInterviewCompleted(sessionId);
                response.finish().success();
                appendInterviewTurn(
                        sessionId, requestId, currentQuestionNumber, questionContent, answerContent, score, totalScore, response);
                return response;
            }

            String nextQuestionNumber = String.valueOf(
                    (nextFlow.getCurrentIndex() == null ? 0 : nextFlow.getCurrentIndex()) + 1);
            String nextQuestion = interviewQuestionCacheService.getQuestionByNumber(sessionId, nextQuestionNumber);
            if (StrUtil.isBlank(nextQuestion)) {
                interviewQuestionCacheService.loadInterviewQuestionsFromDatabase(sessionId);
                nextQuestion = interviewQuestionCacheService.getQuestionByNumber(sessionId, nextQuestionNumber);
            }
            if (StrUtil.isBlank(nextQuestion)) {
                return response.fail("next question does not exist or expired");
            }

            response.withNextQuestion(nextQuestionNumber, nextQuestion, false, 0).success();
            appendInterviewTurn(
                    sessionId, requestId, currentQuestionNumber, questionContent, answerContent, score, totalScore, response);
            return response;
        } catch (Exception e) {
            log.error("Failed to answer interview question, sessionId: {}", sessionId, e);
            return response.fail("failed to process answer: " + e.getMessage());
        }
    }

    public InterviewAnswerRespDTO getNextQuestion(String sessionId) {
        return getCurrentQuestion(sessionId);
    }

    /**
     * Resolve current question by sessionId without moving the flow cursor.
     * Priority:
     * 1) flow state in Redis
     * 2) latest turn log recovery
     * 3) fallback to first question
     */
    public InterviewAnswerRespDTO getCurrentQuestion(String sessionId) {
        InterviewAnswerRespDTO response = InterviewAnswerRespDTO.init();

        if (StrUtil.isBlank(sessionId)) {
            return response.fail("sessionId cannot be empty");
        }

        try {
            Map<String, String> questions = getOrLoadQuestions(sessionId);
            if (questions == null || questions.isEmpty()) {
                return response.fail("interview questions not found");
            }

            InterviewFlowState flowState = interviewQuestionCacheService.getInterviewFlow(sessionId);
            if (flowState == null) {
                CurrentQuestionState recoveredState = recoverCurrentQuestionFromTurns(sessionId, questions);
                if (recoveredState.finished) {
                    response.setTotalScore(interviewQuestionCacheService.getSessionTotalScore(sessionId));
                    return response.finish().success();
                }
                if (recoveredState.hasQuestion()) {
                    fillCurrentQuestionResponse(sessionId, response,
                            recoveredState.questionNumber, recoveredState.questionContent);
                    return response.success();
                }

                interviewQuestionCacheService.initInterviewFlow(sessionId, questions.size());
                flowState = interviewQuestionCacheService.getInterviewFlow(sessionId);
            }

            if (flowState == null) {
                return response.fail("interview flow not initialized");
            }
            if (flowState.isCompleted()) {
                response.setTotalScore(interviewQuestionCacheService.getSessionTotalScore(sessionId));
                return response.finish().success();
            }

            int currentIndex = flowState.getCurrentIndex() == null ? 0 : flowState.getCurrentIndex();
            if (currentIndex < 0) {
                currentIndex = 0;
            }
            if (!questions.isEmpty() && currentIndex >= questions.size()) {
                currentIndex = questions.size() - 1;
            }
            String questionNumber = String.valueOf(currentIndex + 1);
            String questionContent = resolveQuestionByNumber(sessionId, questionNumber, questions);
            if (StrUtil.isBlank(questionContent)) {
                return response.fail("question does not exist or expired");
            }

            fillCurrentQuestionResponse(sessionId, response, questionNumber, questionContent);
            return response.success();
        } catch (Exception e) {
            log.error("Failed to get current question, sessionId: {}", sessionId, e);
            return response.fail("failed to get current question: " + e.getMessage());
        }
    }

    public String evaluateDemeanor(DemeanorEvaluationReqDTO reqDTO) {
        return interviewDemeanorService.evaluateDemeanor(reqDTO);
    }

    private InterviewFlowState ensureInterviewFlow(String sessionId) {
        InterviewFlowState state = interviewQuestionCacheService.getInterviewFlow(sessionId);
        if (state != null) {
            return state;
        }

        Map<String, String> questions = interviewQuestionCacheService.getSessionInterviewQuestions(sessionId);
        if (questions == null || questions.isEmpty()) {
            interviewQuestionCacheService.loadInterviewQuestionsFromDatabase(sessionId);
            questions = interviewQuestionCacheService.getSessionInterviewQuestions(sessionId);
        }
        if (questions == null || questions.isEmpty()) {
            return null;
        }

        interviewQuestionCacheService.initInterviewFlow(sessionId, questions.size());
        return interviewQuestionCacheService.getInterviewFlow(sessionId);
    }

    private void fillNextQuestionFromFlow(String sessionId, InterviewAnswerRespDTO response) {
        InterviewFlowState state = interviewQuestionCacheService.getInterviewFlow(sessionId);
        if (state == null || state.isCompleted()) {
            response.finish();
            return;
        }

        String questionNumber = String.valueOf((state.getCurrentIndex() == null ? 0 : state.getCurrentIndex()) + 1);
        String nextQuestion = interviewQuestionCacheService.getQuestionByNumber(sessionId, questionNumber);
        if (StrUtil.isBlank(nextQuestion)) {
            interviewQuestionCacheService.loadInterviewQuestionsFromDatabase(sessionId);
            nextQuestion = interviewQuestionCacheService.getQuestionByNumber(sessionId, questionNumber);
        }
        if (StrUtil.isBlank(nextQuestion)) {
            response.fail("question does not exist or expired");
            return;
        }

        response.withNextQuestion(questionNumber, nextQuestion, false, 0);
    }

    private void fillCurrentQuestionResponse(
            String sessionId,
            InterviewAnswerRespDTO response,
            String questionNumber,
            String questionContent) {
        response.withCurrentQuestion(questionNumber, questionContent);
        response.withNextQuestion(questionNumber, questionContent, false, 0);
        response.setTotalScore(interviewQuestionCacheService.getSessionTotalScore(sessionId));
    }

    private Map<String, String> getOrLoadQuestions(String sessionId) {
        Map<String, String> questions = interviewQuestionCacheService.getSessionInterviewQuestions(sessionId);
        if (questions == null || questions.isEmpty()) {
            interviewQuestionCacheService.loadInterviewQuestionsFromDatabase(sessionId);
            questions = interviewQuestionCacheService.getSessionInterviewQuestions(sessionId);
        }
        return questions;
    }

    private String resolveQuestionByNumber(String sessionId, String questionNumber, Map<String, String> questions) {
        String normalizedQuestionNumber = normalizeQuestionNumber(questionNumber);
        if (StrUtil.isBlank(normalizedQuestionNumber)) {
            return null;
        }

        String questionContent = questions == null ? null : questions.get(normalizedQuestionNumber);
        if (StrUtil.isBlank(questionContent)) {
            questionContent = interviewQuestionCacheService.getQuestionByNumber(sessionId, normalizedQuestionNumber);
        }
        if (StrUtil.isBlank(questionContent) && !normalizedQuestionNumber.equals(questionNumber)) {
            questionContent = questions == null ? null : questions.get(questionNumber);
            if (StrUtil.isBlank(questionContent)) {
                questionContent = interviewQuestionCacheService.getQuestionByNumber(sessionId, questionNumber);
            }
        }
        return questionContent;
    }

    private CurrentQuestionState recoverCurrentQuestionFromTurns(String sessionId, Map<String, String> questions) {
        List<InterviewTurnLog> turns = interviewQuestionCacheService.getInterviewTurns(sessionId);
        if (turns == null || turns.isEmpty()) {
            return CurrentQuestionState.empty();
        }

        InterviewTurnLog latestTurn = turns.get(turns.size() - 1);
        if (latestTurn == null) {
            return CurrentQuestionState.empty();
        }
        if (Boolean.TRUE.equals(latestTurn.getFinished())) {
            return CurrentQuestionState.finished();
        }

        String nextQuestionNumber = normalizeQuestionNumber(latestTurn.getNextQuestionNumber());
        String nextQuestionContent = resolveQuestionByNumber(sessionId, nextQuestionNumber, questions);
        if (StrUtil.isNotBlank(nextQuestionNumber) && StrUtil.isNotBlank(nextQuestionContent)) {
            return CurrentQuestionState.of(nextQuestionNumber, nextQuestionContent);
        }

        String nextQuestionFromTurn = latestTurn.getNextQuestion();
        if (StrUtil.isNotBlank(nextQuestionFromTurn)) {
            String matchedQuestionNumber = matchQuestionNumberByContent(nextQuestionFromTurn, questions);
            if (StrUtil.isNotBlank(matchedQuestionNumber)) {
                String matchedQuestionContent = resolveQuestionByNumber(sessionId, matchedQuestionNumber, questions);
                if (StrUtil.isNotBlank(matchedQuestionContent)) {
                    return CurrentQuestionState.of(matchedQuestionNumber, matchedQuestionContent);
                }
            }
        }

        Integer answeredQuestionNo = parsePositiveInt(normalizeQuestionNumber(latestTurn.getQuestionNumber()));
        if (answeredQuestionNo != null) {
            int candidateQuestionNo = answeredQuestionNo + 1;
            if (questions != null && !questions.isEmpty() && candidateQuestionNo <= questions.size()) {
                String candidateQuestionNumber = String.valueOf(candidateQuestionNo);
                String candidateQuestionContent = resolveQuestionByNumber(sessionId, candidateQuestionNumber, questions);
                if (StrUtil.isNotBlank(candidateQuestionContent)) {
                    return CurrentQuestionState.of(candidateQuestionNumber, candidateQuestionContent);
                }
            }
        }

        return CurrentQuestionState.empty();
    }

    private String matchQuestionNumberByContent(String questionContent, Map<String, String> questions) {
        if (StrUtil.isBlank(questionContent) || questions == null || questions.isEmpty()) {
            return null;
        }
        String target = questionContent.trim();
        for (Map.Entry<String, String> entry : questions.entrySet()) {
            if (entry == null || StrUtil.isBlank(entry.getValue())) {
                continue;
            }
            if (target.equals(entry.getValue().trim())) {
                return normalizeQuestionNumber(entry.getKey());
            }
        }
        return null;
    }

    private Integer parsePositiveInt(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeQuestionNumber(String questionNumber) {
        if (StrUtil.isBlank(questionNumber)) {
            return null;
        }
        String normalized = questionNumber.trim();
        if (normalized.matches("\\d+")) {
            try {
                return String.valueOf(Integer.parseInt(normalized));
            } catch (Exception ex) {
                return normalized;
            }
        }
        return normalized;
    }

    private void appendInterviewTurn(
            String sessionId,
            String requestId,
            String questionNumber,
            String questionContent,
            String answerContent,
            Integer score,
            Integer totalScore,
            InterviewAnswerRespDTO response) {
        try {
            InterviewTurnLog turn = InterviewTurnLog.builder()
                    .timestamp(System.currentTimeMillis())
                    .requestId(requestId)
                    .questionNumber(questionNumber)
                    .questionContent(questionContent)
                    .answerContent(truncateForLog(answerContent, 1000))
                    .score(score)
                    .totalScore(totalScore)
                    .feedback(response.getFeedback())
                    .followUpNeeded(false)
                    .isFollowUp(false)
                    .followUpCount(0)
                    .nextQuestionNumber(response.getNextQuestionNumber())
                    .nextQuestion(response.getNextQuestion())
                    .finished(response.getFinished())
                    .build();
            interviewQuestionCacheService.appendInterviewTurn(sessionId, turn);
        } catch (Exception ex) {
            log.warn("Failed to append interview turn, sessionId: {}", sessionId, ex);
        }
    }

    private String truncateForLog(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static final class CurrentQuestionState {
        private final boolean finished;
        private final String questionNumber;
        private final String questionContent;

        private CurrentQuestionState(boolean finished, String questionNumber, String questionContent) {
            this.finished = finished;
            this.questionNumber = questionNumber;
            this.questionContent = questionContent;
        }

        private static CurrentQuestionState finished() {
            return new CurrentQuestionState(true, null, null);
        }

        private static CurrentQuestionState of(String questionNumber, String questionContent) {
            return new CurrentQuestionState(false, questionNumber, questionContent);
        }

        private static CurrentQuestionState empty() {
            return new CurrentQuestionState(false, null, null);
        }

        private boolean hasQuestion() {
            return StrUtil.isNotBlank(questionNumber) && StrUtil.isNotBlank(questionContent);
        }
    }
}
