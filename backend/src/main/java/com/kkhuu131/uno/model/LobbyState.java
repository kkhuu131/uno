package com.kkhuu131.uno.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LobbyState {

    private final String code;
    private String hostSessionId;
    private final List<LobbyPlayer> players = new ArrayList<>();
    private LobbyStatus status = LobbyStatus.WAITING;
    private String gameId;

    public LobbyState(String code, String hostSessionId) {
        this.code = code;
        this.hostSessionId = hostSessionId;
    }

    public String getCode() { return code; }

    public synchronized String getHostSessionId() { return hostSessionId; }

    public synchronized LobbyStatus getStatus() { return status; }

    public synchronized String getGameId() { return gameId; }

    public synchronized List<LobbyPlayer> getPlayers() {
        return List.copyOf(players);
    }

    public synchronized int getPlayerCount() {
        return players.size();
    }

    public synchronized boolean hasSession(String sessionId) {
        return players.stream().anyMatch(p -> p.sessionId().equals(sessionId));
    }

    public synchronized void addPlayer(LobbyPlayer player) {
        players.add(player);
    }

    public synchronized void removePlayer(String sessionId) {
        players.removeIf(p -> p.sessionId().equals(sessionId));
        if (players.isEmpty()) return;
        boolean hostStillPresent = players.stream()
                .anyMatch(p -> p.sessionId().equals(hostSessionId));
        if (!hostStillPresent) {
            hostSessionId = players.stream()
                    .min(Comparator.comparingInt(LobbyPlayer::playerIndex))
                    .map(LobbyPlayer::sessionId)
                    .orElseThrow();
        }
    }

    public synchronized void resetForRematch() {
        this.status = LobbyStatus.WAITING;
        this.gameId = null;
    }

    public synchronized void start(String gameId) {
        this.status = LobbyStatus.IN_PROGRESS;
        this.gameId = gameId;
    }
}
