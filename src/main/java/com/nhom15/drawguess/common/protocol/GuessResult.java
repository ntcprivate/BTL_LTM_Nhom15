package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class GuessResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final boolean correct;
    private final int attemptsLeft;
    private final boolean locked;
    private final String reason;
    // Số điểm vừa nhận khi đoán đúng (điểm tốc độ + thưởng đoán đầu tiên); 0 nếu không đúng.
    private final int points;

    public GuessResult(String matchId, int roundIndex, boolean correct, int attemptsLeft, boolean locked, String reason) {
        this(matchId, roundIndex, correct, attemptsLeft, locked, reason, 0);
    }

    public GuessResult(String matchId, int roundIndex, boolean correct, int attemptsLeft, boolean locked, String reason,
                       int points) {
        this.points = points;
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.correct = correct;
        this.attemptsLeft = attemptsLeft;
        this.locked = locked;
        this.reason = reason;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public boolean isCorrect() { return correct; }
    public int getAttemptsLeft() { return attemptsLeft; }
    public boolean isLocked() { return locked; }
    public String getReason() { return reason; }
    public int getPoints() { return points; }
}
