# 后端接口总览

## 基础信息

- 服务基址：`http://localhost:8002`
- 结果包装：统一返回 `Result<T>`
- 鉴权方式：`Authorization: Bearer <token>`
- 健康检查：`GET /actuator/health`

## 统一响应格式

成功：

```json
{
  "code": "0",
  "message": "success",
  "data": {},
  "success": true
}
```

失败：

```json
{
  "code": "A000001",
  "message": "参数错误",
  "data": null,
  "success": false
}
```

## 核心接口分组

### 1. AI 会话

- `POST /api/xunzhi/v1/ai/sessions/{sessionId}/chat`
- `GET /api/xunzhi/v1/ai/history/{sessionId}`
- `GET /api/xunzhi/v1/ai/history/page`

特点：

- 使用 SSE 返回流式文本
- 适合通用对话和多模型接入

### 2. 智能体会话

- `POST /api/xunzhi/v1/agents/sessions`
- `POST /api/xunzhi/v1/agents/sessions/{sessionId}/chat`
- `GET /api/xunzhi/v1/agents/conversations`
- `GET /api/xunzhi/v1/agents/conversations/{sessionId}/messages`

### 3. 面试编排

- `POST /api/xunzhi/v1/interview/sessions/{sessionId}/interview-questions`
- `POST /api/xunzhi/v1/interview/sessions/{sessionId}/interview/answer-json`
- `GET /api/xunzhi/v1/interview/sessions/{sessionId}/restore`
- `GET /api/xunzhi/v1/interview/sessions/{sessionId}/radar-chart`
- `POST /api/xunzhi/v1/interview/interview/record`

### 4. 文件上传与简历预览

- `POST /api/xunzhi/v1/agents/files/upload`
- `GET /api/xunzhi/v1/interview/sessions/{sessionId}/resume/preview`

### 5. 语音与 WebSocket

- `POST /api/xunzhi/v1/xunfei/tts/tasks`
- `GET /api/xunzhi/v1/xunfei/tts/tasks/{taskId}`
- `POST /api/xunzhi/v1/xunfei/tts/synthesize`
- `POST /api/xunzhi/v1/websocket/send-message`

## 典型调用示例

### AI 流式聊天

```bash
curl -N -X POST "http://localhost:8002/api/xunzhi/v1/ai/sessions/demo-session/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "inputMessage": "请做一个 30 秒的自我介绍",
    "sessionId": "demo-session"
  }'
```

### 面试答题（JSON）

```bash
curl -X POST "http://localhost:8002/api/xunzhi/v1/interview/sessions/demo-session/interview/answer-json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "questionNumber": "1",
    "answerContent": "我会先说明问题边界，再给出落地方案。"
  }'
```

## 错误处理约定

- Bean Validation 错误会统一映射为客户端错误码
- 空请求体或 JSON 格式错误会返回明确的 4xx 风格业务响应
- 业务异常统一走 `AbstractException` / `ClientException`

## 说明

本文档聚焦展示级接口导览，而不是完整 API 参考。若后续继续扩展开源发布，可在此基础上补充更完整的请求参数表和响应 schema。
