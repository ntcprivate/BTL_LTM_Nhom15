package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Một người đang online; roomId = 0 nếu chưa ở phòng nào (chỉ người như vậy mới mời được).
public class OnlineUser implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final String avatarKey;
    private final int roomId;
    private final boolean playing;

    public OnlineUser(int userId, String username, String avatarKey, int roomId, boolean playing) {
        this.userId = userId;
        this.username = username;
        this.avatarKey = AvatarCatalog.normalize(avatarKey);
        this.roomId = roomId;
        this.playing = playing;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAvatarKey() { return AvatarCatalog.normalize(avatarKey); }
    public int getRoomId() { return roomId; }
    public boolean isPlaying() { return playing; }
    public boolean isAvailable() { return roomId == 0; }
}
