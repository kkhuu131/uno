package com.kkhuu131.uno.model;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class GameState {
    private static final int DEFAULT_HAND_SIZE = 7;
    private static final int DEFAULT_PLAYER_COUNT = 2;

    private final List<Card> deck = new LinkedList<>(); // draw pile
    private final List<Card> discardPile = new LinkedList<>(); // played cards pile
    private final List<Player> players = new LinkedList<>();
    private boolean isClockwise = true; // true for clockwise, false for counterclockwise

    private int currentPlayerIndex;
    /** Color players must match when the visible top card is a wild. */
    private Color activeColor;

    private int pendingDraw = 0;
    private DrawType pendingDrawType = null;

    public GameState() {
        currentPlayerIndex = 0;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public List<Card> getDeck() {
        return Collections.unmodifiableList(deck);
    }

    public List<Card> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public Color getActiveColor() {
        return activeColor;
    }

    public void initializeGame() {
        deck.clear();
        discardPile.clear();
        players.clear();
        currentPlayerIndex = 0;

        for (Color color : Color.values()) {
            deck.add(new NumberCard(color, 0));
            for (int i = 1; i <= 9; i++) {
                deck.add(new NumberCard(color, i));
                deck.add(new NumberCard(color, i));
            }
            for (int i = 0; i < 2; i++) {
                deck.add(new ActionCard(color, ActionType.DRAW_TWO));
                deck.add(new ActionCard(color, ActionType.SKIP));
                deck.add(new ActionCard(color, ActionType.REVERSE));
            }
        }

        for (int i = 0; i < 4; i++) {
            deck.add(new WildCard(WildType.WILD));
            deck.add(new WildCard(WildType.WILD_DRAW_FOUR));
        }

        Collections.shuffle(deck);

        for (int i = 0; i < DEFAULT_PLAYER_COUNT; i++) {
            players.add(new Player("Player " + (i + 1)));
            Player p = players.get(i);
            for (int j = 0; j < DEFAULT_HAND_SIZE; j++) {
                p.addToHand(deck.remove(0));
            }
        }

        placeStarterCard();
    }

    private void placeStarterCard() {
        Card starter = deck.remove(0);
        discardPile.add(starter);
        if (starter instanceof WildCard) {
            // House rule until we implement "dealer chooses color" for an opening wild.
            activeColor = Color.RED;
        } else {
            activeColor = starter.getColor();
        }
    }

    /**
     * Draw one card from the deck into the player's hand. Does not advance turn.
     * When a +2/+4 stack is pending for the current player, they must stack or {@link #passTurn(Player)} instead.
     */
    public Card drawCard(Player player) {
        requireCurrentPlayer(player);
        if (pendingDraw > 0) {
            throw new IllegalStateException(
                    "Play a stacking card or pass to take the stacked draw — you cannot draw from the deck now");
        }
        ensureDrawPileHasCards();
        Card drawn = deck.remove(0);
        player.addToHand(drawn);
        return drawn;
    }

    /**
     * True when the current player must resolve a stacked draw (play another +2/+4 or take the cards).
     */
    public boolean hasPendingDrawStack() {
        return pendingDraw > 0;
    }

    /**
     * Pass without playing: if a draw stack is pending, take those cards; then advance to the next player.
     */
    public void passTurn(Player player) {
        requireCurrentPlayer(player);
        if (pendingDraw > 0) {
            resolvePendingDraw(player);
        }
        endTurn();
    }

    public void playCard(Player player, Card card) {
        if (card instanceof WildCard) {
            throw new IllegalArgumentException("Wild cards require a chosen color; use playCard(player, card, color)");
        }
        playCardInternal(player, card, null);
    }

    public void playCard(Player player, Card card, Color chosenColor) {
        Objects.requireNonNull(chosenColor, "chosenColor");
        if (!(card instanceof WildCard)) {
            throw new IllegalArgumentException("chosenColor is only valid when playing a wild card");
        }
        playCardInternal(player, card, chosenColor);
    }

    private void playCardInternal(Player player, Card card, Color wildChoice) {
        requireCurrentPlayer(player);
        Objects.requireNonNull(card, "card");

        if (!player.holdsCard(card)) {
            throw new IllegalArgumentException("Card not in player's hand");
        }

        if (!discardPile.isEmpty() && !isValidPlay(card, topDiscard())) {
            throw new IllegalArgumentException("Invalid card play");
        }

        if (!player.removeFromHand(card)) {
            throw new IllegalStateException("Failed to remove card after presence check");
        }
        discardPile.add(card);

        if (card instanceof WildCard) {
            activeColor = wildChoice;
        } else {
            activeColor = card.getColor();
        }

        card.applyEffect(this);
        if (!hasWinner()) {
            endTurn();
        }
    }

    private void advanceTurn() {
        currentPlayerIndex = isClockwise ? (currentPlayerIndex + 1) % players.size() : (currentPlayerIndex - 1 + players.size()) % players.size(); // wrap around, direction of play
    }

    private void requireCurrentPlayer(Player player) {
        if (player != getCurrentPlayer()) {
            throw new IllegalArgumentException("Player is not the current player");
        }
    }

    private Card topDiscard() {
        return discardPile.get(discardPile.size() - 1);
    }

    private boolean isValidPlay(Card played, Card topCard) {
        if (pendingDraw > 0) {
            if (played instanceof ActionCard ac && ac.getAction() == ActionType.DRAW_TWO) {
                return pendingDrawType == DrawType.DRAW_TWO; // only stack on +2
            }
        
            if (played instanceof WildCard wc && wc.getType() == WildType.WILD_DRAW_FOUR) {
                return true; // +4 can stack on both
            }
        
            return false;
        }
        return played.canBePlayedOn(topCard, activeColor);
    }

    /**
     * When the deck is empty, move all cards from the discard pile into the deck except the top
     * (face-up) card, then shuffle. Standard Uno recycling behavior.
     */
    private void ensureDrawPileHasCards() {
        if (!deck.isEmpty()) {
            return;
        }
        if (discardPile.size() <= 1) {
            throw new IllegalStateException("Cannot draw: deck is empty and there are no cards to recycle");
        }
        Card top = discardPile.remove(discardPile.size() - 1);
        deck.addAll(discardPile);
        discardPile.clear();
        discardPile.add(top);
        Collections.shuffle(deck);
    }

    private Player getNextPlayer() {
        return players.get(isClockwise ? (currentPlayerIndex + 1) % players.size() : (currentPlayerIndex - 1 + players.size()) % players.size());
    }

    public void drawCardsForNextPlayer(int count) {
        Player nextPlayer = getNextPlayer();
        for (int i = 0; i < count; i++) {
            ensureDrawPileHasCards();
            nextPlayer.addToHand(deck.remove(0));
        }
    }

    public void skipTurn() {
        advanceTurn();
    }

    public void reverseDirection() {
        if (players.size() > 2) {
            isClockwise = !isClockwise;
        }
        else {
            skipTurn();
        }
    }

    public boolean isPlayable(Card card) {
        return isValidPlay(card, topDiscard());
    }

    public void endTurn() {
        advanceTurn();
    }

    public boolean canPlayAnyCard(Player player) {
        return player.getHand().stream().anyMatch(card -> isPlayable(card));
    }

    public boolean hasWinner() {
        for (Player player : players) {
            if (player.getHand().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Player getWinner() {
        for (Player player : players) {
            if (player.getHand().isEmpty()) {
                return player;
            }
        }
        return null;
    }

    public void resolvePendingDraw(Player player) {
        for (int i = 0; i < pendingDraw; i++) {
            ensureDrawPileHasCards();
            player.addToHand(deck.remove(0));
        }
        pendingDraw = 0;
        pendingDrawType = null;
    }

    // turn flow
    public void takeTurn(Player player, Card cardToPlay, Color chosenColor) {
        requireCurrentPlayer(player);

        // handle pending draw
        if (pendingDraw > 0) {
            if (cardToPlay != null && isValidPlay(cardToPlay, topDiscard())) {
                playCard(player, cardToPlay, chosenColor);
            } else {
                resolvePendingDraw(player);
                advanceTurn();
            }
            return;
        }

        if (cardToPlay != null) {
            playCard(player, cardToPlay, chosenColor);
        } else { // no valid cards, draw until player can play a card
            Card drawn;
            do {
                drawn = drawCard(player);
            } while (!isPlayable(drawn));
            playCard(player, drawn, chosenColor); // autoplay the drawn card
            advanceTurn();
        }
    }
    
    public void addPendingDraw(int count, DrawType type) {
        pendingDraw += count;
        pendingDrawType = type;
    }
}
