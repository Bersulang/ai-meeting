package com.hewei.hzyjy.xunzhi.interview.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hewei.hzyjy.xunzhi.interview.api.io.req.DemeanorScoreDTO;
import com.hewei.hzyjy.xunzhi.interview.api.io.resp.RadarChartDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hewei.hzyjy.xunzhi.interview.dao.entity.InterviewQuestion;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionCacheService;
import com.hewei.hzyjy.xunzhi.interview.service.InterviewQuestionService;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewFlowState;
import com.hewei.hzyjy.xunzhi.interview.service.model.InterviewTurnLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 面试题缓存服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionCacheServiceImpl implements InterviewQuestionCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewQuestionService interviewQuestionService;
    
    /**
     * 面试题缓存前缀
     */
    private static final String INTERVIEW_QUESTIONS_KEY = "interview:questions:session:";
    
    /**
     * 面试建议缓存前缀
     */
    private static final String INTERVIEW_SUGGESTIONS_KEY = "interview:suggestions:session:";
    
    /**
     * 简历评分缓存前缀
     */
    private static final String RESUME_SCORE_KEY = "interview:resume_score:session:";

    /**
     * 简历结构化上下文缓存前缀。
     */
    private static final String RESUME_CONTEXT_KEY = "interview:resume_context:session:";
    
    /**
     * 神态管理评分缓存前缀
     */
    private static final String DEMEANOR_SCORE_KEY = "interview:demeanor_score:session:";
    
    /**
     * 会话分数缓存前缀
     */
    private static final String SESSION_SCORE_KEY = "interview:score:session:";
    private static final String SESSION_SCORE_SUM_KEY = "interview:score_sum:session:";
    private static final String SESSION_SCORE_COUNT_KEY = "interview:score_count:session:";
    
    /**
     * 面试方向缓存前缀
     */
    private static final String INTERVIEW_DIRECTION_KEY = "interview:direction:session:";

    /**
     * Interview flow state key prefix.
     */
    private static final String INTERVIEW_FLOW_KEY = "interview:flow:session:";

    /**
     * Interview answer request-id key prefix for idempotency.
     */
    private static final String INTERVIEW_ANSWER_REQUEST_KEY = "interview:answer:req:session:";

    /**
     * Interview turns key prefix.
     */
    private static final String INTERVIEW_TURNS_KEY = "interview:turns:session:";

    private static final int MAX_TURN_LOGS = 200;

    private static final String FLOW_STATUS_INIT = "INIT";
    private static final String FLOW_STATUS_ASKING = "ASKING";
    private static final String FLOW_STATUS_FOLLOW_UP = "FOLLOW_UP";
    private static final String FLOW_STATUS_COMPLETED = "COMPLETED";
    private static final double RESUME_WEIGHT = 0.25D;
    private static final double INTERVIEW_WEIGHT = 0.55D;
    private static final double DEMEANOR_WEIGHT = 0.20D;
    private static final double PROFESSIONAL_SKILLS_INTERVIEW_WEIGHT = 0.70D;
    private static final double PROFESSIONAL_SKILLS_RESUME_WEIGHT = 0.30D;
    
    /**
     * 缓存过期时间（小时）
     */
    private static final long CACHE_EXPIRE_HOURS = 24;
    
    @Override
    public void cacheInterviewQuestions(String sessionId, List<String> questions) {
        try {
            String cacheKey = INTERVIEW_QUESTIONS_KEY + sessionId;
            
            // 清除旧的缓存
            stringRedisTemplate.delete(cacheKey);
            
            // 存储新的面试题，使用题号作为field，题目作为value
            Map<String, String> questionMap = new HashMap<>();
            for (int i = 0; i < questions.size(); i++) {
                String questionNumber = String.valueOf(i + 1);
                questionMap.put(questionNumber, questions.get(i));
            }
            
            if (!questionMap.isEmpty()) {
                stringRedisTemplate.opsForHash().putAll(cacheKey, questionMap);
                // 设置过期时间
                stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }
            
            log.info("成功缓存会话 {} 的 {} 道面试题", sessionId, questions.size());
        } catch (Exception e) {
            log.error("缓存面试题失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public void cacheInterviewSuggestions(String sessionId, List<String> suggestions) {
        try {
            String cacheKey = INTERVIEW_SUGGESTIONS_KEY + sessionId;
            
            // 清除旧的缓存
            stringRedisTemplate.delete(cacheKey);
            
            // 存储新的面试建议，使用建议编号作为field，建议内容作为value
            Map<String, String> suggestionMap = new HashMap<>();
            for (int i = 0; i < suggestions.size(); i++) {
                String suggestionNumber = String.valueOf(i + 1);
                suggestionMap.put(suggestionNumber, suggestions.get(i));
            }
            
            if (!suggestionMap.isEmpty()) {
                stringRedisTemplate.opsForHash().putAll(cacheKey, suggestionMap);
                // 设置过期时间
                stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }
            
            log.info("成功缓存会话 {} 的 {} 条面试建议", sessionId, suggestions.size());
        } catch (Exception e) {
            log.error("缓存面试建议失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public void cacheResumeScore(String sessionId, Integer resumeScore) {
        try {
            String cacheKey = RESUME_SCORE_KEY + sessionId;
            // 清除旧的缓存
            stringRedisTemplate.delete(cacheKey);

            stringRedisTemplate.opsForValue().set(cacheKey, resumeScore.toString());
            // 设置过期时间
            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("成功缓存会话 {} 的简历评分: {}", sessionId, resumeScore);
        } catch (Exception e) {
            log.error("缓存简历评分失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public void cacheDemeanorScore(String sessionId, Integer demeanorScore) {
        try {
            String cacheKey = DEMEANOR_SCORE_KEY + sessionId;
            stringRedisTemplate.opsForValue().set(cacheKey, demeanorScore.toString());
            // 设置过期时间
            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("成功缓存会话 {} 的神态管理评分: {}", sessionId, demeanorScore);
        } catch (Exception e) {
            log.error("缓存神态管理评分失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getSessionInterviewQuestions(String sessionId) {
        try {
            String cacheKey = INTERVIEW_QUESTIONS_KEY + sessionId;
            Map<Object, Object> rawMap = stringRedisTemplate.opsForHash().entries(cacheKey);
            
            // 使用LinkedHashMap保持插入顺序，并按题号排序
            Map<String, String> questionMap = new LinkedHashMap<>();
            
            // 将题号转换为整数进行排序
            rawMap.entrySet().stream()
                .sorted((entry1, entry2) -> {
                    try {
                        // 提取题号进行数字排序
                        String key1 = entry1.getKey().toString();
                        String key2 = entry2.getKey().toString();
                        
                        // 如果题号是纯数字，按数字排序
                        if (key1.matches("\\d+") && key2.matches("\\d+")) {
                            return Integer.compare(Integer.parseInt(key1), Integer.parseInt(key2));
                        }
                        // 否则按字符串排序
                        return key1.compareTo(key2);
                    } catch (NumberFormatException e) {
                        // 如果转换失败，按字符串排序
                        return entry1.getKey().toString().compareTo(entry2.getKey().toString());
                    }
                })
                .forEach(entry -> {
                    questionMap.put(entry.getKey().toString(), entry.getValue().toString());
                });
            
            log.info("获取会话 {} 的面试题成功，共 {} 道题，已按题号排序", sessionId, questionMap.size());
            return questionMap;
        } catch (Exception e) {
            log.error("获取会话面试题失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    @Override
    public Integer getSessionResumeScore(String sessionId) {
        try {
            String cacheKey = RESUME_SCORE_KEY + sessionId;
            String scoreStr = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(scoreStr)) {
                return Integer.parseInt(scoreStr);
            }
            return null;
        } catch (Exception e) {
            log.error("获取会话简历评分失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void cacheResumeContext(String sessionId, Map<String, Object> resumeContext) {
        try {
            if (StrUtil.isBlank(sessionId)) {
                return;
            }
            String cacheKey = RESUME_CONTEXT_KEY + sessionId;
            if (resumeContext == null || resumeContext.isEmpty()) {
                stringRedisTemplate.delete(cacheKey);
                return;
            }
            String payload = JSON.toJSONString(resumeContext);
            stringRedisTemplate.opsForValue().set(cacheKey, payload);
            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("缓存简历上下文成功，sessionId: {}, keys: {}", sessionId, resumeContext.keySet());
        } catch (Exception e) {
            log.error("缓存简历上下文失败，sessionId: {}, error: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getSessionResumeContext(String sessionId) {
        try {
            if (StrUtil.isBlank(sessionId)) {
                return new HashMap<>();
            }
            String cacheKey = RESUME_CONTEXT_KEY + sessionId;
            String payload = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isBlank(payload)) {
                return new HashMap<>();
            }
            Map<String, Object> parsed = JSON.parseObject(payload, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed == null ? new HashMap<>() : parsed;
        } catch (Exception e) {
            log.error("获取简历上下文失败，sessionId: {}, error: {}", sessionId, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    @Override
    public Integer getSessionDemeanorScore(String sessionId) {
        try {
            String cacheKey = DEMEANOR_SCORE_KEY + sessionId;
            String scoreStr = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(scoreStr)) {
                return Integer.parseInt(scoreStr);
            }
            return null;
        } catch (Exception e) {
            log.error("获取会话神态管理评分失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public Map<String, String> getSessionInterviewSuggestions(String sessionId) {
        try {
            String cacheKey = INTERVIEW_SUGGESTIONS_KEY + sessionId;
            Map<Object, Object> rawMap = stringRedisTemplate.opsForHash().entries(cacheKey);
            
            // 使用LinkedHashMap保持插入顺序，并按建议编号排序
            Map<String, String> suggestionMap = new LinkedHashMap<>();
            
            // 将建议编号转换为整数进行排序
            rawMap.entrySet().stream()
                .sorted((entry1, entry2) -> {
                    try {
                        // 提取建议编号进行数字排序
                        String key1 = entry1.getKey().toString();
                        String key2 = entry2.getKey().toString();
                        
                        // 如果编号是纯数字，按数字排序
                        if (key1.matches("\\d+") && key2.matches("\\d+")) {
                            return Integer.compare(Integer.parseInt(key1), Integer.parseInt(key2));
                        }
                        // 否则按字符串排序
                        return key1.compareTo(key2);
                    } catch (NumberFormatException e) {
                        // 如果转换失败，按字符串排序
                        return entry1.getKey().toString().compareTo(entry2.getKey().toString());
                    }
                })
                .forEach(entry -> {
                    suggestionMap.put(entry.getKey().toString(), entry.getValue().toString());
                });
            
            log.info("获取会话 {} 的面试建议成功，共 {} 条建议，已按编号排序", sessionId, suggestionMap.size());
            return suggestionMap;
        } catch (Exception e) {
            log.error("获取会话面试建议失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    @Override
    public String getQuestionByNumber(String sessionId, String questionNumber) {
        try {
            String cacheKey = INTERVIEW_QUESTIONS_KEY + sessionId;
            Object question = stringRedisTemplate.opsForHash().get(cacheKey, questionNumber);
            return question != null ? question.toString() : null;
        } catch (Exception e) {
            log.error("获取题目失败，会话ID: {}, 题号: {}, 错误: {}", sessionId, questionNumber, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void clearSessionQuestions(String sessionId) {
        try {
            String cacheKey = INTERVIEW_QUESTIONS_KEY + sessionId;
            stringRedisTemplate.delete(cacheKey);
            log.info("清除会话 {} 的面试题缓存", sessionId);
        } catch (Exception e) {
            log.error("清除面试题缓存失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public void clearSessionSuggestions(String sessionId) {
        try {
            String cacheKey = INTERVIEW_SUGGESTIONS_KEY + sessionId;
            stringRedisTemplate.delete(cacheKey);
            log.info("清除会话 {} 的面试建议缓存", sessionId);
        } catch (Exception e) {
            log.error("清除面试建议缓存失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库加载面试题到缓存
     * 优先使用JSON格式数据，如果不存在则使用List格式数据
     */
    public void loadInterviewQuestionsFromDatabase(String sessionId) {
        try {
            InterviewQuestion question = interviewQuestionService.getBySessionId(sessionId);
            if (question == null) {
                log.warn("未找到会话 {} 的面试题数据", sessionId);
                return;
            }
            
            // 优先使用JSON格式数据
            if (StrUtil.isNotBlank(question.getQuestionsJson())) {
                try {
                    Map<String, String> questionsMap = JSON.parseObject(
                        question.getQuestionsJson(), 
                        new TypeReference<LinkedHashMap<String, String>>() {}
                    );
                    
                    String cacheKey = INTERVIEW_QUESTIONS_KEY + sessionId;
                    stringRedisTemplate.delete(cacheKey);
                    
                    if (!questionsMap.isEmpty()) {
                        stringRedisTemplate.opsForHash().putAll(cacheKey, questionsMap);
                        stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    }
                    
                    log.info("从数据库JSON格式加载面试题到缓存成功，会话ID: {}, 题目数量: {}", sessionId, questionsMap.size());
                    return;
                } catch (Exception e) {
                    log.warn("解析面试题JSON数据失败，尝试使用List格式数据，错误: {}", e.getMessage());
                }
            }
            
            // 如果JSON格式数据不存在或解析失败，使用List格式数据
            if (question.getQuestions() != null && !question.getQuestions().isEmpty()) {
                cacheInterviewQuestions(sessionId, question.getQuestions());
                log.info("从数据库List格式加载面试题到缓存成功，会话ID: {}, 题目数量: {}", sessionId, question.getQuestions().size());
            }
            
        } catch (Exception e) {
            log.error("从数据库加载面试题到缓存失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库加载面试建议到缓存
     * 优先使用JSON格式数据，如果不存在则使用List格式数据
     */
    public void loadInterviewSuggestionsFromDatabase(String sessionId) {
        try {
            InterviewQuestion question = interviewQuestionService.getBySessionId(sessionId);
            if (question == null) {
                log.warn("未找到会话 {} 的面试建议数据", sessionId);
                return;
            }
            
            // 优先使用JSON格式数据
            if (StrUtil.isNotBlank(question.getSuggestionsJson())) {
                try {
                    Map<String, String> suggestionsMap = JSON.parseObject(
                        question.getSuggestionsJson(), 
                        new TypeReference<LinkedHashMap<String, String>>() {}
                    );
                    
                    String cacheKey = INTERVIEW_SUGGESTIONS_KEY + sessionId;
                    stringRedisTemplate.delete(cacheKey);
                    
                    if (!suggestionsMap.isEmpty()) {
                        stringRedisTemplate.opsForHash().putAll(cacheKey, suggestionsMap);
                        stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                    }
                    
                    log.info("从数据库JSON格式加载面试建议到缓存成功，会话ID: {}, 建议数量: {}", sessionId, suggestionsMap.size());
                    return;
                } catch (Exception e) {
                    log.warn("解析面试建议JSON数据失败，尝试使用List格式数据，错误: {}", e.getMessage());
                }
            }
            
            // 如果JSON格式数据不存在或解析失败，使用List格式数据
            if (question.getSuggestions() != null && !question.getSuggestions().isEmpty()) {
                cacheInterviewSuggestions(sessionId, question.getSuggestions());
                log.info("从数据库List格式加载面试建议到缓存成功，会话ID: {}, 建议数量: {}", sessionId, question.getSuggestions().size());
            }
            
        } catch (Exception e) {
            log.error("从数据库加载面试建议到缓存失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    /**
     * 从数据库加载简历评分到缓存
     */
    public void loadResumeScoreFromDatabase(String sessionId) {
        try {
            InterviewQuestion question = interviewQuestionService.getBySessionId(sessionId);
            if (question == null || question.getResumeScore() == null) {
                log.warn("未找到会话 {} 的简历评分数据", sessionId);
                return;
            }
            
            cacheResumeScore(sessionId, question.getResumeScore());
            log.info("从数据库加载简历评分到缓存成功，会话ID: {}, 评分: {}", sessionId, question.getResumeScore());
            
        } catch (Exception e) {
            log.error("从数据库加载简历评分到缓存失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public Integer getSessionTotalScore(String sessionId) {
        try {
            String scoreKey = SESSION_SCORE_KEY + sessionId;
            String score = stringRedisTemplate.opsForValue().get(scoreKey);
            Integer cachedAverage = parseInteger(score);
            if (cachedAverage != null) {
                return clampScore(cachedAverage);
            }

            Integer derivedAverage = calculateAverageInterviewScore(getInterviewTurns(sessionId));
            return derivedAverage != null ? derivedAverage : 0;
        } catch (Exception e) {
            log.error("获取会话总分失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return 0;
        }
    }
    
    @Override
    public Integer addSessionScore(String sessionId, Integer score) {
        try {
            String scoreKey = SESSION_SCORE_KEY + sessionId;
            String scoreSumKey = SESSION_SCORE_SUM_KEY + sessionId;
            String scoreCountKey = SESSION_SCORE_COUNT_KEY + sessionId;
            int safeScore = clampScore(score == null ? 0 : score);
            Long scoreSum = stringRedisTemplate.opsForValue().increment(scoreSumKey, safeScore);
            Long answerCount = stringRedisTemplate.opsForValue().increment(scoreCountKey, 1L);
            int averagedScore = calculateAverageFromAggregate(scoreSum, answerCount);
            stringRedisTemplate.opsForValue().set(scoreKey, String.valueOf(averagedScore));
            // 设置过期时间
            stringRedisTemplate.expire(scoreKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            Integer newScore = averagedScore;
            
            log.info("会话 {} 本次得分: {}, 累计总分: {}", sessionId, score, newScore);
            stringRedisTemplate.expire(scoreSumKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.expire(scoreCountKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            return averagedScore;
        } catch (Exception e) {
            log.error("累加会话分数失败，会话ID: {}, 分数: {}, 错误: {}", sessionId, score, e.getMessage(), e);
            return getSessionTotalScore(sessionId);
        }
    }
    
    @Override
    public void resetSessionScore(String sessionId) {
        try {
            String scoreKey = SESSION_SCORE_KEY + sessionId;
            String scoreSumKey = SESSION_SCORE_SUM_KEY + sessionId;
            String scoreCountKey = SESSION_SCORE_COUNT_KEY + sessionId;
            stringRedisTemplate.delete(List.of(scoreKey, scoreSumKey, scoreCountKey));
            log.info("重置会话 {} 的分数", sessionId);
        } catch (Exception e) {
            log.error("重置会话分数失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public RadarChartDTO getRadarChartData(String sessionId) {
        try {
            if (sessionId == null || sessionId != null) {
                Integer resumeScore = clampNullableScore(getSessionResumeScore(sessionId));
                Integer interviewScore = clampNullableScore(getSessionTotalScore(sessionId));
                Integer demeanorScore = clampNullableScore(getSessionDemeanorScore(sessionId));

                RadarChartDTO radarChart = new RadarChartDTO();
                radarChart.setResumeScore(defaultScore(resumeScore));
                radarChart.setInterviewPerformance(defaultScore(interviewScore));
                radarChart.setDemeanorEvaluation(defaultScore(demeanorScore));
                radarChart.setProfessionalSkills(calculateProfessionalSkills(resumeScore, interviewScore));
                radarChart.setPotentialIndex(calculateWeightedComposite(resumeScore, interviewScore, demeanorScore));
                return radarChart;
            }
            // 从缓存获取三个评分
            Integer resumeScore = getSessionResumeScore(sessionId);
            Integer totalScore = getSessionTotalScore(sessionId);
            // 获取神态管理评分（使用神态评分的综合得分）
            String compositeKey = "demeanor:composite:" + sessionId;
            String compositeScoreStr = stringRedisTemplate.opsForValue().get(compositeKey);
            Integer demeanorScore = StrUtil.isNotBlank(compositeScoreStr) ? Integer.parseInt(compositeScoreStr) : null;
            
            RadarChartDTO radarChart = new RadarChartDTO();
            
            // 直接使用0-100范围的整数
            Integer resumeNormalized = resumeScore != null ? resumeScore : 0;
            Integer interviewNormalized = totalScore != null ? Math.min(totalScore, 100) : 0;
            // 神态评分从十分制转换为百分制（乘以10）
            Integer demeanorNormalized = demeanorScore != null ? Math.min(demeanorScore * 10, 100) : 0;
            
            radarChart.setResumeScore(resumeNormalized);
            radarChart.setInterviewPerformance(interviewNormalized);
            radarChart.setDemeanorEvaluation(demeanorNormalized);
            
            // 生成专业技能评分：基于简历评分上下浮动10分以内的随机数
            Integer professionalSkills = resumeNormalized;
            if (resumeScore != null && resumeScore > 0) {
                // 生成-10到10之间的随机数
                int randomOffset = (int)((Math.random() - 0.5) * 20); // -10 到 10
                professionalSkills = Math.max(0, Math.min(100, resumeNormalized + randomOffset));
            }
            radarChart.setProfessionalSkills(professionalSkills);
            
            // 按权重计算用户潜力指数
            // resume: 0.25, interview: 0.4, demeanor: 0.15, professional: 0.2
            Integer potentialIndex = (int)(resumeNormalized * 0.25 + interviewNormalized * 0.4 + 
                                 demeanorNormalized * 0.15 + professionalSkills * 0.2);
            radarChart.setPotentialIndex(potentialIndex);
            
            log.info("获取会话 {} 雷达图数据成功", sessionId);
            return radarChart;
        } catch (Exception e) {
            log.error("获取会话雷达图数据失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            // 返回默认值
            RadarChartDTO defaultChart = new RadarChartDTO();
            defaultChart.setResumeScore(0);
            defaultChart.setInterviewPerformance(0);
            defaultChart.setDemeanorEvaluation(0);
            defaultChart.setProfessionalSkills(0);
            defaultChart.setPotentialIndex(0);
            return defaultChart;
        }
    }
    
    @Override
    public void cacheDemeanorScoreDetails(String sessionId, Integer panicLevel, Integer seriousnessLevel, 
                                          Integer emoticonHandling, Integer compositeScore) {
        try {
            String panicKey = "demeanor:panic:" + sessionId;
            String seriousnessKey = "demeanor:seriousness:" + sessionId;
            String emoticonKey = "demeanor:emoticon:" + sessionId;
            String compositeKey = "demeanor:composite:" + sessionId;
            
            stringRedisTemplate.opsForValue().set(panicKey, panicLevel.toString());
            stringRedisTemplate.opsForValue().set(seriousnessKey, seriousnessLevel.toString());
            stringRedisTemplate.opsForValue().set(emoticonKey, emoticonHandling.toString());
            stringRedisTemplate.opsForValue().set(compositeKey, compositeScore.toString());
            
            // 设置过期时间
            stringRedisTemplate.expire(panicKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.expire(seriousnessKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.expire(emoticonKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            stringRedisTemplate.expire(compositeKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            log.info("成功缓存会话 {} 的神态评分详细数据", sessionId);
        } catch (Exception e) {
            log.error("缓存神态评分详细数据失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public DemeanorScoreDTO getSessionDemeanorScoreDetails(String sessionId) {
        try {
            if (sessionId == null || sessionId != null) {
                String panicKey = "demeanor:panic:" + sessionId;
                String seriousnessKey = "demeanor:seriousness:" + sessionId;
                String emoticonKey = "demeanor:emoticon:" + sessionId;
                String compositeKey = "demeanor:composite:" + sessionId;

                DemeanorScoreDTO demeanorScoreDTO = new DemeanorScoreDTO();
                demeanorScoreDTO.setPanicLevel(defaultScore(parseInteger(stringRedisTemplate.opsForValue().get(panicKey))));
                demeanorScoreDTO.setSeriousnessLevel(defaultScore(parseInteger(stringRedisTemplate.opsForValue().get(seriousnessKey))));
                demeanorScoreDTO.setEmoticonHandling(defaultScore(parseInteger(stringRedisTemplate.opsForValue().get(emoticonKey))));
                demeanorScoreDTO.setCompositeScore(defaultScore(parseInteger(stringRedisTemplate.opsForValue().get(compositeKey))));
                return demeanorScoreDTO;
            }
            String panicKey = "demeanor:panic:" + sessionId;
            String seriousnessKey = "demeanor:seriousness:" + sessionId;
            String emoticonKey = "demeanor:emoticon:" + sessionId;
            String compositeKey = "demeanor:composite:" + sessionId;
            
            String panicStr = stringRedisTemplate.opsForValue().get(panicKey);
            String seriousnessStr = stringRedisTemplate.opsForValue().get(seriousnessKey);
            String emoticonStr = stringRedisTemplate.opsForValue().get(emoticonKey);
            String compositeStr = stringRedisTemplate.opsForValue().get(compositeKey);
            
            DemeanorScoreDTO demeanorScoreDTO = new DemeanorScoreDTO();
            
            // 神态评分从十分制转换为百分制（乘以10）
            demeanorScoreDTO.setPanicLevel(StrUtil.isNotBlank(panicStr) ? 
                Math.min(Integer.parseInt(panicStr) * 10, 100) : 0);
            demeanorScoreDTO.setSeriousnessLevel(StrUtil.isNotBlank(seriousnessStr) ? 
                Math.min(Integer.parseInt(seriousnessStr) * 10, 100) : 0);
            demeanorScoreDTO.setEmoticonHandling(StrUtil.isNotBlank(emoticonStr) ? 
                Math.min(Integer.parseInt(emoticonStr) * 10, 100) : 0);
            demeanorScoreDTO.setCompositeScore(StrUtil.isNotBlank(compositeStr) ? 
                Math.min(Integer.parseInt(compositeStr) * 10, 100) : 0);
            
            log.info("获取会话 {} 神态评分详细数据成功", sessionId);
            return demeanorScoreDTO;
        } catch (Exception e) {
            log.error("获取会话神态评分详细数据失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            // 返回默认值
            DemeanorScoreDTO defaultScore = new DemeanorScoreDTO();
            defaultScore.setPanicLevel(0);
            defaultScore.setSeriousnessLevel(0);
            defaultScore.setEmoticonHandling(0);
            defaultScore.setCompositeScore(0);
            return defaultScore;
        }
    }
    
    @Override
    public void cacheInterviewDirection(String sessionId, String interviewDirection) {
        try {
            String cacheKey = INTERVIEW_DIRECTION_KEY + sessionId;
            stringRedisTemplate.opsForValue().set(cacheKey, interviewDirection);
            // 设置过期时间
            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.info("成功缓存会话 {} 的面试方向: {}", sessionId, interviewDirection);
        } catch (Exception e) {
            log.error("缓存面试方向失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }
    
    @Override
    public String getSessionInterviewDirection(String sessionId) {
        try {
            String cacheKey = INTERVIEW_DIRECTION_KEY + sessionId;
            String direction = stringRedisTemplate.opsForValue().get(cacheKey);
            log.info("获取会话 {} 的面试方向: {}", sessionId, direction);
            return direction;
        } catch (Exception e) {
            log.error("获取会话面试方向失败，会话ID: {}, 错误: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }
    @Override
    public void initInterviewFlow(String sessionId, Integer totalQuestions) {
        if (StrUtil.isBlank(sessionId) || totalQuestions == null || totalQuestions <= 0) {
            return;
        }
        InterviewFlowState state = new InterviewFlowState();
        state.setStatus(FLOW_STATUS_INIT);
        state.setCurrentIndex(0);
        state.setTotalQuestions(totalQuestions);
        state.setFollowUpCount(0);
        state.setMaxFollowUp(2);
        state.setVersion(1);
        saveFlowState(sessionId, state);
        updateInterviewFlowStatus(sessionId, FLOW_STATUS_ASKING);
    }

    @Override
    public InterviewFlowState getInterviewFlow(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        try {
            String cacheKey = INTERVIEW_FLOW_KEY + sessionId;
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(cacheKey);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            InterviewFlowState state = new InterviewFlowState();
            state.setStatus(asString(entries.get("status"), FLOW_STATUS_INIT));
            state.setCurrentIndex(asInt(entries.get("currentIndex"), 0));
            state.setTotalQuestions(asInt(entries.get("totalQuestions"), 0));
            state.setFollowUpCount(asInt(entries.get("followUpCount"), 0));
            state.setMaxFollowUp(asInt(entries.get("maxFollowUp"), 2));
            state.setVersion(asInt(entries.get("version"), 1));
            return state;
        } catch (Exception e) {
            log.error("Failed to get interview flow state, sessionId: {}", sessionId, e);
            return null;
        }
    }

    @Override
    public void updateInterviewFlowStatus(String sessionId, String status) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(status)) {
            return;
        }
        InterviewFlowState state = getInterviewFlow(sessionId);
        if (state == null) {
            return;
        }
        state.setStatus(status);
        state.setVersion((state.getVersion() == null ? 0 : state.getVersion()) + 1);
        saveFlowState(sessionId, state);
    }

    @Override
    public InterviewFlowState incrementFollowUpCount(String sessionId) {
        InterviewFlowState state = getInterviewFlow(sessionId);
        if (state == null) {
            return null;
        }
        state.setFollowUpCount((state.getFollowUpCount() == null ? 0 : state.getFollowUpCount()) + 1);
        state.setStatus(FLOW_STATUS_FOLLOW_UP);
        state.setVersion((state.getVersion() == null ? 0 : state.getVersion()) + 1);
        saveFlowState(sessionId, state);
        return state;
    }

    @Override
    public InterviewFlowState advanceToNextQuestion(String sessionId) {
        InterviewFlowState state = getInterviewFlow(sessionId);
        if (state == null) {
            return null;
        }

        int currentIndex = state.getCurrentIndex() == null ? 0 : state.getCurrentIndex();
        int totalQuestions = state.getTotalQuestions() == null ? 0 : state.getTotalQuestions();
        int nextIndex = currentIndex + 1;
        state.setFollowUpCount(0);

        if (totalQuestions <= 0 || nextIndex >= totalQuestions) {
            state.setStatus(FLOW_STATUS_COMPLETED);
            state.setCurrentIndex(Math.max(currentIndex, 0));
        } else {
            state.setCurrentIndex(nextIndex);
            state.setStatus(FLOW_STATUS_ASKING);
        }

        state.setVersion((state.getVersion() == null ? 0 : state.getVersion()) + 1);
        saveFlowState(sessionId, state);
        return state;
    }

    @Override
    public InterviewFlowState markInterviewCompleted(String sessionId) {
        InterviewFlowState state = getInterviewFlow(sessionId);
        if (state == null) {
            return null;
        }
        state.setStatus(FLOW_STATUS_COMPLETED);
        state.setVersion((state.getVersion() == null ? 0 : state.getVersion()) + 1);
        saveFlowState(sessionId, state);
        return state;
    }

    @Override
    public boolean markAnswerRequestProcessed(String sessionId, String requestId) {
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(requestId)) {
            return true;
        }
        try {
            String cacheKey = INTERVIEW_ANSWER_REQUEST_KEY + sessionId;
            Long added = stringRedisTemplate.opsForSet().add(cacheKey, requestId);
            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            return added != null && added > 0;
        } catch (Exception e) {
            log.error("Failed to record answer request id, sessionId: {}, requestId: {}", sessionId, requestId, e);
            return true;
        }
    }

    @Override
    public void appendInterviewTurn(String sessionId, InterviewTurnLog turnData) {
        if (StrUtil.isBlank(sessionId) || turnData == null) {
            return;
        }
        try {
            String cacheKey = INTERVIEW_TURNS_KEY + sessionId;
            String payload = JSON.toJSONString(turnData);
            stringRedisTemplate.opsForList().rightPush(cacheKey, payload);

            Long size = stringRedisTemplate.opsForList().size(cacheKey);
            if (size != null && size > MAX_TURN_LOGS) {
                long start = size - MAX_TURN_LOGS;
                stringRedisTemplate.opsForList().trim(cacheKey, start, -1);
            }

            stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to append interview turn, sessionId: {}", sessionId, e);
        }
    }

    @Override
    public List<InterviewTurnLog> getInterviewTurns(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return new ArrayList<>();
        }
        try {
            String cacheKey = INTERVIEW_TURNS_KEY + sessionId;
            List<String> rawTurns = stringRedisTemplate.opsForList().range(cacheKey, 0, -1);
            if (rawTurns == null || rawTurns.isEmpty()) {
                return new ArrayList<>();
            }

            List<InterviewTurnLog> turns = new ArrayList<>();
            for (String rawTurn : rawTurns) {
                if (StrUtil.isBlank(rawTurn)) {
                    continue;
                }
                try {
                    InterviewTurnLog parsed = JSON.parseObject(rawTurn, new TypeReference<InterviewTurnLog>() {
                    });
                    if (parsed != null) {
                        turns.add(parsed);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to parse interview turn item, sessionId: {}", sessionId, ex);
                }
            }
            return turns;
        } catch (Exception e) {
            log.error("Failed to get interview turns, sessionId: {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private Integer clampNullableScore(Integer score) {
        if (score == null) {
            return null;
        }
        return clampScore(score);
    }

    private int defaultScore(Integer score) {
        return score == null ? 0 : clampScore(score);
    }

    private int calculateAverageFromAggregate(Long scoreSum, Long answerCount) {
        if (scoreSum == null || answerCount == null || answerCount <= 0) {
            return 0;
        }
        return clampScore((int) Math.round((double) scoreSum / answerCount));
    }

    private Integer calculateAverageInterviewScore(List<InterviewTurnLog> turns) {
        if (turns == null || turns.isEmpty()) {
            return null;
        }

        int scoreSum = 0;
        int scoredTurns = 0;
        for (InterviewTurnLog turn : turns) {
            if (turn == null || turn.getScore() == null) {
                continue;
            }
            scoreSum += clampScore(turn.getScore());
            scoredTurns++;
        }

        if (scoredTurns <= 0) {
            return null;
        }
        return clampScore((int) Math.round((double) scoreSum / scoredTurns));
    }

    private int calculateProfessionalSkills(Integer resumeScore, Integer interviewScore) {
        double weightedScore = 0D;
        double totalWeight = 0D;
        if (resumeScore != null) {
            weightedScore += clampScore(resumeScore) * PROFESSIONAL_SKILLS_RESUME_WEIGHT;
            totalWeight += PROFESSIONAL_SKILLS_RESUME_WEIGHT;
        }
        if (interviewScore != null) {
            weightedScore += clampScore(interviewScore) * PROFESSIONAL_SKILLS_INTERVIEW_WEIGHT;
            totalWeight += PROFESSIONAL_SKILLS_INTERVIEW_WEIGHT;
        }
        if (totalWeight <= 0D) {
            return 0;
        }
        return clampScore((int) Math.round(weightedScore / totalWeight));
    }

    private int calculateWeightedComposite(Integer resumeScore, Integer interviewScore, Integer demeanorScore) {
        double weightedScore = 0D;
        double totalWeight = 0D;
        if (resumeScore != null) {
            weightedScore += clampScore(resumeScore) * RESUME_WEIGHT;
            totalWeight += RESUME_WEIGHT;
        }
        if (interviewScore != null) {
            weightedScore += clampScore(interviewScore) * INTERVIEW_WEIGHT;
            totalWeight += INTERVIEW_WEIGHT;
        }
        if (demeanorScore != null) {
            weightedScore += clampScore(demeanorScore) * DEMEANOR_WEIGHT;
            totalWeight += DEMEANOR_WEIGHT;
        }
        if (totalWeight <= 0D) {
            return 0;
        }
        return clampScore((int) Math.round(weightedScore / totalWeight));
    }

    private void saveFlowState(String sessionId, InterviewFlowState state) {
        String cacheKey = INTERVIEW_FLOW_KEY + sessionId;
        Map<String, String> payload = new HashMap<>();
        payload.put("status", asString(state.getStatus(), FLOW_STATUS_INIT));
        payload.put("currentIndex", String.valueOf(state.getCurrentIndex() == null ? 0 : state.getCurrentIndex()));
        payload.put("totalQuestions", String.valueOf(state.getTotalQuestions() == null ? 0 : state.getTotalQuestions()));
        payload.put("followUpCount", String.valueOf(state.getFollowUpCount() == null ? 0 : state.getFollowUpCount()));
        payload.put("maxFollowUp", String.valueOf(state.getMaxFollowUp() == null ? 2 : state.getMaxFollowUp()));
        payload.put("version", String.valueOf(state.getVersion() == null ? 1 : state.getVersion()));
        stringRedisTemplate.opsForHash().putAll(cacheKey, payload);
        stringRedisTemplate.expire(cacheKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private Integer asInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString();
        return StrUtil.isBlank(str) ? defaultValue : str;
    }
}
