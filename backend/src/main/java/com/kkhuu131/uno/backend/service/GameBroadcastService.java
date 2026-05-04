package com.kkhuu131.uno.backend.service;

import com.kkhuu131.uno.backend.web.GameSnapshotMapper;
import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.PrivateHandUpdate;
import com.kkhuu131.uno.model.GameState;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GameBroadcastService {

    private final SimpMessagingTemplate messaging;
    private final GameSnapshotMapper mapper;

    public GameBroadcastService(SimpMessagingTemplate messaging, GameSnapshotMapper mapper) {
        this.messaging = messaging;
        this.mapper = mapper;
    }

    /**
     * Pushes a public (hand-redacted) snapshot to all subscribers of this game, then pushes
     * each player's private hand to their personal topic.
     */
    public void broadcastUpdate(String gameId, GameState state, Map<String, Integer> sessionMap) {
        messaging.convertAndSend("/topic/games/" + gameId, mapper.toPublicSnapshot(gameId, state));
        sessionMap.forEach((sessionId, playerIndex) -> {
            List<CardView> hand = state.getPlayers().get(playerIndex).getHand()
                    .stream().map(CardView::from).toList();
            messaging.convertAndSend(
                    "/topic/games/" + gameId + "/private/" + sessionId,
                    new PrivateHandUpdate(gameId, playerIndex, hand));
        });
    }
}
