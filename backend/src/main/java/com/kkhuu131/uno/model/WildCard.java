package com.kkhuu131.uno.model;

import java.util.Objects;

public final class WildCard extends Card {
    private final WildType type;

    public WildCard(WildType type) {
        super(null);
        this.type = Objects.requireNonNull(type, "type");
    }

    public WildType getType() {
        return type;
    }

    @Override
    public boolean canBePlayedOn(Card topCard, Color activeColor) {
        return true;
    }

    @Override
    public void applyEffect(GameState gameState) {
        switch (type) {
            case WILD:
                // No effect, color already set in GameState
                break;
            case WILD_DRAW_FOUR:
                gameState.drawCardsForNextPlayer(4);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WildCard wildCard = (WildCard) o;
        return type == wildCard.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public String toString() {
        return "WildCard{" + type + "}";
    }
}
