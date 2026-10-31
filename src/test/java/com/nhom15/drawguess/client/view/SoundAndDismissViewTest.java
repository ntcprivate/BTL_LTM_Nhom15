package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.client.controller.GameController;
import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class SoundAndDismissViewTest {
    private static final String MATCH = "00000000-0000-0000-0000-000000000009";
    private final List<Sounds.Effect> played = new ArrayList<>();
    private final List<Boolean> saved = new ArrayList<>();

    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        } catch (IllegalStateException alreadyStarted) {
            ready.complete(null);
        }
        ready.get(15, TimeUnit.SECONDS);
    }

    @BeforeEach void record() {
        UiTheme.motionPreference = () -> true;
        Sounds.player = played::add;
        Sounds.saveMuted = saved::add;
        Sounds.mutedProperty().set(false);
    }

    @AfterEach void restore() throws Exception {
        UiTheme.motionPreference = () -> Platform.getPreferences().isReducedMotion();
        Sounds.mutedProperty().set(false);
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    private static Message timer(GamePhase phase, int seconds) {
        return new Message(MessageType.TIME_UPDATE, new TimerInfo(MATCH, phase, seconds));
    }

    @Test void gameEventsPlayTheirSoundsAndMutingSilencesThem() throws Exception {
        ui(() -> {
            StackPane root = new StackPane();
            show(root);
            GameController game = new GameController(root, 1, new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60),
                    Map.of(), message -> {}, () -> {}, () -> {}, () -> {});
            assertEquals(List.of(Sounds.Effect.START), played, "Bắt đầu vẽ");
            played.clear();

            for (int s : new int[]{7, 6, 5, 5, 4, 3, 2, 1, 0}) { game.handleMessage(timer(GamePhase.DRAWING, s)); }
            assertEquals(5, played.size(), "Tích tắc 5 giây cuối, mỗi giây một lần dù server gửi trùng");
            assertTrue(played.stream().allMatch(Sounds.Effect.TICK::equals));
            played.clear();
            game.handleMessage(timer(GamePhase.UPLOADING, 3));
            assertTrue(played.isEmpty(), "Lúc chờ gửi tranh không tích tắc");

            game.handleMessage(new Message(MessageType.ROUND_START,
                    new RoundInfo(MATCH, 0, 2, 2, "lan", "Động vật", png(), 30, "_ _ _")));
            game.handleMessage(new Message(MessageType.GUESS_RESULT, new GuessResult(MATCH, 0, false, 2, false, "WRONG_ANSWER")));
            game.handleMessage(new Message(MessageType.GUESS_RESULT, new GuessResult(MATCH, 0, true, 1, true, "CORRECT", 4)));
            game.handleMessage(new Message(MessageType.TIME_UP, new TimerInfo(MATCH, GamePhase.ROUND_END, 0)));
            game.handleMessage(new Message(MessageType.GAME_RESULT, new GameResult(MATCH,
                    List.of(new ScoreEntry(1, "vu", 5, 1, 3.0, true), new ScoreEntry(2, "lan", 1, 2, 0, true)))));
            assertEquals(List.of(Sounds.Effect.WRONG, Sounds.Effect.CORRECT, Sounds.Effect.TIME_UP, Sounds.Effect.RESULT), played);
            played.clear();

            game.nextStage(new DrawingPrompt(MATCH, 1, "Đồ ăn", "Phở", 60, 1, 2));
            assertEquals(List.of(Sounds.Effect.START), played, "Sang vòng mới");
            played.clear();

            Sounds.setMuted(true);
            game.handleMessage(timer(GamePhase.DRAWING, 3));
            game.handleMessage(new Message(MessageType.TIME_UP, new TimerInfo(MATCH, GamePhase.ROUND_END, 0)));
            assertTrue(played.isEmpty(), "Tắt tiếng thì không phát gì");
            assertEquals(List.of(true), saved, "Lựa chọn tắt tiếng được lưu lại");
            game.close();
        });
    }

    @Test void everySpeakerButtonFollowsTheSameMuteSetting() throws Exception {
        ui(() -> {
            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            GameView game = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            Button inLobby = (Button) lobby.lookup(".sound-toggle");
            Button inGame = (Button) game.lookup(".sound-toggle");
            assertEquals("Tắt âm thanh", inLobby.getAccessibleText());
            assertNotNull(inLobby.getGraphic().lookup(".icon-volume"));

            inLobby.fire();
            assertTrue(Sounds.isMuted());
            assertEquals("Bật âm thanh", inGame.getAccessibleText(), "Tắt ở phòng chờ thì nút ở màn chơi cũng đổi");
            assertNotNull(inGame.getGraphic().lookup(".icon-volume-off"));
            inGame.fire();
            assertFalse(Sounds.isMuted());
            assertEquals(List.of(Sounds.Effect.MESSAGE), played, "Bật lại thì phát một tiếng nhỏ để thử");

            played.clear();
            lobby.startCountdown(3);
            assertEquals(List.of(Sounds.Effect.TICK), played, "Đếm ngược vào trận có tiếng tích tắc");
            lobby.stopCountdown();
        });
    }

    @Test void startCountdownTicksEverySecondUntilTheMatchBegins() throws Exception {
        AtomicReference<LobbyView> lobby = new AtomicReference<>();
        ui(() -> {
            LobbyView view = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            Stage stage = new Stage();
            stage.setOpacity(0);
            stage.setScene(new Scene(view, 1000, 760));
            stage.show();
            lobby.set(view);
            view.startCountdown(3);
        });
        Thread.sleep(3400);
        ui(() -> {
            assertEquals(List.of(Sounds.Effect.TICK, Sounds.Effect.TICK, Sounds.Effect.TICK), played,
                    "Đếm 3, 2, 1 mỗi giây một tiếng; tới 0 (đang vào trận) thì không kêu nữa");
            lobby.get().stopCountdown();
        });
    }

    @Test void clickingOutsideADialogCancelsIt() throws Exception {
        ui(() -> {
            StackPane root = new StackPane(new Label("Màn hình game"));
            show(root);
            ButtonType leave = new ButtonType("Rời phòng", ButtonBar.ButtonData.OK_DONE);
            ButtonType stay = new ButtonType("Ở lại", ButtonBar.ButtonData.CANCEL_CLOSE);
            Dialog<ButtonType> dialog = ThemedDialog.create(root.getScene().getWindow(), ThemedDialog.Tone.DANGER,
                    Icons.Glyph.LOG_OUT, "Rời phòng?", "Bạn sẽ bỏ dở trận.", leave, stay);
            assertEquals(Modality.NONE, dialog.getModality(), "Không khóa cửa sổ chính để nhận được cú bấm bên ngoài");
            dialog.show();
            Node scrim = root.lookup(".dialog-scrim");
            assertNotNull(scrim);
            // Phím bấm ở cửa sổ chính (khi người chơi chọn lại nó) không lọt xuống màn hình phía sau.
            List<String> keys = new ArrayList<>();
            root.getChildren().getFirst().addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> keys.add("leaked"));
            javafx.scene.input.KeyEvent enter = new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false);
            Event.fireEvent(root.getChildren().getFirst(), enter);
            assertTrue(keys.isEmpty(), "Phím không tới được màn hình phía sau");
            assertTrue(dialog.isShowing());
            press(scrim);
            assertFalse(dialog.isShowing(), "Bấm vào vùng tối bên ngoài thì đóng hộp thoại");
            assertEquals(stay, dialog.getResult(), "Đóng bằng cách bấm ra ngoài = chọn nút hủy");
            assertNull(root.lookup(".dialog-scrim"));

            // Lời mời: bấm ra ngoài = từ chối.
            AtomicReference<String> answer = new AtomicReference<>();
            Dialog<ButtonType> invite = InvitationPrompt.show(root.getScene().getWindow(),
                    new Invitation(2, "lan", 1, "vu", 7), () -> answer.set("accept"), () -> answer.set("decline"));
            press(root.lookup(".dialog-scrim"));
            assertFalse(invite.isShowing());
            assertEquals("decline", answer.get());

            // Bảng danh sách người online: bấm ra ngoài thì đóng, cú bấm vẫn tới màn hình phía sau.
            List<String> clicks = new ArrayList<>();
            Label behind = (Label) root.getChildren().getFirst();
            behind.setOnMousePressed(event -> clicks.add("behind"));
            Dialog<ButtonType> panel = ThemedDialog.create(root.getScene().getWindow(), ThemedDialog.Tone.INFO,
                    Icons.Glyph.USERS, "Người đang online", null, new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
            ThemedDialog.asPanel(panel);
            panel.show();
            assertNull(root.lookup(".dialog-scrim"));
            press(behind);
            assertFalse(panel.isShowing());
            assertEquals(List.of("behind"), clicks);

            // Không có chỗ đặt lớp tối thì hộp thoại vẫn khóa cửa sổ như cũ.
            Dialog<ButtonType> plain = ThemedDialog.create(null, ThemedDialog.Tone.INFO, Icons.Glyph.MAIL, "Thông báo", null,
                    new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE));
            assertEquals(Modality.APPLICATION_MODAL, plain.getModality());
        });
    }

    private static void press(Node node) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null));
    }

    private static byte[] png() {
        try {
            var image = new java.awt.image.BufferedImage(500, 500, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void show(StackPane root) {
        Stage stage = new Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }
}
