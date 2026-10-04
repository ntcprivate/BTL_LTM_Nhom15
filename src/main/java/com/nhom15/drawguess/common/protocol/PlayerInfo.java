package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final boolean ready;
    private final boolean online;

    public PlayerInfo(int userId, String username, boolean ready, boolean online) {
        this.userId = userId;
        this.username = username;
        this.ready = ready;
        this.online = online;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public boolean isReady() { return ready; }
    public boolean isOnline() { return online; }
}
