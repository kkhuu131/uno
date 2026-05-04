package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.web.dto.LobbyPlayerView;
import com.kkhuu131.uno.backend.web.dto.LobbySnapshot;
import com.kkhuu131.uno.model.LobbyPlayer;
import com.kkhuu131.uno.model.LobbyState;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LobbySnapshotMapper {

    public LobbySnapshot toSnapshot(LobbyState lobby) {
        List<LobbyPlayerView> playerViews = lobby.getPlayers().stream()
                .map(p -> new LobbyPlayerView(p.playerIndex(), p.displayName()))
                .toList();
        int hostPlayerIndex = lobby.getPlayers().stream()
                .filter(p -> p.sessionId().equals(lobby.getHostSessionId()))
                .findFirst()
                .map(LobbyPlayer::playerIndex)
                .orElse(0);
        return new LobbySnapshot(
                lobby.getCode(),
                hostPlayerIndex,
                playerViews,
                lobby.getStatus().name(),
                lobby.getGameId());
    }
}
