package com.kkhuu131.uno.model;

import java.util.Objects;

public final class NumberCard extends Card {
    private final int number;

    public NumberCard(Color color, int number) {
        super(color);
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    @Override
    public boolean canBePlayedOn(Card topCard, Color activeColor) {
        boolean colorMatches = colorsEqual(getColor(), activeColor);
        boolean numberMatches = topCard instanceof NumberCard nc && nc.getNumber() == number;
        return colorMatches || numberMatches;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NumberCard that = (NumberCard) o;
        return number == that.number && colorsEqual(getColor(), that.getColor());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getColor(), number);
    }

    @Override
    public String toString() {
        return "NumberCard{" + getColor() + " " + number + "}";
    }
}
