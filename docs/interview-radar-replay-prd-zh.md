# 面试报告雷达图与回放 PRD（后端）

更新时间：2026-04-08

## 1. 背景与问题
- 报告页顶部分数可展示，但“能力雷达图维度数据”和“面试问答回放”在前端经常显示为空。
- 当前后端已有评分、建议、会话快照能力，但对前端是“半结构化”输出，接入成本高、容错差。

## 2. 现状评估（基于当前代码）
### 2.1 已有能力
- 已有雷达图接口：`GET /api/xunzhi/v1/interview/sessions/{sessionId}/radar-chart`
- 已有面试记录落库：`POST /api/xunzhi/v1/interview/interview/record/save-from-redis/{sessionId}`
- 已有回放原始数据来源：`InterviewTurnLog` 写入 Redis，并在保存记录时写入 `session_snapshot_json.turns`

### 2.2 缺口
- `GET /interview/record/{sessionId}` 仅返回 `sessionSnapshotJson` 字符串，缺少可直接渲染的结构化字段。
- 前端若不自行解析快照 JSON，就无法稳定拿到雷达维度和回放列表。
- 文档与当前后端行为有偏差（仍描述旧的语音/追问参数）。

## 3. 目标
1. 前端无需解析快照字符串即可直接拿到：
- 雷达图聚合对象
- 雷达图维度列表
- 回放条目列表
2. 兼容旧前端：保留原字段，不破坏已上线接口。
3. 为“面试回访”提供可持续扩展的数据基础（记录级详情、回放导出、二次训练入口）。

## 4. 范围
### 4.1 本期（P0）
- 增强 `GET /interview/record/{sessionId}` 响应结构
- 回放项增加评分反馈字段
- 新增后端文档说明

### 4.2 下期（P1/P2）
- 回访列表筛选增强（按岗位、分数区间、时间范围）
- 报告导出（JSON/PDF）接口
- 回访任务状态（待复盘/已复盘）与标签能力

## 5. 接口设计（本期）
接口：`GET /api/xunzhi/v1/interview/interview/record/{sessionId}`

在原有 `InterviewRecordRespDTO` 基础上新增：
- `radarChart`
- `radarDimensions: Array<{ key, label, score }>`
- `playbackItems: Array<{ seq, timestamp, requestId, questionNumber, questionContent, answerContent, score, feedback, totalScore, nextQuestionNumber, nextQuestion, finished }>`

### 数据来源优先级
1. `sessionSnapshotJson`（历史稳定快照）
2. Redis 当前缓存（兜底）

## 6. 验收标准
1. 有记录的会话，`record/{sessionId}` 返回 `radarDimensions` 非空（5个维度）。
2. 完成过答题的会话，`playbackItems` 非空，顺序与答题轮次一致。
3. 老字段（`sessionSnapshotJson`、`interviewSuggestionsMap` 等）保持不变。
4. 空数据场景返回空数组而非报错。

## 7. 实施计划
### Step 1（已实现）
- 新增 DTO：
  - `RadarDimensionItemRespDTO`
  - `InterviewPlaybackItemRespDTO`
- 扩展 `InterviewRecordRespDTO` 结构化字段
- `InterviewRecordServiceImpl#getBySessionId` 增加报告字段组装逻辑
- `InterviewTurnLog` 增加 `feedback` 字段，答题编排写入回放

### Step 2（建议下一步）
- 更新前端对接：优先读取 `radarDimensions` 和 `playbackItems`
- 若为空再降级读取 `sessionSnapshotJson`

### Step 3（建议下一步）
- 增加导出接口（按 `sessionId` 导出报告）
- 增加“回访状态”字段与更新接口

## 8. 风险与回滚
- 风险：旧记录可能无 `turns` 或 `radar` 快照
- 处理：本期实现已提供 Redis 兜底与空数组兜底
- 回滚：仅删除新增字段赋值逻辑即可，不影响旧接口主流程
