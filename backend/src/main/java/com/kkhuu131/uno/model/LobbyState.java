package com.kkhuu131.uno.model;

import java.util.ArrayList;
import java.util.List;

public class LobbyState {

    private final String code;
    private final String hostSessionId;
    private final List<LobbyPlayer> players = new ArrayList<>();
    private LobbyStatus status = LobbyStatus.WAITING;
    private String gameId;

    public LobbyState(String code, String hostSessionId) {
        this.code = code;
        this.hostSessionId = hostSessionId;
    }

    public String getCode() { return code; }

    public String getHostSessionId() { return hostSessionId; }

    public LobbyStatus getStatus() { return status; }

    public String getGameId() { return gameId; }

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

    public synchronized void start(String gameId) {
        this.status = LobbyStatus.IN_PROGRESS;
        this.gameId = gameId;
    }
}
