package com.kkhuu131.uno.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Player {
    private final String name;
    private final List<Card> hand;
    private boolean active;

    public Player(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.hand = new ArrayList<>();
        this.active = true;
    }

    public String getName() {
        return name;
    }

    /**
     * View only: callers cannot add/remove cards without going through the game rules.
     */
    public List<Card> getHand() {
        return Collections.unmodifiableList(hand);
    }

    boolean addToHand(Card card) {
        Objects.requireNonNull(card, "card");
        return hand.add(card);
    }

    boolean removeFromHand(Card card) {
        return hand.remove(card);
    }

    boolean holdsCard(Card card) {
        return hand.contains(card);
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Player player = (Player) o;
        return name.equals(player.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Player{name='" + name + "'}";
    }
}
