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
    private final String hint;
    private final int stage;
    private final int stageCount;

    public RoundInfo(String matchId, int roundIndex, int totalRounds, int drawerId, String drawerName, String category, byte[] image, int seconds) {
        this(matchId, roundIndex, totalRounds, drawerId, drawerName, category, image, seconds, null);
    }

    public RoundInfo(String matchId, int roundIndex, int totalRounds, int drawerId, String drawerName, String category,
                     byte[] image, int seconds, String hint) {
        this(matchId, roundIndex, totalRounds, drawerId, drawerName, category, image, seconds, hint, 0, 1);
    }

    // `roundIndex`/`totalRounds` đếm trên cả trận; mỗi vòng có totalRounds / stageCount lượt.
    public RoundInfo(String matchId, int roundIndex, int totalRounds, int drawerId, String drawerName, String category,
                     byte[] image, int seconds, String hint, int stage, int stageCount) {
        this.stage = stage;
        this.stageCount = stageCount;
        this.hint = hint;
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
    public String getHint() { return hint; }
    public int getStage() { return stage; }
    public int getStageCount() { return Math.max(1, stageCount); }
}
