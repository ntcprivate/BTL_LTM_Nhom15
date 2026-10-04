package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class ScoreEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final int score;
    private final int rank;
    private final double averageGuessTime;
    private final boolean online;

    public ScoreEntry(int userId, String username, int score, int rank, double averageGuessTime, boolean online) {
        this.userId = userId;
        this.username = username;
        this.score = score;
        this.rank = rank;
        this.averageGuessTime = averageGuessTime;
        this.online = online;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public int getScore() { return score; }
    public int getRank() { return rank; }
    public double getAverageGuessTime() { return averageGuessTime; }
    public boolean isOnline() { return online; }
}
