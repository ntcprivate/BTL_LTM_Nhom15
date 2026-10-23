package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final boolean ready;
    private final boolean online;
    private final String avatarKey;

    public PlayerInfo(int userId, String username, boolean ready, boolean online) {
        this(userId, username, ready, online, AvatarCatalog.DEFAULT);
    }

    public PlayerInfo(int userId, String username, boolean ready, boolean online, String avatarKey) {
        this.userId = userId;
        this.username = username;
        this.ready = ready;
        this.online = online;
        this.avatarKey = AvatarCatalog.normalize(avatarKey);
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isReady() { return ready; }
    public boolean isOnline() { return online; }
    public String getAvatarKey() { return AvatarCatalog.normalize(avatarKey); }
}
