package com.kkhuu131.uno.model;

import java.util.Objects;

public abstract class Card {
    private final Color color;

    protected Card(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    /**
     * @param topCard    the current top card on the discard pile
     * @param activeColor the color that must be matched when the pile shows a wild (declared color)
     */
    public abstract boolean canBePlayedOn(Card topCard, Color activeColor);

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    protected static boolean colorsEqual(Color a, Color b) {
        return Objects.equals(a, b);
    }
}
