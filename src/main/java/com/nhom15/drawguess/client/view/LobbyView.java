package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
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

import java.util.Locale;

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
    private boolean playing;

    public LobbyView(int userId, Runnable ready, Runnable leave, Runnable logout) {
        this.userId = userId;
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

    private VBox playerSlot(PlayerInfo player, int position) {
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
        for (int index = 0; index < 8; index++) {
            PlayerInfo player = index < room.getPlayers().size() ? room.getPlayers().get(index) : null;
            VBox slot = playerSlot(player, index);
            players.add(slot, index % 4, index / 4);
            GridPane.setHgrow(slot, Priority.ALWAYS);
            GridPane.setVgrow(slot, Priority.ALWAYS);
        }
        boolean ready = room.getPlayers().stream().anyMatch(p -> p.getUserId() == userId && p.isReady());
        long readyPlayers = room.getPlayers().stream().filter(PlayerInfo::isReady).count();
        playerCount.setText(room.getPlayers().size() + "/8");
        readyCount.setText(readyPlayers + "/" + room.getPlayers().size());
        readyButton.setText(ready ? "Hủy sẵn sàng" : "Sẵn sàng");
        playing = room.getStatus() == RoomStatus.PLAYING || room.getStatus() == RoomStatus.GAME_STARTING;
        if (room.getStatus() == RoomStatus.PLAYING) {
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

    public void showMessage(String message) { status.setText(message); }
}
