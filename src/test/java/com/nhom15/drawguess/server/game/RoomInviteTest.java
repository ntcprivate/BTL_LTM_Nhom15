package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RoomInviteTest {
    private final AtomicLong clock = new AtomicLong();
    private final RoomManager manager = new RoomManager(null, clock::get);
    private final Map<Integer, List<Message>> inbox = new HashMap<>();

    private List<Message> messages(int id) { return inbox.computeIfAbsent(id, ignored -> new ArrayList<>()); }
    private UserInfo user(int id) { return new UserInfo(id, "player" + id, "USER"); }
    private void join(int id, int roomId) { manager.join(user(id), roomId, messages(id)::add); }
    private void invite(int from, int to) { manager.invite(user(from), user(to), messages(to)::add, messages(from)::add); }
    private Object lastReply(int id) { return messages(id).getLast().getData(); }
    private long invitations(int id) { return messages(id).stream().filter(m -> m.getType() == MessageType.INVITATION).count(); }

    @Test
    void sameInviteeCanBeInvitedAgainOnlyAfterCooldown() {
        join(1, 1);
        invite(1, 2);
        assertEquals("INVITE_PLAYER", lastReply(1));
        Invitation invitation = (Invitation) messages(2).getLast().getData();
        assertEquals(1, invitation.getRoomId());
        assertEquals(2, invitation.getToUserId());

        clock.addAndGet((RoomManager.INVITE_COOLDOWN_SECONDS - 1) * 1_000_000_000L);
        invite(1, 2);
        assertEquals("INVITE_COOLDOWN", lastReply(1));
        invite(1, 3);
        assertEquals("INVITE_PLAYER", lastReply(1), "Thời gian chờ chỉ tính cho từng cặp người mời - người được mời");
        clock.addAndGet(1_000_000_000L);
        invite(1, 2);
        assertEquals("INVITE_PLAYER", lastReply(1));
        assertEquals(2, invitations(2));
    }

    @Test
    void inviteIsRejectedWhenRoomCannotTakeTheInvitee() {
        invite(1, 2);
        assertEquals("NOT_IN_ROOM", lastReply(1));
        join(1, 1);
        invite(1, 1);
        assertEquals("INVALID_INVITE", lastReply(1));
        manager.invite(user(1), new UserInfo(9, "admin", "ADMIN"), messages(9)::add, messages(1)::add);
        assertEquals("TARGET_OFFLINE", lastReply(1));
        manager.invite(user(1), null, null, messages(1)::add);
        assertEquals("TARGET_OFFLINE", lastReply(1));

        join(3, 1);
        manager.kick(1, 3, messages(1)::add);
        invite(1, 3);
        assertEquals("TARGET_KICKED", lastReply(1));

        join(4, 2);
        invite(1, 4);
        assertEquals("TARGET_BUSY", lastReply(1));

        for (int id = 10; id < 17; id++) { join(id, 1); }
        invite(1, 5);
        assertEquals("ROOM_FULL", lastReply(1));
        assertEquals(0, invitations(5));
    }

    @Test
    void inviteIsRejectedDuringAGameAndDescribeShowsWhereEachPlayerIs() {
        join(1, 1); join(2, 1);
        assertTrue(manager.describe(user(3)).isAvailable());
        assertEquals(1, manager.describe(user(2)).getRoomId());
        assertFalse(manager.describe(user(2)).isPlaying());
        manager.ready(1, true, messages(1)::add);
        manager.ready(2, true, messages(2)::add);
        assertTrue(manager.describe(user(2)).isPlaying());
        invite(1, 3);
        assertEquals("ROOM_PLAYING", lastReply(1));
    }
}
