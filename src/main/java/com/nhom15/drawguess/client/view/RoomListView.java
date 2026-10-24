package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.RoomInfo;
import com.nhom15.drawguess.common.protocol.RoomStatus;
import com.nhom15.drawguess.common.protocol.PlayerInfo;
import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

public class RoomListView extends VBox {
    private final ListView<RoomInfo> rooms = new ListView<>();
    private final CheckBox joinableOnly = new CheckBox("Chỉ hiện phòng còn chỗ");
    private List<RoomInfo> allRooms = List.of();
    private final Label availableRooms = new Label("0");
    private final Label activeRooms = new Label("0");
    private final Label onlinePlayers = new Label("0");
    private final Button joinButton = new Button("Vào phòng");
    private final Button refreshButton = new Button("Làm mới");
    private final Button homeButton = new Button("Về trang chủ");
    private final Label status = new Label("Chọn phòng để tham gia.");
    private final AvatarView userAvatar;
    private final Label userName = new Label();

    public RoomListView(UserInfo user, Runnable join, Runnable refresh, Runnable home) {
        UiTheme.apply(this);
        getStyleClass().add("content-card");
        setSpacing(16);
        setPadding(new Insets(24, 28, 24, 28));
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        Label title = new Label("Chọn phòng chơi");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("100 phòng luôn sẵn sàng • tối đa 8 người mỗi phòng");
        subtitle.getStyleClass().add("subtle-text");
        VBox heading = new VBox(3, title, subtitle);
        userAvatar = new AvatarView(user.getAvatarKey(), 42);
        userName.getStyleClass().add("user-profile-name");
        Label userRole = new Label("NGƯỜI CHƠI");
        userRole.setMaxWidth(Double.MAX_VALUE);
        userRole.setAlignment(Pos.CENTER);
        userName.setMaxWidth(Double.MAX_VALUE);
        userName.setAlignment(Pos.CENTER);
        VBox userText = new VBox(1, userRole, userName);
        userText.setAlignment(Pos.CENTER);
        userText.setMinWidth(78);
        userText.getStyleClass().add("user-profile-text");
        HBox userChip = new HBox(9, userAvatar, userText);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.setMaxHeight(Region.USE_PREF_SIZE);
        userChip.getStyleClass().add("user-profile-chip");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        ArtworkCard painting = new ArtworkCard(
                "/images/Starry-Night-Over-the-Rhone-1888.jpg", "Starry Night Over the Rhône", 260, 86);
        HBox header = new HBox(14, heading, headerSpacer, painting, userChip);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox statistics = new HBox(12,
                statistic(availableRooms, "PHÒNG CÓ THỂ VÀO"),
                statistic(activeRooms, "TRẬN ĐANG DIỄN RA"),
                statistic(onlinePlayers, "NGƯỜI ĐANG ONLINE"));

        rooms.setPrefHeight(430);
        VBox.setVgrow(rooms, Priority.ALWAYS);
        rooms.setPlaceholder(new Label("Không có phòng nào còn chỗ. Bỏ chọn bộ lọc để xem tất cả các phòng."));
        // Nhấp đúp hoặc Enter để vào phòng đang chọn.
        rooms.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && getSelectedRoom() != null && !joinButton.isDisabled()) { join.run(); }
        });
        rooms.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && getSelectedRoom() != null && !joinButton.isDisabled()) { join.run(); }
        });
        joinableOnly.setSelected(true);
        joinableOnly.selectedProperty().addListener((observable, before, selected) -> applyFilter());
        Label hint = new Label("Nhấp đúp vào phòng để vào nhanh");
        hint.getStyleClass().add("subtle-text");
        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        HBox filterRow = new HBox(12, joinableOnly, filterSpacer, hint);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        rooms.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(RoomInfo room, boolean empty) {
                super.updateItem(room, empty);
                if (empty || room == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String roomStatus = switch (room.getStatus()) {
                    case WAITING -> "Đang chờ";
                    case READY_CHECK -> "Chờ sẵn sàng";
                    case GAME_STARTING -> "Đang bắt đầu";
                    case PLAYING -> "Đang chơi";
                    case FINISHED -> "Đã kết thúc";
                };
                String description = switch (room.getStatus()) {
                    case WAITING -> "Có thể tham gia ngay";
                    case READY_CHECK -> "Người chơi đang chuẩn bị";
                    case GAME_STARTING -> "Trận đấu sắp bắt đầu";
                    case PLAYING -> "Đang diễn ra";
                    case FINISHED -> "Đang trở về phòng chờ";
                };
                Label roomNumber = new Label(String.format("%03d", room.getRoomId()));
                roomNumber.getStyleClass().add("room-number");
                Label roomName = new Label(String.format("Phòng %03d", room.getRoomId()));
                roomName.getStyleClass().add("room-name");
                Label roomDescription = new Label(description);
                roomDescription.getStyleClass().add("room-description");
                VBox identity = new VBox(3, roomName, roomDescription);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label playerCount = new Label(room.getPlayers().size() + "/8 người");
                playerCount.getStyleClass().add("room-player-count");
                Label statusBadge = new Label(roomStatus);
                statusBadge.getStyleClass().addAll("room-status-badge", "room-status-" + room.getStatus().name().toLowerCase());
                HBox row = new HBox(14, roomNumber, identity, spacer, playerCount, statusBadge);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("room-row");
                setText(null);
                setGraphic(row);
            }
        });
        joinButton.setOnAction(event -> join.run());
        refreshButton.setOnAction(event -> refresh.run());
        homeButton.setOnAction(event -> home.run());
        joinButton.getStyleClass().add("primary-button");
        refreshButton.getStyleClass().add("ghost-button");
        homeButton.getStyleClass().add("secondary-button");
        HBox actions = new HBox(12, joinButton, refreshButton, homeButton);
        actions.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        UiTheme.status(status, "info");
        getChildren().addAll(header, statistics, filterRow, rooms, actions, status);
        updateUser(user);
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

    public RoomInfo getSelectedRoom() { return rooms.getSelectionModel().getSelectedItem(); }

    public void updateRooms(List<RoomInfo> data) {
        allRooms = List.copyOf(data);
        applyFilter();
        availableRooms.setText(String.valueOf(data.stream().filter(room -> room.getStatus() == RoomStatus.WAITING
                || room.getStatus() == RoomStatus.READY_CHECK).count()));
        activeRooms.setText(String.valueOf(data.stream().filter(room -> room.getStatus() == RoomStatus.PLAYING
                || room.getStatus() == RoomStatus.GAME_STARTING).count()));
        onlinePlayers.setText(String.valueOf(data.stream().flatMap(room -> room.getPlayers().stream())
                .filter(PlayerInfo::isOnline).count()));
    }

    private void applyFilter() {
        RoomInfo selected = getSelectedRoom();
        rooms.getItems().setAll(allRooms.stream().filter(room -> !joinableOnly.isSelected() || isJoinable(room)).toList());
        if (selected != null) {
            rooms.getItems().stream().filter(r -> r.getRoomId() == selected.getRoomId()).findFirst()
                    .ifPresent(r -> rooms.getSelectionModel().select(r));
        }
    }

    private static boolean isJoinable(RoomInfo room) {
        return (room.getStatus() == RoomStatus.WAITING || room.getStatus() == RoomStatus.READY_CHECK)
                && room.getPlayers().size() < 8;
    }

    public void setBusy(boolean busy) {
        joinButton.setDisable(busy);
        refreshButton.setDisable(busy);
        homeButton.setDisable(busy);
    }

    public void showMessage(String message) { status.setText(message); }

    public void updateUser(UserInfo user) {
        userAvatar.setAvatar(user.getAvatarKey());
        userName.setText(user.getUsername().toUpperCase(Locale.ROOT));
    }
}
