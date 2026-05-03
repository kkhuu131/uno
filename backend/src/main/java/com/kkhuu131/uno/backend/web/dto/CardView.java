package com.kkhuu131.uno.backend.web.dto;

import com.kkhuu131.uno.model.ActionCard;
import com.kkhuu131.uno.model.Card;
import com.kkhuu131.uno.model.NumberCard;
import com.kkhuu131.uno.model.WildCard;

/**
 * JSON-safe description of a card. Order in {@link PlayerStateView#hand()} matches {@code handIndex} in
 * {@link PlayCardRequest} (same order as {@link com.kkhuu131.uno.model.Player#getHand()}).
 */
public record CardView(
		String kind,
		String color,
		Integer number,
		String action,
		String wildType
) {
	public static CardView from(Card card) {
		return switch (card) {
			case NumberCard nc -> new CardView(
					"NUMBER",
					nc.getColor().name(),
					nc.getNumber(),
					null,
					null
			);
			case ActionCard ac -> new CardView(
					"ACTION",
					ac.getColor().name(),
					null,
					ac.getAction().name(),
					null
			);
			case WildCard wc -> new CardView(
					"WILD",
					null,
					null,
					null,
					wc.getType().name()
			);
			default -> throw new IllegalStateException("Unsupported card type: " + card.getClass());
		};
	}
}
