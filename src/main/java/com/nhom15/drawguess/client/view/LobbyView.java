package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class LobbyView extends VBox {
    private final int userId;
    private final Label title = new Label();
    private final Label status = new Label();
    private final ListView<String> players = new ListView<>();
    private final Button readyButton = new Button("Sẵn sàng");
    private final Button leaveButton = new Button("Rời phòng");
    private final Button logoutButton = new Button("Đăng xuất");
    private boolean playing;

    public LobbyView(int userId, Runnable ready, Runnable leave, Runnable logout) {
        this.userId = userId;
        setSpacing(14);
        setPadding(new Insets(24));
        players.setPrefHeight(290);
        readyButton.setOnAction(event -> ready.run());
        leaveButton.setOnAction(event -> leave.run());
        logoutButton.setOnAction(event -> logout.run());
        HBox actions = new HBox(12, readyButton, leaveButton, logoutButton);
        actions.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        getChildren().addAll(title, players, status, actions);
    }

    public void update(RoomInfo room) {
        title.setText(String.format("Phòng %03d — %d/8 người", room.getRoomId(), room.getPlayers().size()));
        players.getItems().setAll(room.getPlayers().stream().map(p -> p.getUsername()
                + (p.getUserId() == userId ? " (bạn)" : "") + " — "
                + (!p.isOnline() ? "Mất kết nối" : p.isReady() ? "Sẵn sàng" : "Chưa sẵn sàng")).toList());
        boolean ready = room.getPlayers().stream().anyMatch(p -> p.getUserId() == userId && p.isReady());
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
