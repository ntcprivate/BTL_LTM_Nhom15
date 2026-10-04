package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class RoundInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final int totalRounds;
    private final int drawerId;
    private final String drawerName;
    private final String category;
    private final byte[] image;
    private final int seconds;

    public RoundInfo(String matchId, int roundIndex, int totalRounds, int drawerId, String drawerName, String category, byte[] image, int seconds) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.totalRounds = totalRounds;
        this.drawerId = drawerId;
        this.drawerName = drawerName;
        this.category = category;
        this.image = image == null ? null : image.clone();
        this.seconds = seconds;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public int getTotalRounds() { return totalRounds; }
    public int getDrawerId() { return drawerId; }
    public String getDrawerName() { return drawerName; }
    public String getCategory() { return category; }
    public byte[] getImage() { return image == null ? null : image.clone(); }
    public int getSeconds() { return seconds; }
}
