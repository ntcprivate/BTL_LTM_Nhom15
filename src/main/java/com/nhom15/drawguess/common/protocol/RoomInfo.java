package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;
import java.util.List;

public class RoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int roomId;
    private final RoomStatus status;
    private final List<PlayerInfo> players;
    private final int hostId;

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players) {
        this(roomId, status, players, 0);
    }

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players, int hostId) {
        this.roomId = roomId;
        this.status = status;
        this.players = List.copyOf(players);
        this.hostId = hostId;
    }

    public int getRoomId() { return roomId; }
    public RoomStatus getStatus() { return status; }
    public List<PlayerInfo> getPlayers() { return players; }
    public int getHostId() { return hostId; }

    @Override
    public String toString() {
        String label = switch (status) {
            case WAITING -> "Đang chờ";
            case READY_CHECK -> "Chờ sẵn sàng";
            case GAME_STARTING -> "Chuẩn bị bắt đầu";
            case PLAYING -> "Đang chơi";
            case FINISHED -> "Đã kết thúc";
        };
        return String.format("Phòng %03d  |  %d/8 người  |  %s", roomId, players.size(), label);
    }
}
