package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class DrawingPrompt implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roomId;
    private final String category;
    private final String word;
    private final int seconds;

    public DrawingPrompt(String matchId, int roomId, String category, String word, int seconds) {
        this.matchId = matchId;
        this.roomId = roomId;
        this.category = category;
        this.word = word;
        this.seconds = seconds;
    }

    public String getMatchId() { return matchId; }
    public int getRoomId() { return roomId; }
    public String getCategory() { return category; }
    public String getWord() { return word; }
    public int getSeconds() { return seconds; }
}
