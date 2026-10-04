package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class TimerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final GamePhase phase;
    private final int seconds;

    public TimerInfo(String matchId, GamePhase phase, int seconds) {
        this.matchId = matchId;
        this.phase = phase;
        this.seconds = seconds;
    }

    public String getMatchId() { return matchId; }
    public GamePhase getPhase() { return phase; }
    public int getSeconds() { return seconds; }
}
