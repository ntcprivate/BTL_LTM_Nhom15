package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RoomReconnectTest {
    private final RoomManager manager = new RoomManager(null, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);

    private UserInfo user(int id) { return new UserInfo(id, "player" + id, "USER"); }
    private RoomInfo room(int id) { return manager.getRooms().get(id - 1); }
    private PlayerInfo player(int roomId, int userId) {
        return room(roomId).getPlayers().stream().filter(p -> p.getUserId() == userId).findFirst().orElseThrow();
    }

    private List<Message> join(int id, int roomId) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        manager.join(user(id), roomId, messages::add);
        return messages;
    }

    private void startGame(int players) {
        for (int id = 1; id <= players; id++) { join(id, 1); }
        for (int id = 1; id <= players; id++) { manager.ready(id, true, ignored -> {}); }
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
    }

    @Test
    void droppedPlayerLogsBackIntoTheRunningMatch() {
        startGame(3);
        manager.disconnect(2);
        assertFalse(player(1, 2).isOnline());

        List<Message> again = new ArrayList<>();
        assertTrue(manager.reconnect(user(2), again::add));
        RoomInfo restored = assertInstanceOf(RoomInfo.class, again.getFirst().getData());
        assertEquals(MessageType.JOIN_SUCCESS, again.getFirst().getType());
        assertEquals(RoomStatus.PLAYING, restored.getStatus());
        assertTrue(player(1, 2).isOnline());
        assertEquals("ALREADY_IN_ROOM", join(2, 5).getFirst().getData(), "Đã về lại trận thì không vào phòng khác được");
    }

    @Test
    void droppedPlayerCanAlsoRejoinFromTheRoomList() {
        startGame(2);
        manager.disconnect(1);
        assertEquals(MessageType.JOIN_SUCCESS, join(1, 1).getFirst().getType());
        assertTrue(player(1, 1).isOnline());
        assertEquals("ROOM_PLAYING", join(3, 1).getFirst().getData(), "Người ngoài vẫn không vào được trận đang chơi");
    }

    @Test
    void leavingOrLoggingOutDuringTheMatchGivesUpTheSeat() {
        startGame(3);
        manager.leave(1, ignored -> {});
        manager.logout(2, null);
        assertFalse(manager.reconnect(user(1), ignored -> {}));
        assertFalse(manager.reconnect(user(2), ignored -> {}));
        assertEquals("ROOM_PLAYING", join(1, 1).getFirst().getData());
        assertEquals("ROOM_PLAYING", join(2, 1).getFirst().getData());
    }

    @Test
    void oldConnectionCannotRemoveAPlayerWhoAlreadyReconnected() {
        List<Message> first = new ArrayList<>();
        Consumer<Message> oldConnection = first::add;
        manager.join(user(1), 1, oldConnection);
        join(2, 1);

        // Máy vừa mất mạng đăng nhập lại khi server chưa nhận ra kết nối cũ đã chết.
        List<Message> second = new ArrayList<>();
        assertTrue(manager.reconnect(user(1), second::add));
        assertEquals(MessageType.JOIN_SUCCESS, second.getFirst().getType());

        manager.disconnect(1, oldConnection);
        assertEquals(2, room(1).getPlayers().size(), "Kết nối cũ đóng muộn không kéo người chơi ra khỏi phòng");
        manager.disconnect(1, second::add);
        assertEquals(2, room(1).getPlayers().size(), "Chỉ đúng đối tượng gửi đang giữ chỗ mới được xử lý");
    }

    @Test
    void reconnectWithoutAnUnfinishedRoomDoesNothing() {
        List<Message> messages = new ArrayList<>();
        assertFalse(manager.reconnect(user(9), messages::add));
        assertTrue(messages.isEmpty());
    }
}
