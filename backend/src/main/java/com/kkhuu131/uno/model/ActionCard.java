package com.kkhuu131.uno.model;

import java.util.Objects;

public final class ActionCard extends Card {
    private final ActionType action;

    public ActionCard(Color color, ActionType action) {
        super(color);
        this.action = Objects.requireNonNull(action, "action");
    }

    public ActionType getAction() {
        return action;
    }

    @Override
    public boolean canBePlayedOn(Card topCard, Color activeColor) {
        boolean colorMatches = colorsEqual(getColor(), activeColor);
        boolean actionMatches = topCard instanceof ActionCard ac && ac.getAction() == action;
        return colorMatches || actionMatches;
    }

    @Override
    public void applyEffect(GameState gameState) {
        switch (action) {
            case DRAW_TWO:
                gameState.drawCardsForNextPlayer(2);
                break;
            case SKIP:
                gameState.skipTurn();
                break;
            case REVERSE:
                gameState.reverseDirection();
                break;
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
        ActionCard that = (ActionCard) o;
        return action == that.action && colorsEqual(getColor(), that.getColor());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getColor(), action);
    }

    @Override
    public String toString() {
        return "ActionCard{" + getColor() + " " + action + "}";
    }
}
