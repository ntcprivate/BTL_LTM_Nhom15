package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Lời mời vào phòng: gửi cho người được mời (INVITATION) và báo lại người mời khi bị từ chối (INVITE_DECLINED).
public class Invitation implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int fromUserId;
    private final String fromUsername;
    private final int toUserId;
    private final String toUsername;
    private final int roomId;

    public Invitation(int fromUserId, String fromUsername, int toUserId, String toUsername, int roomId) {
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.toUserId = toUserId;
        this.toUsername = toUsername;
        this.roomId = roomId;
    }

    public int getFromUserId() { return fromUserId; }
    public String getFromUsername() { return fromUsername; }
    public int getToUserId() { return toUserId; }
    public String getToUsername() { return toUsername; }
    public int getRoomId() { return roomId; }
}
