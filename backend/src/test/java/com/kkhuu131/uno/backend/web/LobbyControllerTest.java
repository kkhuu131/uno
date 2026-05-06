package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.config.CorsProperties;
import com.kkhuu131.uno.backend.exception.ApiExceptionHandler;
import com.kkhuu131.uno.backend.exception.LobbyNotFoundException;
import com.kkhuu131.uno.backend.service.GameSessionService;
import com.kkhuu131.uno.backend.service.LobbyBroadcastService;
import com.kkhuu131.uno.backend.service.LobbySessionService;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.LobbyState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LobbyController.class)
@Import({LobbySnapshotMapper.class, ApiExceptionHandler.class})
@EnableConfigurationProperties(CorsProperties.class)
class LobbyControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean LobbySessionService lobbySessionService;
    @MockitoBean GameSessionService gameSessionService;
    @MockitoBean LobbyBroadcastService broadcastService;

    private LobbyState twoPlayerLobby(String code, String hostId) {
        LobbyState lobby = new LobbyState(code, hostId);
        lobby.addPlayer(new LobbyPlayer(hostId, "Host", 0));
        lobby.addPlayer(new LobbyPlayer("s1", "Guest", 1));
        return lobby;
    }

    @Test
    void leaveLobby_returns204_andBroadcasts_whenLobbyStillExists() throws Exception {
        LobbyState lobby = twoPlayerLobby("UNO-ABCD", "s0");
        doNothing().when(lobbySessionService).leaveLobby(eq("UNO-ABCD"), eq("s0"));
        when(lobbySessionService.findLobby("UNO-ABCD")).thenReturn(Optional.of(lobby));

        mvc.perform(post("/api/lobbies/UNO-ABCD/leave")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNoContent());

        verify(broadcastService).broadcastUpdate(lobby);
    }

    @Test
    void leaveLobby_returns204_noBroadcast_whenLobbyGone() throws Exception {
        doNothing().when(lobbySessionService).leaveLobby(eq("UNO-ABCD"), eq("s0"));
        when(lobbySessionService.findLobby("UNO-ABCD")).thenReturn(Optional.empty());

        mvc.perform(post("/api/lobbies/UNO-ABCD/leave")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNoContent());

        verify(broadcastService, never()).broadcastUpdate(any());
    }

    @Test
    void resetLobby_returns200WithSnapshot_andBroadcasts() throws Exception {
        LobbyState lobby = twoPlayerLobby("UNO-EFGH", "s0");
        when(lobbySessionService.resetLobby(eq("UNO-EFGH"), any(GameSessionService.class)))
                .thenReturn(Optional.of(lobby));

        mvc.perform(post("/api/lobbies/UNO-EFGH/reset")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UNO-EFGH"));

        verify(broadcastService).broadcastUpdate(lobby);
    }

    @Test
    void resetLobby_noOp_returns200WithSnapshot_andNoBroadcast() throws Exception {
        LobbyState lobby = twoPlayerLobby("UNO-EFGH", "s0");
        when(lobbySessionService.resetLobby(eq("UNO-EFGH"), any(GameSessionService.class)))
                .thenReturn(Optional.empty());
        when(lobbySessionService.findLobby("UNO-EFGH")).thenReturn(Optional.of(lobby));

        mvc.perform(post("/api/lobbies/UNO-EFGH/reset")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UNO-EFGH"));

        verify(broadcastService, never()).broadcastUpdate(any());
    }

    @Test
    void resetLobby_notFound_returns404() throws Exception {
        when(lobbySessionService.resetLobby(eq("UNO-XXXX"), any(GameSessionService.class)))
                .thenThrow(new LobbyNotFoundException("UNO-XXXX"));

        mvc.perform(post("/api/lobbies/UNO-XXXX/reset")
                .header("X-Session-Id", "s0"))
                .andExpect(status().isNotFound());
    }
}
