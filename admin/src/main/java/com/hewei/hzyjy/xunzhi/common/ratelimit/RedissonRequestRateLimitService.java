package com.hewei.hzyjy.xunzhi.common.ratelimit;

import com.hewei.hzyjy.xunzhi.common.config.user.UserFlowRiskControlConfiguration;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed request rate limiter backed by Redisson.
 */
@Service
@RequiredArgsConstructor
public class RedissonRequestRateLimitService implements RequestRateLimitService {

    private final RedissonClient redissonClient;
    private final UserFlowRiskControlConfiguration configuration;

    @Override
    public boolean tryAcquire(String key) {
        RRateLimiter limiter = redissonClient.getRateLimiter(resolveKeyPrefix() + ":" + key);
        limiter.trySetRate(
                RateType.OVERALL,
                resolveMaxAccessCount(),
                resolveTimeWindowSeconds(),
                RateIntervalUnit.SECONDS
        );
        limiter.expire(resolveExpirationSeconds(), TimeUnit.SECONDS);
        return limiter.tryAcquire(resolveRequestedTokens());
    }

    private long resolveExpirationSeconds() {
        return Math.max(resolveTimeWindowSeconds() * 2, 60L);
    }

    private long resolveTimeWindowSeconds() {
        return configuration.getTimeWindowSeconds() != null ? configuration.getTimeWindowSeconds() : 1L;
    }

    private long resolveMaxAccessCount() {
        return configuration.getMaxAccessCount() != null ? configuration.getMaxAccessCount() : 20L;
    }

    private long resolveRequestedTokens() {
        return configuration.getRequestedTokens() != null ? configuration.getRequestedTokens() : 1L;
    }

    private String resolveKeyPrefix() {
        String keyPrefix = configuration.getKeyPrefix();
        return keyPrefix == null || keyPrefix.isBlank() ? "xunzhi-agent:rate-limit" : keyPrefix;
    }
}
