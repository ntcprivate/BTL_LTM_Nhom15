package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class GameChatAndRematchViewTest {
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

    @BeforeEach void stillFrames() { UiTheme.motionPreference = () -> true; }

    @AfterEach void closeWindows() throws Exception {
        UiTheme.motionPreference = () -> Platform.getPreferences().isReducedMotion();
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    private static GameView game(List<String> sent) {
        GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
        view.setOnChat(sent::add);
        return view;
    }

    private static ChatMessage message(int from, String name, String text) {
        return new ChatMessage(from, name, text, 0);
    }

    @Test void wideWindowPutsChatLeftOfTheCanvasInBalance() throws Exception {
        ui(() -> {
            List<String> sent = new ArrayList<>();
            GameView view = game(sent);
            show(view, 1440, 860);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60));
            view.addChat(message(2, "lan", "Chào cả phòng, vẽ đẹp nhé!"));
            view.addChat(message(1, "vu", "Ok luôn, chúc may mắn"));
            view.addChat(message(3, "minh", "Mình vẽ con gì đây ta... gợi ý một chút đi các bạn ơi"));
            view.applyCss(); view.layout();

            assertTrue(view.isWideLayout());
            Node chatColumn = view.getChat().getParent();
            Node scoreColumn = view.lookup(".score-list").getParent().getParent();
            Node canvas = view.lookup(".canvas-frame").getParent();
            Bounds chat = chatColumn.localToScene(chatColumn.getLayoutBounds());
            Bounds board = canvas.localToScene(canvas.getLayoutBounds());
            Bounds scores = scoreColumn.localToScene(scoreColumn.getLayoutBounds());
            assertTrue(chat.getMaxX() <= board.getMinX() && board.getMaxX() <= scores.getMinX(), "Chat | ô vẽ | bảng điểm " + chat + " " + board + " " + scores);
            assertEquals(chat.getWidth(), scores.getWidth(), 1, "Hai cột hai bên rộng bằng nhau");
            assertEquals(chat.getHeight(), scores.getHeight(), 1, "và cao bằng nhau");
            assertEquals(board.getMinX() - chat.getMaxX(), scores.getMinX() - board.getMaxX(), 1, "Khoảng cách hai bên ô vẽ bằng nhau");
            assertFalse(view.lookup(".side-tabs").isVisible(), "Cửa sổ rộng không cần thẻ chuyển");
            assertTrue(view.lookupAll(".chat-mine").size() == 1 && view.lookupAll(".chat-theirs").size() == 2);
            snapshot(view, "game-chat-wide.png");

            view.getChat().getInput().setText("  xin chào  ");
            ((Button) view.getChat().lookup(".chat-send")).fire();
            assertEquals(List.of("xin chào"), sent);
            assertEquals("", view.getChat().getInput().getText());
            view.showChatNotice("Tin nhắn có chứa từ khóa đang được đoán nên không gửi được.");
            assertTrue(view.getChat().lookup(".chat-notice").isVisible());
        });
    }

    @Test void narrowWindowMergesChatIntoTheSideColumnWithUnreadCount() throws Exception {
        ui(() -> {
            GameView view = game(new ArrayList<>());
            show(view, 1100, 780);
            view.showRound(new RoundInfo(MATCH, 0, 2, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            view.applyCss(); view.layout();
            assertFalse(view.isWideLayout());
            ToggleButton chatTab = tab(view, "Trò chuyện");
            assertTrue(chatTab.isVisible());
            assertFalse(view.getChat().isVisible(), "Mặc định hiện bảng điểm");

            view.addChat(message(2, "lan", "Ai đoán ra chưa?"));
            view.addChat(message(3, "minh", "Chưa luôn"));
            assertEquals("Trò chuyện (2)", chatTab.getText(), "Đếm tin chưa đọc khi khung chat đang ẩn");

            chatTab.fire();
            view.applyCss(); view.layout();
            assertTrue(view.getChat().isVisible());
            assertFalse(view.lookup(".score-list").isVisible());
            assertEquals("Trò chuyện", chatTab.getText(), "Mở khung chat thì hết tin chưa đọc");
            chatTab.fire();
            assertTrue(chatTab.isSelected(), "Bấm lại thẻ đang chọn không bỏ chọn nó");
            snapshot(view, "game-chat-narrow.png");
        });
    }

    @Test void resultScreenOffersPlayAgainAndShowsWhoIsStillDeciding() throws Exception {
        ui(() -> {
            List<String> actions = new ArrayList<>();
            GameView view = game(new ArrayList<>());
            view.setOnPlayAgain(() -> actions.add("play-again"));
            show(view, 1440, 860);
            view.showResult(new GameResult(MATCH, List.of(new ScoreEntry(2, "lan", 12, 1, 4.0, true),
                    new ScoreEntry(1, "vu", 9, 2, 6.0, true), new ScoreEntry(3, "minh", 7, 3, 8.0, true))));
            Button playAgain = button(view, "Chơi lại");
            assertTrue(playAgain.isVisible() && !playAgain.isDisabled());
            assertTrue(playAgain.getStyleClass().contains("primary-button"));

            view.showRematch(new RoomInfo(7, RoomStatus.READY_CHECK, List.of(new PlayerInfo(2, "lan", true, true),
                    new PlayerInfo(1, "vu", false, true), new PlayerInfo(3, "minh", true, true)), 2));
            view.applyCss(); view.layout();
            Label rematch = (Label) view.lookup(".rematch-status");
            assertTrue(rematch.isVisible());
            assertEquals("2/3 người muốn chơi lại • Đang chờ: bạn", rematch.getText());
            assertFalse(rematch.isTextTruncated());
            snapshot(view, "game-result-rematch.png");

            playAgain.fire();
            assertEquals(List.of("play-again"), actions);
            assertTrue(playAgain.isDisabled(), "Chỉ gửi một lần");

            view.showRematch(new RoomInfo(7, RoomStatus.WAITING, List.of(new PlayerInfo(1, "vu", true, true)), 1));
            assertTrue(rematch.getText().startsWith("Cần ít nhất 2 người"));
            String before = rematch.getText();
            view.showRematch(new RoomInfo(7, RoomStatus.FINISHED, List.of(), 1));
            assertEquals(before, rematch.getText(), "Bỏ qua trạng thái FINISHED tạm thời của phòng");
        });
    }

    @Test void correctGuessShowsThePointsEarned() throws Exception {
        ui(() -> {
            GameView view = game(new ArrayList<>());
            show(view, 1440, 860);
            view.showRound(new RoundInfo(MATCH, 0, 2, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            view.showGuessResult(new GuessResult(MATCH, 0, true, 2, true, "CORRECT", 4));
            assertTrue(view.lookupAll(".label").stream()
                    .anyMatch(n -> "Đúng rồi! +4 điểm".equals(((Label) n).getText())));
        });
    }

    @Test void restoredDraftBecomesTheBaseOfTheDrawingUntilCleared() throws Exception {
        ui(() -> {
            GameView view = game(new ArrayList<>());
            show(view, 1440, 860);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 40));
            assertFalse(view.takeChanges(), "Chưa vẽ gì thì không cần gửi bản nháp");
            view.restoreDrawing(sampleImage());
            assertFalse(view.takeChanges(), "Tranh lấy lại từ server không phải nét mới");
            int center = 250 * 500 + 250;
            assertEquals(0xff000000, view.captureDrawing()[center], "Hình tròn đen của bản nháp hiện lại trên canvas");

            javafx.scene.canvas.Canvas canvas = (javafx.scene.canvas.Canvas) view.lookup(".canvas-frame").getParent()
                    .getChildrenUnmodifiable().stream().filter(javafx.scene.canvas.Canvas.class::isInstance).findFirst().orElseThrow();
            drag(canvas, 20, 20, 60, 20);
            assertTrue(view.takeChanges());
            assertFalse(view.takeChanges(), "Đã lấy thay đổi thì đặt lại");
            button(view, "Hoàn tác").fire();
            assertTrue(view.takeChanges(), "Hoàn tác cũng là thay đổi");
            assertEquals(0xff000000, view.captureDrawing()[center], "Hoàn tác không làm mất tranh nền");

            button(view, "Xóa tranh").fire();
            assertEquals(0xffffffff, view.captureDrawing()[center], "Xóa tranh xóa luôn tranh nền");
        });
    }

    @Test void guessingCorrectlyDisablesReportAndReportingLocksGuessing() throws Exception {
        ui(() -> {
            GameView view = game(new ArrayList<>());
            show(view, 1440, 860);
            view.showRound(new RoundInfo(MATCH, 0, 3, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            Button report = button(view, "Báo cáo tranh");
            assertFalse(report.isDisabled());
            view.showGuessResult(new GuessResult(MATCH, 0, true, 2, true, "CORRECT", 4));
            assertTrue(report.isDisabled(), "Đoán đúng rồi thì không báo cáo");
            assertEquals("Bạn đã đoán đúng nên không báo cáo tranh này.", ((Label) view.lookup(".report-status")).getText());
            view.showReportStatus(new ReportStatus(MATCH, 0, 2, 1, 2, false));
            assertTrue(((Label) view.lookup(".report-status")).getText().endsWith("Bạn đã đoán đúng nên không báo cáo."));

            // Lượt mới: người chưa đoán đúng báo cáo thì ô đoán bị khóa.
            view.showRound(new RoundInfo(MATCH, 1, 3, 3, "minh", "Động vật", sampleImage(), 30, "_ _"), 1);
            assertFalse(report.isDisabled(), "Lượt mới thì báo cáo lại được");
            TextField answer = (TextField) view.lookup(".guess-row .text-field");
            assertFalse(answer.isDisabled());
            view.markReported();
            assertTrue(answer.isDisabled());
            assertTrue(button(view, "Đoán").isDisabled());
            assertEquals("Bạn đã báo cáo tranh này nên không đoán tiếp. Chờ lượt sau nhé.",
                    view.lookupAll(".label").stream().map(n -> ((Label) n).getText())
                            .filter(t -> t != null && t.startsWith("Bạn đã báo cáo")).findFirst().orElseThrow());
            view.showGuessResult(new GuessResult(MATCH, 1, false, 3, true, "REPORTED"));
            assertTrue(answer.isDisabled());
            snapshot(view, "game-reported-locks-guess.png");
        });
    }

    @Test void nextStageClearsTheBoardAndShowsTheNewTopic() throws Exception {
        ui(() -> {
            GameView view = game(new ArrayList<>());
            view.getChat().addHistory(List.of(message(2, "lan", "Tin ở phòng chờ")));
            assertEquals(0, view.getChat().unreadProperty().get(), "Tin cũ ở phòng chờ không tính là chưa đọc");
            show(view, 1440, 860);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60, 0, 2));
            Label category = (Label) view.lookup(".game-header .section-title");
            assertEquals("Vòng 1/2: Động vật", category.getText());
            javafx.scene.canvas.Canvas canvas = canvas(view);
            drag(canvas, 100, 100, 300, 300);
            view.showRound(new RoundInfo(MATCH, 1, 6, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _", 0, 2), 1);
            assertFalse(canvas.isVisible());

            view.showDrawing(new DrawingPrompt(MATCH, 1, "Đồ ăn", "Phở", 60, 1, 2));
            view.applyCss(); view.layout();
            assertEquals("Vòng 2/2: Đồ ăn", category.getText());
            assertTrue(canvas.isVisible(), "Ô vẽ hiện lại cho vòng mới");
            assertFalse(view.lookup(".canvas-frame").getParent().getChildrenUnmodifiable().stream()
                    .filter(javafx.scene.image.ImageView.class::isInstance).findFirst().orElseThrow().isVisible());
            assertEquals(0xffffffff, view.captureDrawing()[200 * 500 + 200], "Ô vẽ trắng, không còn nét của vòng trước");
            assertFalse(view.takeChanges());
            assertTrue(button(view, "Hoàn tác").isDisabled());
            assertFalse(view.lookup(".game-hint").isVisible(), "Gợi ý của lượt đoán trước đã ẩn");
            snapshot(view, "game-next-stage.png");

            view.showRound(new RoundInfo(MATCH, 4, 6, 3, "minh", "Đồ ăn", sampleImage(), 30, "_ _", 1, 2), 1);
            assertTrue(view.lookupAll(".game-word").stream()
                    .anyMatch(n -> ((Label) n).getText().equals("Tranh của minh — lượt 2/3")), "Đếm lượt trong vòng");
        });
    }

    private static javafx.scene.canvas.Canvas canvas(Parent view) {
        return (javafx.scene.canvas.Canvas) view.lookup(".canvas-frame").getParent().getChildrenUnmodifiable().stream()
                .filter(javafx.scene.canvas.Canvas.class::isInstance).findFirst().orElseThrow();
    }

    private static void drag(javafx.scene.canvas.Canvas canvas, double x1, double y1, double x2, double y2) {
        canvas.fireEvent(mouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, x1, y1));
        canvas.fireEvent(mouse(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, x2, y2));
        canvas.fireEvent(mouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, x2, y2));
    }

    private static javafx.scene.input.MouseEvent mouse(javafx.event.EventType<javafx.scene.input.MouseEvent> type, double x, double y) {
        return new javafx.scene.input.MouseEvent(type, x, y, x, y, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static ToggleButton tab(Parent view, String text) {
        return (ToggleButton) view.lookupAll(".side-tab").stream()
                .filter(n -> ((ToggleButton) n).getText().startsWith(text)).findFirst().orElseThrow();
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
