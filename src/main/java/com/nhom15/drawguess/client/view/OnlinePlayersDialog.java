package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.OnlineUser;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.util.Duration;

import java.util.function.Consumer;

// Hộp thoại không chặn liệt kê người đang online; khi đang mở thì xin lại danh sách mỗi 5 giây.
// `invite` null thì chỉ để xem (màn hình chọn phòng: chưa ở phòng nào nên chưa mời được ai).
final class OnlinePlayersDialog {
    private static final Duration REFRESH = Duration.seconds(5);
    private final OnlinePlayersPanel panel;
    private final Dialog<ButtonType> dialog;
    private final Timeline refresh;
    private final Runnable request;

    OnlinePlayersDialog(Node anchor, Icons.Glyph glyph, String title, String message,
                        Consumer<OnlineUser> invite, Runnable request) {
        this.request = request;
        panel = new OnlinePlayersPanel(invite);
        dialog = ThemedDialog.create(anchor.getScene() == null ? null : anchor.getScene().getWindow(),
                ThemedDialog.Tone.INFO, glyph, title, message,
                new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
        // Không chặn màn hình phía sau: vẫn bấm Sẵn sàng, chọn phòng... khi hộp thoại đang mở.
        ThemedDialog.asPanel(dialog);
        dialog.getDialogPane().setContent(panel);
        refresh = new Timeline(new KeyFrame(REFRESH, event -> request.run()));
        refresh.setCycleCount(Animation.INDEFINITE);
        dialog.setOnHidden(event -> refresh.stop());
    }

    void open() {
        request.run();
        refresh.play();
        if (!dialog.isShowing()) { dialog.show(); }
    }

    void close() {
        dialog.close();
    }

    OnlinePlayersPanel panel() {
        return panel;
    }
}
