package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class GuessRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final String answer;

    public GuessRequest(String matchId, int roundIndex, String answer) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.answer = answer;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public String getAnswer() { return answer; }
}
