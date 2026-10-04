package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.sql.SQLException;

public class RoomManager {
    public static final int ROOM_COUNT = 100;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 8;
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, Room> memberships = new ConcurrentHashMap<>();
    private final GameManager gameManager;

    public RoomManager() {
        this(null);
    }

    public RoomManager(GameManager gameManager) {
        this.gameManager = gameManager;
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
            if (room.status == RoomStatus.PLAYING || room.status == RoomStatus.GAME_STARTING) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "ROOM_PLAYING"));
                return;
            }
            player.ready = ready;
            updateWaitingStatus(room);
            broadcast(room, MessageType.READY_STATUS);
            startIfReady(room);
        }
    }

    public void leave(int userId, Consumer<Message> sender) {
        removePlayer(userId, sender);
    }

    public void disconnect(int userId) {
        removePlayer(userId, null);
    }

    private void removePlayer(int userId, Consumer<Message> sender) {
        Room room = memberships.get(userId);
        if (room == null) {
            if (sender != null) {
                sender.accept(new Message(MessageType.ACTION_FAILED, "NOT_IN_ROOM"));
            }
            return;
        }
        synchronized (room) {
            if (!memberships.remove(userId, room)) {
                return;
            }
            if (room.status == RoomStatus.PLAYING) {
                Player player = room.players.get(userId);
                player.online = false;
                player.ready = false;
                player.sender = null;
                if (gameManager != null) { gameManager.disconnect(room.id, userId); }
            } else {
                room.players.remove(userId);
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

    private void updateWaitingStatus(Room room) {
        room.status = room.players.size() >= MIN_PLAYERS
                ? RoomStatus.READY_CHECK : RoomStatus.WAITING;
    }

    private void startIfReady(Room room) {
        if (room.status != RoomStatus.READY_CHECK || room.players.size() < MIN_PLAYERS
                || room.players.values().stream().anyMatch(p -> !p.ready || !p.online)) {
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
                broadcast(room, MessageType.READY_STATUS);
                for (Player player : room.players.values()) {
                    player.sender.accept(new Message(MessageType.GAME_ERROR, error));
                }
                return;
            }
        }
        // Cùng khóa phòng: các READY đồng thời chỉ khởi động trận một lần.
        room.status = RoomStatus.GAME_STARTING;
        broadcast(room, MessageType.GAME_STARTING);
        room.status = RoomStatus.PLAYING;
        broadcast(room, MessageType.START_GAME);
        if (gameManager != null) {
            Map<Integer, Consumer<Message>> senders = new LinkedHashMap<>();
            room.players.forEach((id, player) -> senders.put(id, player.sender));
            gameManager.start(snapshot(room), plan, senders, room, () -> finishRoom(room));
        }
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
            } else if (message.getType() == MessageType.GUESS) {
                gameManager.guess(room.id, userId, message.getData(), receivedAt, reply);
            }
        }
    }

    private void finishRoom(Room room) {
        synchronized (room) {
            room.status = RoomStatus.FINISHED;
            broadcast(room, MessageType.ROOM_INFO);
            room.players.values().removeIf(p -> !p.online);
            room.players.values().forEach(p -> p.ready = false);
            updateWaitingStatus(room);
            broadcast(room, MessageType.ROOM_INFO);
        }
    }

    private RoomInfo snapshot(Room room) {
        List<PlayerInfo> players = room.players.values().stream()
                .map(p -> new PlayerInfo(p.user.getId(), p.user.getUsername(), p.ready, p.online))
                .toList();
        return new RoomInfo(room.id, room.status, players);
    }

    private void broadcast(Room room, MessageType type) {
        RoomInfo info = snapshot(room);
        for (Player player : room.players.values()) {
            if (player.online && player.sender != null) {
                player.sender.accept(new Message(type, info));
            }
        }
    }

    private static class Room {
        final int id;
        final Map<Integer, Player> players = new LinkedHashMap<>();
        RoomStatus status = RoomStatus.WAITING;

        Room(int id) { this.id = id; }
    }

    private static class Player {
        final UserInfo user;
        Consumer<Message> sender;
        boolean ready;
        boolean online = true;

        Player(UserInfo user, Consumer<Message> sender) {
            this.user = user;
            this.sender = sender;
        }
    }
}
