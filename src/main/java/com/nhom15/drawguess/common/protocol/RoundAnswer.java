package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class RoundAnswer implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final String word;

    public RoundAnswer(String matchId, int roundIndex, String word) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.word = word;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public String getWord() { return word; }
}
