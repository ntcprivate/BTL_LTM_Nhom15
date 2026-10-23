package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    // Nhãn trạng thái đang đếm ngược -> thời điểm (System.nanoTime) người chơi bị đưa ra khỏi phòng.
    private final Map<Label, Long> countdowns = new HashMap<>();
    private Long ownKickDeadline;
    private boolean playing;

    public LobbyView(int userId, Runnable ready, Runnable leave, Runnable logout, IntConsumer kick) {
        this.userId = userId;
        this.kick = kick;
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(16);
        setPadding(new Insets(28));
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
        VBox playerBoard = new VBox(14, boardTitle, players);
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
        HBox actions = new HBox(12, readyButton, leaveButton, logoutButton);
        actions.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        UiTheme.status(status, "info");
        getChildren().addAll(header, statistics, playerBoard, status, actions);
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshCountdowns()));
        countdown.setCycleCount(Animation.INDEFINITE);
        countdown.play();
    }

    private void refreshCountdowns() {
        long now = System.nanoTime();
        countdowns.forEach((label, deadline) -> label.setText(countdownText(secondsLeft(deadline, now))));
        if (ownKickDeadline != null) { status.setText(ownWarning(secondsLeft(ownKickDeadline, now))); }
    }

    private static int secondsLeft(long deadline, long now) {
        return (int) Math.max(0, (deadline - now + 999_999_999L) / 1_000_000_000L);
    }

    private static String countdownText(int seconds) {
        return "Chưa sẵn sàng • rời phòng sau " + seconds + " giây";
    }

    private static String ownWarning(int seconds) {
        return "Mọi người đang chờ bạn! Hãy bấm Sẵn sàng trong " + seconds
                + " giây, nếu không bạn sẽ được đưa ra khỏi phòng.";
    }

    private void confirmKick(PlayerInfo player) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Mời " + player.getUsername() + " ra khỏi phòng? Người này sẽ không vào lại được phòng cho tới khi phòng trống.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.setTitle("Mời người chơi ra");
        if (getScene() != null) { confirm.initOwner(getScene().getWindow()); }
        confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored -> kick.accept(player.getUserId()));
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
        if (player != null && player.getKickInSeconds() >= 0) {
            countdowns.put(playerState, System.nanoTime() + player.getKickInSeconds() * 1_000_000_000L);
            playerState.setText(countdownText(player.getKickInSeconds()));
        }
        if (player != null) {
            playerState.getStyleClass().add(player.isOnline() && player.isReady()
                    ? "player-ready" : player.isOnline() ? "player-waiting" : "player-offline");
        }
        VBox information = new VBox(5, name, playerState);
        if (player != null && player.getUserId() == hostId) {
            Label host = new Label("★ Chủ phòng");
            host.getStyleClass().add("room-host");
            information.getChildren().addFirst(host);
        }
        if (player != null && canKick && player.getUserId() != userId) {
            Button kickButton = new Button("Mời ra");
            kickButton.getStyleClass().addAll("danger-button", "kick-button");
            kickButton.setOnAction(event -> confirmKick(player));
            information.getChildren().add(kickButton);
        }
        Label mark = new Label(player != null && player.isReady() ? "✓" : "");
        mark.getStyleClass().add("ready-mark");
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
        countdowns.clear();
        playing = room.getStatus() == RoomStatus.PLAYING || room.getStatus() == RoomStatus.GAME_STARTING;
        boolean canKick = room.getHostId() == userId && !playing;
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
        int ownKickIn = room.getPlayers().stream().filter(p -> p.getUserId() == userId)
                .mapToInt(PlayerInfo::getKickInSeconds).findFirst().orElse(-1);
        ownKickDeadline = ownKickIn >= 0 ? System.nanoTime() + ownKickIn * 1_000_000_000L : null;
        if (ownKickDeadline != null) {
            status.setText(ownWarning(ownKickIn));
        } else if (room.getStatus() == RoomStatus.PLAYING) {
            status.setText("Trận đấu đã bắt đầu.");
        } else if (room.getStatus() == RoomStatus.GAME_STARTING) {
            status.setText("Tất cả đã sẵn sàng. Chuẩn bị bắt đầu!");
        } else if (room.getPlayers().size() < 2) {
            status.setText("Cần ít nhất 2 người và tất cả sẵn sàng để bắt đầu.");
        } else {
            long count = room.getPlayers().stream().filter(PlayerInfo::isReady).count();
            status.setText("Sẵn sàng: " + count + "/" + room.getPlayers().size());
        }
        setBusy(false);
    }

    public void setBusy(boolean busy) {
        readyButton.setDisable(busy || playing);
        leaveButton.setDisable(busy);
        logoutButton.setDisable(busy);
    }

    public void showMessage(String message) {
        ownKickDeadline = null;
        status.setText(message);
    }
}
