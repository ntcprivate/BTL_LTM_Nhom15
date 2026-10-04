package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class GameResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final java.util.List<ScoreEntry> players;

    public GameResult(String matchId, java.util.List<ScoreEntry> players) {
        this.matchId = matchId;
        this.players = java.util.List.copyOf(players);
    }

    public String getMatchId() { return matchId; }
    public java.util.List<ScoreEntry> getPlayers() { return players; }
}
