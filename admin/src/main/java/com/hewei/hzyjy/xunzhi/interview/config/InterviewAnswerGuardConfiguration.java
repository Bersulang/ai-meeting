package com.hewei.hzyjy.xunzhi.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Guard configuration for interview answer concurrency and idempotency.
 */
@Data
@Component
@ConfigurationProperties(prefix = "xunzhi-agent.interview.answer-guard")
public class InterviewAnswerGuardConfiguration {

    /**
     * Lock lease time in seconds for session + question lock.
     */
    private Long lockExpireSeconds = 120L;

    /**
     * Processing marker ttl in seconds.
     */
    private Long processingExpireSeconds = 120L;

    /**
     * Replay result ttl in hours.
     */
    private Long replayExpireHours = 24L;

    /**
     * Lock wait time in milliseconds.
     */
    private Long lockWaitMillis = 0L;
}
