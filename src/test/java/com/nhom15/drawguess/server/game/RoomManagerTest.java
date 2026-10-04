package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RoomManagerTest {
    private final RoomManager manager = new RoomManager();

    private UserInfo user(int id) { return new UserInfo(id, "player" + id, "USER"); }
    private RoomInfo room(int id) { return manager.getRooms().get(id - 1); }

    private List<Message> join(int id, int roomId) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        manager.join(user(id), roomId, messages::add);
        return messages;
    }

    @Test
    void createsExactlyOneHundredEmptyRooms() {
        assertEquals(100, manager.getRooms().size());
        for (int id = 1; id <= 100; id++) {
            assertEquals(id, room(id).getRoomId());
            assertEquals(RoomStatus.WAITING, room(id).getStatus());
            assertTrue(room(id).getPlayers().isEmpty());
        }
    }

    @Test
    void missingRoomAndDoubleJoinAreRejected() {
        assertEquals("ROOM_NOT_FOUND", join(1, 0).getFirst().getData());
        assertEquals("ROOM_NOT_FOUND", join(1, 101).getFirst().getData());
        join(1, 1);
        assertEquals("ALREADY_IN_ROOM", join(1, 2).getFirst().getData());
        assertTrue(room(2).getPlayers().isEmpty());
    }

    @Test
    void joinOrderIsPreservedAndPlayersInitiallyNotReady() {
        join(8, 1);
        join(3, 1);
        join(5, 1);
        assertEquals(List.of(8, 3, 5), room(1).getPlayers().stream().map(PlayerInfo::getUserId).toList());
        assertTrue(room(1).getPlayers().stream().noneMatch(PlayerInfo::isReady));
    }

    @Test
    void fewerThanMinimumReadyPlayersCannotStart() {
        for (int id = 1; id < RoomManager.MIN_PLAYERS; id++) {
            join(id, 1);
            manager.ready(id, true, ignored -> {});
        }
        assertEquals(RoomStatus.WAITING, room(1).getStatus());
    }

    @Test
    void twoReadyPlayersCanStart() {
        join(1, 1);
        join(2, 1);
        manager.ready(1, true, ignored -> {});
        assertEquals(RoomStatus.READY_CHECK, room(1).getStatus());
        manager.ready(2, true, ignored -> {});
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
    }

    @Test
    void cancelReadyPreventsStartUntilEveryoneIsReadyAgain() {
        for (int id = 1; id <= 5; id++) { join(id, 1); }
        for (int id = 1; id <= 4; id++) { manager.ready(id, true, ignored -> {}); }
        manager.ready(1, false, ignored -> {});
        manager.ready(5, true, ignored -> {});
        assertEquals(RoomStatus.READY_CHECK, room(1).getStatus());
        manager.ready(1, true, ignored -> {});
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
    }

    @Test
    void leavingNotReadyPlayerCanStartRemainingFourReadyPlayers() {
        for (int id = 1; id <= 5; id++) { join(id, 1); }
        for (int id = 1; id <= 4; id++) { manager.ready(id, true, ignored -> {}); }
        manager.leave(5, ignored -> {});
        assertEquals(4, room(1).getPlayers().size());
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
    }

    @Test
    void disconnectBeforeGameRemovesPlayerAndClearsMembership() {
        for (int id = 1; id <= 4; id++) { join(id, 1); }
        manager.disconnect(4);
        assertEquals(3, room(1).getPlayers().size());
        assertEquals(RoomStatus.READY_CHECK, room(1).getStatus());
        assertEquals(MessageType.JOIN_SUCCESS, join(4, 2).getFirst().getType());
    }

    @Test
    void disconnectDuringGamePreservesOfflineParticipant() {
        List<Message> offlineMessages = join(1, 1);
        for (int id = 2; id <= 4; id++) { join(id, 1); }
        for (int id = 1; id <= 4; id++) { manager.ready(id, true, ignored -> {}); }
        int received = offlineMessages.size();
        manager.disconnect(1);
        assertEquals(4, room(1).getPlayers().size());
        assertFalse(room(1).getPlayers().getFirst().isOnline());
        assertEquals(received, offlineMessages.size());
        assertEquals("ROOM_PLAYING", join(5, 1).getFirst().getData());
    }

    @Test
    void snapshotsRemainUnchangedAfterFurtherUpdates() {
        join(1, 1);
        RoomInfo before = room(1);
        manager.ready(1, true, ignored -> {});
        assertFalse(before.getPlayers().getFirst().isReady());
        assertTrue(room(1).getPlayers().getFirst().isReady());
        assertThrows(UnsupportedOperationException.class, () -> before.getPlayers().clear());
    }

    @Test
    void readyOutsideRoomIsRejected() {
        List<Message> messages = new ArrayList<>();
        manager.ready(1, true, messages::add);
        assertEquals(MessageType.ACTION_FAILED, messages.getFirst().getType());
        assertEquals("NOT_IN_ROOM", messages.getFirst().getData());
    }

    @Test
    void concurrentJoinNeverExceedsEightPlayers() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<List<Message>> outcomes = new ArrayList<>();
        for (int i = 0; i < 20; i++) { outcomes.add(Collections.synchronizedList(new ArrayList<>())); }
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int index = i;
                tasks.add(executor.submit(() -> {
                    assertTrue(gate.await(5, TimeUnit.SECONDS));
                    manager.join(user(index + 1), 1, outcomes.get(index)::add);
                    return null;
                }));
            }
            gate.countDown();
            for (var task : tasks) { task.get(5, TimeUnit.SECONDS); }
        }
        assertEquals(8, room(1).getPlayers().size());
        assertEquals(8, outcomes.stream().filter(m -> m.getFirst().getType() == MessageType.JOIN_SUCCESS).count());
        assertEquals(12, outcomes.stream().filter(m -> "ROOM_FULL".equals(m.getFirst().getData())).count());
    }

    @Test
    void simultaneousReadyStartsExactlyOnceForEachPlayer() throws Exception {
        List<List<Message>> messages = new ArrayList<>();
        for (int id = 1; id <= 4; id++) { messages.add(join(id, 1)); }
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                int playerId = id;
                tasks.add(executor.submit(() -> {
                    assertTrue(gate.await(5, TimeUnit.SECONDS));
                    manager.ready(playerId, true, ignored -> {});
                    return null;
                }));
            }
            gate.countDown();
            for (var task : tasks) { task.get(5, TimeUnit.SECONDS); }
        }
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
        manager.ready(1, true, ignored -> {});
        for (List<Message> received : messages) {
            assertEquals(1, received.stream().filter(m -> m.getType() == MessageType.START_GAME).count());
        }
    }
}
