package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.OnlineUser;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// Danh sách người đang online trong hộp thoại "Mời người chơi". Chỉ mời được người đang rảnh.
public class OnlinePlayersPanel extends VBox {
    // Khớp thời gian chờ của server giữa hai lần mời cùng một người.
    private static final Duration INVITE_COOLDOWN = Duration.seconds(10);
    private final ListView<OnlineUser> list = new ListView<>();
    private final Label hint = new Label();
    private final Consumer<OnlineUser> invite;
    private final Set<Integer> recentlyInvited = new HashSet<>();
    private boolean canInvite = true;

    public OnlinePlayersPanel(Consumer<OnlineUser> invite) {
        super(10);
        this.invite = invite;
        UiTheme.apply(this);
        getStyleClass().add("online-panel");
        Label title = new Label("Người đang online");
        title.getStyleClass().add("section-title");
        hint.getStyleClass().add("subtle-text");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        list.getStyleClass().add("online-list");
        list.setPlaceholder(new Label("Đang tải danh sách..."));
        list.setCellFactory(view -> new UserCell());
        list.setPrefSize(400, 340);
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().addAll(title, hint, list);
        if (invite == null) {
            // Chỉ để xem: hộp thoại chứa danh sách đã có tiêu đề riêng.
            getChildren().remove(title);
            hint.setText("Muốn chơi cùng ai, hãy vào một phòng rồi bấm Mời người chơi để mời người đang rảnh.");
        } else {
            setCanInvite(true, null);
        }
    }

    public void setUsers(List<OnlineUser> users) {
        list.getItems().setAll(users);
        list.setPlaceholder(new Label("Chưa có người chơi nào khác đang online."));
    }

    public void setCanInvite(boolean allowed, String reason) {
        canInvite = allowed;
        hint.setText(allowed ? "Chỉ mời được người đang rảnh (chưa vào phòng nào)." : reason);
        list.refresh();
    }

    private void invite(OnlineUser user) {
        recentlyInvited.add(user.getUserId());
        list.refresh();
        PauseTransition cooldown = new PauseTransition(INVITE_COOLDOWN);
        cooldown.setOnFinished(event -> {
            recentlyInvited.remove(user.getUserId());
            list.refresh();
        });
        cooldown.play();
        invite.accept(user);
    }

    private class UserCell extends ListCell<OnlineUser> {
        private final AvatarView avatar = new AvatarView(null, 32);
        private final Label name = new Label();
        private final Label state = new Label();
        private final Button inviteButton = Icons.attach(new Button("Mời"), Icons.Glyph.USER_PLUS, 14);
        private final HBox row;

        UserCell() {
            name.getStyleClass().add("online-name");
            state.getStyleClass().add("online-state");
            inviteButton.getStyleClass().addAll("secondary-button", "invite-button");
            inviteButton.setMinWidth(Region.USE_PREF_SIZE);
            VBox text = new VBox(2, name, state);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            inviteButton.setVisible(invite != null);
            inviteButton.setManaged(invite != null);
            row = new HBox(10, avatar, text, inviteButton);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override protected void updateItem(OnlineUser user, boolean empty) {
            super.updateItem(user, empty);
            setText(null);
            if (empty || user == null) {
                setGraphic(null);
                return;
            }
            avatar.setAvatar(user.getAvatarKey());
            name.setText(user.getUsername());
            String room = String.format("%03d", user.getRoomId());
            state.setText(user.isAvailable() ? "Đang rảnh" : user.isPlaying() ? "Đang chơi ở phòng " + room : "Đang ở phòng " + room);
            state.getStyleClass().removeAll("online-free", "online-busy");
            state.getStyleClass().add(user.isAvailable() ? "online-free" : "online-busy");
            boolean invited = recentlyInvited.contains(user.getUserId());
            inviteButton.setText(invited ? "Đã mời" : "Mời");
            inviteButton.setDisable(!canInvite || !user.isAvailable() || invited);
            inviteButton.setAccessibleText("Mời " + user.getUsername() + " vào phòng");
            inviteButton.setOnAction(event -> invite(user));
            setGraphic(row);
        }
    }
}
