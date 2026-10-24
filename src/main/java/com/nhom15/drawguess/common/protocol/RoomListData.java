package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;
import java.util.List;

/** Dữ liệu danh sách phòng và tổng số tài khoản đang có phiên đăng nhập. */
public final class RoomListData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<RoomInfo> rooms;
    private final int onlineUsers;

    public RoomListData(List<RoomInfo> rooms, int onlineUsers) {
        this.rooms = List.copyOf(rooms);
        this.onlineUsers = Math.max(0, onlineUsers);
    }

    public List<RoomInfo> getRooms() {
        return rooms;
    }

    public int getOnlineUsers() {
        return onlineUsers;
    }
}
