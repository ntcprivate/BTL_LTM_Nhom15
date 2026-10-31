package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class GameResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final java.util.List<ScoreEntry> players;
    // Bảng xếp hạng người vẽ của trận.
    private final java.util.List<DrawerEntry> drawers;

    public GameResult(String matchId, java.util.List<ScoreEntry> players) {
        this(matchId, players, java.util.List.of());
    }

    public GameResult(String matchId, java.util.List<ScoreEntry> players, java.util.List<DrawerEntry> drawers) {
        this.matchId = matchId;
        this.players = java.util.List.copyOf(players);
        this.drawers = java.util.List.copyOf(drawers);
    }

    public String getMatchId() { return matchId; }
    public java.util.List<ScoreEntry> getPlayers() { return players; }
    public java.util.List<DrawerEntry> getDrawers() { return drawers == null ? java.util.List.of() : drawers; }
}
