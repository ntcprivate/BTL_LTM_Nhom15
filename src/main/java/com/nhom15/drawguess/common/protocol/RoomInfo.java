package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;
import java.util.List;

public class RoomInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_STAGES = 5;
    // Chủ đề của từng vòng đấu do chủ phòng chọn; chuỗi rỗng là ngẫu nhiên.
    public static final List<String> DEFAULT_STAGES = List.of("");
    private final int roomId;
    private final RoomStatus status;
    private final List<PlayerInfo> players;
    private final int hostId;
    private final List<String> stages;

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players) {
        this(roomId, status, players, 0);
    }

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players, int hostId) {
        this(roomId, status, players, hostId, DEFAULT_STAGES);
    }

    public RoomInfo(int roomId, RoomStatus status, List<PlayerInfo> players, int hostId, List<String> stages) {
        this.stages = List.copyOf(stages);
        this.roomId = roomId;
        this.status = status;
        this.players = List.copyOf(players);
        this.hostId = hostId;
    }

    public int getRoomId() { return roomId; }
    public RoomStatus getStatus() { return status; }
    public List<PlayerInfo> getPlayers() { return players; }
    public int getHostId() { return hostId; }
    public List<String> getStages() { return stages == null ? DEFAULT_STAGES : stages; }

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
