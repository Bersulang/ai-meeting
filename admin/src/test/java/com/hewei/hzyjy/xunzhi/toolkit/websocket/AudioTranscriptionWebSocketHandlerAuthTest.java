package com.hewei.hzyjy.xunzhi.toolkit.websocket;

import com.hewei.hzyjy.xunzhi.common.util.SaTokenUtil;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioTranscriptionWebSocketHandlerAuthTest {

    @Mock
    private SaTokenUtil saTokenUtil;

    @Mock
    private Session session;

    private AudioTranscriptionWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AudioTranscriptionWebSocketHandler();
        handler.setSaTokenUtil(saTokenUtil);
    }

    @Test
    void onOpen_ShouldReject_WhenTokenMissing() throws IOException {
        when(session.getId()).thenReturn("ws-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getRequestParameterMap()).thenReturn(Collections.emptyMap());

        handler.onOpen(session, "100");

        ArgumentCaptor<CloseReason> reasonCaptor = ArgumentCaptor.forClass(CloseReason.class);
        verify(session).close(reasonCaptor.capture());
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, reasonCaptor.getValue().getCloseCode());
    }

    @Test
    void onOpen_ShouldReject_WhenTokenInvalid() throws IOException {
        when(session.getId()).thenReturn("ws-2");
        when(session.isOpen()).thenReturn(true);
        when(session.getRequestParameterMap()).thenReturn(Map.of("token", List.of("bad-token")));
        when(saTokenUtil.isValidToken("bad-token")).thenReturn(false);

        handler.onOpen(session, "100");

        ArgumentCaptor<CloseReason> reasonCaptor = ArgumentCaptor.forClass(CloseReason.class);
        verify(session).close(reasonCaptor.capture());
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, reasonCaptor.getValue().getCloseCode());
    }

    @Test
    void onOpen_ShouldReject_WhenPathUserDoesNotMatchTokenUser() throws IOException {
        when(session.getId()).thenReturn("ws-3");
        when(session.isOpen()).thenReturn(true);
        when(session.getRequestParameterMap()).thenReturn(Map.of("token", List.of("valid-token")));
        when(saTokenUtil.isValidToken("valid-token")).thenReturn(true);
        when(saTokenUtil.getUsernameByToken("valid-token")).thenReturn("owner");
        when(saTokenUtil.getUserIdByUsername("owner")).thenReturn(100L);

        handler.onOpen(session, "101");

        ArgumentCaptor<CloseReason> reasonCaptor = ArgumentCaptor.forClass(CloseReason.class);
        verify(session).close(reasonCaptor.capture());
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, reasonCaptor.getValue().getCloseCode());
    }
}
