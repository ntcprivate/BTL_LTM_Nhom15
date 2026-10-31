package com.nhom15.drawguess.client.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Function;

// Hộp thoại theo chủ đề Starry Draw: thẻ kem viền vàng bo góc thay cho khung cửa sổ xám của hệ điều hành.
// Đầu thẻ là dải "bầu trời đêm" có sao và nét xoáy kiểu Van Gogh, màu theo loại hộp thoại
// (thông tin / cảnh báo / nguy hiểm); huy hiệu icon nằm vắt giữa dải màu và thân thẻ; nút ghi rõ hành động.
public final class ThemedDialog {
    public enum Tone { INFO, WARNING, DANGER }

    private static final double SKY_HEIGHT = 78;
    private static final double BADGE_SIZE = 68;
    // Bề rộng cố định cho tiêu đề và nội dung để chiều cao chữ xuống dòng được tính đúng
    // (DialogPane đo phần đầu thẻ trước khi biết bề rộng thật).
    private static final double TEXT_WIDTH = 396;
    // Thứ tự nút của Windows (nút chính trước, nút hủy sau) nhưng không có khoảng trống co giãn,
    // để CSS căn cả hàng nút vào giữa (ButtonBar bỏ qua khoảng co giãn ở cuối nên "+...+" không căn giữa được).
    private static final String CENTERED_BUTTON_ORDER = "L_E_U_FBXI_YNOCAH_R";
    // Đánh dấu hộp thoại dạng bảng (không làm tối phía sau, vẫn thao tác được phía sau).
    private static final String PANEL = "themed-dialog-panel";

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
        // Lớp tối phía sau thay cho việc khóa cửa sổ chính, để bấm ra ngoài hộp thoại thì đóng được nó
        // (cửa sổ bị khóa thì không nhận được cú bấm). Không có lớp tối thì vẫn khóa như cũ.
        if (owner != null && owner.getScene() != null && owner.getScene().getRoot() instanceof StackPane) {
            dialog.initModality(Modality.NONE);
        }

        DialogPane pane = dialog.getDialogPane();
        UiTheme.apply(pane);
        pane.getStyleClass().addAll("themed-dialog", "dialog-" + tone.name().toLowerCase());
        pane.setAccessibleText(message == null ? title : title + ". " + message);
        Node hero = hero(glyph, title, message);
        makeDraggable(hero.lookup(".dialog-sky-layer"), pane);
        pane.setHeader(hero);
        // Không có nội dung thêm thì để vùng nội dung rỗng, tránh khoảng trống mặc định giữa chữ và hàng nút.
        Region none = new Region();
        none.setPrefSize(0, 0);
        pane.setContent(none);
        pane.getButtonTypes().setAll(buttons);
        styleButtons(pane, tone);
        if (pane.lookup(".button-bar") instanceof ButtonBar bar) { bar.setButtonOrder(CENTERED_BUTTON_ORDER); }

        // Cửa sổ trong suốt để chỉ thấy thẻ bo góc và bóng đổ của nó.
        Scene scene = pane.getScene();
        scene.setFill(Color.TRANSPARENT);
        if (scene.getRoot() != pane) { scene.getRoot().setStyle("-fx-background-color: transparent;"); }
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> attachScrim(dialog));
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> popIn(pane));
        return dialog;
    }

    // Phần đầu thẻ: dải trời đêm, huy hiệu icon, tiêu đề và nội dung căn giữa.
    // Màu theo loại lấy từ lớp "dialog-info/warning/danger" của phần tử cha (hộp thoại hoặc lớp phủ).
    static VBox hero(Icons.Glyph glyph, String title, String message) {
        Region sky = new Region();
        sky.getStyleClass().add("dialog-sky");
        Pane swirls = new Pane(swirls());
        swirls.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(swirls.widthProperty());
        clip.heightProperty().bind(swirls.heightProperty());
        swirls.setClip(clip);
        StackPane skyLayer = new StackPane(sky, swirls);
        skyLayer.getStyleClass().add("dialog-sky-layer");
        skyLayer.setMinHeight(SKY_HEIGHT);
        skyLayer.setPrefHeight(SKY_HEIGHT);
        skyLayer.setMaxHeight(SKY_HEIGHT);

        Label badge = new Label();
        badge.getStyleClass().add("dialog-badge");
        badge.setGraphic(Icons.icon(glyph, 30, badge.textFillProperty()));
        badge.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        badge.setMinSize(BADGE_SIZE, BADGE_SIZE);
        badge.setPrefSize(BADGE_SIZE, BADGE_SIZE);
        badge.setMaxSize(BADGE_SIZE, BADGE_SIZE);

        StackPane top = new StackPane(skyLayer, badge);
        StackPane.setAlignment(skyLayer, Pos.TOP_CENTER);
        StackPane.setAlignment(badge, Pos.BOTTOM_CENTER);
        double topHeight = SKY_HEIGHT + BADGE_SIZE / 2;
        top.setMinHeight(topHeight);
        top.setPrefHeight(topHeight);

        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        heading.setTextAlignment(TextAlignment.CENTER);
        centerText(heading);
        VBox hero = new VBox(8, top, heading);
        if (message != null && !message.isBlank()) {
            Label body = new Label(message);
            body.getStyleClass().add("dialog-message");
            body.setWrapText(true);
            body.setTextAlignment(TextAlignment.CENTER);
            centerText(body);
            hero.getChildren().add(body);
        }
        hero.getStyleClass().add("dialog-hero");
        hero.setAlignment(Pos.TOP_CENTER);
        hero.setFillWidth(true);
        return hero;
    }

    private static void centerText(Label label) {
        label.setAlignment(Pos.TOP_CENTER);
        label.setPrefWidth(TEXT_WIDTH);
        label.setMaxWidth(TEXT_WIDTH);
        label.setMinHeight(Region.USE_PREF_SIZE);
    }

    // Vài nét xoáy và một vòng xoắn nhỏ, gợi bầu trời trong tranh "Đêm đầy sao"; chỉ để trang trí.
    private static Group swirls() {
        SVGPath waves = new SVGPath();
        waves.setContent("M-10 30 C 40 6, 90 54, 140 30 S 240 6, 290 30 S 390 54, 440 30 S 520 8, 560 26"
                + "M-10 58 C 50 40, 100 78, 160 58 S 260 40, 320 58 S 420 76, 480 58 S 540 44, 570 54");
        SVGPath spiral = new SVGPath();
        spiral.setContent("M412 22 a 12 12 0 1 1 -12 12 a 7 7 0 1 1 7 -7 a 3 3 0 1 1 -3 3"
                + "M58 46 a 9 9 0 1 0 9 -9 a 5 5 0 1 0 -5 5");
        for (SVGPath path : new SVGPath[] { waves, spiral }) {
            path.getStyleClass().add("dialog-swirl");
            path.setFill(null);
        }
        return new Group(waves, spiral);
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

    // Hộp thoại dạng bảng (danh sách người online): không làm tối phía sau và vẫn thao tác được phía sau.
    public static void asPanel(Dialog<?> dialog) {
        dialog.initModality(Modality.NONE);
        dialog.getDialogPane().getStyleClass().add(PANEL);
    }

    // Bấm ra ngoài hộp thoại thì đóng nó, giống bấm nút hủy/đóng (lời mời: coi như từ chối).
    static void dismiss(Dialog<?> dialog) {
        if (!dialog.isShowing()) { return; }
        DialogPane pane = dialog.getDialogPane();
        for (ButtonType type : pane.getButtonTypes()) {
            if (type.getButtonData().isCancelButton() && pane.lookupButton(type) instanceof Button cancel) {
                cancel.fire();
                return;
            }
        }
        dialog.close();
    }

    // Hộp thoại chặn thao tác thì làm tối màn hình phía sau để mắt tập trung vào hộp thoại; bấm vào lớp tối để đóng.
    // Hộp thoại dạng bảng thì không phủ, vì người dùng vẫn thao tác phía sau; bấm ra ngoài cũng đóng nó.
    private static void attachScrim(Dialog<?> dialog) {
        Window owner = dialog.getOwner();
        if (owner == null || owner.getScene() == null) { return; }
        Scene ownerScene = owner.getScene();
        if (dialog.getDialogPane().getStyleClass().contains(PANEL)) {
            // Cú bấm vẫn tới phía sau (ví dụ bấm "Sẵn sàng" thì vừa đóng danh sách vừa sẵn sàng).
            EventHandler<MouseEvent> outside = event -> dismiss(dialog);
            ownerScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outside);
            dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> ownerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outside));
            return;
        }
        if (!(ownerScene.getRoot() instanceof StackPane root)) { return; }
        Region scrim = new Region();
        UiTheme.apply(scrim);
        scrim.getStyleClass().add("dialog-scrim");
        scrim.setOnMousePressed(event -> {
            event.consume();
            dismiss(dialog);
        });
        root.getChildren().add(scrim);
        // Cửa sổ chính không bị khóa nữa nên có thể được chọn lại (bấm thanh tiêu đề, Alt+Tab). Chặn phím ở đó để
        // không lọt xuống màn hình phía sau; Esc thì đóng hộp thoại. Không giật focus về hộp thoại, vì Windows
        // sẽ bỏ luôn cú bấm chuột đang kích hoạt cửa sổ chính và lớp tối không nhận được cú bấm ra ngoài.
        EventHandler<KeyEvent> blockKeys = event -> {
            event.consume();
            if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE) { dismiss(dialog); }
        };
        ownerScene.addEventFilter(KeyEvent.ANY, blockKeys);
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> ownerScene.removeEventFilter(KeyEvent.ANY, blockKeys));
        if (!UiTheme.reducedMotion()) {
            FadeTransition fade = new FadeTransition(Duration.millis(150), scrim);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        }
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, event -> root.getChildren().remove(scrim));
    }

    // Thẻ hiện ra với độ nảy nhẹ, rồi huy hiệu "bật" lên để mắt nhìn vào nội dung chính.
    static void popIn(Node pane) {
        if (UiTheme.reducedMotion()) {
            pane.setOpacity(1);
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(160), pane);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(240), pane);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(BACK_OUT);
        new ParallelTransition(fade, scale).play();
        Node badge = pane.lookup(".dialog-badge");
        if (badge != null) {
            ScaleTransition pop = new ScaleTransition(Duration.millis(260), badge);
            pop.setDelay(Duration.millis(90));
            pop.setFromX(0.5);
            pop.setFromY(0.5);
            pop.setToX(1);
            pop.setToY(1);
            pop.setInterpolator(BACK_OUT);
            badge.setScaleX(0.5);
            badge.setScaleY(0.5);
            pop.play();
        }
    }

    // Lắc nhẹ huy hiệu (như chuông reo) để báo có thứ vừa tới, ví dụ lời mời. Chỉ chạy một lần.
    static void ring(Dialog<?> dialog) {
        Node badge = dialog.getDialogPane().lookup(".dialog-badge");
        if (badge == null || UiTheme.reducedMotion()) { return; }
        RotateTransition shake = new RotateTransition(Duration.millis(70), badge);
        shake.setDelay(Duration.millis(320));
        shake.setFromAngle(-12);
        shake.setToAngle(12);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.setOnFinished(event -> badge.setRotate(0));
        shake.play();
    }

    // Đường cong "vượt nhẹ rồi về" (ease-out-back); Interpolator.SPLINE không cho điểm điều khiển vượt quá 1.
    private static final Interpolator BACK_OUT = new Interpolator() {
        @Override protected double curve(double t) {
            double overshoot = 1.70158;
            double u = t - 1;
            return u * u * ((overshoot + 1) * u + overshoot) + 1;
        }
    };

    private static Window windowOf(Node anchor) {
        return anchor == null || anchor.getScene() == null ? null : anchor.getScene().getWindow();
    }
}
