package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.view.LobbyView;
import com.nhom15.drawguess.client.view.HistoryView;
import com.nhom15.drawguess.client.view.RoomListView;
import com.nhom15.drawguess.client.view.UiTheme;
import com.nhom15.drawguess.client.view.HomeView;
import com.nhom15.drawguess.client.view.AvatarPickerView;
import com.nhom15.drawguess.client.view.InvitationPrompt;
import com.nhom15.drawguess.common.protocol.*;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Mọi phương thức của controller này được gọi trên JavaFX Application Thread.
public class RoomController {
    private final StackPane root;
    private UserInfo user;
    private final RoomListView roomList;
    private final LobbyView lobby;
    private final HomeView home;
    private final AvatarPickerView avatarPicker;
    private final Consumer<Message> send;
    private final Runnable logout;
    private final HistoryView history;
    private boolean viewingHistory;
    private GameController game;
    private RoomInfo currentRoom;
    private boolean loggingOut;
    // Thông báo hiển thị ở danh sách phòng sau khi tải xong, ví dụ lý do bị đưa ra khỏi phòng.
    private String pendingRoomListNotice;
    // Hộp thoại lời mời đang mở; lời mời mới thay lời mời cũ.
    private Dialog<ButtonType> invitationPrompt;
    private String lastInvitedName;
    // Server vừa đưa người chơi về lại trận đang chơi (sau khi mất kết nối).
    private boolean resumingMatch;
    // Chat của phòng hiện tại, để màn chơi hiện cả những tin đã nhắn ở phòng chờ.
    private final List<ChatMessage> roomChat = new ArrayList<>();
    private static final int CHAT_HISTORY = 200;

    public RoomController(StackPane root, UserInfo user, Consumer<Message> send, Runnable logout) {
        this.root = root;
        this.user = user;
        this.send = send;
        this.logout = logout;
        home = new HomeView(user, this::showRooms, this::showHistory, this::showAvatar, logout);
        avatarPicker = new AvatarPickerView(user, this::saveAvatar, this::show);
        roomList = new RoomListView(user, this::join, this::refresh, this::show);
        history = new HistoryView(send, this::show);
        lobby = new LobbyView(user.getId(), this::toggleReady, this::leave, logout, this::kick);
        lobby.setOnInvite(this::invite, () -> send.accept(new Message(MessageType.GET_ONLINE_USERS)));
        roomList.setOnShowOnline(() -> send.accept(new Message(MessageType.GET_ONLINE_USERS)));
        lobby.setOnNotReady(() -> send.accept(new Message(MessageType.CANCEL_READY)));
        lobby.setOnStagesChanged(stages -> send.accept(new Message(MessageType.SET_STAGES, new ArrayList<>(stages))));
        lobby.setOnChat(text -> send.accept(new Message(MessageType.CHAT_SEND, text)));
    }

    private void resetChat() {
        roomChat.clear();
        lobby.clearChat();
    }

    private void invite(OnlineUser target) {
        lastInvitedName = target.getUsername();
        lobby.showMessage("Đang gửi lời mời tới " + target.getUsername() + "...");
        send.accept(new Message(MessageType.INVITE_PLAYER, target.getUserId()));
    }

    private void showInvitation(Invitation invitation) {
        // Đã ở trong phòng (ví dụ vừa tự vào phòng khác) thì không làm phiền, báo từ chối luôn.
        if (currentRoom != null) {
            declineInvitation(invitation);
            return;
        }
        // Đóng hộp thoại cũ (đã đóng thì không làm gì); hộp thoại cũ được tính là từ chối.
        if (invitationPrompt != null) { invitationPrompt.close(); }
        invitationPrompt = InvitationPrompt.show(root.getScene() == null ? null : root.getScene().getWindow(),
                invitation, () -> acceptInvitation(invitation), () -> declineInvitation(invitation));
    }

    private void acceptInvitation(Invitation invitation) {
        if (loggingOut || currentRoom != null) { return; }
        // Chuyển về danh sách phòng để nếu không vào được thì người chơi vẫn thấy lý do.
        showRooms();
        roomList.setBusy(true);
        send.accept(new Message(MessageType.JOIN_ROOM, invitation.getRoomId()));
    }

    private void declineInvitation(Invitation invitation) {
        if (!loggingOut) { send.accept(new Message(MessageType.DECLINE_INVITE, invitation)); }
    }

    public void show() {
        viewingHistory = false;
        UiTheme.show(root, home);
    }

    private void showRooms() {
        viewingHistory = false;
        UiTheme.show(root, roomList);
        refresh();
    }

    private void showHistory() {
        viewingHistory = true;
        UiTheme.show(root, history);
        send.accept(new Message(MessageType.GET_HISTORY));
    }

    private void showAvatar() {
        viewingHistory = false;
        avatarPicker.updateUser(user);
        avatarPicker.showInfo("Chọn một bức tranh làm hình đại diện của bạn.");
        UiTheme.show(root, avatarPicker);
    }

    private void saveAvatar() {
        String selected = avatarPicker.getSelectedAvatar();
        if (selected.equals(user.getAvatarKey())) {
            avatarPicker.showInfo("Đây đã là avatar hiện tại của bạn.");
            return;
        }
        avatarPicker.setBusy(true);
        avatarPicker.showInfo("Đang lưu avatar...");
        send.accept(new Message(MessageType.UPDATE_AVATAR, selected));
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

    private void kick(int targetId) {
        lobby.setBusy(true);
        send.accept(new Message(MessageType.KICK_PLAYER, targetId));
    }

    private void leave() {
        if (game != null) { game.setLeaving(); }
        lobby.setBusy(true);
        lobby.showMessage("Đang rời phòng...");
        send.accept(new Message(MessageType.LEAVE_ROOM));
    }

    public void setLoggingOut() {
        loggingOut = true;
        home.setBusy(true);
        avatarPicker.setBusy(true);
        roomList.setBusy(true);
        lobby.setBusy(true);
        roomList.showMessage("Đang đăng xuất...");
        lobby.showMessage("Đang đăng xuất...");
        lobby.closeInvitePanel();
        roomList.closeOnlineList();
        if (invitationPrompt != null) { invitationPrompt.close(); }
        if (game != null) { game.setLeaving(); game.close(); }
    }

    public void handleMessage(Message message) {
        if (loggingOut) { return; }
        if (message.getType() == MessageType.AVATAR_UPDATED) {
            if (message.getData() instanceof UserInfo updated && updated.getId() == user.getId()) {
                user = updated;
                home.updateUser(updated);
                roomList.updateUser(updated);
                avatarPicker.updateUser(updated);
                avatarPicker.setBusy(false);
                avatarPicker.showMessage("Đã lưu avatar vào tài khoản.", true);
            }
            return;
        }
        if (message.getType() == MessageType.AVATAR_FAILED) {
            avatarPicker.setBusy(false);
            avatarPicker.showMessage(switch (String.valueOf(message.getData())) {
                case "INVALID_AVATAR" -> "Avatar không hợp lệ. Hãy chọn lại.";
                case "DATABASE_ERROR" -> "Không lưu được avatar. Hãy kiểm tra MySQL và registration.sql.";
                default -> "Không thể cập nhật avatar lúc này.";
            }, false);
            return;
        }
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
            if (game != null && game.isFor(prompt.getMatchId())) {
                game.nextStage(prompt);
                return;
            }
            if (game != null) { game.close(); }
            Map<Integer, String> avatars = currentRoom.getPlayers().stream()
                    .collect(Collectors.toMap(PlayerInfo::getUserId, PlayerInfo::getAvatarKey, (a, b) -> a));
            game = new GameController(root, user.getId(), prompt, avatars, send, this::returnToLobby, this::leave, logout);
            game.setOnPlayAgain(this::playAgain);
            game.loadChat(roomChat);
            if (resumingMatch) {
                resumingMatch = false;
                game.showMessage("Đã vào lại trận. Bạn chơi tiếp từ chỗ đang dở.");
            }
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
                    roomList.showMessage(pendingRoomListNotice != null ? pendingRoomListNotice : "Chọn phòng để tham gia.");
                    pendingRoomListNotice = null;
                }
            }
            case ONLINE_COUNT -> {
                if (message.getData() instanceof Integer count) { roomList.updateOnlineCount(count); }
            }
            case ONLINE_USERS -> {
                if (message.getData() instanceof List<?> data && data.stream().allMatch(OnlineUser.class::isInstance)) {
                    List<OnlineUser> users = data.stream().map(OnlineUser.class::cast).toList();
                    lobby.showOnlineUsers(users);
                    roomList.showOnlineUsers(users);
                }
            }
            case TOPICS -> {
                if (message.getData() instanceof List<?> data && data.stream().allMatch(String.class::isInstance)) {
                    lobby.setTopics(data.stream().map(String.class::cast).toList());
                }
            }
            case CHAT_MESSAGE -> {
                if (message.getData() instanceof ChatMessage chat && currentRoom != null) {
                    roomChat.add(chat);
                    if (roomChat.size() > CHAT_HISTORY) { roomChat.removeFirst(); }
                    lobby.addChat(chat);
                }
            }
            case START_COUNTDOWN -> {
                if (message.getData() instanceof Integer seconds && currentRoom != null) { lobby.startCountdown(seconds); }
            }
            case START_CANCELLED -> {
                if (message.getData() instanceof String name && currentRoom != null) {
                    lobby.stopCountdown();
                    lobby.showMessage(name.equals(user.getUsername())
                            ? "Bạn đã báo chưa sẵn sàng. Bấm Sẵn sàng khi bạn chuẩn bị xong."
                            : name + " chưa sẵn sàng nên trận chưa bắt đầu. Trận sẽ đếm ngược lại khi mọi người cùng sẵn sàng.");
                }
            }
            case INVITATION -> {
                if (message.getData() instanceof Invitation invitation) { showInvitation(invitation); }
            }
            case INVITE_DECLINED -> {
                if (message.getData() instanceof Invitation invitation && currentRoom != null) {
                    lobby.showMessage(invitation.getToUsername() + " không nhận lời mời.");
                }
            }
            case JOIN_SUCCESS -> {
                if (message.getData() instanceof RoomInfo room) {
                    viewingHistory = false;
                    if (invitationPrompt != null) { invitationPrompt.close(); }
                    if (currentRoom == null || currentRoom.getRoomId() != room.getRoomId()) { resetChat(); }
                    currentRoom = room;
                    send.accept(new Message(MessageType.GET_TOPICS));
                    roomList.closeOnlineList();
                    lobby.update(room);
                    // Phòng đang chơi mà vẫn vào được nghĩa là server đưa người chơi về lại trận của mình.
                    resumingMatch = room.getStatus() == RoomStatus.PLAYING;
                    if (resumingMatch) { lobby.showMessage("Đang vào lại trận đang chơi..."); }
                    UiTheme.show(root, lobby);
                }
            }
            case ROOM_INFO, READY_STATUS, GAME_STARTING, START_GAME -> {
                if (message.getData() instanceof RoomInfo room && currentRoom != null
                        && room.getRoomId() == currentRoom.getRoomId()) {
                    currentRoom = room;
                    lobby.update(room);
                    if (game != null) { game.showRematch(room); }
                }
            }
            case KICKED -> {
                if (game != null) { game.close(); game = null; }
                currentRoom = null;
                resetChat();
                lobby.closeInvitePanel();
                lobby.stopCountdown();
                pendingRoomListNotice = "Bạn đã bị chủ phòng mời ra khỏi phòng.";
                if (!viewingHistory) { showRooms(); }
            }
            case ACTION_SUCCESS -> {
                if ("INVITE_PLAYER".equals(message.getData()) && currentRoom != null) {
                    lobby.showMessage("Đã gửi lời mời tới " + lastInvitedName + ".");
                }
                if ("LEAVE_ROOM".equals(message.getData())) {
                    if (game != null) { game.close(); game = null; }
                    lobby.closeInvitePanel();
                    lobby.stopCountdown();
                    currentRoom = null;
                    resetChat();
                    showRooms();
                }
            }
            case JOIN_FAILED, ACTION_FAILED -> {
                String code = String.valueOf(message.getData());
                String chatError = GameController.chatError(code);
                if (chatError != null) {
                    // Đang ở màn chơi thì GameController đã hiện lỗi trong khung chat của trận.
                    if (game == null) { lobby.showChatNotice(chatError); }
                    return;
                }
                // Đổi vòng đấu không thành công: hiện lại vòng đấu đúng theo server.
                if (currentRoom != null && List.of("INVALID_STAGES", "NOT_HOST", "ROOM_PLAYING").contains(code)) {
                    lobby.update(currentRoom);
                }
                String reason = switch (code) {
                    case "ROOM_NOT_FOUND" -> "Phòng không tồn tại.";
                    case "ROOM_FULL" -> "Phòng đã đủ 8 người.";
                    case "ROOM_PLAYING" -> "Phòng đã bắt đầu trận đấu.";
                    case "ALREADY_IN_ROOM" -> "Bạn đang ở trong một phòng.";
                    case "NOT_IN_ROOM" -> "Bạn chưa tham gia phòng.";
                    case "NOT_LOGGED_IN" -> "Hãy đăng nhập trước khi tham gia phòng.";
                    case "FORBIDDEN" -> "Tài khoản không có quyền tham gia phòng.";
                    case "NOT_HOST" -> "Chỉ chủ phòng mới làm được việc này.";
                    case "INVALID_STAGES" -> "Vòng đấu không hợp lệ: cần 1–5 vòng và không trùng chủ đề.";
                    case "INVALID_TARGET" -> "Người chơi này không còn trong phòng.";
                    case "TARGET_OFFLINE" -> "Người này không còn online.";
                    case "TARGET_BUSY" -> "Người này đang ở trong một phòng khác.";
                    case "INVITE_COOLDOWN" -> "Bạn vừa mời người này. Hãy chờ vài giây rồi mời lại.";
                    case "INVALID_INVITE" -> "Không thể mời người chơi này.";
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

    // "Chơi lại" ở màn kết quả: về phòng chờ và tự báo sẵn sàng; cả phòng cùng sẵn sàng thì đếm ngược vào ván mới.
    private void playAgain() {
        returnToLobby();
        if (currentRoom == null) { return; }
        boolean ready = currentRoom.getPlayers().stream().anyMatch(p -> p.getUserId() == user.getId() && p.isReady());
        if (!ready) {
            lobby.setBusy(true);
            send.accept(new Message(MessageType.READY));
        }
    }

    private void returnToLobby() {
        if (game != null) { game.close(); game = null; }
        if (currentRoom == null) { showRooms(); }
        else { lobby.update(currentRoom); UiTheme.show(root, lobby); }
    }

    public void close() {
        loggingOut = true;
        lobby.closeInvitePanel();
        lobby.stopCountdown();
        roomList.closeOnlineList();
        if (invitationPrompt != null) { invitationPrompt.close(); }
        if (game != null) { game.close(); }
    }
}
