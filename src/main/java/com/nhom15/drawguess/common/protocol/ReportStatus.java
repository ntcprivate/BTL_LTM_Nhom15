package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Số báo cáo của tranh đang đoán; `rejected` = đã đủ quá nửa người đoán, người vẽ mất điểm vòng này.
public class ReportStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String matchId;
    private final int roundIndex;
    private final int drawerId;
    private final int reports;
    private final int needed;
    private final boolean rejected;

    public ReportStatus(String matchId, int roundIndex, int drawerId, int reports, int needed, boolean rejected) {
        this.matchId = matchId;
        this.roundIndex = roundIndex;
        this.drawerId = drawerId;
        this.reports = reports;
        this.needed = needed;
        this.rejected = rejected;
    }

    public String getMatchId() { return matchId; }
    public int getRoundIndex() { return roundIndex; }
    public int getDrawerId() { return drawerId; }
    public int getReports() { return reports; }
    public int getNeeded() { return needed; }
    public boolean isRejected() { return rejected; }
}
