package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.GameSnapshotResponse;
import com.kkhuu131.uno.backend.web.dto.PlayerStateView;
import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.Color;
import com.kkhuu131.uno.model.GameState;
import com.kkhuu131.uno.model.Player;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GameSnapshotMapper {

    /** Full snapshot — all hands visible. Used for hot-seat / backward-compatible games. */
    public GameSnapshotResponse toSnapshot(String gameId, GameState state) {
        return buildSnapshot(gameId, state, -2); // -2 = include all hands
    }

    /**
     * Personalized snapshot — {@code callerPlayerIndex}'s hand is populated; all others
     * have {@code hand = null} and only {@code handSize}.
     */
    public GameSnapshotResponse toSnapshot(String gameId, GameState state, int callerPlayerIndex) {
        return buildSnapshot(gameId, state, callerPlayerIndex);
    }

    /** Public snapshot — all hands redacted; only hand sizes are included. */
    public GameSnapshotResponse toPublicSnapshot(String gameId, GameState state) {
        return buildSnapshot(gameId, state, -1); // -1 = no hands
    }

    private GameSnapshotResponse buildSnapshot(String gameId, GameState state, int callerPlayerIndex) {
        Color active = state.getActiveColor();
        String activeColorName = active != null ? active.name() : null;
        List<Player> playerList = state.getPlayers();

        List<PlayerStateView> players = new ArrayList<>();
        for (int i = 0; i < playerList.size(); i++) {
            Player p = playerList.get(i);
            boolean includeHand = callerPlayerIndex == -2 || callerPlayerIndex == i;
            List<CardView> hand = includeHand
                    ? p.getHand().stream().map(CardView::from).toList()
                    : null;
            players.add(new PlayerStateView(p.getName(), hand, p.getHand().size()));
        }

        List<Card> discard = state.getDiscardPile();
        CardView top = discard.isEmpty() ? null : CardView.from(discard.get(discard.size() - 1));

        String status;
        Integer winnerPlayerIndex = null;
        String winnerName = null;
        if (state.hasWinner()) {
            status = "FINISHED";
            Player winner = state.getWinner();
            if (winner != null) {
                winnerName = winner.getName();
                for (int i = 0; i < playerList.size(); i++) {
                    if (playerList.get(i) == winner) {
                        winnerPlayerIndex = i;
                        break;
                    }
                }
            }
        } else {
            status = "IN_PROGRESS";
        }

        return new GameSnapshotResponse(
                gameId,
                state.getCurrentPlayerIndex(),
                activeColorName,
                players,
                top,
                status,
                winnerPlayerIndex,
                winnerName,
                state.hasPendingDrawStack());
    }
}
