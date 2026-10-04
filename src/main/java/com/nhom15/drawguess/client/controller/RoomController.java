package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.view.LobbyView;
import com.nhom15.drawguess.client.view.HistoryView;
import com.nhom15.drawguess.client.view.RoomListView;
import com.nhom15.drawguess.common.protocol.*;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.Consumer;

// Mọi phương thức của controller này được gọi trên JavaFX Application Thread.
public class RoomController {
    private final StackPane root;
    private final UserInfo user;
    private final RoomListView roomList;
    private final LobbyView lobby;
    private final Consumer<Message> send;
    private final Runnable logout;
    private final HistoryView history;
    private boolean viewingHistory;
    private GameController game;
    private RoomInfo currentRoom;
    private boolean loggingOut;

    public RoomController(StackPane root, UserInfo user, Consumer<Message> send, Runnable logout) {
        this.root = root;
        this.user = user;
        this.send = send;
        this.logout = logout;
        roomList = new RoomListView(user, this::join, this::refresh, logout);
        history = new HistoryView(send, this::show);
        roomList.setOnHistory(() -> {
            viewingHistory = true;
            root.getChildren().setAll(history);
            send.accept(new Message(MessageType.GET_HISTORY));
        });
        lobby = new LobbyView(user.getId(), this::toggleReady, this::leave, logout);
    }

    public void show() {
        viewingHistory = false;
        root.getChildren().setAll(roomList);
        refresh();
    }

    private void refresh() {
        roomList.setBusy(true);
        roomList.showMessage("Đang tải danh sách phòng...");
        send.accept(new Message(MessageType.GET_ROOMS));
    }

    private void join() {
        RoomInfo selected = roomList.getSelectedRoom();
        if (selected == null) {
            roomList.showMessage("Hãy chọn một phòng.");
            return;
        }
        roomList.setBusy(true);
        roomList.showMessage("Đang vào phòng...");
        send.accept(new Message(MessageType.JOIN_ROOM, selected.getRoomId()));
    }

    private void toggleReady() {
        if (currentRoom == null) { return; }
        boolean ready = currentRoom.getPlayers().stream()
                .anyMatch(p -> p.getUserId() == user.getId() && p.isReady());
        lobby.setBusy(true);
        send.accept(new Message(ready ? MessageType.CANCEL_READY : MessageType.READY));
    }

    private void leave() {
        if (game != null) { game.setLeaving(); }
        lobby.setBusy(true);
        lobby.showMessage("Đang rời phòng...");
        send.accept(new Message(MessageType.LEAVE_ROOM));
    }

    public void setLoggingOut() {
        loggingOut = true;
        roomList.setBusy(true);
        lobby.setBusy(true);
        roomList.showMessage("Đang đăng xuất...");
        lobby.showMessage("Đang đăng xuất...");
        if (game != null) { game.setLeaving(); game.close(); }
    }

    public void handleMessage(Message message) {
        if (loggingOut) { return; }
        if (message.getType() == MessageType.HISTORY_LIST || message.getType() == MessageType.HISTORY_DETAIL
                || message.getType() == MessageType.HISTORY_IMAGE || message.getType() == MessageType.HISTORY_IMAGE_FAILED
                || message.getType() == MessageType.HISTORY_FAILED) {
            if (viewingHistory) { history.handleMessage(message); }
            else if (game != null) { game.handleMessage(message); }
            else if (message.getType() == MessageType.HISTORY_FAILED) { lobby.showMessage("Không lưu được lịch sử trận. Kiểm tra MySQL và history.sql."); }
            return;
        }
        if (message.getType() == MessageType.DRAW_WORD && message.getData() instanceof DrawingPrompt prompt
                && currentRoom != null && currentRoom.getRoomId() == prompt.getRoomId()) {
            if (game != null) { game.close(); }
            game = new GameController(root, user.getId(), prompt, send, this::returnToLobby, this::leave, logout);
            return;
        }
        if (game != null) { game.handleMessage(message); }
        if (message.getType() == MessageType.GAME_ERROR && game == null) {
            lobby.showMessage(GameController.errorMessage(String.valueOf(message.getData())));
            return;
        }
        switch (message.getType()) {
            case ROOM_LIST -> {
                if (message.getData() instanceof List<?> data && data.stream().allMatch(RoomInfo.class::isInstance)) {
                    roomList.updateRooms(data.stream().map(RoomInfo.class::cast).toList());
                    roomList.setBusy(false);
                    roomList.showMessage("Chọn phòng để tham gia.");
                }
            }
            case JOIN_SUCCESS -> {
                if (message.getData() instanceof RoomInfo room) {
                    currentRoom = room;
                    lobby.update(room);
                    root.getChildren().setAll(lobby);
                }
            }
            case ROOM_INFO, READY_STATUS, GAME_STARTING, START_GAME -> {
                if (message.getData() instanceof RoomInfo room && currentRoom != null
                        && room.getRoomId() == currentRoom.getRoomId()) {
                    currentRoom = room;
                    lobby.update(room);
                }
            }
            case ACTION_SUCCESS -> {
                if ("LEAVE_ROOM".equals(message.getData())) {
                    if (game != null) { game.close(); game = null; }
                    currentRoom = null;
                    show();
                }
            }
            case JOIN_FAILED, ACTION_FAILED -> {
                String reason = switch (String.valueOf(message.getData())) {
                    case "ROOM_NOT_FOUND" -> "Phòng không tồn tại.";
                    case "ROOM_FULL" -> "Phòng đã đủ 8 người.";
                    case "ROOM_PLAYING" -> "Phòng đã bắt đầu trận đấu.";
                    case "ALREADY_IN_ROOM" -> "Bạn đang ở trong một phòng.";
                    case "NOT_IN_ROOM" -> "Bạn chưa tham gia phòng.";
                    case "NOT_LOGGED_IN" -> "Hãy đăng nhập trước khi tham gia phòng.";
                    case "FORBIDDEN" -> "Tài khoản không có quyền tham gia phòng.";
                    default -> "Yêu cầu không hợp lệ.";
                };
                roomList.setBusy(false);
                lobby.setBusy(false);
                if (currentRoom == null) { roomList.showMessage(reason); }
                else { lobby.showMessage(reason); }
            }
            default -> { }
        }
    }

    private void returnToLobby() {
        if (game != null) { game.close(); game = null; }
        if (currentRoom == null) { show(); }
        else { lobby.update(currentRoom); root.getChildren().setAll(lobby); }
    }

    public void close() {
        loggingOut = true;
        if (game != null) { game.close(); }
    }
}
