package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomStartCountdownTest {
    // Bộ hẹn giờ chạy tay: giữ các việc đã hẹn để test tự quyết định lúc "hết 5 giây".
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private final RoomManager manager = new RoomManager(null, System::nanoTime, (task, seconds) -> {
        scheduled.add(task);
        delays.add(seconds);
    });
    private final Map<Integer, List<Message>> inbox = new LinkedHashMap<>();

    private RoomInfo room() { return manager.getRooms().getFirst(); }

    private void join(int id) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        inbox.put(id, messages);
        manager.join(new UserInfo(id, "player" + id, "USER"), 1, messages::add);
    }

    private void ready(int id, boolean ready) { manager.ready(id, ready, inbox.get(id)::add); }

    private long count(int id, MessageType type) {
        return inbox.get(id).stream().filter(m -> m.getType() == type).count();
    }

    private Message last(int id, MessageType type) {
        return inbox.get(id).stream().filter(m -> m.getType() == type).reduce((a, b) -> b).orElseThrow();
    }

    private void allReady(int players) {
        for (int id = 1; id <= players; id++) { join(id); }
        for (int id = 1; id <= players; id++) { ready(id, true); }
    }

    @Test
    void everyoneReadyStartsAFiveSecondCountdownBeforeTheMatch() {
        allReady(3);
        assertEquals(RoomStatus.GAME_STARTING, room().getStatus());
        assertEquals(List.of(RoomManager.START_COUNTDOWN_SECONDS), delays);
        assertEquals(5, RoomManager.START_COUNTDOWN_SECONDS);
        for (int id = 1; id <= 3; id++) {
            assertEquals(5, last(id, MessageType.START_COUNTDOWN).getData());
            assertEquals(0, count(id, MessageType.START_GAME), "Chưa vào trận khi đang đếm ngược");
        }

        scheduled.getFirst().run();
        assertEquals(RoomStatus.PLAYING, room().getStatus());
        for (int id = 1; id <= 3; id++) { assertEquals(1, count(id, MessageType.START_GAME)); }
    }

    @Test
    void pressingNotReadyDuringTheCountdownCancelsTheMatch() {
        allReady(3);
        ready(2, false);
        assertEquals(RoomStatus.READY_CHECK, room().getStatus());
        for (int id = 1; id <= 3; id++) {
            assertEquals("player2", last(id, MessageType.START_CANCELLED).getData(), "Cả phòng biết ai chưa sẵn sàng");
        }
        assertFalse(room().getPlayers().get(1).isReady());

        scheduled.getFirst().run();
        assertEquals(RoomStatus.READY_CHECK, room().getStatus(), "Hết giờ của lượt đếm đã hủy thì không vào trận");
        assertEquals(0, count(1, MessageType.START_GAME));

        // Sẵn sàng lại thì đếm lại từ đầu; chỉ lượt đếm mới được bắt đầu trận.
        ready(2, true);
        assertEquals(2, scheduled.size());
        scheduled.getFirst().run();
        assertEquals(RoomStatus.GAME_STARTING, room().getStatus());
        scheduled.getLast().run();
        assertEquals(RoomStatus.PLAYING, room().getStatus());
    }

    @Test
    void leavingDuringTheCountdownRestartsItForTheOthers() {
        allReady(3);
        manager.leave(3, inbox.get(3)::add);
        assertEquals(RoomStatus.GAME_STARTING, room().getStatus());
        assertEquals(2, scheduled.size(), "Hai người còn lại vẫn sẵn sàng nên đếm lại từ đầu");
        scheduled.getFirst().run();
        assertEquals(RoomStatus.GAME_STARTING, room().getStatus());
        scheduled.getLast().run();
        assertEquals(RoomStatus.PLAYING, room().getStatus());
        assertEquals(2, room().getPlayers().size());
    }

    @Test
    void lastSecondLeaveOfAPairCancelsTheCountdown() {
        allReady(2);
        manager.disconnect(2);
        assertEquals(RoomStatus.WAITING, room().getStatus());
        scheduled.getFirst().run();
        assertEquals(RoomStatus.WAITING, room().getStatus());
        assertEquals(0, count(1, MessageType.START_GAME));
    }

    @Test
    void nobodyCanJoinAndReadyAgainChangesNothingDuringTheCountdown() {
        allReady(2);
        join(4);
        assertEquals("ROOM_PLAYING", last(4, MessageType.JOIN_FAILED).getData());
        ready(1, true);
        assertEquals(RoomStatus.GAME_STARTING, room().getStatus());
        assertEquals(1, scheduled.size());
        assertEquals(RoomStatus.GAME_STARTING,
                ((RoomInfo) last(1, MessageType.READY_STATUS).getData()).getStatus(), "Người bấm vẫn nhận trạng thái phòng");
    }
}
