package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class RoundHint implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final String hint;

    public RoundHint(String matchId, int roundIndex, String hint) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.hint = hint;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public String getHint() { return hint; }
}
