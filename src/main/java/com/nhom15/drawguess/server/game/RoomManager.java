package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.sql.SQLException;

public class RoomManager {
    public static final int ROOM_COUNT = 100;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 8;
    public static final int INVITE_COOLDOWN_SECONDS = 10;
    // Mọi người sẵn sàng thì đếm ngược chừng này giây rồi mới vào trận; trong lúc đó ai cũng hủy được.
    public static final int START_COUNTDOWN_SECONDS = 5;
    public static final int CHAT_MAX_LENGTH = 200;
    // Mỗi người gửi tối đa một tin nhắn mỗi 0,4 giây để không ai làm trôi khung chat.
    private static final long CHAT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(400);

    // Hẹn giờ chạy một việc sau vài giây; test dùng IMMEDIATE hoặc bộ hẹn giờ chạy tay.
    public interface StartTimer {
        StartTimer IMMEDIATE = (task, seconds) -> task.run();

        void schedule(Runnable task, int seconds);
    }

    private static final ScheduledExecutorService COUNTDOWN_THREAD = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Room-Start-Countdown");
        thread.setDaemon(true);
        return thread;
    });
    public static final StartTimer REAL_TIME = (task, seconds) -> COUNTDOWN_THREAD.schedule(task, seconds, TimeUnit.SECONDS);
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, Room> memberships = new ConcurrentHashMap<>();
    private final GameManager gameManager;
    private final LongSupplier clock;
    private final StartTimer startTimer;
    // (người mời, người được mời) -> lúc mời gần nhất, để chặn mời liên tục cùng một người.
    private final Map<Long, Long> lastInvites = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastChats = new ConcurrentHashMap<>();

    public RoomManager() {
        this(null);
    }

    public RoomManager(GameManager gameManager) {
        this(gameManager, System::nanoTime);
    }

    public RoomManager(GameManager gameManager, LongSupplier clock) {
        this(gameManager, clock, REAL_TIME);
    }

    public RoomManager(GameManager gameManager, LongSupplier clock, StartTimer startTimer) {
        this.gameManager = gameManager;
        this.clock = clock;
        this.startTimer = startTimer;
        for (int id = 1; id <= ROOM_COUNT; id++) {
            rooms.put(id, new Room(id));
        }
    }

    public List<RoomInfo> getRooms() {
        List<RoomInfo> result = new ArrayList<>();
        for (Room room : rooms.values()) {
            synchronized (room) {
                result.add(snapshot(room));
            }
        }
        return result;
    }

    public void join(UserInfo user, int roomId, Consumer<Message> sender) {
        Room room = rooms.get(roomId);
        if (room == null) {
            sender.accept(new Message(MessageType.JOIN_FAILED, "ROOM_NOT_FOUND"));
            return;
        }
        // Đặt chỗ theo user trước để một người không vào hai phòng đồng thời.
        if (memberships.putIfAbsent(user.getId(), room) != null) {
            sender.accept(new Message(MessageType.JOIN_FAILED, "ALREADY_IN_ROOM"));
            return;
        }
        synchronized (room) {
            Player returning = room.players.get(user.getId());
            if (room.status == RoomStatus.PLAYING && canRejoin(returning)) {
                rejoin(room, returning, sender);
                return;
            }
            if (room.status == RoomStatus.PLAYING || room.status == RoomStatus.GAME_STARTING) {
                memberships.remove(user.getId(), room);
                sender.accept(new Message(MessageType.JOIN_FAILED, "ROOM_PLAYING"));
                return;
            }
            if (room.players.size() >= MAX_PLAYERS) {
                memberships.remove(user.getId(), room);
                sender.accept(new Message(MessageType.JOIN_FAILED, "ROOM_FULL"));
                return;
            }
            room.players.put(user.getId(), new Player(user, sender));
            if (room.hostId == 0) { room.hostId = user.getId(); }
            updateWaitingStatus(room);
            sender.accept(new Message(MessageType.JOIN_SUCCESS, snapshot(room)));
            broadcast(room, MessageType.ROOM_INFO);
        }
    }

    public void ready(int userId, boolean ready, Consumer<Message> sender) {
        Room room = memberships.get(userId);
        if (room == null) {
            sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            return;
        }
        synchronized (room) {
            Player player = room.players.get(userId);
            if (player == null || !player.online || memberships.get(userId) != room) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
                return;
            }
            if (room.status == RoomStatus.PLAYING) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "ROOM_PLAYING"));
                return;
            }
            player.ready = ready;
            if (room.status == RoomStatus.GAME_STARTING) {
                // Đang đếm ngược: bấm "Tôi chưa sẵn sàng!" thì hủy, cả phòng quay lại chờ sẵn sàng.
                if (!ready) {
                    updateWaitingStatus(room);
                    broadcast(room, MessageType.READY_STATUS);
                    broadcast(room, MessageType.START_CANCELLED, player.user.getUsername());
                } else {
                    sender.accept(new Message(MessageType.READY_STATUS, snapshot(room)));
                }
                return;
            }
            updateWaitingStatus(room);
            broadcast(room, MessageType.READY_STATUS);
            startIfReady(room);
        }
    }

    // Chủ động rời phòng: đang chơi thì bỏ trận và không vào lại được.
    public void leave(int userId, Consumer<Message> sender) {
        removePlayer(userId, null, sender, true);
    }

    public void disconnect(int userId) {
        removePlayer(userId, null, null, false);
    }

    // Kết nối `owner` bị đứt: đang chơi thì giữ chỗ để người chơi đăng nhập lại và vào tiếp trận.
    // Nếu người chơi đã nối lại bằng kết nối mới thì kết nối cũ không được kéo họ ra nữa.
    public void disconnect(int userId, Consumer<Message> owner) {
        removePlayer(userId, owner, null, false);
    }

    // Đăng xuất: giống rời phòng nhưng không có phản hồi riêng.
    public void logout(int userId, Consumer<Message> owner) {
        removePlayer(userId, owner, null, true);
    }

    private void removePlayer(int userId, Consumer<Message> owner, Consumer<Message> sender, boolean abandon) {
        Room room = memberships.get(userId);
        if (room == null) {
            if (sender != null) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            }
            return;
        }
        synchronized (room) {
            Player player = room.players.get(userId);
            if (owner != null && (player == null || player.sender != owner)) {
                return;
            }
            if (!memberships.remove(userId, room)) {
                return;
            }
            if (room.status == RoomStatus.PLAYING) {
                player.online = false;
                player.ready = false;
                player.sender = null;
                player.abandoned = abandon;
                if (gameManager != null) { gameManager.disconnect(room.id, userId); }
            } else {
                // Rời phòng giữa lúc đếm ngược thì lượt đếm cũ bị bỏ; nếu những người còn lại vẫn sẵn sàng
                // thì startIfReady bên dưới đếm lại từ đầu.
                room.players.remove(userId);
                updateHost(room);
                updateWaitingStatus(room);
            }
            if (sender != null) {
                sender.accept(new Message(MessageType.ACTION_SUCCESS, "LEAVE_ROOM"));
            }
            broadcast(room, MessageType.ROOM_INFO);
            // Một người chưa ready rời phòng có thể làm tất cả người còn lại ready.
            startIfReady(room);
        }
    }

    public void kick(int hostId, int targetId, Consumer<Message> sender) {
        Room room = memberships.get(hostId);
        if (room == null) {
            sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            return;
        }
        synchronized (room) {
            if (memberships.get(hostId) != room) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            } else if (room.hostId != hostId) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_HOST"));
            } else if (room.status == RoomStatus.PLAYING || room.status == RoomStatus.GAME_STARTING) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "ROOM_PLAYING"));
            } else if (targetId == hostId || !room.players.containsKey(targetId)) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "INVALID_TARGET"));
            } else {
                // Người bị mời ra vẫn tự vào lại hoặc được mời lại vào phòng như bình thường.
                expel(room, targetId, "HOST_KICK");
                sender.accept(new Message(MessageType.ACTION_SUCCESS, "KICK_PLAYER"));
                broadcast(room, MessageType.ROOM_INFO);
                startIfReady(room);
            }
        }
    }

    private void expel(Room room, int userId, String reason) {
        Player player = room.players.remove(userId);
        memberships.remove(userId, room);
        updateHost(room);
        updateWaitingStatus(room);
        if (player != null && player.sender != null) {
            player.sender.accept(new Message(MessageType.KICKED, reason));
        }
    }

    // Chủ phòng rời đi thì người vào phòng sớm nhất còn lại làm chủ phòng.
    private void updateHost(Room room) {
        if (room.players.containsKey(room.hostId)) { return; }
        room.hostId = room.players.keySet().stream().findFirst().orElse(0);
    }

    private void updateWaitingStatus(Room room) {
        room.status = room.players.size() >= MIN_PLAYERS
                ? RoomStatus.READY_CHECK : RoomStatus.WAITING;
    }

    // Cùng khóa phòng: các READY đồng thời chỉ bắt đầu một lượt đếm ngược.
    private void startIfReady(Room room) {
        if (room.status != RoomStatus.READY_CHECK || !everyoneReady(room)) {
            return;
        }
        room.status = RoomStatus.GAME_STARTING;
        long countdown = ++room.countdownId;
        broadcast(room, MessageType.GAME_STARTING);
        broadcast(room, MessageType.START_COUNTDOWN, START_COUNTDOWN_SECONDS);
        startTimer.schedule(() -> {
            synchronized (room) {
                // Lượt đếm đã bị hủy (có người chưa sẵn sàng, rời phòng...) hoặc đã được đếm lại.
                if (room.countdownId == countdown && room.status == RoomStatus.GAME_STARTING) { beginMatch(room); }
            }
        }, START_COUNTDOWN_SECONDS);
    }

    private boolean everyoneReady(Room room) {
        return room.players.size() >= MIN_PLAYERS
                && room.players.values().stream().allMatch(p -> p.ready && p.online);
    }

    private void beginMatch(Room room) {
        if (!everyoneReady(room)) {
            updateWaitingStatus(room);
            broadcast(room, MessageType.READY_STATUS);
            return;
        }
        GamePlan plan = null;
        if (gameManager != null) {
            String error = null;
            try {
                plan = gameManager.prepare(room.players.size());
                if (plan == null) { error = "NOT_ENOUGH_WORDS"; }
            } catch (SQLException e) {
                System.err.println("Không tải được từ khóa: SQLState=" + e.getSQLState());
                error = "GAME_DATABASE_ERROR";
            }
            if (error != null) {
                room.players.values().forEach(p -> p.ready = false);
                updateWaitingStatus(room);
                broadcast(room, MessageType.READY_STATUS);
                for (Player player : room.players.values()) {
                    player.sender.accept(new Message(MessageType.GAME_ERROR, error));
                }
                return;
            }
        }
        room.status = RoomStatus.PLAYING;
        broadcast(room, MessageType.START_GAME);
        if (gameManager != null) {
            Map<Integer, Consumer<Message>> senders = new LinkedHashMap<>();
            room.players.forEach((id, player) -> senders.put(id, player.sender));
            gameManager.start(snapshot(room), plan, senders, room, () -> finishRoom(room));
        }
    }

    // Sau khi đăng nhập: đưa người chơi về phòng đang giữ chỗ (kết nối cũ còn treo)
    // hoặc về trận đang chơi mà họ bị mất kết nối giữa chừng. Trả về true nếu đã đưa về phòng.
    public boolean reconnect(UserInfo user, Consumer<Message> sender) {
        Room held = memberships.get(user.getId());
        if (held != null) {
            synchronized (held) {
                Player player = held.players.get(user.getId());
                if (memberships.get(user.getId()) == held && player != null) {
                    player.sender = sender;
                    restore(held, player, sender);
                    return true;
                }
            }
        }
        for (Room room : rooms.values()) {
            synchronized (room) {
                Player player = room.players.get(user.getId());
                if (room.status == RoomStatus.PLAYING && canRejoin(player)
                        && memberships.putIfAbsent(user.getId(), room) == null) {
                    rejoin(room, player, sender);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canRejoin(Player player) {
        return player != null && !player.online && !player.abandoned;
    }

    // Gọi khi đã giữ khóa phòng và đã đặt chỗ trong memberships.
    private void rejoin(Room room, Player player, Consumer<Message> sender) {
        player.online = true;
        player.sender = sender;
        restore(room, player, sender);
    }

    private void restore(Room room, Player player, Consumer<Message> sender) {
        sender.accept(new Message(MessageType.JOIN_SUCCESS, snapshot(room)));
        if (room.status == RoomStatus.PLAYING && gameManager != null) {
            gameManager.reconnect(room.id, player.user.getId(), sender);
        }
        broadcast(room, MessageType.ROOM_INFO);
    }

    // Vị trí của một người đang online: phòng đang ở (0 nếu chưa vào phòng) và phòng đó có đang chơi không.
    public OnlineUser describe(UserInfo user) {
        Room room = memberships.get(user.getId());
        if (room == null) {
            return new OnlineUser(user.getId(), user.getUsername(), user.getAvatarKey(), 0, false);
        }
        synchronized (room) {
            boolean playing = room.status == RoomStatus.PLAYING || room.status == RoomStatus.GAME_STARTING;
            return new OnlineUser(user.getId(), user.getUsername(), user.getAvatarKey(), room.id, playing);
        }
    }

    // Người trong phòng mời một người đang online và chưa ở phòng nào. Người được mời tự gửi JOIN_ROOM nếu đồng ý.
    public void invite(UserInfo inviter, UserInfo target, Consumer<Message> targetSender, Consumer<Message> reply) {
        Room room = memberships.get(inviter.getId());
        if (room == null) {
            reply.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            return;
        }
        if (target == null || targetSender == null || !"USER".equals(target.getRole())) {
            reply.accept(new Message(MessageType.ACTION_FAILED, "TARGET_OFFLINE"));
            return;
        }
        synchronized (room) {
            String error = null;
            if (memberships.get(inviter.getId()) != room) {
                error = "NOT_IN_ROOM";
            } else if (target.getId() == inviter.getId()) {
                error = "INVALID_INVITE";
            } else if (room.status == RoomStatus.PLAYING || room.status == RoomStatus.GAME_STARTING) {
                error = "ROOM_PLAYING";
            } else if (room.players.size() >= MAX_PLAYERS) {
                error = "ROOM_FULL";
            } else if (memberships.containsKey(target.getId())) {
                error = "TARGET_BUSY";
            } else if (!tryStartCooldown(inviter.getId(), target.getId())) {
                error = "INVITE_COOLDOWN";
            }
            if (error != null) {
                reply.accept(new Message(MessageType.ACTION_FAILED, error));
                return;
            }
            targetSender.accept(new Message(MessageType.INVITATION, new Invitation(inviter.getId(),
                    inviter.getUsername(), target.getId(), target.getUsername(), room.id)));
            reply.accept(new Message(MessageType.ACTION_SUCCESS, "INVITE_PLAYER"));
        }
    }

    // Chat trong phòng (lúc chờ, lúc chơi và lúc xem kết quả). Không cho gửi tin để lộ từ khóa của trận đang chơi.
    public void chat(UserInfo user, Object payload, Consumer<Message> reply) {
        Room room = memberships.get(user.getId());
        if (room == null) {
            reply.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            return;
        }
        String text = payload instanceof String value ? value.strip() : "";
        if (text.isEmpty() || text.length() > CHAT_MAX_LENGTH) {
            reply.accept(new Message(MessageType.ACTION_FAILED, "INVALID_CHAT"));
            return;
        }
        synchronized (room) {
            String error = null;
            long now = clock.getAsLong();
            Long previous = lastChats.get(user.getId());
            if (memberships.get(user.getId()) != room) {
                error = "NOT_IN_ROOM";
            } else if (previous != null && now - previous < CHAT_INTERVAL_NANOS) {
                error = "CHAT_TOO_FAST";
            } else if (gameManager != null && gameManager.revealsWord(room.id, text)) {
                error = "CHAT_HAS_ANSWER";
            }
            if (error != null) {
                reply.accept(new Message(MessageType.ACTION_FAILED, error));
                return;
            }
            lastChats.put(user.getId(), now);
            broadcast(room, MessageType.CHAT_MESSAGE,
                    new ChatMessage(user.getId(), user.getUsername(), text, System.currentTimeMillis()));
        }
    }

    private boolean tryStartCooldown(int inviterId, int targetId) {
        long now = clock.getAsLong();
        long cooldown = TimeUnit.SECONDS.toNanos(INVITE_COOLDOWN_SECONDS);
        lastInvites.values().removeIf(time -> now - time >= cooldown);
        return lastInvites.putIfAbsent(((long) inviterId << 32) | (targetId & 0xffffffffL), now) == null;
    }

    public void handleGameMessage(int userId, Message message, long receivedAt, Consumer<Message> reply) {
        Room room = memberships.get(userId);
        if (room == null) { reply.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM")); return; }
        synchronized (room) {
            if (memberships.get(userId) != room) {
                reply.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            } else if (gameManager == null) {
                reply.accept(new Message(MessageType.ACTION_FAILED, "GAME_UNAVAILABLE"));
            } else if (message.getType() == MessageType.DRAW_UPLOAD) {
                gameManager.upload(room.id, userId, message.getData(), reply);
            } else if (message.getType() == MessageType.DRAW_DRAFT) {
                gameManager.draft(room.id, userId, message.getData());
            } else if (message.getType() == MessageType.GUESS) {
                gameManager.guess(room.id, userId, message.getData(), receivedAt, reply);
            } else if (message.getType() == MessageType.REPORT_DRAWING) {
                gameManager.report(room.id, userId, message.getData(), reply);
            }
        }
    }

    private void finishRoom(Room room) {
        synchronized (room) {
            room.status = RoomStatus.FINISHED;
            broadcast(room, MessageType.ROOM_INFO);
            room.players.values().removeIf(p -> !p.online);
            room.players.values().forEach(p -> p.ready = false);
            updateHost(room);
            updateWaitingStatus(room);
            broadcast(room, MessageType.ROOM_INFO);
        }
    }

    private RoomInfo snapshot(Room room) {
        List<PlayerInfo> players = room.players.values().stream()
                .map(p -> new PlayerInfo(p.user.getId(), p.user.getUsername(), p.ready, p.online,
                        p.user.getAvatarKey()))
                .toList();
        return new RoomInfo(room.id, room.status, players, room.hostId);
    }

    private void broadcast(Room room, MessageType type) {
        broadcast(room, type, snapshot(room));
    }

    private void broadcast(Room room, MessageType type, Object data) {
        for (Player player : room.players.values()) {
            if (player.online && player.sender != null) {
                player.sender.accept(new Message(type, data));
            }
        }
    }

    private static class Room {
        final int id;
        final Map<Integer, Player> players = new LinkedHashMap<>();
        RoomStatus status = RoomStatus.WAITING;
        int hostId;
        // Tăng mỗi lần bắt đầu đếm ngược, để việc hẹn giờ của lượt đếm cũ biết mình đã hết hiệu lực.
        long countdownId;

        Room(int id) { this.id = id; }
    }

    private static class Player {
        final UserInfo user;
        Consumer<Message> sender;
        boolean ready;
        boolean online = true;
        // Chủ động rời phòng/đăng xuất khi đang chơi thì không được vào lại trận đó.
        boolean abandoned;

        Player(UserInfo user, Consumer<Message> sender) {
            this.user = user;
            this.sender = sender;
        }
    }
}
