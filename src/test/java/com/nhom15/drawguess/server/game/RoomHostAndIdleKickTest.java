package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RoomHostAndIdleKickTest {
    private final AtomicLong clock = new AtomicLong();
    private final RoomManager manager = new RoomManager(null, clock::get, false);
    private final Map<Integer, List<Message>> inbox = new HashMap<>();

    private List<Message> messages(int id) { return inbox.computeIfAbsent(id, ignored -> new ArrayList<>()); }
    private void join(int id, int roomId) { manager.join(new UserInfo(id, "player" + id, "USER"), roomId, messages(id)::add); }
    private void ready(int id, boolean ready) { manager.ready(id, ready, messages(id)::add); }
    private void kick(int host, int target) { manager.kick(host, target, messages(host)::add); }
    private RoomInfo room(int id) { return manager.getRooms().get(id - 1); }
    private List<Integer> players(int roomId) { return room(roomId).getPlayers().stream().map(PlayerInfo::getUserId).toList(); }
    private Message last(int id) { return messages(id).getLast(); }
    private int kickIn(int roomId, int userId) {
        return room(roomId).getPlayers().stream().filter(p -> p.getUserId() == userId).findFirst().orElseThrow().getKickInSeconds();
    }
    private void advance(int seconds) { clock.addAndGet(seconds * 1_000_000_000L); manager.tick(); }

    @Test
    void firstJoinerIsHostAndHostPassesToEarliestRemainingPlayer() {
        join(1, 1); join(2, 1); join(3, 1);
        assertEquals(1, room(1).getHostId());
        manager.leave(1, messages(1)::add);
        assertEquals(2, room(1).getHostId());
        manager.leave(2, messages(2)::add);
        manager.leave(3, messages(3)::add);
        assertEquals(0, room(1).getHostId());
        join(4, 1);
        assertEquals(4, room(1).getHostId());
    }

    @Test
    void onlyHostCanKickAndKickedPlayerCannotRejoinUntilRoomIsEmpty() {
        join(1, 1); join(2, 1); join(3, 1);
        kick(2, 3);
        assertEquals("NOT_HOST", last(2).getData());
        kick(1, 1);
        assertEquals("INVALID_TARGET", last(1).getData());
        kick(1, 9);
        assertEquals("INVALID_TARGET", last(1).getData());

        kick(1, 3);
        assertTrue(messages(1).stream().anyMatch(m -> m.getType() == MessageType.ACTION_SUCCESS
                && "KICK_PLAYER".equals(m.getData())));
        assertEquals(MessageType.ROOM_INFO, last(1).getType());
        assertEquals(MessageType.KICKED, last(3).getType());
        assertEquals("HOST_KICK", last(3).getData());
        assertEquals(List.of(1, 2), players(1));

        join(3, 1);
        assertEquals(MessageType.JOIN_FAILED, last(3).getType());
        assertEquals("KICKED_FROM_ROOM", last(3).getData());
        join(3, 2);
        assertEquals(List.of(3), players(2));

        manager.leave(1, messages(1)::add);
        manager.leave(2, messages(2)::add);
        manager.leave(3, messages(3)::add);
        join(3, 1);
        assertEquals(List.of(3), players(1));
    }

    @Test
    void hostCannotKickDuringGame() {
        join(1, 1); join(2, 1);
        ready(1, true); ready(2, true);
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
        kick(1, 2);
        assertEquals("ROOM_PLAYING", last(1).getData());
        assertEquals(List.of(1, 2), players(1));
    }

    @Test
    void kickingTheOnlyUnreadyPlayerStartsTheGame() {
        join(1, 1); join(2, 1); join(3, 1);
        ready(1, true); ready(2, true);
        kick(1, 3);
        assertEquals(RoomStatus.PLAYING, room(1).getStatus());
    }

    @Test
    void nobodyIsRemovedWhileNoPlayerIsReady() {
        join(1, 1); join(2, 1); join(3, 1);
        advance(300);
        assertEquals(List.of(1, 2, 3), players(1));
        assertEquals(-1, kickIn(1, 2));
    }

    @Test
    void unreadyPlayersAreRemovedSixtySecondsAfterSomeoneIsReady() {
        join(1, 1); join(2, 1); join(3, 1);
        advance(100);
        ready(1, true);
        assertEquals(-1, kickIn(1, 1));
        assertEquals(60, kickIn(1, 2));
        advance(59);
        assertEquals(List.of(1, 2, 3), players(1));
        assertEquals(1, kickIn(1, 3));
        advance(1);
        assertEquals(List.of(1), players(1));
        assertEquals("IDLE_TIMEOUT", last(2).getData());
        assertEquals(MessageType.KICKED, last(3).getType());
        assertEquals(RoomStatus.WAITING, room(1).getStatus());
    }

    @Test
    void lateJoinersAndCancelledReadyGetAFullCountdown() {
        join(1, 1); join(2, 1);
        ready(1, true);
        advance(30);
        join(3, 1);
        advance(30);
        assertEquals(List.of(1, 3), players(1));
        assertEquals(30, kickIn(1, 3));

        advance(29);
        ready(1, false);
        advance(100);
        assertEquals(List.of(1, 3), players(1));

        ready(3, true);
        assertEquals(60, kickIn(1, 1));
        advance(59);
        assertEquals(List.of(1, 3), players(1));
        advance(1);
        assertEquals(List.of(3), players(1));
        assertEquals(3, room(1).getHostId());
    }
}
