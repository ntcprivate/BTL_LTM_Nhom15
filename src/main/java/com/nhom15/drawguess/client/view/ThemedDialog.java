package com.nhom15.drawguess.client.view;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Function;

// Hộp thoại theo chủ đề Starry Draw: thẻ kem viền vàng bo góc thay cho khung cửa sổ xám của hệ điều hành,
// có huy hiệu icon theo loại (thông tin / cảnh báo / nguy hiểm) và nút ghi rõ hành động thay cho OK/Cancel.
public final class ThemedDialog {
    public enum Tone { INFO, WARNING, DANGER }

    // Cách hiện hộp thoại xác nhận; test thay bằng hàm trả lời sẵn để không phải chờ người bấm.
    static Function<Dialog<ButtonType>, Optional<ButtonType>> presenter = Dialog::showAndWait;

    private ThemedDialog() {
    }

    // Hỏi lại trước một thao tác; trả về true khi người dùng chọn nút `confirmText`.
    public static boolean confirm(Node anchor, Tone tone, Icons.Glyph glyph, String title, String message,
                                  String confirmText, String cancelText) {
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType(cancelText, ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = create(windowOf(anchor), tone, glyph, title, message, yes, no);
        return presenter.apply(dialog).filter(yes::equals).isPresent();
    }

    // Dựng hộp thoại (chưa hiện). Nội dung thêm (danh sách, thanh đếm ngược...) đặt bằng getDialogPane().setContent.
    public static Dialog<ButtonType> create(Window owner, Tone tone, Icons.Glyph glyph, String title, String message,
                                            ButtonType... buttons) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) { dialog.initOwner(owner); }

        DialogPane pane = dialog.getDialogPane();
        UiTheme.apply(pane);
        pane.getStyleClass().addAll("themed-dialog", "dialog-" + tone.name().toLowerCase());
        pane.setAccessibleText(message == null ? title : title + ". " + message);
        pane.setHeader(header(pane, glyph, title, message));
        // Không có nội dung thêm thì để vùng nội dung rỗng, tránh khoảng trống mặc định giữa chữ và hàng nút.
        Region none = new Region();
        none.setPrefSize(0, 0);
        pane.setContent(none);
        pane.getButtonTypes().setAll(buttons);
        styleButtons(pane, tone);

        // Cửa sổ trong suốt để chỉ thấy thẻ bo góc và bóng đổ của nó.
        Scene scene = pane.getScene();
        scene.setFill(Color.TRANSPARENT);
        if (scene.getRoot() != pane) { scene.getRoot().setStyle("-fx-background-color: transparent;"); }
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> attachScrim(dialog));
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> popIn(pane));
        return dialog;
    }

    private static Node header(DialogPane pane, Icons.Glyph glyph, String title, String message) {
        Label badge = new Label();
        badge.getStyleClass().add("dialog-badge");
        badge.setGraphic(Icons.icon(glyph, 22, badge.textFillProperty()));
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        VBox text = new VBox(6, heading);
        if (message != null && !message.isBlank()) {
            Label body = new Label(message);
            body.getStyleClass().add("dialog-message");
            body.setWrapText(true);
            body.setMinHeight(Region.USE_PREF_SIZE);
            text.getChildren().add(body);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox header = new HBox(16, badge, text);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.TOP_LEFT);
        makeDraggable(header, pane);
        return header;
    }

    private static void styleButtons(DialogPane pane, Tone tone) {
        for (ButtonType type : pane.getButtonTypes()) {
            Button button = (Button) pane.lookupButton(type);
            boolean cancel = type.getButtonData().isCancelButton();
            button.getStyleClass().add(cancel ? "ghost-button" : tone == Tone.DANGER ? "danger-button" : "primary-button");
            // Thao tác nguy hiểm không để Enter vô tình xác nhận: Enter chọn nút hủy.
            if (tone == Tone.DANGER) { button.setDefaultButton(cancel); }
        }
    }

    // Không còn thanh tiêu đề của hệ điều hành nên kéo phần đầu thẻ để di chuyển hộp thoại.
    private static void makeDraggable(Node handle, DialogPane pane) {
        double[] grab = new double[2];
        handle.setOnMousePressed(event -> {
            Window window = pane.getScene().getWindow();
            grab[0] = event.getScreenX() - window.getX();
            grab[1] = event.getScreenY() - window.getY();
        });
        handle.setOnMouseDragged(event -> {
            Window window = pane.getScene().getWindow();
            window.setX(event.getScreenX() - grab[0]);
            window.setY(event.getScreenY() - grab[1]);
        });
    }

    // Hộp thoại chặn thao tác thì làm tối màn hình phía sau để mắt tập trung vào hộp thoại.
    // Hộp thoại không chặn (danh sách mời) thì không phủ, vì người dùng vẫn thao tác phía sau.
    private static void attachScrim(Dialog<?> dialog) {
        Window owner = dialog.getOwner();
        if (dialog.getModality() == Modality.NONE || owner == null || owner.getScene() == null
                || !(owner.getScene().getRoot() instanceof StackPane root)) {
            return;
        }
        Region scrim = new Region();
        UiTheme.apply(scrim);
        scrim.getStyleClass().add("dialog-scrim");
        root.getChildren().add(scrim);
        if (!UiTheme.reducedMotion()) {
            FadeTransition fade = new FadeTransition(Duration.millis(150), scrim);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> root.getChildren().remove(scrim));
    }

    private static void popIn(Node pane) {
        if (UiTheme.reducedMotion()) {
            pane.setOpacity(1);
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(160), pane);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), pane);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1);
        scale.setToY(1);
        new ParallelTransition(fade, scale).play();
    }

    private static Window windowOf(Node anchor) {
        return anchor == null || anchor.getScene() == null ? null : anchor.getScene().getWindow();
    }
}
