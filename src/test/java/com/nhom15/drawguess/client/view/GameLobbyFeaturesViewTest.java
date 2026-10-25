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
            Button undo = button(view, "Hoàn tác");
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

    @Test void onlyHostSeesKickButtonsAndNobodyIsCountedDown() throws Exception {
        ui(() -> {
            RoomInfo room = new RoomInfo(7, RoomStatus.READY_CHECK, List.of(
                    new PlayerInfo(1, "chuphong", true, true),
                    new PlayerInfo(2, "nguoi2", false, true),
                    new PlayerInfo(3, "nguoi3", true, true)), 1);

            LobbyView host = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(host);
            host.update(room);
            host.applyCss(); host.layout();
            assertEquals(2, host.lookupAll(".kick-button").size());
            assertEquals(1, host.lookupAll(".room-host").size());
            assertTrue(host.lookupAll(".player-state").stream()
                    .anyMatch(n -> ((Label) n).getText().equals("Chưa sẵn sàng")));
            assertTrue(host.lookupAll(".player-state").stream().noneMatch(n -> ((Label) n).isTextTruncated()),
                    "Dòng trạng thái trong phòng chờ không được bị cắt chữ");
            snapshot(host, "lobby-host.png");

            LobbyView idle = new LobbyView(2, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(idle);
            idle.update(room);
            idle.applyCss(); idle.layout();
            assertEquals(0, idle.lookupAll(".kick-button").size());
            // Người chưa sẵn sàng không còn bị đếm ngược hay dọa đưa ra khỏi phòng.
            assertTrue(idle.lookupAll(".label").stream().noneMatch(n -> n instanceof Label l
                    && (l.getText().contains("giây") || l.getText().contains("đưa ra khỏi phòng"))));
            snapshot(idle, "lobby-unready.png");

            idle.update(new RoomInfo(7, RoomStatus.PLAYING, room.getPlayers(), 1));
            host.update(new RoomInfo(7, RoomStatus.PLAYING, room.getPlayers(), 1));
            host.applyCss(); host.layout();
            assertEquals(0, host.lookupAll(".kick-button").size());
        });
    }

    @Test void guessersCanReportTheDrawingAndSeeWhenItIsRejected() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, java.util.Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60));
            Button report = button(view, "Báo cáo tranh");
            assertFalse(report.getParent().isVisible(), "Lúc vẽ không có thanh báo cáo");
            assertTrue(view.lookup(".toolbar").isVisible());

            view.showRound(new RoundInfo(MATCH, 0, 2, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            view.applyCss(); view.layout();
            assertTrue(report.getParent().isVisible() && report.isVisible() && !report.isDisabled());
            assertNotNull(report.getGraphic().lookup(".icon-flag"));
            Label status = (Label) view.lookup(".report-status");
            assertEquals("Tranh không đúng từ khóa? Bạn có thể báo cáo.", status.getText());

            view.markReported();
            assertEquals("Đã báo cáo", report.getText());
            assertTrue(report.isDisabled());
            view.showReportStatus(new ReportStatus(MATCH, 0, 2, 1, 2, false));
            assertEquals("Báo cáo: 1/2 (cần quá nửa người đoán)", status.getText());
            view.showReportStatus(new ReportStatus(MATCH, 0, 2, 2, 2, true));
            view.applyCss(); view.layout();
            assertEquals("Tranh đã bị báo cáo: lan không được điểm ở vòng này.", status.getText());
            assertTrue(status.getStyleClass().contains("report-rejected"));
            assertFalse(status.isTextTruncated());
            snapshot(view, "game-report.png");

            // Vòng mới: nút báo cáo mở lại; người vẽ không thấy nút báo cáo tranh của chính mình.
            view.showRound(new RoundInfo(MATCH, 1, 2, 1, "vu", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            assertFalse(report.isVisible());
            assertEquals("Người đoán có thể báo cáo nếu tranh không đúng từ khóa.", status.getText());
            assertFalse(status.getStyleClass().contains("report-rejected"));
            view.showRound(new RoundInfo(MATCH, 1, 2, 2, "lan", "Động vật", sampleImage(), 30, "_ _ _"), 1);
            assertEquals("Báo cáo tranh", report.getText());
            assertFalse(report.isDisabled());
        });
    }

    @Test void onlinePanelOnlyInvitesAvailablePlayers() throws Exception {
        ui(() -> {
            List<OnlineUser> invited = new java.util.ArrayList<>();
            OnlinePlayersPanel panel = new OnlinePlayersPanel(invited::add);
            show(panel);
            panel.setUsers(List.of(new OnlineUser(2, "lan", AvatarCatalog.DEFAULT, 0, false),
                    new OnlineUser(3, "hoa", AvatarCatalog.DEFAULT, 3, false),
                    new OnlineUser(4, "minh", AvatarCatalog.DEFAULT, 5, true)));
            panel.applyCss(); panel.layout();
            List<String> states = panel.lookupAll(".online-state").stream().map(n -> ((Label) n).getText()).toList();
            assertTrue(states.containsAll(List.of("Đang rảnh", "Đang ở phòng 003", "Đang chơi ở phòng 005")), states.toString());
            List<Button> buttons = panel.lookupAll(".invite-button").stream().map(Button.class::cast).toList();
            assertEquals(1, buttons.stream().filter(b -> !b.isDisabled()).count(), "Chỉ mời được người đang rảnh");
            Button free = buttons.stream().filter(b -> !b.isDisabled()).findFirst().orElseThrow();
            assertEquals("Mời lan vào phòng", free.getAccessibleText());
            snapshot(panel, "invite-panel.png");

            free.fire();
            assertEquals(List.of(2), invited.stream().map(OnlineUser::getUserId).toList());
            panel.applyCss(); panel.layout();
            assertTrue(panel.lookupAll(".invite-button").stream().map(Button.class::cast)
                    .anyMatch(b -> "Đã mời".equals(b.getText()) && b.isDisabled()), "Vừa mời thì tạm khóa nút");

            panel.setCanInvite(false, "Phòng đã đủ 8 người.");
            panel.applyCss(); panel.layout();
            assertTrue(panel.lookupAll(".invite-button").stream().allMatch(javafx.scene.Node::isDisabled));
            assertTrue(panel.lookupAll(".label").stream().anyMatch(n -> "Phòng đã đủ 8 người.".equals(((Label) n).getText())));
        });
    }

    @Test void lobbyInviteButtonOpensOnlineListAndFollowsRoomState() throws Exception {
        ui(() -> {
            java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            lobby.setOnInvite(user -> {}, requests::incrementAndGet);
            show(lobby);
            RoomInfo room = new RoomInfo(7, RoomStatus.READY_CHECK, List.of(new PlayerInfo(1, "vu", false, true),
                    new PlayerInfo(2, "lan", false, true)), 1);
            lobby.update(room);
            Button invite = button(lobby, "Mời người chơi");
            assertFalse(invite.isDisabled());
            assertNotNull(invite.getGraphic().lookup(".icon-user-plus"));

            invite.fire();
            assertEquals(1, requests.get(), "Mở hộp thoại thì xin danh sách người online ngay");
            assertNotNull(lobby.getOnlinePanel());
            lobby.showOnlineUsers(List.of(new OnlineUser(3, "hoa", AvatarCatalog.DEFAULT, 0, false)));
            lobby.getOnlinePanel().applyCss(); lobby.getOnlinePanel().layout();
            assertEquals(1, lobby.getOnlinePanel().lookupAll(".invite-button").size());
            snapshot(lobby.getOnlinePanel(), "lobby-invite-dialog.png");

            lobby.update(new RoomInfo(7, RoomStatus.PLAYING, room.getPlayers(), 1));
            assertTrue(invite.isDisabled(), "Đang chơi thì không mời thêm");
            List<PlayerInfo> full = new java.util.ArrayList<>();
            for (int id = 1; id <= 8; id++) { full.add(new PlayerInfo(id, "p" + id, false, true)); }
            lobby.update(new RoomInfo(7, RoomStatus.READY_CHECK, full, 1));
            assertTrue(invite.isDisabled(), "Phòng đủ 8 người thì không mời thêm");
        });
    }

    @Test void invitationPromptAcceptsOrCountsClosingAsDecline() throws Exception {
        ui(() -> {
            Invitation invitation = new Invitation(1, "lan", 2, "vu", 7);
            List<String> answers = new java.util.ArrayList<>();
            javafx.scene.control.Dialog<javafx.scene.control.ButtonType> prompt = InvitationPrompt.show(null, invitation,
                    () -> answers.add("accept"), () -> answers.add("decline"));
            assertEquals("lan mời bạn vào Phòng 007", prompt.getHeaderText());
            ((Button) prompt.getDialogPane().lookupButton(InvitationPrompt.ACCEPT)).fire();
            assertEquals(List.of("accept"), answers);

            javafx.scene.control.Dialog<javafx.scene.control.ButtonType> second = InvitationPrompt.show(null, invitation,
                    () -> answers.add("accept"), () -> answers.add("decline"));
            second.close();
            assertEquals(List.of("accept", "decline"), answers, "Đóng hộp thoại (hoặc hết hạn) tính là từ chối");
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
