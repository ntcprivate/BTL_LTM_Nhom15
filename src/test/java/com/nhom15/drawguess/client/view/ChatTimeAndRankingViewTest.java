package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class ChatTimeAndRankingViewTest {
    private static final String MATCH = "00000000-0000-0000-0000-000000000011";

    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try {
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
        } catch (IllegalStateException alreadyStarted) {
            ready.complete(null);
        }
        ready.get(15, TimeUnit.SECONDS);
    }

    @AfterEach void hideWindows() throws Exception {
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    private static long at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Test void everyChatMessageShowsWhenItWasSent() throws Exception {
        ui(() -> {
            LobbyChatDock dock = new LobbyChatDock(1);
            Parent page = show(dock);
            dock.setExpanded(true);
            LocalDate today = LocalDate.now();
            dock.add(new ChatMessage(2, "lan", "Hôm qua vui ghê", at(today.minusDays(1), 21, 7)));
            dock.add(new ChatMessage(1, "vu", "Chào cả phòng", at(today, 14, 5)));
            relayout(page);
            List<String> times = texts(dock, ".chat-time");
            String yesterday = String.format("21:07 %02d/%02d", today.minusDays(1).getDayOfMonth(),
                    today.minusDays(1).getMonthValue());
            assertEquals(List.of(yesterday, "14:05"), times, "Hôm nay chỉ hiện giờ, hôm khác kèm ngày");
            dock.setExpanded(false);
            relayout(page);
            assertEquals("Bạn · 14:05", texts(dock, ".chat-dock-name").getFirst(), "Dòng tin mới nhất cũng có giờ gửi");
        });
    }

    @Test void resultSwitchesBetweenGuesserAndDrawerRankingsWithAverageTimes() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(1, "irises", 2, "wheatfield", 3, "cafe-night", 4, "starry-night"),
                    () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con chó", 60));
            List<ScoreEntry> players = List.of(new ScoreEntry(2, "lan", 12, 1, 4.0, true),
                    new ScoreEntry(1, "vu", 9, 2, 6.5, true), new ScoreEntry(3, "hoa", 5, 3, 12.25, true),
                    new ScoreEntry(4, "minh", 1, 4, Double.POSITIVE_INFINITY, true));
            List<DrawerEntry> drawers = List.of(new DrawerEntry(3, "hoa", 1, 3, 0, 2.0, true),
                    new DrawerEntry(1, "vu", 2, 3, 0, 4.0, true), new DrawerEntry(4, "minh", 3, 1, 0, 9.0, true),
                    new DrawerEntry(2, "lan", 4, 0, 1, Double.POSITIVE_INFINITY, true));
            view.showResult(new GameResult(MATCH, players, drawers));
            relayout(page);

            List<ToggleButton> tabs = view.lookupAll(".result-tab").stream().map(ToggleButton.class::cast).toList();
            assertEquals(List.of("Người đoán", "Người vẽ"), tabs.stream().map(ToggleButton::getText).toList());
            assertTrue(tabs.get(0).isSelected(), "Mặc định xem xếp hạng người đoán");
            assertEquals("Người thắng: lan", texts(view, ".result-winner").getFirst());
            assertTrue(texts(view, ".podium-detail").contains("TB đoán " + GameView.seconds(4.0)),
                    "Bục có thời gian đoán trung bình");
            assertEquals("4.  minh  —  1 điểm  ·  Chưa đoán đúng", texts(view, ".result-other").getFirst());
            snapshot(page, "game-result-guessers.png");

            tabs.get(1).fire();
            relayout(page);
            assertTrue(tabs.get(1).isSelected());
            assertEquals("Vẽ đẹp nhất: hoa", texts(view, ".result-winner").getFirst());
            assertNotNull(view.lookup(".result-winner .icon-sparkle"));
            assertEquals(3, view.lookupAll(".podium-step").size(), "Chỉ hiện bục của phần đang xem");
            assertTrue(texts(view, ".podium-score").contains("3 lượt đoán trúng"));
            assertTrue(texts(view, ".podium-detail").contains("TB đoán ra sau " + GameView.seconds(2.0)));
            assertEquals("4.  lan  —  0 lượt đoán trúng  ·  Chưa ai đoán trúng · 1 bị báo cáo",
                    texts(view, ".result-other").getFirst());
            snapshot(page, "game-result-drawers.png");

            tabs.get(1).fire();
            assertTrue(tabs.get(1).isSelected(), "Bấm lại phần đang xem thì vẫn giữ lựa chọn");
            tabs.get(0).fire();
            relayout(page);
            assertEquals("Người thắng: lan", texts(view, ".result-winner").getFirst());

            // Cả bảng (kể cả 8 người) nằm gọn trong khung 500x500; người ngoài top 3 cuộn được.
            Node panel = view.lookup(".result-panel");
            assertTrue(panel.getLayoutBounds().getHeight() <= 500.5, "Bảng kết quả không tràn khung vẽ");
            assertNotNull(view.lookup(".result-others"));
            assertInstanceOf(ScrollPane.class, view.lookup(".result-others"));
        });
    }

    @Test void eightPlayersStillFitTheBoardAndOldServersShowOnlyOneRanking() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            List<ScoreEntry> players = new java.util.ArrayList<>();
            List<DrawerEntry> drawers = new java.util.ArrayList<>();
            for (int id = 1; id <= 8; id++) {
                players.add(new ScoreEntry(id, "nguoichoi" + id, 20 - id, id, id * 1.5, true));
                drawers.add(new DrawerEntry(id, "nguoichoi" + id, id, 8 - id, id % 3, id * 2.0, true));
            }
            view.showResult(new GameResult(MATCH, players, drawers));
            view.showRematch(new RoomInfo(1, RoomStatus.WAITING, List.of(
                    new PlayerInfo(1, "nguoichoi1", true, false), new PlayerInfo(2, "nguoichoi2", true, false))));
            relayout(page);
            Node panel = view.lookup(".result-panel");
            assertTrue(panel.getLayoutBounds().getHeight() <= 500.5);
            ((ToggleButton) view.lookupAll(".result-tab").stream().toList().get(1)).fire();
            relayout(page);
            snapshot(page, "game-result-8-drawers.png");
            for (Node podium : view.lookupAll(".podium-step")) {
                var bounds = podium.localToScene(podium.getLayoutBounds());
                var board = panel.localToScene(panel.getLayoutBounds());
                assertTrue(bounds.getMaxY() <= board.getMaxY() + 0.5, "Bục không bị đẩy ra ngoài khung");
            }

            view.showResult(new GameResult(MATCH, players));
            relayout(page);
            assertFalse(view.lookup(".result-tabs").isVisible(), "Không có bảng người vẽ thì không hiện nút chọn");
        });
    }

    private static Parent show(Parent view) {
        StackPane page = new StackPane(view);
        page.setPadding(new javafx.geometry.Insets(24));
        UiTheme.apply(page);
        Stage stage = new Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(page, 1000, 760));
        stage.show();
        relayout(page);
        return page;
    }

    private static void relayout(Parent page) { page.applyCss(); page.layout(); }

    private static List<String> texts(Parent root, String selector) {
        return root.lookupAll(selector).stream().filter(Labeled.class::isInstance)
                .filter(Node::isVisible).map(n -> ((Labeled) n).getText()).toList();
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

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }
}
