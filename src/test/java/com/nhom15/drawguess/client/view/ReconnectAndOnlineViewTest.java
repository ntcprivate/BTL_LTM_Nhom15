package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.client.controller.AuthController;
import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.dao.WordDAO;
import com.nhom15.drawguess.server.game.DrawingStore;
import com.nhom15.drawguess.server.game.GameManager;
import com.nhom15.drawguess.server.game.GamePlan;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.network.ClientHandler;
import com.nhom15.drawguess.server.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
class ReconnectAndOnlineViewTest {
    @TempDir Path drawings;

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

    @Test void roomListShowsWhoIsOnlineWithoutInviteButtons() throws Exception {
        ui(() -> {
            List<String> requests = new ArrayList<>();
            RoomListView rooms = new RoomListView(new UserInfo(1, "vu", "USER"), () -> {}, () -> {}, () -> {});
            rooms.setOnShowOnline(() -> requests.add("GET_ONLINE_USERS"));
            show(rooms, 1000, 760);
            rooms.updateOnlineCount(3);
            Button open = button(rooms, "Xem danh sách");
            assertNotNull(open.getGraphic().lookup(".icon-users"));
            assertFalse(open.isTextTruncated(), "Nút không bị cắt chữ ở cửa sổ 1000px");
            assertEquals("Xem danh sách người đang online", open.getAccessibleText());
            snapshot(rooms, "room-list-online.png", Color.web("#0b3478"));

            open.fire();
            assertEquals(List.of("GET_ONLINE_USERS"), requests, "Mở danh sách thì xin server ngay");
            rooms.showOnlineUsers(List.of(new OnlineUser(2, "lan", AvatarCatalog.DEFAULT, 0, false),
                    new OnlineUser(3, "minh", AvatarCatalog.DEFAULT, 5, true)));
            var pane = (javafx.scene.control.DialogPane) rooms.getOnlinePanel().getParent();
            pane.applyCss(); pane.layout();
            assertEquals("Người đang online", ((Label) pane.lookup(".dialog-title")).getText());
            assertTrue(pane.lookupAll(".invite-button").stream().noneMatch(Node::isVisible), "Ở màn hình chọn phòng chỉ xem, không mời");
            assertTrue(pane.lookupAll(".online-state").stream().map(n -> ((Label) n).getText()).toList()
                    .containsAll(List.of("Đang rảnh", "Đang chơi ở phòng 005")));
            assertEquals(javafx.stage.Modality.NONE, ((javafx.stage.Stage) pane.getScene().getWindow()).getModality());
            pane.setOpacity(1); pane.setScaleX(1); pane.setScaleY(1);
            snapshot(pane, "room-list-online-dialog.png", Color.web("#0b3478"));
            rooms.closeOnlineList();
        });
    }

    @Test void reconnectOverlayExplainsWhatIsHappening() throws Exception {
        ui(() -> {
            List<String> cancelled = new ArrayList<>();
            StackPane screen = new StackPane(new Label("Màn hình game"));
            ReconnectOverlay overlay = new ReconnectOverlay(() -> cancelled.add("cancel"));
            screen.getChildren().add(overlay);
            show(screen, 1000, 700);
            overlay.showAttempt(2, 5);
            assertTrue(overlay.lookupAll(".label").stream()
                    .anyMatch(n -> "Đang thử kết nối lại (lần 2/5)...".equals(((Label) n).getText())));
            assertNotNull(overlay.lookup(".icon-wifi-off"));
            snapshot(screen, "reconnect-overlay.png", Color.web("#0b3478"));
            button(overlay, "Về màn hình đăng nhập").fire();
            assertEquals(List.of("cancel"), cancelled);
        });
    }

    // Luồng đầy đủ: client thật, server thật. Kết nối của người chơi 1 bị cắt giữa trận;
    // client tự đăng nhập lại và được đưa về đúng trận.
    @Test void clientReconnectsByItselfAndReturnsToTheMatch() throws Exception {
        try (TestServer server = new TestServer(drawings)) {
            StackPane root = new StackPane();
            root.getStyleClass().add("app-screen");
            UiTheme.apply(root);
            AuthController[] auth = new AuthController[1];
            ui(() -> {
                auth[0] = new AuthController(root, new TCPClient());
                show(root, 1100, 800);
                LoginView login = (LoginView) root.getChildren().getFirst();
                textField(login, ClientConfigDefaults.HOST).setText("127.0.0.1");
                textField(login, ClientConfigDefaults.PORT).setText(String.valueOf(server.port()));
                login.setUsername("1");
                ((TextField) login.lookup(".password-input")).setText("USER");
                button(login, "Đăng nhập").fire();
            });
            await(() -> root.getChildren().getFirst() instanceof HomeView, "Vào trang chủ sau khi đăng nhập");
            ui(() -> ((Button) root.lookupAll(".button").stream()
                    .filter(n -> "Vào phòng chơi".equals(((Button) n).getAccessibleText())).findFirst().orElseThrow()).fire());
            await(() -> root.getChildren().getFirst() instanceof RoomListView rooms
                    && !rooms.lookupAll(".list-view").isEmpty()
                    && !((ListView<?>) rooms.lookup(".list-view")).getItems().isEmpty(), "Tải xong danh sách phòng");
            ui(() -> {
                RoomListView rooms = (RoomListView) root.getChildren().getFirst();
                ((ListView<?>) rooms.lookup(".list-view")).getSelectionModel().select(0);
                button(rooms, "Vào phòng").fire();
            });
            await(() -> root.getChildren().getFirst() instanceof LobbyView, "Vào phòng chờ");
            ui(() -> button((Parent) root.getChildren().getFirst(), "Sẵn sàng").fire());

            TCPClient other = server.player(2);
            send(other, MessageType.JOIN_ROOM, 1);
            send(other, MessageType.READY, null);
            String word = ((DrawingPrompt) receive(other, MessageType.DRAW_WORD).getData()).getWord();
            await(() -> root.getChildren().getFirst() instanceof GameView, "Trận bắt đầu");
            assertNotNull(word);

            server.dropFirstConnection();
            await(() -> root.getChildren().stream().anyMatch(ReconnectOverlay.class::isInstance), "Hiện thông báo đang kết nối lại");
            await(() -> root.getChildren().size() == 1 && root.getChildren().getFirst() instanceof GameView game
                    && game.lookupAll(".label").stream().anyMatch(n -> ((Label) n).getText() != null
                    && ((Label) n).getText().startsWith("Đã vào lại trận")), "Tự về lại trận đang chơi");
            assertTrue(server.rooms.getRooms().getFirst().getPlayers().stream().allMatch(PlayerInfo::isOnline));
            ui(() -> {
                Node game = root.getChildren().getFirst();
                game.setOpacity(1); game.setTranslateY(0); // bỏ qua hiệu ứng chuyển màn hình khi chụp
                snapshot(root, "reconnected-game.png", Color.web("#0b3478"));
            });
            ui(auth[0]::close);
        }
    }

    // Tên các ô cài đặt máy chủ được nhận ra qua giá trị mặc định của chúng.
    private static final class ClientConfigDefaults {
        static final String HOST = com.nhom15.drawguess.client.config.ClientConfig.load().host();
        static final String PORT = String.valueOf(com.nhom15.drawguess.client.config.ClientConfig.load().port());
    }

    private static TextField textField(Parent view, String currentText) {
        return (TextField) view.lookupAll(".text-field").stream()
                .filter(n -> n instanceof TextField field && currentText.equals(field.getText())).findFirst().orElseThrow();
    }

    private static void send(TCPClient client, MessageType type, Object payload) {
        assertTrue(client.sendMessage(new Message(type, payload)));
    }

    private static Message receive(TCPClient client, MessageType type) {
        for (int count = 0; count < 100; count++) {
            Message message = client.receiveMessage();
            assertNotNull(message, "Không nhận được " + type);
            if (message.getType() == type) { return message; }
        }
        throw new AssertionError("Không tìm thấy " + type);
    }

    private static void await(BooleanSupplier condition, String what) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Platform.runLater(() -> {
                try { result.complete(condition.getAsBoolean()); } catch (Throwable e) { result.completeExceptionally(e); }
            });
            if (result.get(5, TimeUnit.SECONDS)) { return; }
            Thread.sleep(50);
        }
        fail("Hết thời gian chờ: " + what);
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

    private static void snapshot(Parent view, String filename, Color background) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(background);
            WritableImage image = view.snapshot(params, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Files.createDirectories(Path.of("target/qa"));
            ImageIO.write(png, "png", Path.of("target/qa", filename).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // Server thật trên cổng ngẫu nhiên; tên đăng nhập là số id, mật khẩu là vai trò.
    private static final class TestServer implements AutoCloseable {
        final RoomManager rooms;
        final GameManager game;
        final SessionManager sessions = new SessionManager();
        final ServerSocket listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final List<TCPClient> clients = new ArrayList<>();
        final Thread acceptor;

        TestServer(Path drawings) throws IOException {
            WordDAO words = new WordDAO() {
                @Override public GamePlan selectWords(int count) {
                    return new GamePlan("Động vật", List.of("Con mèo", "Con chó", "Con voi").subList(0, count));
                }
            };
            game = new GameManager(words, new DrawingStore(drawings), System::nanoTime, true,
                    new com.nhom15.drawguess.server.dao.HistoryDAO() { @Override public void save(HistoryData.Detail d) {} });
            rooms = new RoomManager(game, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);
            UserDAO users = new UserDAO() {
                @Override public UserInfo login(String username, String password) {
                    return new UserInfo(Integer.parseInt(username), "player" + username, password);
                }
            };
            acceptor = new Thread(() -> {
                int id = 0;
                while (!listener.isClosed()) {
                    try {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        Thread handler = new Thread(new ClientHandler(socket, ++id, users, sessions, rooms), "Reconnect-Test-Client");
                        handler.setDaemon(true);
                        handler.start();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "Reconnect-Test-Server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        int port() { return listener.getLocalPort(); }

        TCPClient player(int id) {
            TCPClient client = new TCPClient();
            clients.add(client);
            assertTrue(client.connect("127.0.0.1", port()));
            assertTrue(client.setReadTimeout(5000));
            send(client, MessageType.LOGIN, new LoginRequest(Integer.toString(id), "USER"));
            receive(client, MessageType.LOGIN_SUCCESS);
            return client;
        }

        // Cắt kết nối đầu tiên (của giao diện) từ phía server, giống rớt mạng.
        void dropFirstConnection() throws IOException {
            sockets.getFirst().close();
        }

        @Override public void close() throws Exception {
            for (TCPClient client : clients) { client.disconnect(); }
            listener.close();
            for (Socket socket : sockets) { socket.close(); }
            game.close();
        }
    }
}
