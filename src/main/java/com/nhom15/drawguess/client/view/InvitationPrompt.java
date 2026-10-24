package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.Invitation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

// Hộp thoại "X mời bạn vào phòng": có thanh đếm ngược và tự đóng (coi như từ chối) sau 30 giây.
public final class InvitationPrompt {
    public static final Duration EXPIRY = Duration.seconds(30);
    public static final ButtonType ACCEPT = new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType DECLINE = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);

    private InvitationPrompt() {
    }

    public static Dialog<ButtonType> show(Window owner, Invitation invitation, Runnable accept, Runnable decline) {
        Dialog<ButtonType> dialog = ThemedDialog.create(owner, ThemedDialog.Tone.INFO, Icons.Glyph.MAIL,
                invitation.getFromUsername() + " mời bạn vào Phòng " + String.format("%03d", invitation.getRoomId()),
                "Vào phòng chờ ngay để chơi cùng nhau.", ACCEPT, DECLINE);
        dialog.setTitle("Lời mời chơi");

        ProgressBar countdown = new ProgressBar(1);
        countdown.getStyleClass().add("dialog-countdown");
        countdown.setMaxWidth(Double.MAX_VALUE);
        Label remaining = new Label();
        remaining.getStyleClass().add("dialog-countdown-text");
        remaining.textProperty().bind(Bindings.createStringBinding(() -> "Tự từ chối sau "
                + (int) Math.ceil(countdown.getProgress() * EXPIRY.toSeconds()) + " giây", countdown.progressProperty()));
        VBox body = new VBox(6, countdown, remaining);
        body.getStyleClass().add("dialog-body");
        dialog.getDialogPane().setContent(body);

        Timeline expiry = new Timeline(new KeyFrame(EXPIRY, new KeyValue(countdown.progressProperty(), 0)));
        expiry.setOnFinished(event -> dialog.close());
        dialog.setOnHidden(event -> {
            expiry.stop();
            if (dialog.getResult() == ACCEPT) { accept.run(); } else { decline.run(); }
        });
        dialog.show();
        expiry.play();
        return dialog;
    }
}
