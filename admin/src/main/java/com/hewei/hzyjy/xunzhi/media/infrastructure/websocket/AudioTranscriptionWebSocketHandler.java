package com.hewei.hzyjy.xunzhi.media.infrastructure.websocket;

import com.alibaba.fastjson2.JSON;
import com.hewei.hzyjy.xunzhi.auth.application.WebSocketAuthService;
import com.hewei.hzyjy.xunzhi.media.infrastructure.integration.XunfeiAudioService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-time speech-to-text WebSocket endpoint.
 */
@Slf4j
@Component
@ServerEndpoint(value = "/api/xunzhi/v1/xunfei/audio-to-text/{userId}")
public class AudioTranscriptionWebSocketHandler {

    private static volatile XunfeiAudioService xunfeiAudioService;
    private static volatile WebSocketAuthService webSocketAuthService;
    private static volatile ScheduledExecutorService heartbeatExecutor;

    @Autowired
    public void setXunfeiAudioService(XunfeiAudioService service) {
        AudioTranscriptionWebSocketHandler.xunfeiAudioService = service;
    }

    @Autowired
    public void setWebSocketAuthService(WebSocketAuthService service) {
        AudioTranscriptionWebSocketHandler.webSocketAuthService = service;
    }

    @Autowired
    public void setHeartbeatExecutor(@Qualifier("scheduledExecutorService") ScheduledExecutorService scheduledExecutorService) {
        AudioTranscriptionWebSocketHandler.heartbeatExecutor = scheduledExecutorService;
    }

    private static final ConcurrentMap<String, Session> USER_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> SESSION_USER_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, TranscriptionSessionContext> TRANSCRIPTION_CONTEXTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ScheduledFuture<?>> HEARTBEAT_TASKS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        if (!isAuthorizedUser(session, userId)) {
            log.warn("WebSocket auth failed, userId={}, sessionId={}", userId, session.getId());
            closeSession(session, "Unauthorized websocket connection");
            return;
        }

        String sessionId = session.getId();
        USER_SESSIONS.put(userId, session);
        SESSION_USER_MAP.put(sessionId, userId);
        log.info("WebSocket connected, userId={}, sessionId={}", userId, sessionId);

        sendMessage(session, createResponse("connected", "WebSocket connected", userId));
        startHeartbeat(session);
    }

    private boolean isAuthorizedUser(Session session, String pathUserId) {
        if (webSocketAuthService == null) {
            log.error("WebSocketAuthService is not injected, reject websocket connection");
            return false;
        }
        return webSocketAuthService.isAuthorized(session, pathUserId);
    }

    private void closeSession(Session session, String reason) {
        if (session == null) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
            }
        } catch (IOException ex) {
            log.warn("Failed to close websocket session, sessionId={}", session.getId(), ex);
        }
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        String userId = SESSION_USER_MAP.get(session.getId());
        log.info("Received text message, userId={}, message={}", userId, message);

        try {
            WebSocketMessage wsMessage = JSON.parseObject(message, WebSocketMessage.class);
            handleControlMessage(session, userId, wsMessage);
        } catch (Exception ex) {
            sendMessage(session, createResponse("info", "Received text message: " + message, null));
        }
    }

    @OnMessage
    public void onMessage(Session session, ByteBuffer byteBuffer) {
        String sessionId = session.getId();
        String userId = SESSION_USER_MAP.get(sessionId);
        log.debug("Received audio chunk, userId={}, sessionId={}, bytes={}",
                userId, sessionId, byteBuffer.remaining());

        try {
            byte[] audioData = new byte[byteBuffer.remaining()];
            byteBuffer.get(audioData);

            TranscriptionSessionContext context = TRANSCRIPTION_CONTEXTS.computeIfAbsent(
                    sessionId, ignored -> createAndStartTranscriptionSession(session, userId)
            );
            if (context == null || !context.active.get()) {
                sendMessage(session, createResponse("error", "Transcription session is not ready", null));
                return;
            }

            context.audioOutputStream.write(audioData);
            context.audioOutputStream.flush();
        } catch (Exception ex) {
            log.error("Failed to process audio chunk, userId={}, sessionId={}", userId, sessionId, ex);
            sendMessage(session, createResponse("error", "Failed to process audio chunk: " + ex.getMessage(), null));
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        String sessionId = session.getId();
        String userId = SESSION_USER_MAP.get(sessionId);

        stopTranscriptionSession(sessionId);
        cancelHeartbeat(sessionId);

        if (userId != null) {
            USER_SESSIONS.remove(userId);
            SESSION_USER_MAP.remove(sessionId);
        }
        String reason = closeReason != null ? closeReason.getReasonPhrase() : "unknown";
        log.info("WebSocket closed, userId={}, sessionId={}, reason={}",
                userId, sessionId, reason);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String sessionId = session != null ? session.getId() : null;
        String userId = sessionId != null ? SESSION_USER_MAP.get(sessionId) : null;
        log.error("WebSocket error, userId={}, sessionId={}", userId, sessionId, error);

        if (sessionId != null) {
            stopTranscriptionSession(sessionId);
            cancelHeartbeat(sessionId);
        }
        sendMessage(session, createResponse("error", "WebSocket error: " + error.getMessage(), null));
    }

    private void handleControlMessage(Session session, String userId, WebSocketMessage message) {
        String type = message != null ? message.getType() : null;
        if (type == null) {
            sendMessage(session, createResponse("unknown_command", "Missing command type", null));
            return;
        }

        switch (type) {
            case "ping" -> sendMessage(session, createResponse("pong", "pong", String.valueOf(System.currentTimeMillis())));
            case "start_transcription" -> startTranscriptionSession(session, userId);
            case "stop_transcription" -> {
                stopTranscriptionSession(session.getId());
                sendMessage(session, createResponse("transcription_stopped", "Transcription stopped", null));
            }
            case "get_status" -> sendMessage(session, createResponse("status", "Connection is healthy", userId));
            default -> sendMessage(session, createResponse("unknown_command", "Unknown command: " + type, null));
        }
    }

    private void startHeartbeat(Session session) {
        if (heartbeatExecutor == null) {
            log.warn("scheduledExecutorService is not injected, skip heartbeat, sessionId={}", session.getId());
            return;
        }
        String sessionId = session.getId();
        ScheduledFuture<?> oldTask = HEARTBEAT_TASKS.remove(sessionId);
        if (oldTask != null) {
            oldTask.cancel(true);
        }

        ScheduledFuture<?> task = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                sendMessage(session, createResponse("heartbeat", "heartbeat", String.valueOf(System.currentTimeMillis())));
            }
        }, 30, 30, TimeUnit.SECONDS);
        HEARTBEAT_TASKS.put(sessionId, task);
    }

    private void cancelHeartbeat(String sessionId) {
        ScheduledFuture<?> task = HEARTBEAT_TASKS.remove(sessionId);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void startTranscriptionSession(Session session, String userId) {
        String sessionId = session.getId();
        stopTranscriptionSession(sessionId);

        TranscriptionSessionContext context = createAndStartTranscriptionSession(session, userId);
        if (context != null) {
            TRANSCRIPTION_CONTEXTS.put(sessionId, context);
            sendMessage(session, createResponse("transcription_started", "Transcription started", null));
        } else {
            sendMessage(session, createResponse("error", "Failed to start transcription", null));
        }
    }

    private TranscriptionSessionContext createAndStartTranscriptionSession(Session session, String userId) {
        String sessionId = session.getId();
        try {
            if (xunfeiAudioService == null) {
                log.error("XunfeiAudioService is not injected yet, cannot start transcription. sessionId={}", sessionId);
                return null;
            }
            PipedInputStream audioInputStream = new PipedInputStream(64 * 1024);
            PipedOutputStream audioOutputStream = new PipedOutputStream(audioInputStream);
            AtomicBoolean active = new AtomicBoolean(true);

            CompletableFuture<String> future = xunfeiAudioService.realTimeAudioToText(audioInputStream, partial ->
                    sendMessage(session, createResponse("transcription", "Partial snapshot", partial, true))
            );

            TranscriptionSessionContext context = new TranscriptionSessionContext(audioInputStream, audioOutputStream, active);
            future.whenComplete((finalResult, throwable) -> {
                if (throwable != null && !isExpectedStopException(context, throwable)) {
                    log.error("Transcription failed, userId={}, sessionId={}", userId, sessionId, throwable);
                    sendMessage(session, createResponse("error", "Transcription failed: " + throwable.getMessage(), null));
                } else {
                    log.info("Transcription finished, userId={}, sessionId={}", userId, sessionId);
                    if (finalResult != null) {
                        sendMessage(session, createResponse("final", "Transcription completed", finalResult, true));
                    }
                }
                cleanupTranscriptionContext(sessionId, context);
            });
            return context;
        } catch (Exception ex) {
            log.error("Failed to create transcription session, userId={}, sessionId={}", userId, sessionId, ex);
            return null;
        }
    }

    private void stopTranscriptionSession(String sessionId) {
        TranscriptionSessionContext context = TRANSCRIPTION_CONTEXTS.remove(sessionId);
        if (context == null) {
            return;
        }
        context.active.set(false);
        context.stopRequested.set(true);
        closeQuietly(context.audioOutputStream);
    }

    private void cleanupTranscriptionContext(String sessionId, TranscriptionSessionContext context) {
        TRANSCRIPTION_CONTEXTS.remove(sessionId, context);
        context.active.set(false);
        closeQuietly(context.audioOutputStream);
        closeQuietly(context.audioInputStream);
    }

    private boolean isExpectedStopException(TranscriptionSessionContext context, Throwable throwable) {
        if (!context.stopRequested.get()) {
            return false;
        }
        Throwable cursor = throwable;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (msg != null && (msg.contains("Pipe closed") || msg.contains("Stream closed"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // no-op
        }
    }

    private void sendMessage(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException ex) {
                log.error("Failed to send message, sessionId={}", session.getId(), ex);
            }
        }
    }

    public static void sendMessageToUser(String userId, String type, String message, String data) {
        Session session = USER_SESSIONS.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("User is offline, userId={}", userId);
            return;
        }
        try {
            session.getBasicRemote().sendText(createStaticResponse(type, message, data));
        } catch (IOException ex) {
            log.error("Failed to send message to user, userId={}", userId, ex);
        }
    }

    public static void broadcastMessage(String type, String message, String data) {
        String payload = createStaticResponse(type, message, data);
        USER_SESSIONS.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(payload);
                } catch (IOException ex) {
                    log.error("Broadcast failed, userId={}", userId, ex);
                }
            }
        });
    }

    public static Set<String> getOnlineUsers() {
        return USER_SESSIONS.keySet();
    }

    public static boolean isUserOnline(String userId) {
        Session session = USER_SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    private String createResponse(String type, String message, String data) {
        return createResponse(type, message, data, false);
    }

    private String createResponse(String type, String message, String data, boolean isSnapshot) {
        WebSocketResponse response = new WebSocketResponse();
        response.setType(type);
        response.setMessage(message);
        response.setData(data);
        response.setIsSnapshot(isSnapshot);
        response.setUpdateAction(resolveUpdateAction(type));
        response.setTimestamp(System.currentTimeMillis());
        return JSON.toJSONString(response);
    }

    private static String createStaticResponse(String type, String message, String data) {
        WebSocketResponse response = new WebSocketResponse();
        response.setType(type);
        response.setMessage(message);
        response.setData(data);
        response.setIsSnapshot(false);
        response.setUpdateAction(resolveUpdateAction(type));
        response.setTimestamp(System.currentTimeMillis());
        return JSON.toJSONString(response);
    }

    private static String resolveUpdateAction(String type) {
        if ("transcription".equals(type)) {
            return "replace";
        }
        if ("final".equals(type)) {
            return "archive";
        }
        return "none";
    }

    @Data
    public static class WebSocketResponse {
        private String type;
        private String message;
        private String data;
        private Boolean isSnapshot;
        private String updateAction;
        private Long timestamp;
    }

    @Data
    public static class WebSocketMessage {
        private String type;
    }

    private static class TranscriptionSessionContext {
        private final PipedInputStream audioInputStream;
        private final PipedOutputStream audioOutputStream;
        private final AtomicBoolean active;
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        private TranscriptionSessionContext(PipedInputStream audioInputStream,
                                            PipedOutputStream audioOutputStream,
                                            AtomicBoolean active) {
            this.audioInputStream = audioInputStream;
            this.audioOutputStream = audioOutputStream;
            this.active = active;
        }
    }
}
