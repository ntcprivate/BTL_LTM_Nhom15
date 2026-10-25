package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class LobbyView extends VBox {
    private final int userId;
    private final Label title = new Label();
    private final Label status = new Label();
    private final GridPane players = new GridPane();
    private final Label playerCount = new Label("0/8");
    private final Label readyCount = new Label("0/0");
    private final Button readyButton = new Button("Sẵn sàng");
    private final Button leaveButton = new Button("Rời phòng");
    private final Button logoutButton = new Button("Đăng xuất");
    private final IntConsumer kick;
    private final Button inviteButton = Icons.attach(new Button("Mời người chơi"), Icons.Glyph.USER_PLUS, 16);
    private Consumer<OnlineUser> invite = user -> {};
    private Runnable requestOnlineUsers = () -> {};
    private OnlinePlayersDialog invitePanel;
    private boolean canInvite;
    private boolean playing;
    private final HBox actions;
    // Đếm ngược khi mọi người đã sẵn sàng; bấm "Tôi chưa sẵn sàng!" thì không vào trận.
    private final HBox countdownBar;
    private final Label countdownNumber = new Label();
    private final Label countdownTitle = new Label();
    private final Button notReadyButton = new Button("Tôi chưa sẵn sàng!");
    private Runnable onNotReady = () -> {};
    private Timeline countdownTimer;
    private int countdownLeft;

    public LobbyView(int userId, Runnable ready, Runnable leave, Runnable logout, IntConsumer kick) {
        this.userId = userId;
        this.kick = kick;
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(10);
        setPadding(new Insets(18));
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Danh sách người chơi và trạng thái sẵn sàng");
        subtitle.getStyleClass().add("subtle-text");
        VBox heading = new VBox(4, title, subtitle);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        ArtworkCard painting = new ArtworkCard(
                "/images/Almond-Blossoms-1890.jpg", "Almond Blossoms • 1890", 300, 92);
        HBox header = new HBox(16, heading, headerSpacer, painting);
        header.setAlignment(Pos.CENTER_LEFT);
        players.setHgap(14);
        players.setVgap(14);
        for (int index = 0; index < 4; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setHgrow(Priority.ALWAYS);
            players.getColumnConstraints().add(column);
        }
        for (int index = 0; index < 2; index++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(50);
            row.setVgrow(Priority.ALWAYS);
            players.getRowConstraints().add(row);
        }
        Label boardTitle = new Label("Người chơi trong phòng");
        boardTitle.getStyleClass().add("section-title");
        inviteButton.getStyleClass().add("secondary-button");
        inviteButton.setMinWidth(Region.USE_PREF_SIZE);
        inviteButton.setTooltip(new Tooltip("Xem ai đang online và mời họ vào phòng"));
        inviteButton.setOnAction(event -> openInvitePanel());
        Region boardSpacer = new Region();
        HBox.setHgrow(boardSpacer, Priority.ALWAYS);
        HBox boardHeader = new HBox(10, boardTitle, boardSpacer, inviteButton);
        boardHeader.setAlignment(Pos.CENTER_LEFT);
        VBox playerBoard = new VBox(14, boardHeader, players);
        playerBoard.getStyleClass().add("lobby-board");
        VBox.setVgrow(players, Priority.ALWAYS);
        VBox.setVgrow(playerBoard, Priority.ALWAYS);
        HBox statistics = new HBox(12,
                statistic(playerCount, "SỐ NGƯỜI CHƠI"),
                statistic(readyCount, "ĐÃ SẴN SÀNG"));
        readyButton.setOnAction(event -> ready.run());
        leaveButton.setOnAction(event -> leave.run());
        logoutButton.setOnAction(event -> logout.run());
        readyButton.getStyleClass().add("primary-button");
        leaveButton.getStyleClass().add("ghost-button");
        logoutButton.getStyleClass().add("danger-button");
        actions = new HBox(12, readyButton, leaveButton, logoutButton);
        actions.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        UiTheme.status(status, "info");
        countdownBar = countdownBar();
        getChildren().addAll(header, statistics, playerBoard, status, actions, countdownBar);
    }

    private HBox countdownBar() {
        countdownNumber.getStyleClass().add("start-countdown-number");
        countdownNumber.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        countdownTitle.getStyleClass().add("start-countdown-title");
        Label hint = new Label("Chưa chuẩn bị xong? Bấm nút bên cạnh để cả phòng chờ thêm.");
        hint.getStyleClass().add("start-countdown-hint");
        hint.setWrapText(true);
        VBox text = new VBox(2, countdownTitle, hint);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        notReadyButton.getStyleClass().add("danger-button");
        notReadyButton.setMinWidth(Region.USE_PREF_SIZE);
        notReadyButton.setOnAction(event -> {
            notReadyButton.setDisable(true);
            notReadyButton.setText("Đang báo...");
            onNotReady.run();
        });
        HBox bar = new HBox(16, countdownNumber, text, notReadyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("start-countdown");
        bar.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        bar.setVisible(false);
        bar.setManaged(false);
        return bar;
    }

    public void setOnNotReady(Runnable action) {
        onNotReady = action;
    }

    // Server báo mọi người đã sẵn sàng: hiện đếm ngược thay cho hàng nút Sẵn sàng / Rời phòng.
    public void startCountdown(int seconds) {
        stopTimer();
        countdownLeft = seconds;
        notReadyButton.setText("Tôi chưa sẵn sàng!");
        notReadyButton.setDisable(false);
        showCountdown(true);
        renderCountdown();
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            countdownLeft = Math.max(0, countdownLeft - 1);
            renderCountdown();
        }));
        countdownTimer.setCycleCount(seconds);
        countdownTimer.play();
    }

    public void stopCountdown() {
        stopTimer();
        showCountdown(false);
    }

    public boolean isCountingDown() {
        return countdownBar.isVisible();
    }

    private void stopTimer() {
        if (countdownTimer != null) { countdownTimer.stop(); }
        countdownTimer = null;
    }

    private void showCountdown(boolean visible) {
        countdownBar.setVisible(visible);
        countdownBar.setManaged(visible);
        actions.setVisible(!visible);
        actions.setManaged(!visible);
    }

    private void renderCountdown() {
        countdownNumber.setText(String.valueOf(countdownLeft));
        countdownTitle.setText(countdownLeft > 0 ? "Trận đấu bắt đầu sau " + countdownLeft + " giây" : "Đang vào trận...");
        countdownBar.setAccessibleText(countdownTitle.getText());
        if (countdownLeft == 0) { notReadyButton.setDisable(true); }
        if (!UiTheme.reducedMotion()) {
            ScaleTransition pulse = new ScaleTransition(Duration.millis(220), countdownNumber);
            pulse.setFromX(1.18);
            pulse.setFromY(1.18);
            pulse.setToX(1);
            pulse.setToY(1);
            pulse.play();
        }
    }

    private void confirmKick(PlayerInfo player) {
        if (ThemedDialog.confirm(this, ThemedDialog.Tone.DANGER, Icons.Glyph.USER_MINUS,
                "Mời " + player.getUsername() + " ra khỏi phòng?",
                "Người này sẽ quay về danh sách phòng. Họ vẫn có thể tự vào lại hoặc được mời lại.", "Mời ra", "Hủy")) {
            kick.accept(player.getUserId());
        }
    }

    private VBox statistic(Label value, String caption) {
        value.getStyleClass().add("stat-value");
        Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        VBox card = new VBox(2, value, label);
        card.getStyleClass().add("stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox playerSlot(PlayerInfo player, int position, int hostId, boolean canKick) {
        Node avatar;
        if (player == null) {
            Label placeholder = new Label("+");
            placeholder.getStyleClass().add("player-avatar");
            avatar = placeholder;
        } else {
            avatar = new AvatarView(player.getAvatarKey(), 48);
        }
        Label name = new Label(player == null ? "Vị trí " + (position + 1)
                : player.getUsername().toUpperCase(Locale.ROOT)
                + (player.getUserId() == userId ? "  • Bạn" : ""));
        name.getStyleClass().add("player-name");
        if (player != null) {
            name.setTooltip(new Tooltip(player.getUsername()));
        }
        String state = player == null ? "Đang chờ người chơi"
                : !player.isOnline() ? "Mất kết nối"
                : player.isReady() ? "Đã sẵn sàng" : "Chưa sẵn sàng";
        Label playerState = new Label(state);
        playerState.getStyleClass().add("player-state");
        if (player != null) {
            playerState.getStyleClass().add(player.isOnline() && player.isReady()
                    ? "player-ready" : player.isOnline() ? "player-waiting" : "player-offline");
        }
        VBox information = new VBox(5, name, playerState);
        if (player != null && player.getUserId() == hostId) {
            Label host = Icons.attach(new Label("Chủ phòng"), Icons.Glyph.STAR, 13);
            host.setGraphicTextGap(4);
            host.getStyleClass().add("room-host");
            information.getChildren().addFirst(host);
        }
        if (player != null && canKick && player.getUserId() != userId) {
            Button kickButton = new Button("Mời ra");
            kickButton.getStyleClass().addAll("danger-button", "kick-button");
            kickButton.setOnAction(event -> confirmKick(player));
            information.getChildren().add(kickButton);
        }
        Label mark = new Label();
        mark.getStyleClass().add("ready-mark");
        if (player != null && player.isReady()) {
            Icons.attach(mark, Icons.Glyph.CHECK, 22);
            mark.setAccessibleText("Đã sẵn sàng");
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox identity = new HBox(10, avatar, spacer, mark);
        identity.setAlignment(Pos.CENTER_LEFT);
        VBox slot = new VBox(10, identity, information);
        slot.setAlignment(Pos.CENTER_LEFT);
        slot.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        slot.getStyleClass().add(player == null ? "player-slot-empty" : "player-slot");
        return slot;
    }

    public void update(RoomInfo room) {
        title.setText(String.format("Phòng %03d — %d/8 người", room.getRoomId(), room.getPlayers().size()));
        players.getChildren().clear();
        playing = room.getStatus() == RoomStatus.PLAYING || room.getStatus() == RoomStatus.GAME_STARTING;
        boolean canKick = room.getHostId() == userId && !playing;
        canInvite = !playing && room.getPlayers().size() < 8;
        if (invitePanel != null) {
            invitePanel.panel().setCanInvite(canInvite, playing ? "Trận đấu đã bắt đầu nên không mời thêm được."
                    : "Phòng đã đủ 8 người.");
        }
        if (playing) { closeInvitePanel(); }
        if (room.getStatus() != RoomStatus.GAME_STARTING) { stopCountdown(); }
        for (int index = 0; index < 8; index++) {
            PlayerInfo player = index < room.getPlayers().size() ? room.getPlayers().get(index) : null;
            VBox slot = playerSlot(player, index, room.getHostId(), canKick);
            players.add(slot, index % 4, index / 4);
            GridPane.setHgrow(slot, Priority.ALWAYS);
            GridPane.setVgrow(slot, Priority.ALWAYS);
        }
        boolean ready = room.getPlayers().stream().anyMatch(p -> p.getUserId() == userId && p.isReady());
        long readyPlayers = room.getPlayers().stream().filter(PlayerInfo::isReady).count();
        playerCount.setText(room.getPlayers().size() + "/8");
        readyCount.setText(readyPlayers + "/" + room.getPlayers().size());
        readyButton.setText(ready ? "Hủy sẵn sàng" : "Sẵn sàng");
        if (room.getStatus() == RoomStatus.PLAYING) {
            status.setText("Trận đấu đã bắt đầu.");
        } else if (room.getStatus() == RoomStatus.GAME_STARTING) {
            status.setText("Tất cả đã sẵn sàng!");
        } else if (room.getPlayers().size() < 2) {
            status.setText("Cần ít nhất 2 người và tất cả sẵn sàng để bắt đầu.");
        } else if (!ready) {
            // Thẻ thống kê đã hiện số người sẵn sàng; dòng này chỉ hướng dẫn việc cần làm tiếp.
            status.setText("Bấm Sẵn sàng khi bạn đã chuẩn bị xong. Trận bắt đầu khi tất cả cùng sẵn sàng."
                    + (canKick ? " Bạn là chủ phòng nên có thể mời người chơi ra." : ""));
        } else {
            long waiting = room.getPlayers().size() - readyPlayers;
            status.setText(waiting == 0 ? "Tất cả đã sẵn sàng!"
                    : "Bạn đã sẵn sàng. Đang chờ " + waiting + " người nữa bấm Sẵn sàng...");
        }
        setBusy(false);
    }

    public void setBusy(boolean busy) {
        readyButton.setDisable(busy || playing);
        inviteButton.setDisable(busy || !canInvite);
        leaveButton.setDisable(busy);
        logoutButton.setDisable(busy);
    }

    // `invite` gửi lời mời, `requestOnlineUsers` xin server danh sách người online (gọi lại mỗi 5 giây khi hộp thoại mở).
    public void setOnInvite(Consumer<OnlineUser> invite, Runnable requestOnlineUsers) {
        this.invite = invite;
        this.requestOnlineUsers = requestOnlineUsers;
    }

    public void openInvitePanel() {
        if (invitePanel == null) {
            invitePanel = new OnlinePlayersDialog(this, Icons.Glyph.USER_PLUS, "Mời người chơi",
                    "Danh sách tự làm mới mỗi 5 giây. Kéo phần đầu hộp thoại để di chuyển.",
                    user -> invite.accept(user), () -> requestOnlineUsers.run());
            invitePanel.panel().setCanInvite(canInvite, "Phòng đang chơi hoặc đã đủ người.");
        }
        invitePanel.open();
    }

    public void closeInvitePanel() {
        if (invitePanel != null) { invitePanel.close(); }
    }

    public void showOnlineUsers(List<OnlineUser> users) {
        if (invitePanel != null) { invitePanel.panel().setUsers(users); }
    }

    public OnlinePlayersPanel getOnlinePanel() {
        return invitePanel == null ? null : invitePanel.panel();
    }

    public void showMessage(String message) {
        status.setText(message);
    }
}
