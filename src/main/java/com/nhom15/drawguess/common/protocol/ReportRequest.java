package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Người đoán báo cáo tranh của vòng đang đoán.
public class ReportRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;

    public ReportRequest(String matchId, int roundIndex) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
}
