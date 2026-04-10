package com.hewei.hzyjy.xunzhi.common.config.user;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Request rate-limit configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "xunzhi-agent.flow-limit")
public class UserFlowRiskControlConfiguration {

    /**
     * Whether request rate limiting is enabled.
     */
    private Boolean enable = true;

    /**
     * Preferred window size in seconds.
     */
    private Long timeWindowSeconds = 1L;

    /**
     * Legacy property kept for compatibility with existing local configs.
     */
    private Long timeWindow;

    /**
     * Max number of permits allowed in the configured window.
     */
    private Long maxAccessCount = 20L;

    /**
     * Number of permits consumed per request.
     */
    private Long requestedTokens = 1L;

    /**
     * Shared Redis key prefix for rate limiters.
     */
    private String keyPrefix = "xunzhi-agent:rate-limit";

    /**
     * Paths that should bypass request rate limiting.
     */
    private List<String> skipPathPrefixes = new ArrayList<>(List.of("/actuator", "/error"));

    public Long getTimeWindowSeconds() {
        return timeWindowSeconds != null ? timeWindowSeconds : (timeWindow != null ? timeWindow : 1L);
    }
}
