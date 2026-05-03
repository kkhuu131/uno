package com.kkhuu131.uno.backend.web;

import com.kkhuu131.uno.backend.web.dto.CardView;
import com.kkhuu131.uno.backend.web.dto.GameSnapshotResponse;
import com.kkhuu131.uno.backend.web.dto.PlayerStateView;
import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.Color;
import com.kkhuu131.uno.model.GameState;
import com.kkhuu131.uno.model.Player;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps {@link GameState} to API snapshots; keeps {@link GameController} thin and this logic unit-testable. */
@Component
public class GameSnapshotMapper {

	public GameSnapshotResponse toSnapshot(String gameId, GameState state) {
		Color active = state.getActiveColor();
		String activeColorName = active != null ? active.name() : null;
		List<Player> playerList = state.getPlayers();
		List<PlayerStateView> players =
				playerList.stream()
						.map(
								p -> new PlayerStateView(
										p.getName(),
										p.getHand().stream().map(CardView::from).toList()))
						.toList();
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
				state.hasPendingDrawStack()
		);
	}
}
