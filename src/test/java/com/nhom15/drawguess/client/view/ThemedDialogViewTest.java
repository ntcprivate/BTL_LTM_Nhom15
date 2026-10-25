package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class ThemedDialogViewTest {
    private static final String MATCH = "00000000-0000-0000-0000-000000000001";
    private final List<Dialog<ButtonType>> asked = new ArrayList<>();

    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        } catch (IllegalStateException alreadyStarted) {
            ready.complete(null);
        }
        ready.get(15, TimeUnit.SECONDS);
    }

    // Ảnh chụp lấy trạng thái cuối, không chụp giữa hiệu ứng mở hộp thoại.
    @BeforeEach void stillFrames() { UiTheme.motionPreference = () -> true; }

    @AfterEach void restore() throws Exception {
        ThemedDialog.presenter = Dialog::showAndWait;
        UiTheme.motionPreference = () -> javafx.application.Platform.getPreferences().isReducedMotion();
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    @Test void confirmDialogUsesTheThemeAndNamesTheAction() throws Exception {
        ui(() -> {
            ButtonType leave = new ButtonType("Rời phòng", ButtonBar.ButtonData.OK_DONE);
            ButtonType stay = new ButtonType("Ở lại chơi", ButtonBar.ButtonData.CANCEL_CLOSE);
            Dialog<ButtonType> dialog = ThemedDialog.create(null, ThemedDialog.Tone.DANGER, Icons.Glyph.LOG_OUT,
                    "Rời phòng khi đang chơi?", "Trận đấu chưa kết thúc. Nếu rời phòng bây giờ, bạn sẽ bỏ dở trận "
                            + "và không thể quay lại.", leave, stay);
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss(); pane.layout();

            assertEquals(StageStyle.TRANSPARENT, ((javafx.stage.Stage) pane.getScene().getWindow()).getStyle());
            assertEquals(Color.TRANSPARENT, pane.getScene().getFill());
            assertTrue(pane.getStyleClass().containsAll(List.of("themed-dialog", "dialog-danger")));
            assertNotNull(pane.lookup(".dialog-badge .icon-log-out"));
            assertEquals("Rời phòng khi đang chơi?", ((Label) pane.lookup(".dialog-title")).getText());

            Button confirm = (Button) pane.lookupButton(leave);
            Button cancel = (Button) pane.lookupButton(stay);
            assertTrue(confirm.getStyleClass().contains("danger-button"));
            assertTrue(cancel.getStyleClass().contains("ghost-button"));
            assertTrue(cancel.isDefaultButton() && !confirm.isDefaultButton(), "Enter không được vô tình xác nhận rời phòng");
            assertFalse(((Label) pane.lookup(".dialog-message")).isTextTruncated());
            snapshot(pane, "dialog-leave.png");

            Dialog<ButtonType> info = ThemedDialog.create(null, ThemedDialog.Tone.WARNING, Icons.Glyph.FLAG,
                    "Báo cáo tranh của lan?", "Chỉ báo cáo khi tranh không liên quan tới từ khóa.",
                    new ButtonType("Báo cáo", ButtonBar.ButtonData.OK_DONE), new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE));
            Button report = (Button) info.getDialogPane().lookupButton(info.getDialogPane().getButtonTypes().getFirst());
            assertTrue(report.getStyleClass().contains("primary-button"));
            assertTrue(report.isDefaultButton());
        });
    }

    @Test void gameAndLobbyAskThroughThemedDialogs() throws Exception {
        ui(() -> {
            List<String> done = new ArrayList<>();
            GameView game = new GameView(1, java.util.Map.of(), () -> {}, () -> {}, () -> done.add("leave"), () -> done.add("logout"));
            show(game);
            game.showRound(new RoundInfo(MATCH, 0, 2, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);

            answer(dialog -> Optional.empty());
            button(game, "Rời phòng").fire();
            assertEquals("Rời phòng khi đang chơi?", asked.getLast().getHeaderText());
            assertEquals(List.of("Rời phòng", "Ở lại chơi"), buttonTexts(asked.getLast()));
            assertTrue(done.isEmpty(), "Đóng hộp thoại thì vẫn ở lại phòng");

            answer(dialog -> Optional.of(dialog.getDialogPane().getButtonTypes().getFirst()));
            button(game, "Đăng xuất").fire();
            DialogPane logout = asked.getLast().getDialogPane();
            logout.applyCss();
            assertNotNull(logout.lookup(".icon-power"));
            assertEquals(List.of("logout"), done);

            button(game, "Báo cáo tranh").fire();
            assertEquals("Báo cáo tranh của lan?", asked.getLast().getHeaderText());
            assertTrue(asked.getLast().getDialogPane().getStyleClass().contains("dialog-warning"));
            assertTrue(button(game, "Báo cáo tranh").isDisabled());

            List<Integer> kicked = new ArrayList<>();
            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, kicked::add);
            show(lobby);
            lobby.update(new RoomInfo(7, RoomStatus.READY_CHECK, List.of(new PlayerInfo(1, "vu", false, true),
                    new PlayerInfo(2, "lan", false, true)), 1));
            lobby.applyCss(); lobby.layout();
            ((Button) lobby.lookup(".kick-button")).fire();
            assertEquals("Mời lan ra khỏi phòng?", asked.getLast().getHeaderText());
            assertEquals(List.of("Mời ra", "Hủy"), buttonTexts(asked.getLast()));
            assertEquals(List.of(2), kicked);
        });
    }

    @Test void invitationShowsACountdownInTheTheme() throws Exception {
        ui(() -> {
            Dialog<ButtonType> prompt = InvitationPrompt.show(null, new Invitation(1, "lan", 2, "vu", 7), () -> {}, () -> {});
            DialogPane pane = prompt.getDialogPane();
            pane.applyCss(); pane.layout();
            assertTrue(pane.getStyleClass().contains("dialog-info"));
            assertNotNull(pane.lookup(".icon-mail"));
            assertEquals("Tự từ chối sau 30 giây", ((Label) pane.lookup(".dialog-countdown-text")).getText());
            assertTrue(((Button) pane.lookupButton(InvitationPrompt.ACCEPT)).getStyleClass().contains("primary-button"));
            pane.setOpacity(1); pane.setScaleX(1); pane.setScaleY(1); // bỏ qua hiệu ứng hiện dần khi chụp
            snapshot(pane, "dialog-invitation.png");
            prompt.close();
        });
    }

    @Test void lobbyInviteListOpensInAThemedNonBlockingDialog() throws Exception {
        ui(() -> {
            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            lobby.setOnInvite(user -> {}, () -> {});
            show(lobby);
            lobby.update(new RoomInfo(7, RoomStatus.READY_CHECK, List.of(new PlayerInfo(1, "vu", false, true)), 1));
            button(lobby, "Mời người chơi").fire();
            lobby.showOnlineUsers(List.of(new OnlineUser(3, "hoa", AvatarCatalog.DEFAULT, 0, false),
                    new OnlineUser(4, "minh", AvatarCatalog.DEFAULT, 5, true)));
            DialogPane pane = (DialogPane) lobby.getOnlinePanel().getParent();
            pane.applyCss(); pane.layout();
            assertTrue(pane.getStyleClass().contains("themed-dialog"));
            assertEquals(javafx.stage.Modality.NONE, ((javafx.stage.Stage) pane.getScene().getWindow()).getModality());
            assertEquals("Mời người chơi", ((Label) pane.lookup(".dialog-title")).getText());
            pane.setOpacity(1); pane.setScaleX(1); pane.setScaleY(1);
            snapshot(pane, "dialog-invite-list.png");
        });
    }

    @Test void dialogButtonsKeepKeyboardFocusRingAndVisibleOutlines() throws Exception {
        ui(() -> {
            ButtonType leave = new ButtonType("Rời phòng", ButtonBar.ButtonData.OK_DONE);
            ButtonType stay = new ButtonType("Ở lại chơi", ButtonBar.ButtonData.CANCEL_CLOSE);
            DialogPane pane = ThemedDialog.create(null, ThemedDialog.Tone.DANGER, Icons.Glyph.LOG_OUT, "Rời phòng?",
                    "Dòng một" + (char) 10 + "Dòng hai", leave, stay).getDialogPane();
            pane.applyCss();
            Button confirm = (Button) pane.lookupButton(leave);
            Button cancel = (Button) pane.lookupButton(stay);
            assertEquals(Color.web("#a8802a"), borderColor(cancel), "Viền nút phụ đủ đậm so với nền thẻ");

            javafx.css.PseudoClass focusVisible = javafx.css.PseudoClass.getPseudoClass("focus-visible");
            confirm.pseudoClassStateChanged(focusVisible, true);
            cancel.pseudoClassStateChanged(focusVisible, true);
            pane.applyCss();
            assertEquals(Color.web("#1d4ed8"), borderColor(confirm), "Nút đỏ vẫn có viền focus xanh khi dùng Tab");
            assertEquals(Color.web("#1d4ed8"), borderColor(cancel));
            assertEquals(2, confirm.getBorder().getStrokes().getFirst().getWidths().getTop());

            pane.layout();
            Label twoLines = (Label) pane.lookup(".dialog-message");
            DialogPane single = ThemedDialog.create(null, ThemedDialog.Tone.DANGER, Icons.Glyph.LOG_OUT, "Rời phòng?",
                    "Dòng một", leave, stay).getDialogPane();
            single.applyCss(); single.layout();
            double lineHeight = twoLines.getHeight() - ((Label) single.lookup(".dialog-message")).getHeight();
            assertTrue(lineHeight >= twoLines.getFont().getSize() * 1.5,
                    "Khoảng cách dòng ít nhất 1.5 lần cỡ chữ: " + lineHeight);
        });
    }

    @Test void blockingDialogsDimTheScreenBehindAndNonBlockingOnesDoNot() throws Exception {
        ui(() -> {
            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(new Label("Màn hình game"));
            show(root);
            javafx.stage.Window owner = root.getScene().getWindow();
            Dialog<ButtonType> modal = ThemedDialog.create(owner, ThemedDialog.Tone.DANGER, Icons.Glyph.POWER, "Đăng xuất?", null,
                    new ButtonType("Đăng xuất", ButtonBar.ButtonData.OK_DONE), new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE));
            modal.show();
            assertEquals(1, root.lookupAll(".dialog-scrim").size(), "Hộp thoại chặn thao tác thì làm tối phía sau");
            modal.close();
            assertEquals(0, root.lookupAll(".dialog-scrim").size(), "Đóng hộp thoại thì bỏ lớp tối");

            Dialog<ButtonType> panel = ThemedDialog.create(owner, ThemedDialog.Tone.INFO, Icons.Glyph.USER_PLUS, "Mời người chơi", null,
                    new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
            panel.initModality(javafx.stage.Modality.NONE);
            panel.show();
            assertEquals(0, root.lookupAll(".dialog-scrim").size(), "Danh sách mời không chặn nên không làm tối");
            panel.close();
        });
    }

    @Test void reducedMotionSkipsDialogAndScreenAnimations() throws Exception {
        ui(() -> {
            UiTheme.motionPreference = () -> true;
            Dialog<ButtonType> dialog = ThemedDialog.create(null, ThemedDialog.Tone.INFO, Icons.Glyph.MAIL, "Lời mời", null,
                    new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialog.show();
            DialogPane pane = dialog.getDialogPane();
            assertEquals(1, pane.getOpacity());
            assertEquals(1, pane.getScaleX());
            dialog.close();

            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(new Label("cũ"));
            show(root);
            Label next = new Label("mới");
            UiTheme.show(root, next);
            assertEquals(1, next.getOpacity());
            assertEquals(0, next.getTranslateY());

            UiTheme.motionPreference = () -> false;
            Label animated = new Label("có hiệu ứng");
            UiTheme.show(root, animated);
            assertTrue(animated.getOpacity() < 1, "Không bật giảm chuyển động thì vẫn có hiệu ứng");
        });
    }

    private static Color borderColor(Button button) {
        return (Color) button.getBorder().getStrokes().getFirst().getTopStroke();
    }

    private static byte[] sampleImage() {
        try {
            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            var output = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void answer(Function<Dialog<ButtonType>, Optional<ButtonType>> reply) {
        ThemedDialog.presenter = dialog -> {
            asked.add(dialog);
            return reply.apply(dialog);
        };
    }

    private static List<String> buttonTexts(Dialog<ButtonType> dialog) {
        return dialog.getDialogPane().getButtonTypes().stream().map(ButtonType::getText).toList();
    }

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }

    private static void show(Parent view) {
        var stage = new javafx.stage.Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(view, 1100, 780));
        stage.show();
        view.applyCss();
        view.layout();
    }

    private static Button button(Parent view, String text) {
        return (Button) view.lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals(text)).findFirst().orElseThrow();
    }

    // Chụp trên nền xanh đêm của game để xem thẻ hộp thoại như khi hiện thật.
    private static void snapshot(Parent view, String filename) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#0b3478"));
            WritableImage image = view.snapshot(params, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Files.createDirectories(Path.of("target/qa"));
            ImageIO.write(png, "png", Path.of("target/qa", filename).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
