package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RoomChatTest {
    private final AtomicLong clock = new AtomicLong();
    private final RoomManager manager = new RoomManager(null, clock::get, RoomManager.StartTimer.IMMEDIATE);
    private final Map<Integer, List<Message>> inbox = new HashMap<>();

    private List<Message> messages(int id) { return inbox.computeIfAbsent(id, ignored -> new ArrayList<>()); }
    private UserInfo user(int id) { return new UserInfo(id, "player" + id, "USER"); }
    private void join(int id, int roomId) { manager.join(user(id), roomId, messages(id)::add); }
    private void chat(int id, Object text) { manager.chat(user(id), text, messages(id)::add); }
    private List<String> chats(int id) {
        return messages(id).stream().filter(m -> m.getType() == MessageType.CHAT_MESSAGE)
                .map(m -> ((ChatMessage) m.getData()).getUsername() + ": " + ((ChatMessage) m.getData()).getText()).toList();
    }
    private Object lastError(int id) {
        return messages(id).stream().filter(m -> m.getType() == MessageType.ACTION_FAILED).reduce((a, b) -> b).orElseThrow().getData();
    }

    @Test
    void chatReachesOnlyPlayersOfTheSameRoom() {
        join(1, 1); join(2, 1); join(3, 2);
        chat(1, "  Chào cả phòng!  ");
        assertEquals(List.of("player1: Chào cả phòng!"), chats(1), "Người gửi cũng thấy tin của mình");
        assertEquals(List.of("player1: Chào cả phòng!"), chats(2));
        assertTrue(chats(3).isEmpty(), "Phòng khác không nhận được");
    }

    @Test
    void emptyTooLongTooFastOrOutsideARoomIsRejected() {
        chat(9, "xin chào");
        assertEquals("NOT_IN_ROOM", lastError(9));
        join(1, 1);
        chat(1, "   ");
        assertEquals("INVALID_CHAT", lastError(1));
        chat(1, "a".repeat(RoomManager.CHAT_MAX_LENGTH + 1));
        assertEquals("INVALID_CHAT", lastError(1));
        chat(1, 42);
        assertEquals("INVALID_CHAT", lastError(1));

        chat(1, "một");
        chat(1, "hai");
        assertEquals("CHAT_TOO_FAST", lastError(1));
        clock.addAndGet(400_000_000L);
        chat(1, "ba");
        assertEquals(List.of("player1: một", "player1: ba"), chats(1));
    }
}
