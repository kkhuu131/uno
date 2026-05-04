package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.web.LobbySnapshotMapper;
import com.kkhuu131.uno.model.LobbyState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LobbyBroadcastService {

    private final SimpMessagingTemplate messaging;
    private final LobbySnapshotMapper mapper;

    public LobbyBroadcastService(SimpMessagingTemplate messaging, LobbySnapshotMapper mapper) {
        this.messaging = messaging;
        this.mapper = mapper;
    }

    public void broadcastUpdate(LobbyState lobby) {
        messaging.convertAndSend("/topic/lobbies/" + lobby.getCode(), mapper.toSnapshot(lobby));
    }
}
