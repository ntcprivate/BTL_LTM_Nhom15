package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class LobbyCountdownViewTest {
    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        } catch (IllegalStateException alreadyStarted) {
            ready.complete(null);
        }
        ready.get(15, TimeUnit.SECONDS);
    }

    @AfterEach void closeWindows() throws Exception {
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    private static RoomInfo room(RoomStatus status) {
        return new RoomInfo(7, status, List.of(new PlayerInfo(1, "vu", true, true),
                new PlayerInfo(2, "lan", true, true), new PlayerInfo(3, "minh", true, true)), 1);
    }

    @Test void countdownReplacesTheButtonsAndCanBeCancelled() throws Exception {
        ui(() -> {
            List<String> sent = new ArrayList<>();
            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            lobby.setOnNotReady(() -> sent.add("CANCEL_READY"));
            show(lobby, 1000, 760);
            lobby.update(room(RoomStatus.GAME_STARTING));
            lobby.startCountdown(5);
            lobby.applyCss(); lobby.layout();

            assertTrue(lobby.isCountingDown());
            Button notReady = button(lobby, "Tôi chưa sẵn sàng!");
            assertTrue(notReady.isVisible() && !notReady.isDisabled());
            assertFalse(button(lobby, "Rời phòng").getParent().isVisible(), "Hàng nút thường được thay bằng thanh đếm ngược");
            assertEquals("5", ((Label) lobby.lookup(".start-countdown-number")).getText());
            Label title = (Label) lobby.lookup(".start-countdown-title");
            assertEquals("Trận đấu bắt đầu sau 5 giây", title.getText());
            assertFalse(title.isTextTruncated());
            assertFalse(notReady.isTextTruncated());
            lobby.lookup(".start-countdown-number").setScaleX(1);
            lobby.lookup(".start-countdown-number").setScaleY(1);
            snapshot(lobby, "lobby-countdown.png");

            notReady.fire();
            assertEquals(List.of("CANCEL_READY"), sent);
            assertTrue(notReady.isDisabled(), "Chỉ gửi một lần");

            // Server hủy đếm ngược: phòng quay lại chờ sẵn sàng, hàng nút thường hiện lại.
            lobby.update(room(RoomStatus.READY_CHECK));
            lobby.applyCss(); lobby.layout();
            assertFalse(lobby.isCountingDown());
            assertTrue(button(lobby, "Rời phòng").getParent().isVisible());
        });
    }

    @Test void countdownTicksDownEverySecondAndEndsWhenTheMatchStarts() throws Exception {
        LobbyView[] lobby = new LobbyView[1];
        ui(() -> {
            lobby[0] = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(lobby[0], 1000, 760);
            lobby[0].update(room(RoomStatus.GAME_STARTING));
            lobby[0].startCountdown(2);
        });
        Thread.sleep(1300);
        ui(() -> assertEquals("1", ((Label) lobby[0].lookup(".start-countdown-number")).getText()));
        Thread.sleep(1000);
        ui(() -> {
            assertEquals("Đang vào trận...", ((Label) lobby[0].lookup(".start-countdown-title")).getText());
            assertTrue(button(lobby[0], "Tôi chưa sẵn sàng!").isDisabled(), "Hết giờ thì không hủy được nữa");
            lobby[0].update(room(RoomStatus.PLAYING));
            assertFalse(lobby[0].isCountingDown());
        });
    }

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }

    private static void show(Parent view, double width, double height) {
        var stage = new javafx.stage.Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(view, width, height));
        stage.show();
        view.applyCss();
        view.layout();
    }

    private static Button button(Parent view, String text) {
        return (Button) view.lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && text.equals(b.getText())).findFirst().orElseThrow();
    }

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
