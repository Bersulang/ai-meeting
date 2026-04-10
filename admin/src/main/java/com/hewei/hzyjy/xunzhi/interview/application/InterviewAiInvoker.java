package com.hewei.hzyjy.xunzhi.interview.application;

import cn.hutool.core.util.StrUtil;
import com.hewei.hzyjy.xunzhi.agent.dao.entity.AgentPropertiesDO;
import com.hewei.hzyjy.xunzhi.toolkit.xunfei.XingChenAIClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
/**
 * 面试 AI 调用器：
 * 对 XingChen 的 chat 调用做统一封装，业务层只关心传参与结果。
*/
public class InterviewAiInvoker {

    private final XingChenAIClient xingChenAIClient;

    public String callAiSync(String prompt, String sessionId, AgentPropertiesDO agentProperties) throws Exception {
        // 纯文本同步调用（无附件、无额外参数）。
        return doChat(prompt, sessionId, agentProperties, null, null);
    }

    // 带附件 URL 的同步调用（例如简历、图片）。
    public String callAiSyncWithFile(
            String prompt,
            String sessionId,
            AgentPropertiesDO agentProperties,
            String fileUrl) throws Exception {
        return doChat(prompt, sessionId, agentProperties, fileUrl, null);
    }

    // 带 parameters 的同步调用，常用于工作流节点变量透传。
    public String callAiSyncWithParameters(
            String sessionId,
            AgentPropertiesDO agentProperties,
            Map<String, Object> parameters) throws Exception {
        Object rawInput = parameters == null ? null : parameters.get("AGENT_USER_INPUT");
        String input = rawInput == null ? "" : rawInput.toString().trim();
        return doChat(StrUtil.blankToDefault(input, ""), sessionId, agentProperties, null, parameters);
    }

    private String doChat(
            String input,
            String sessionId,
            AgentPropertiesDO agentProperties,
            String fileUrl,
            Map<String, Object> parameters) throws Exception {
        // 通过 outputStream 收集完整返回文本（非流式）。
        StringBuilder aiResponse = new StringBuilder();
        xingChenAIClient.chat(
                input,
                StrUtil.isNotBlank(sessionId) ? sessionId : "evaluation_" + System.currentTimeMillis(),
                "{}",
                false,
                new OutputStream() {
                    @Override
                    public void write(int b) {
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        aiResponse.append(new String(b, off, len, StandardCharsets.UTF_8));
                    }
                },
                data -> {
                },
                agentProperties.getApiKey(),
                agentProperties.getApiSecret(),
                agentProperties.getApiFlowId(),
                fileUrl,
                parameters
        );
        return aiResponse.toString();
    }
}
