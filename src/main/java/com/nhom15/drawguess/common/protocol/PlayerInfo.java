package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final boolean ready;
    private final boolean online;
    private final String avatarKey;
    // Số giây trước khi bị tự động đưa ra khỏi phòng vì chưa sẵn sàng; -1 nếu không bị đếm ngược.
    private final int kickInSeconds;

    public PlayerInfo(int userId, String username, boolean ready, boolean online) {
        this(userId, username, ready, online, AvatarCatalog.DEFAULT);
    }

    public PlayerInfo(int userId, String username, boolean ready, boolean online, String avatarKey) {
        this(userId, username, ready, online, avatarKey, -1);
    }

    public PlayerInfo(int userId, String username, boolean ready, boolean online, String avatarKey,
                      int kickInSeconds) {
        this.userId = userId;
        this.username = username;
        this.ready = ready;
        this.online = online;
        this.avatarKey = AvatarCatalog.normalize(avatarKey);
        this.kickInSeconds = kickInSeconds;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isReady() { return ready; }
    public boolean isOnline() { return online; }
    public String getAvatarKey() { return AvatarCatalog.normalize(avatarKey); }
    public int getKickInSeconds() { return kickInSeconds; }
}
