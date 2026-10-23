package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class GameLobbyFeaturesViewTest {
    private static final String MATCH = "00000000-0000-0000-0000-000000000001";

    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        } catch (IllegalStateException alreadyStarted) {
            ready.complete(null);
        }
        ready.get(15, TimeUnit.SECONDS);
    }

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }

    @AfterEach void closeWindows() throws Exception {
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    private static void show(Parent view) {
        var stage = new javafx.stage.Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(view, 1100, 780));
        stage.show();
        view.applyCss();
        view.layout();
    }

    private static void snapshot(Parent view, String filename) {
        try {
            WritableImage image = view.snapshot(null, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Files.createDirectories(Path.of("target/qa"));
            ImageIO.write(png, "png", Path.of("target/qa", filename).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Button button(Parent view, String text) {
        return (Button) view.lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals(text)).findFirst().orElseThrow();
    }

    private static Canvas findCanvas(Parent parent) {
        for (var child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Canvas canvas) { return canvas; }
            if (child instanceof Parent nested) {
                Canvas found = findCanvas(nested);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static int pixel(Canvas canvas, int x, int y) {
        return canvas.snapshot(null, null).getPixelReader().getArgb(x, y);
    }

    private static void press(Canvas canvas, double x, double y) {
        // MouseEvent nhận tọa độ theo scene; JavaFX tự đổi sang tọa độ của canvas khi phát sự kiện.
        var scene = canvas.localToScene(x, y);
        canvas.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, scene.getX(), scene.getY(), x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    @Test void undoRestoresPreviousStrokeAndClearedDrawing() throws Exception {
        ui(() -> {
            GameView view = new GameView(() -> {}, () -> {}, () -> {}, () -> {});
            show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60));
            Canvas canvas = findCanvas(view);
            Button undo = button(view, "↶ Hoàn tác");
            Button clear = button(view, "Xóa tranh");
            int white = pixel(canvas, 100, 100);
            assertTrue(undo.isDisabled());

            press(canvas, 100, 100);
            press(canvas, 300, 300);
            assertNotEquals(white, pixel(canvas, 100, 100));
            assertNotEquals(white, pixel(canvas, 300, 300));
            assertFalse(undo.isDisabled());

            clear.fire();
            assertEquals(white, pixel(canvas, 100, 100));
            undo.fire();
            assertNotEquals(white, pixel(canvas, 100, 100), "Hoàn tác phải khôi phục tranh vừa bị xóa");
            assertNotEquals(white, pixel(canvas, 300, 300));

            undo.fire();
            assertEquals(white, pixel(canvas, 300, 300), "Hoàn tác phải bỏ nét vẽ gần nhất");
            assertNotEquals(white, pixel(canvas, 100, 100));
            undo.fire();
            assertEquals(white, pixel(canvas, 100, 100));
            assertTrue(undo.isDisabled());
            snapshot(view, "game-drawing-undo.png");
        });
    }

    @Test void guessingRoundShowsMaskedHintThenRevealedLetter() throws Exception {
        ui(() -> {
            GameView view = new GameView(() -> {}, () -> {}, () -> {}, () -> {});
            show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con chó", 60));
            Label hint = (Label) view.lookup(".game-hint");
            assertFalse(hint.isVisible());
            view.showRound(new RoundInfo(MATCH, 0, 2, 2, "player2", "Động vật", sampleImage(), 30, "_ _ _   _ _ _"), 1);
            assertTrue(hint.isVisible());
            assertEquals("Gợi ý: _ _ _   _ _ _", hint.getText());
            view.showHint("_ _ _   m _ _", true);
            assertEquals("Gợi ý: _ _ _   m _ _   (đã mở 1 chữ)", hint.getText());
            snapshot(view, "game-hint.png");
            view.showAnswer("Con mèo");
            assertEquals("Đáp án: Con mèo", hint.getText());
        });
    }

    @Test void onlyHostSeesKickButtonsAndPlayersSeeCountdown() throws Exception {
        ui(() -> {
            RoomInfo room = new RoomInfo(7, RoomStatus.READY_CHECK, List.of(
                    new PlayerInfo(1, "chuphong", true, true, AvatarCatalog.DEFAULT, -1),
                    new PlayerInfo(2, "nguoi2", false, true, AvatarCatalog.DEFAULT, 42),
                    new PlayerInfo(3, "nguoi3", true, true, AvatarCatalog.DEFAULT, -1)), 1);

            LobbyView host = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(host);
            host.update(room);
            host.applyCss(); host.layout();
            assertEquals(2, host.lookupAll(".kick-button").size());
            assertEquals(1, host.lookupAll(".room-host").size());
            assertTrue(host.lookupAll(".player-state").stream()
                    .anyMatch(n -> ((Label) n).getText().equals("Chưa sẵn sàng • rời phòng sau 42 giây")));
            snapshot(host, "lobby-host.png");

            LobbyView idle = new LobbyView(2, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(idle);
            idle.update(room);
            idle.applyCss(); idle.layout();
            assertEquals(0, idle.lookupAll(".kick-button").size());
            assertTrue(idle.lookupAll(".label").stream().anyMatch(n -> n instanceof Label l
                    && l.getText().startsWith("Mọi người đang chờ bạn! Hãy bấm Sẵn sàng trong 42 giây")));
            snapshot(idle, "lobby-countdown.png");

            idle.update(new RoomInfo(7, RoomStatus.PLAYING, room.getPlayers(), 1));
            host.update(new RoomInfo(7, RoomStatus.PLAYING, room.getPlayers(), 1));
            host.applyCss(); host.layout();
            assertEquals(0, host.lookupAll(".kick-button").size());
        });
    }

    private static byte[] sampleImage() {
        try {
            BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 500, 500);
            graphics.setColor(java.awt.Color.BLACK); graphics.fillOval(150, 150, 200, 200);
            graphics.dispose();
            var output = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
