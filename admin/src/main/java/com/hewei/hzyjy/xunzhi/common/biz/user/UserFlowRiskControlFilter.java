package com.hewei.hzyjy.xunzhi.common.biz.user;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.common.config.user.UserFlowRiskControlConfiguration;
import com.hewei.hzyjy.xunzhi.common.convention.exception.ClientException;
import com.hewei.hzyjy.xunzhi.common.convention.result.Results;
import com.hewei.hzyjy.xunzhi.common.ratelimit.RequestRateLimitKeyResolver;
import com.hewei.hzyjy.xunzhi.common.ratelimit.RequestRateLimitService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PrintWriter;

import static com.hewei.hzyjy.xunzhi.common.convention.errorcode.BaseErrorCode.FLOW_LIMIT_ERROR;

/**
 * Global request rate-limit filter.
 */
@Slf4j
@RequiredArgsConstructor
public class UserFlowRiskControlFilter implements Filter {

    private final UserFlowRiskControlConfiguration userFlowRiskControlConfiguration;
    private final RequestRateLimitService requestRateLimitService;
    private final RequestRateLimitKeyResolver requestRateLimitKeyResolver;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (shouldSkip(httpRequest.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = requestRateLimitKeyResolver.resolve(httpRequest);
        boolean allowed;
        try {
            allowed = requestRateLimitService.tryAcquire(key);
        } catch (Throwable ex) {
            log.error("Request rate limiting failed, key={}", key, ex);
            writeFailure((HttpServletResponse) response);
            return;
        }

        if (!allowed) {
            writeFailure((HttpServletResponse) response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(String requestUri) {
        if (requestUri == null || userFlowRiskControlConfiguration.getSkipPathPrefixes() == null) {
            return false;
        }
        return userFlowRiskControlConfiguration.getSkipPathPrefixes().stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .anyMatch(requestUri::startsWith);
    }

    private void writeFailure(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(JSON.toJSONString(Results.failure(new ClientException(FLOW_LIMIT_ERROR))));
        }
    }
}
