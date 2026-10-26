package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Một dòng bảng xếp hạng người vẽ: tranh càng nhiều người đoán trúng, càng ít bị báo cáo
// và được đoán ra càng nhanh thì xếp càng cao.
public class DrawerEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final int rank;
    // Số lượt người khác đoán trúng tranh của người này (không tính tranh bị báo cáo).
    private final int solvedCount;
    // Số tranh bị quá nửa người đoán báo cáo.
    private final int rejectedCount;
    // Thời gian trung bình (giây) để người khác đoán trúng tranh; vô cực nếu chưa ai đoán trúng.
    private final double averageSolveTime;
    private final boolean online;

    public DrawerEntry(int userId, String username, int rank, int solvedCount, int rejectedCount,
                       double averageSolveTime, boolean online) {
        this.userId = userId;
        this.username = username;
        this.rank = rank;
        this.solvedCount = solvedCount;
        this.rejectedCount = rejectedCount;
        this.averageSolveTime = averageSolveTime;
        this.online = online;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public int getRank() { return rank; }
    public int getSolvedCount() { return solvedCount; }
    public int getRejectedCount() { return rejectedCount; }
    public double getAverageSolveTime() { return averageSolveTime; }
    public boolean isOnline() { return online; }
}
