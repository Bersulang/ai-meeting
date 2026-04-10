# Backend Architecture Hardening - Phase 1 PRD

## 1. Document Info
- Version: v1.0
- Date: 2026-03-21
- Scope: `virtual-character-backend` backend (`admin` module)
- Target timeline: 7 working days

## 2. Background
The system is functional, but architecture-level risks are still high:
- Session resources are partially accessed by `sessionId` without strict owner checks.
- WebSocket audio transcription can be impersonated by path `userId`.
- Identity context is split across two models, with legacy header-based user injection.
- Sensitive keys exist in config defaults and plain text.
- Concurrency model is inconsistent (ad hoc executors, non-atomic message sequence allocation).

## 3. Phase 1 Goals
- Close all P0 security gaps: owner checks, WebSocket auth hardening, secret governance.
- Standardize identity path to Sa-Token + `@CurrentUser`.
- Stabilize concurrency in core chat/interview flows.
- Add a minimal regression safety net for security-critical paths.

## 4. Non-goals
- No full domain redesign in this phase.
- No new infrastructure (for example, MQ migration).
- No full API rewrite; only high-risk path hardening with compatibility kept.

## 5. In Scope
- Agent conversation/message flows
- Interview conversation/record flows
- AI conversation/message flows
- WebSocket audio transcription flow
- User auth and context propagation flow
- Config and secret loading flow

## 6. Work Breakdown

### 6.1 PR-00 Build and baseline
- Priority: P0
- ETA: 0.5 day
- Tasks:
  - Fix local compile blocker (`Failed setting boot class path`).
  - Align JDK 17 and Maven compile settings.
  - Add a minimal CI job (`compile + test`).
- Deliverables:
  - Build runbook update
  - CI baseline pipeline

### 6.2 PR-01 Owner check coverage for Agent and Interview
- Priority: P0
- ETA: 1.5 days
- Tasks:
  - Enforce owner validation for all session-based read/write operations.
  - Disallow service calls that operate by raw `sessionId` only.
  - Route all checks through `ConversationAccessGuard.requireOwnedConversation(...)`.
- Deliverables:
  - Updated controllers/services with `@CurrentUser` userId flow
  - Negative tests for forged `sessionId`

### 6.3 PR-02 Owner check coverage for AI module
- Priority: P0
- ETA: 1 day
- Tasks:
  - Add owner validation to AI conversation and message history operations.
  - Remove access paths that allow cross-user session access after login.
- Deliverables:
  - Hardened AI controller/service paths
  - Regression tests for unauthorized session access

### 6.4 PR-03 WebSocket auth hardening
- Priority: P0
- ETA: 1.5 days
- Tasks:
  - Require valid token on WebSocket connect.
  - Validate token user and path `userId` consistency.
  - Reject unauthenticated or mismatched connections.
  - Remove unnecessary auth bypass rules.
- Deliverables:
  - Hardened WebSocket handshake validation
  - Security tests for no-token, expired-token, mismatched-userId

### 6.5 PR-04 Identity context unification
- Priority: P0
- ETA: 1 day
- Tasks:
  - Decommission header-based identity as auth source.
  - Standardize on Sa-Token + `@CurrentUser UserContext`.
  - Remove business auth coupling to legacy context model.
- Deliverables:
  - Unified identity path
  - Cleaned context usage in filters/services

### 6.6 PR-05 Secret and config governance
- Priority: P0
- ETA: 0.5 day
- Tasks:
  - Remove plain text secrets and production-usable defaults.
  - Use env-based injection only.
  - Add startup validation for required secret fields.
- Deliverables:
  - Cleaned config files
  - Secret validation policy

### 6.7 PR-06 Concurrency and executor consolidation
- Priority: P1
- ETA: 0.5 day
- Tasks:
  - Remove ad hoc `new SimpleAsyncTaskExecutor()` in business classes.
  - Use shared configured executors only.
  - Change message sequence allocation to atomic strategy.
- Deliverables:
  - Stable async execution path
  - No duplicate or conflicting message sequence in concurrent requests

### 6.8 PR-07 Tests and rollout plan
- Priority: P1
- ETA: 1.5 days
- Tasks:
  - Add regression tests for session auth and WebSocket auth.
  - Provide rollout and rollback runbook.
- Test suites:
  - Session ownership authorization tests
  - WebSocket auth tests
  - Interview session data authorization tests

## 7. Milestones
- M1 (Day 1-2): PR-00, PR-01
- M2 (Day 3-4): PR-02, PR-03
- M3 (Day 5): PR-04, PR-05
- M4 (Day 6-7): PR-06, PR-07, full regression, rollout notes

## 8. Definition of Done
- Security:
  - Non-owner access to session resources is blocked.
  - WebSocket unauthorized connections are blocked.
- Configuration:
  - No production-usable plain text secrets in repo.
- Stability:
  - No message sequence collision under concurrent load.
  - No ad hoc executor creation in business flows.
- Quality:
  - New security regression tests pass.
  - CI compile and tests pass.

## 9. Risks and rollback
- Risk 1: Frontend depends on old behavior.
  - Mitigation: rollout in audit mode first, then enforce blocking.
- Risk 2: Historical session data does not satisfy new ownership rules.
  - Mitigation: temporary compatibility repair script and data fix window.
- Risk 3: Harder local debugging after WebSocket auth enforcement.
  - Mitigation: local-profile debug toggle only (disabled by default).

## 10. Phase 2 Preview
- Split oversized orchestration classes by responsibility.
- Standardize error code and request tracing conventions.
- Dependency cleanup (WebMVC/WebFlux boundaries, JSON stack unification, duplicate dependency removal).
