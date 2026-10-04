package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;
import java.util.List;

public class RoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int roomId;
    private final RoomStatus status;
    private final List<PlayerInfo> players;

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players) {
        this.roomId = roomId;
        this.status = status;
        this.players = List.copyOf(players);
    }

    public int getRoomId() { return roomId; }
    public RoomStatus getStatus() { return status; }
    public List<PlayerInfo> getPlayers() { return players; }

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
