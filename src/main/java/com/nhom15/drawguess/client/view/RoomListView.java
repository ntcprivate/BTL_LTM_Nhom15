package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.RoomInfo;
import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

public class RoomListView extends VBox {
    private final ListView<RoomInfo> rooms = new ListView<>();
    private final Button historyButton = new Button("Lịch sử trận đấu");
    public void setOnHistory(Runnable history) { historyButton.setOnAction(e -> history.run()); }
    private final Button joinButton = new Button("Vào phòng");
    private final Button refreshButton = new Button("Làm mới");
    private final Button logoutButton = new Button("Đăng xuất");
    private final Label status = new Label("Chọn phòng để tham gia.");

    public RoomListView(UserInfo user, Runnable join, Runnable refresh, Runnable logout) {
        setSpacing(12);
        setPadding(new Insets(24));
        rooms.setPrefHeight(330);
        joinButton.setOnAction(event -> join.run());
        refreshButton.setOnAction(event -> refresh.run());
        logoutButton.setOnAction(event -> logout.run());
        HBox actions = new HBox(12, joinButton, refreshButton, historyButton, logoutButton);
        actions.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        getChildren().addAll(new Label("Xin chào, " + user.getUsername()),
                new Label("Danh sách phòng — tối đa 8 người/phòng"), rooms, actions, status);
    }

    public RoomInfo getSelectedRoom() { return rooms.getSelectionModel().getSelectedItem(); }

    public void updateRooms(List<RoomInfo> data) {
        RoomInfo selected = getSelectedRoom();
        rooms.getItems().setAll(data);
        if (selected != null) {
            data.stream().filter(r -> r.getRoomId() == selected.getRoomId()).findFirst()
                    .ifPresent(r -> rooms.getSelectionModel().select(r));
        }
    }

    public void setBusy(boolean busy) {
        historyButton.setDisable(busy);
        joinButton.setDisable(busy);
        refreshButton.setDisable(busy);
        logoutButton.setDisable(busy);
    }

    public void showMessage(String message) { status.setText(message); }
}
