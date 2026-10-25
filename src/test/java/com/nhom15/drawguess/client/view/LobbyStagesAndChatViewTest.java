package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
class LobbyStagesAndChatViewTest {
    private static final List<String> TOPICS = List.of("Động vật", "Đồ ăn", "Phương tiện", "Thiên nhiên", "Nghề nghiệp");

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

    private static RoomInfo room(int hostId, RoomStatus status, List<String> stages) {
        return new RoomInfo(7, status, List.of(new PlayerInfo(1, "vu", false, true),
                new PlayerInfo(2, "lan", true, true), new PlayerInfo(3, "minh", false, true)), hostId, stages);
    }

    private static LobbyView lobby(int userId) {
        return new LobbyView(userId, () -> {}, () -> {}, () -> {}, ignored -> {});
    }

    @SuppressWarnings("unchecked")
    private static List<ComboBox<String>> combos(Parent view) {
        return view.lookupAll(".stage-row .combo-box").stream().map(n -> (ComboBox<String>) n).toList();
    }

    @Test void hostPicksATopicForEachStage() throws Exception {
        ui(() -> {
            List<List<String>> sent = new ArrayList<>();
            LobbyView lobby = lobby(1);
            lobby.setOnStagesChanged(sent::add);
            lobby.setTopics(TOPICS);
            show(lobby, 1100, 720);
            lobby.update(room(1, RoomStatus.READY_CHECK, List.of("Động vật", "", "Đồ ăn")));
            lobby.applyCss(); lobby.layout();

            List<ComboBox<String>> rows = combos(lobby);
            assertEquals(3, rows.size());
            assertEquals("Động vật", rows.getFirst().getValue());
            assertEquals(StagePanel.RANDOM, rows.get(1).getValue());
            assertFalse(rows.get(1).getItems().contains("Động vật"), "Chủ đề đã chọn ở vòng khác không hiện lại");
            assertTrue(rows.getFirst().getItems().contains("Động vật"));
            assertEquals("3/5 vòng", ((Label) lobby.lookup(".stage-summary")).getText());
            assertSideColumnFits(lobby);
            javafx.scene.control.ScrollPane scroll = (javafx.scene.control.ScrollPane) lobby.lookup(".stage-scroll");
            assertTrue(scroll.getViewportBounds().getHeight() >= ((Parent) scroll.getContent()).prefHeight(-1) - 1,
                    "Cửa sổ 1100x720 vẫn thấy đủ 3 vòng không cần cuộn");
            snapshot(lobby, "lobby-stages-host.png");

            rows.get(1).setValue("Thiên nhiên");
            assertEquals(List.of("Động vật", "Thiên nhiên", "Đồ ăn"), sent.getLast());
            button(lobby, "Thêm vòng").fire();
            assertEquals(List.of("Động vật", "Thiên nhiên", "Đồ ăn", ""), sent.getLast());
            Button remove = (Button) lobby.lookupAll(".stage-remove").iterator().next();
            assertEquals("Bỏ vòng 1", remove.getAccessibleText());
            remove.fire();
            assertEquals(List.of("Thiên nhiên", "Đồ ăn", ""), sent.getLast());

            lobby.update(room(1, RoomStatus.READY_CHECK, List.of("", "", "", "", "")));
            assertTrue(button(lobby, "Thêm vòng").isDisabled(), "Tối đa 5 vòng");
            lobby.update(room(1, RoomStatus.READY_CHECK, List.of("")));
            assertTrue(((Button) lobby.lookup(".stage-remove")).isDisabled(), "Luôn còn ít nhất 1 vòng");
        });
    }

    @Test void otherPlayersOnlySeeTheStages() throws Exception {
        ui(() -> {
            LobbyView lobby = lobby(2);
            lobby.setTopics(TOPICS);
            show(lobby, 1440, 860);
            lobby.update(room(1, RoomStatus.READY_CHECK, List.of("Động vật", "", "Đồ ăn")));
            lobby.applyCss(); lobby.layout();
            assertTrue(combos(lobby).isEmpty(), "Không phải chủ phòng thì không sửa được");
            assertFalse(button(lobby, "Thêm vòng").isVisible());
            List<String> shown = lobby.lookupAll(".stage-topic").stream().map(n -> ((Label) n).getText()).toList();
            assertEquals(List.of("Động vật", StagePanel.RANDOM, "Đồ ăn"), shown);
            assertSideColumnFits(lobby);
            snapshot(lobby, "lobby-stages-guest.png");

            // Chủ phòng cũng không sửa được khi đang đếm ngược vào trận.
            LobbyView host = lobby(1);
            host.update(room(1, RoomStatus.GAME_STARTING, List.of("Động vật")));
            assertTrue(combos(host).isEmpty());
        });
    }

    @Test void chatShowsTheLatestMessageUntilTheIconOpensTheWholeConversation() throws Exception {
        ui(() -> {
            List<String> sent = new ArrayList<>();
            LobbyView lobby = lobby(1);
            lobby.setOnChat(sent::add);
            lobby.setTopics(TOPICS);
            show(lobby, 1100, 720);
            lobby.update(room(1, RoomStatus.READY_CHECK, List.of("Động vật", "")));
            LobbyChatDock dock = lobby.getChatDock();
            assertFalse(dock.isExpanded());
            assertTrue(((Label) lobby.lookup(".chat-dock-text")).getText().startsWith("Chưa có tin nhắn"));

            lobby.addChat(new ChatMessage(2, "lan", "Chào cả nhà!", 0));
            lobby.addChat(new ChatMessage(3, "minh", "Chơi 2 vòng nhé, vòng 2 ngẫu nhiên cho vui", 0));
            lobby.applyCss(); lobby.layout();
            assertEquals("minh", ((Label) lobby.lookup(".chat-dock-name")).getText());
            assertEquals("Chơi 2 vòng nhé, vòng 2 ngẫu nhiên cho vui", ((Label) lobby.lookup(".chat-dock-text")).getText());
            Label badge = (Label) lobby.lookup(".chat-dock-badge");
            assertTrue(badge.isVisible());
            assertEquals("2", badge.getText());
            assertTrue(lobby.getStagePanel().isVisible(), "Thu gọn thì vẫn thấy vòng đấu");
            snapshot(lobby, "lobby-chat-collapsed.png");

            ((Button) lobby.lookup(".chat-dock-toggle")).fire();
            lobby.applyCss(); lobby.layout();
            assertTrue(dock.isExpanded());
            assertFalse(lobby.getStagePanel().isVisible(), "Mở chat thì chat chiếm cả cột");
            assertFalse(badge.isVisible(), "Đã mở thì hết tin chưa đọc");
            assertEquals(2, lobby.lookupAll(".chat-bubble").size());
            assertSideColumnFits(lobby);
            snapshot(lobby, "lobby-chat-open.png");

            dock.getChat().getInput().setText("Ok!");
            ((Button) lobby.lookup(".chat-send")).fire();
            assertEquals(List.of("Ok!"), sent);

            button(lobby, "Thu gọn").fire();
            assertFalse(dock.isExpanded());
            assertTrue(lobby.getStagePanel().isVisible());

            lobby.clearChat();
            assertTrue(((Label) lobby.lookup(".chat-dock-text")).getText().startsWith("Chưa có tin nhắn"));
        });
    }

    // Cột vòng đấu/chat nằm gọn bên phải bảng người chơi, không tràn khỏi màn hình.
    private static void assertSideColumnFits(LobbyView lobby) {
        Node side = lobby.lookup(".lobby-side");
        Node board = lobby.lookup(".lobby-board");
        Bounds s = side.localToScene(side.getLayoutBounds());
        Bounds b = board.localToScene(board.getLayoutBounds());
        assertTrue(b.getMaxX() <= s.getMinX(), "Bảng người chơi bên trái cột vòng đấu");
        assertEquals(b.getHeight(), s.getHeight(), 1, "Cột bên phải cao bằng bảng người chơi");
        assertTrue(s.getMaxX() <= lobby.getScene().getWidth(), "Không tràn ngang");
        for (Node label : lobby.lookupAll(".label")) {
            if (label instanceof Label l && l.isVisible() && isInside(l, side) && !l.isWrapText()
                    && !l.getStyleClass().contains("chat-dock-text") && !l.getStyleClass().contains("chat-text")) {
                assertFalse(l.isTextTruncated(), "Chữ bị cắt: " + l.getText());
            }
        }
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) { if (n == ancestor) { return true; } }
        return false;
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
            view.applyCss(); view.layout();
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
