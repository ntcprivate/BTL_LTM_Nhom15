package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class UiImprovementsViewTest {
    private static final String MATCH = "00000000-0000-0000-0000-000000000002";

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
        done.get(20, TimeUnit.SECONDS);
    }

    @AfterEach void closeWindows() throws Exception {
        ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
    }

    // Kích thước gần với cửa sổ nhỏ nhất của ứng dụng (980x740) sau khi trừ lề.
    private static Parent show(Parent view) {
        StackPane page = new StackPane(view);
        page.setPadding(new javafx.geometry.Insets(24));
        UiTheme.apply(page);
        var stage = new javafx.stage.Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(page, 1000, 760));
        stage.show();
        page.applyCss();
        page.layout();
        return page;
    }

    private static void relayout(Parent page) { page.applyCss(); page.layout(); }

    private static void snapshot(Parent view, String filename) {
        try {
            WritableImage image = view.snapshot(null, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Files.createDirectories(Path.of("target/qa"));
            ImageIO.write(png, "png", Path.of("target/qa", filename).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static List<String> texts(Parent root, String selector) {
        return root.lookupAll(selector).stream().filter(Labeled.class::isInstance)
                .map(n -> ((Labeled) n).getText()).toList();
    }

    private static <T extends Node> T find(Parent root, Class<T> type, java.util.function.Predicate<T> filter) {
        return root.lookupAll("*").stream().filter(type::isInstance).map(type::cast).filter(filter).findFirst()
                .orElseGet(() -> findDeep(root, type, filter));
    }

    private static <T extends Node> T findDeep(Parent parent, Class<T> type, java.util.function.Predicate<T> filter) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (type.isInstance(child) && filter.test(type.cast(child))) { return type.cast(child); }
            if (child instanceof Parent nested) {
                T found = findDeep(nested, type, filter);
                if (found != null) { return found; }
            }
        }
        return null;
    }

    private static ScoreEntry entry(int id, String name, int score, int rank, boolean solved) {
        return new ScoreEntry(id, name, score, rank, 1.0, true, solved);
    }

    @Test void drawingToolbarFitsAndEraserShowsItsState() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con mèo", 60));
            relayout(page);
            for (String text : List.of("Tẩy", "Hoàn tác", "Xóa tranh", "Rời phòng", "Đăng xuất", "Đoán")) {
                Labeled control = find(view, Labeled.class, c -> text.equals(c.getText()));
                assertNotNull(control, text);
                assertFalse(control.isTextTruncated(), "Chữ bị cắt: " + text);
            }
            Label stroke = find(view, Label.class, l -> "Nét".equals(l.getText()));
            assertFalse(stroke.isTextTruncated());
            Label status = (Label) view.lookup(".status-banner");
            assertFalse(status.isTextTruncated(), "Dòng trạng thái bị cắt");

            ToggleButton eraser = find(view, ToggleButton.class, t -> "Tẩy".equals(t.getText()));
            eraser.fire();
            assertEquals("Đang tẩy", eraser.getText());
            assertNotNull(eraser.getGraphic().lookup(".icon-check"), "Đang tẩy phải có icon dấu tích");
            assertTrue(eraser.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("selected")));
            snapshot(page, "game-toolbar-eraser.png");
            eraser.fire();
            assertEquals("Tẩy", eraser.getText());
            assertNull(eraser.getGraphic());
        });
    }

    @Test void timerTurnsUrgentInLastTenSeconds() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            show(view);
            Label timer = (Label) view.lookup(".timer-chip");
            view.updateTimer(new TimerInfo(MATCH, GamePhase.GUESSING, 20));
            assertFalse(timer.getStyleClass().contains("timer-urgent"));
            view.updateTimer(new TimerInfo(MATCH, GamePhase.GUESSING, 8));
            assertTrue(timer.getStyleClass().contains("timer-urgent"));
            assertEquals(1, timer.getStyleClass().stream().filter("timer-urgent"::equals).count());
            view.updateTimer(new TimerInfo(MATCH, GamePhase.ROUND_END, 3));
            assertFalse(timer.getStyleClass().contains("timer-urgent"));
        });
    }

    @Test void scoreboardHighlightsSelfDrawerAndSolvedPlayers() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(1, "irises", 2, "wheatfield", 3, "cafe-night"),
                    () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con chó", 60));
            view.showRound(new RoundInfo(MATCH, 0, 3, 2, "hoa", "Động vật", GameLobbyFeaturesViewTestImages.sample(), 30,
                    "_ _ _   _ _ _"), 1);
            view.updateScores(List.of(entry(3, "lan", 3, 0, true), entry(2, "hoa", 2, 0, false), entry(1, "vu", 0, 0, false)));
            relayout(page);
            assertTrue(texts(view, ".score-name").contains("vu  • Bạn"));
            List<String> badges = texts(view, ".score-badge");
            assertTrue(badges.contains("Người vẽ"), badges.toString());
            assertTrue(badges.contains("Đoán đúng"), badges.toString());
            Label solved = find(view, Label.class, l -> "Đoán đúng".equals(l.getText()));
            assertNotNull(solved.getGraphic().lookup(".icon-check"));
            assertTrue(view.lookupAll(".score-badge").stream().filter(n -> !"Đoán đúng".equals(((Label) n).getText()))
                    .allMatch(n -> ((Label) n).getGraphic() == null), "Chỉ huy hiệu Đoán đúng mới có icon");
            assertTrue(badges.contains("Đang đoán"), badges.toString());
            assertFalse(badges.contains("Đang vẽ"), "Trong pha đoán không ai đang vẽ");
            assertEquals(1, view.lookupAll(".score-row-self").size());
            assertEquals(List.of("1", "2", "3"), texts(view, ".score-position"));
            snapshot(page, "game-scoreboard.png");
        });
    }

    @Test void drawingPhaseMarksEveryoneAsDrawing() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con chó", 60));
            view.updateScores(List.of(entry(1, "vu", 0, 0, false), entry(2, "hoa", 0, 0, false)));
            relayout(page);
            assertEquals(List.of("Đang vẽ", "Đang vẽ"), texts(view, ".score-badge").stream()
                    .filter(text -> !text.isEmpty()).toList());
        });
    }

    @Test void resultShowsPodiumWithWinner() throws Exception {
        ui(() -> {
            GameView view = new GameView(1, Map.of(1, "irises", 2, "wheatfield", 3, "cafe-night", 4, "starry-night"),
                    () -> {}, () -> {}, () -> {}, () -> {});
            Parent page = show(view);
            view.showDrawing(new DrawingPrompt(MATCH, 1, "Động vật", "Con chó", 60));
            view.showResult(new GameResult(MATCH, List.of(entry(3, "lan", 6, 1, false), entry(1, "vu", 4, 2, false),
                    entry(2, "hoa", 3, 3, false), entry(4, "minh", 1, 4, false))));
            relayout(page);
            assertTrue(view.lookup(".result-panel").isVisible());
            assertEquals(3, view.lookupAll(".podium-step").size());
            assertEquals("Người thắng: lan", texts(view, ".result-winner").getFirst());
            assertNotNull(view.lookup(".result-winner .icon-star"));
            assertTrue(texts(view, ".result-other").getFirst().startsWith("4.  minh"));
            assertTrue(find(view, Button.class, b -> "Về phòng chờ".equals(b.getText())).isVisible());
            snapshot(page, "game-result.png");
        });
    }

    @Test void roomListFiltersFullRoomsAndJoinsOnDoubleClick() throws Exception {
        ui(() -> {
            AtomicInteger joins = new AtomicInteger();
            RoomListView view = new RoomListView(new UserInfo(1, "vu", "USER"), joins::incrementAndGet, () -> {}, () -> {});
            Parent page = show(view);
            List<PlayerInfo> full = new ArrayList<>();
            for (int id = 1; id <= 8; id++) { full.add(new PlayerInfo(id, "p" + id, false, true)); }
            view.updateRooms(List.of(new RoomInfo(1, RoomStatus.WAITING, List.of()),
                    new RoomInfo(2, RoomStatus.READY_CHECK, full),
                    new RoomInfo(3, RoomStatus.PLAYING, List.of(new PlayerInfo(9, "x", true, true)))));
            @SuppressWarnings("unchecked")
            ListView<RoomInfo> list = (ListView<RoomInfo>) view.lookup(".list-view");
            assertEquals(List.of(1), list.getItems().stream().map(RoomInfo::getRoomId).toList());
            CheckBox filter = find(view, CheckBox.class, c -> true);
            filter.setSelected(false);
            assertEquals(3, list.getItems().size());
            filter.setSelected(true);

            list.getSelectionModel().select(0);
            list.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 10, 10, 10, 10, MouseButton.PRIMARY, 2,
                    false, false, false, false, false, false, false, false, false, true, null));
            assertEquals(1, joins.get());
            relayout(page);
            snapshot(page, "rooms-filter.png");
        });
    }

    @Test void loginCollapsesServerSettingsAndRegisterShowsServer() throws Exception {
        ui(() -> {
            LoginView login = new LoginView("192.168.1.10", 5555);
            Parent page = show(login);
            TextField host = find(login, TextField.class, t -> "192.168.1.10".equals(t.getText()));
            assertFalse(host.isVisible() && host.getParent().getParent().isVisible(), "Ô máy chủ phải được thu gọn");
            assertTrue(texts(login, ".server-summary").contains("Máy chủ: 192.168.1.10:5555"));
            snapshot(page, "login-collapsed.png");
            login.showServerSettings(true);
            relayout(page);
            assertTrue(host.getParent().getParent().isVisible());
            host.setText("10.0.0.5");
            assertTrue(texts(login, ".server-summary").contains("Máy chủ: 10.0.0.5:5555"));
            relayout(page);
            snapshot(page, "login-server-settings.png");

            RegisterView register = new RegisterView();
            register.setServerAddress(login.getServerAddress());
            Parent registerPage = show(register);
            assertTrue(texts(register, ".server-summary").getFirst().contains("10.0.0.5:5555"));
            relayout(registerPage);
            assertFalse(((Label) register.lookup(".server-summary")).isTextTruncated());
            snapshot(registerPage, "register-server.png");
        });
    }

    // F9 + F12: nhãn nối với ô nhập; lỗi hiện ngay dưới ô sai và con trỏ về ô sai đầu tiên.
    @Test void authFormsLinkLabelsAndShowErrorsUnderTheWrongField() throws Exception {
        ui(() -> {
            LoginView login = new LoginView("localhost", 5555);
            Parent page = show(login);
            List<Label> labels = login.lookupAll(".field-label").stream().map(Label.class::cast).toList();
            assertEquals(4, labels.size());
            assertTrue(labels.stream().allMatch(l -> l.getLabelFor() instanceof TextInputControl), "Mọi nhãn phải nối với ô nhập");
            TextField username = find(login, TextField.class, t -> "Tên đăng nhập".equals(t.getPromptText()));
            PasswordField password = find(login, PasswordField.class, t -> true);
            TextField port = find(login, TextField.class, t -> "5555".equals(t.getText()));
            assertSame(password, labels.stream().filter(l -> "Mật khẩu".equals(l.getText())).findFirst().orElseThrow().getLabelFor());
            assertTrue(login.lookupAll(".field-error").stream().noneMatch(Node::isVisible));

            login.showFieldErrors(Map.of(AuthField.SERVER_PORT, "Cổng server phải là số từ 1 đến 65535.",
                    AuthField.USERNAME, "Tên đăng nhập phải có từ 1 đến 50 ký tự."));
            relayout(page);
            List<Label> errors = login.lookupAll(".field-error").stream().filter(Node::isVisible).map(Label.class::cast).toList();
            assertEquals(2, errors.size());
            assertSame(username.getParent(), errors.getFirst().getParent(), "Lỗi phải nằm ngay trong khối của ô sai");
            assertTrue(port.isVisible() && port.getParent().getParent().isVisible(), "Lỗi ở cổng phải mở phần máy chủ");
            assertSame(username, login.getScene().getFocusOwner(), "Con trỏ phải về ô sai đầu tiên");
            assertTrue(username.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("invalid")));
            assertEquals("Tên đăng nhập phải có từ 1 đến 50 ký tự.", username.getAccessibleHelp());
            assertEquals("Nhập tài khoản để đăng nhập.", ((Label) login.lookup(".status-banner")).getText());
            assertTrue(errors.stream().noneMatch(Label::isTextTruncated));
            assertTrue(port.getWidth() < 160, "Lỗi dài không được làm ô cổng phình ra: " + port.getWidth());
            snapshot(page, "login-field-errors.png");

            username.setText("vu");
            assertEquals(1, login.lookupAll(".field-error").stream().filter(Node::isVisible).count(), "Sửa ô thì lỗi của ô đó tự ẩn");
            assertTrue(username.getPseudoClassStates().stream().noneMatch(p -> p.getPseudoClassName().equals("invalid")));

            RegisterView register = new RegisterView();
            Parent registerPage = show(register);
            register.showFieldErrors(Map.of(AuthField.CONFIRM_PASSWORD, "Mật khẩu nhập lại không khớp."));
            relayout(registerPage);
            PasswordField confirm = (PasswordField) register.lookupAll(".field-label").stream().map(Label.class::cast)
                    .filter(l -> "Nhập lại mật khẩu".equals(l.getText())).findFirst().orElseThrow().getLabelFor();
            assertSame(confirm, register.getScene().getFocusOwner());
            snapshot(registerPage, "register-field-errors.png");
        });
    }

    // F10: nút con mắt hiện/ẩn mật khẩu mà không làm mất chữ đã gõ.
    @Test void passwordToggleRevealsAndHidesTypedText() throws Exception {
        ui(() -> {
            LoginView login = new LoginView("localhost", 5555);
            Parent page = show(login);
            PasswordField password = find(login, PasswordField.class, t -> true);
            ToggleButton eye = (ToggleButton) login.lookup(".password-toggle");
            password.setText("bimat123");
            relayout(page);
            javafx.scene.text.Text shown = (javafx.scene.text.Text) password.lookup(".text");
            assertNotEquals("bimat123", shown.getText(), "Mặc định phải che mật khẩu");
            assertEquals("Hiện mật khẩu", eye.getAccessibleText());
            assertNotNull(eye.getGraphic().lookup(".icon-eye"));

            eye.fire();
            relayout(page);
            shown = (javafx.scene.text.Text) password.lookup(".text");
            assertEquals("bimat123", shown.getText());
            assertEquals("bimat123", login.getPassword());
            assertEquals("Ẩn mật khẩu", eye.getAccessibleText());
            assertNotNull(eye.getGraphic().lookup(".icon-eye-off"));
            assertTrue(eye.getWidth() >= 24 && eye.getHeight() >= 24, "Vùng bấm tối thiểu 24px");
            snapshot(page, "login-password-shown.png");

            login.clearPassword();
            relayout(page);
            assertEquals("Hiện mật khẩu", eye.getAccessibleText(), "Xóa mật khẩu thì quay về chế độ che");
        });
    }

    // F11: biểu tượng là icon vector thống nhất, không còn ký tự làm icon.
    @Test void iconsReplaceGlyphCharacters() throws Exception {
        ui(() -> {
            LoginView login = new LoginView("localhost", 5555);
            show(login);
            Label brand = (Label) login.lookup(".brand-chip");
            assertEquals("STARRY DRAW", brand.getText());
            assertEquals(2, brand.getGraphic().lookupAll(".icon-sparkle").size());

            LobbyView lobby = new LobbyView(1, () -> {}, () -> {}, () -> {}, ignored -> {});
            show(lobby);
            lobby.update(new RoomInfo(3, RoomStatus.READY_CHECK, List.of(new PlayerInfo(1, "vu", true, true),
                    new PlayerInfo(2, "lan", false, true)), 1));
            Label host = (Label) lobby.lookup(".room-host");
            assertEquals("Chủ phòng", host.getText());
            assertNotNull(host.getGraphic().lookup(".icon-star"));
            List<Label> marks = lobby.lookupAll(".ready-mark").stream().map(Label.class::cast).toList();
            assertEquals(1, marks.stream().filter(m -> m.getGraphic() != null).count());
            assertEquals("Đã sẵn sàng", marks.stream().filter(m -> m.getGraphic() != null).findFirst().orElseThrow().getAccessibleText());

            String glyphs = "\u2605\u2713\u21B6\u2726\u2192";
            for (Parent root : List.<Parent>of(login, lobby)) {
                List<String> withGlyph = root.lookupAll("*").stream().filter(Labeled.class::isInstance)
                        .map(n -> ((Labeled) n).getText()).filter(t -> t != null && t.chars().anyMatch(c -> glyphs.indexOf(c) >= 0)).toList();
                assertTrue(withGlyph.isEmpty(), "Còn ký tự làm icon: " + withGlyph);
            }
        });
    }

    @Test void historyBackButtonSaysHome() throws Exception {
        ui(() -> {
            HistoryView view = new HistoryView(message -> {}, () -> {});
            show(view);
            assertNotNull(find(view, Button.class, b -> "Về trang chủ".equals(b.getText())));
            assertNull(find(view, Button.class, b -> "Về danh sách phòng".equals(b.getText())));
        });
    }

    @Test void adminShowsVietnameseDifficultyLabelsAndInlineStatus() throws Exception {
        ui(() -> {
            List<Message> sent = new ArrayList<>();
            AdminView view = new AdminView(new UserInfo(1, "admin", "ADMIN"), sent::add, () -> {});
            Parent page = show(view);
            view.handleMessage(new Message(MessageType.CATALOG, new Catalog(List.of(new Category(1, "Động vật", "")),
                    List.of(new Word(1, 1, "Con mèo", "EASY"), new Word(2, 1, "Con voi", "HARD")))));
            @SuppressWarnings("unchecked")
            ListView<Category> categories = (ListView<Category>) view.lookupAll(".list-view").stream()
                    .filter(n -> ((ListView<?>) n).getItems().stream().anyMatch(Category.class::isInstance)).findFirst().orElseThrow();
            categories.getSelectionModel().select(0);
            relayout(page);
            List<String> cells = texts(view, ".list-cell");
            assertTrue(cells.contains("Con mèo  —  Dễ"), cells.toString());
            assertTrue(cells.contains("Con voi  —  Khó"), cells.toString());
            assertTrue(texts(view, ".field-label").containsAll(List.of("Tên chủ đề", "Mô tả", "Từ khóa", "Độ khó")));
            for (String text : List.of("Thêm", "Cập nhật", "Xóa")) {
                for (Node node : view.lookupAll(".button")) {
                    if (node instanceof Button b && text.equals(b.getText())) { assertFalse(b.isTextTruncated(), "Chữ bị cắt: " + text); }
                }
            }

            Button addWord = view.lookupAll(".button").stream().map(Button.class::cast)
                    .filter(b -> "Thêm".equals(b.getText())).reduce((a, b) -> b).orElseThrow();
            TextField word = find(view, TextField.class, t -> t.getPromptText() != null && t.getPromptText().startsWith("Từ khóa"));
            word.setText("Con hổ");
            addWord.fire();
            view.handleMessage(new Message(MessageType.CATALOG_FAILED, "DUPLICATE_OR_INVALID_REFERENCE"));
            relayout(page);
            List<String> cardStatus = texts(view, ".card-status");
            assertTrue(cardStatus.contains("Tên đã tồn tại hoặc chủ đề không hợp lệ."), cardStatus.toString());
            snapshot(page, "admin-inline.png");
        });
    }
}

final class GameLobbyFeaturesViewTestImages {
    private GameLobbyFeaturesViewTestImages() {
    }

    static byte[] sample() {
        try {
            BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 500, 500);
            graphics.setColor(java.awt.Color.BLUE); graphics.drawOval(150, 150, 200, 200);
            graphics.dispose();
            var output = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
