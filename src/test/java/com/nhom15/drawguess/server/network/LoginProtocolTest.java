package com.nhom15.drawguess.server.network;

import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.common.protocol.LoginRequest;
import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.MessageType;
import com.nhom15.drawguess.common.protocol.UserInfo;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.session.SessionManager;
import com.nhom15.drawguess.common.protocol.HistoryData;
import com.nhom15.drawguess.server.game.HistoryImageService;
import com.nhom15.drawguess.server.game.RoomManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LoginProtocolTest {
    private static final UserInfo PLAYER = new UserInfo(15, "người_chơi", "USER");

    private static UserDAO acceptingDao(UserInfo user) {
        return new UserDAO() {
            @Override
            public UserInfo login(String username, String password) {
                return user;
            }
        };
    }

    @Test
    void historyImageRequiresLoginAndUsesAuthenticatedIdentityOverTcp() throws Exception {
        String matchId = java.util.UUID.randomUUID().toString();
        byte[] image = {1, 2, 3};
        AtomicInteger calls = new AtomicInteger();
        HistoryImageService images = new HistoryImageService() {
            @Override public Message getImage(int userId, Object payload) {
                assertEquals(PLAYER.getId(), userId);
                assertEquals(new HistoryData.ImageRequest(matchId, 0), payload);
                calls.incrementAndGet();
                return new Message(MessageType.HISTORY_IMAGE, new HistoryData.DrawingImage(matchId, 0, image));
            }
        };
        try (ConnectionPair pair = new ConnectionPair(acceptingDao(PLAYER), new SessionManager(), images)) {
            Message request = new Message(MessageType.GET_HISTORY_IMAGE, new HistoryData.ImageRequest(matchId, 0));
            Message denied = pair.request(request);
            assertEquals(MessageType.HISTORY_IMAGE_FAILED, denied.getType());
            var failure = assertInstanceOf(HistoryData.ImageFailure.class, denied.getData());
            assertEquals("NOT_LOGGED_IN", failure.reason()); assertEquals(matchId, failure.matchId());
            assertEquals(0, calls.get());
            pair.login();
            Message response = pair.request(request);
            assertEquals(MessageType.HISTORY_IMAGE, response.getType());
            assertArrayEquals(image, assertInstanceOf(HistoryData.DrawingImage.class, response.getData()).png());
            assertEquals(1, calls.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET_HISTORY", "GET_HISTORY_DETAIL", "GET_CATALOG", "EDIT_CATALOG"})
    void dataEndpointsRequireLogin(String type) throws Exception {
        try(ConnectionPair pair=new ConnectionPair(acceptingDao(PLAYER),new SessionManager())) {
            Message response=pair.request(new Message(MessageType.valueOf(type)));
            assertEquals(type.contains("CATALOG") ? MessageType.CATALOG_FAILED : MessageType.HISTORY_FAILED,response.getType());
            assertEquals("NOT_LOGGED_IN",response.getData());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET_CATALOG", "EDIT_CATALOG"})
    void playerCannotReadOrModifyAdminCatalog(String type) throws Exception {
        try(ConnectionPair pair=new ConnectionPair(acceptingDao(PLAYER),new SessionManager())) {
            assertEquals(MessageType.LOGIN_SUCCESS,pair.login().getType());
            Message response=pair.request(new Message(MessageType.valueOf(type)));
            assertEquals(MessageType.CATALOG_FAILED,response.getType()); assertEquals("FORBIDDEN",response.getData());
        }
    }

    @Test
    void authenticatedDataRequestsRejectMalformedPayloads() throws Exception {
        try(ConnectionPair pair=new ConnectionPair(acceptingDao(new UserInfo(15,"admin","ADMIN")),new SessionManager())) {
            pair.login();
            Message edit=pair.request(new Message(MessageType.EDIT_CATALOG,"wrong"));
            assertEquals(MessageType.CATALOG_FAILED,edit.getType()); assertEquals("INVALID_REQUEST",edit.getData());
            Message history=pair.request(new Message(MessageType.GET_HISTORY_DETAIL,"../../other"));
            assertEquals(MessageType.HISTORY_FAILED,history.getType()); assertEquals("INVALID_REQUEST",history.getData());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void successfulLoginReturnsServerIdentityAndRole(String role) throws Exception {
        UserDAO dao = new UserDAO() {
            @Override
            public UserInfo login(String username, String password) {
                assertEquals("người_chơi", username);
                assertEquals(" Mật Khẩu ", password);
                return new UserInfo(15, username, role);
            }
        };
        try (ConnectionPair pair = new ConnectionPair(dao, new SessionManager())) {
            Message response = pair.login(new LoginRequest(" người_chơi ", " Mật Khẩu "));
            assertEquals(MessageType.LOGIN_SUCCESS, response.getType());
            UserInfo user = assertInstanceOf(UserInfo.class, response.getData());
            assertEquals(15, user.getId());
            assertEquals("người_chơi", user.getUsername());
            assertEquals(role, user.getRole());
        }
    }

    static Stream<Object[]> invalidRequests() {
        return Stream.of(
                new Object[]{null, "INVALID_REQUEST"},
                new Object[]{"wrong payload", "INVALID_REQUEST"},
                new Object[]{new LoginRequest(null, "secret"), "INVALID_USERNAME"},
                new Object[]{new LoginRequest("   ", "secret"), "INVALID_USERNAME"},
                new Object[]{new LoginRequest("a".repeat(51), "secret"), "INVALID_USERNAME"},
                new Object[]{new LoginRequest("player", null), "INVALID_PASSWORD"},
                new Object[]{new LoginRequest("player", "   "), "INVALID_PASSWORD"},
                new Object[]{new LoginRequest("player", "a".repeat(256)), "INVALID_PASSWORD"});
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidPayloadDoesNotQueryDatabase(Object payload, String reason) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UserDAO dao = new UserDAO() {
            @Override
            public UserInfo login(String username, String password) {
                calls.incrementAndGet();
                return PLAYER;
            }
        };
        try (ConnectionPair pair = new ConnectionPair(dao, new SessionManager())) {
            assertFailure(pair.login(payload), reason);
            assertEquals(0, calls.get());
        }
    }

    @Test
    void incorrectCredentialsDoNotCreateSession() throws Exception {
        SessionManager sessions = new SessionManager();
        try (ConnectionPair rejected = new ConnectionPair(acceptingDao(null), sessions);
             ConnectionPair valid = new ConnectionPair(acceptingDao(PLAYER), sessions)) {
            assertFailure(rejected.login(), "INVALID_CREDENTIALS");
            assertEquals(MessageType.LOGIN_SUCCESS, valid.login().getType());
        }
    }

    @Test
    void databaseErrorReturnsFailureAndConnectionCanRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UserDAO dao = new UserDAO() {
            @Override
            public UserInfo login(String username, String password) throws SQLException {
                if (calls.getAndIncrement() == 0) {
                    throw new SQLException("Unavailable", "08001");
                }
                return PLAYER;
            }
        };
        try (ConnectionPair pair = new ConnectionPair(dao, new SessionManager())) {
            assertFailure(pair.login(), "DATABASE_ERROR");
            assertEquals(MessageType.LOGIN_SUCCESS, pair.login().getType());
        }
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        try (ConnectionPair pair = new ConnectionPair(
                acceptingDao(new UserInfo(15, "player", "OTHER")), new SessionManager())) {
            assertFailure(pair.login(), "INVALID_ROLE");
        }
    }

    @Test
    void cannotReplaceAuthenticatedIdentityOnSameSocket() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UserDAO dao = new UserDAO() {
            @Override
            public UserInfo login(String username, String password) {
                return new UserInfo(calls.incrementAndGet(), username, "USER");
            }
        };
        try (ConnectionPair pair = new ConnectionPair(dao, new SessionManager())) {
            assertEquals(MessageType.LOGIN_SUCCESS, pair.login().getType());
            assertFailure(pair.login(new LoginRequest("other", "secret")), "ALREADY_LOGGED_IN");
            assertEquals(1, calls.get());
        }
    }

    @Test
    void logoutReleasesAccountForAnotherClient() throws Exception {
        SessionManager sessions = new SessionManager();
        try (ConnectionPair first = new ConnectionPair(acceptingDao(PLAYER), sessions);
             ConnectionPair second = new ConnectionPair(acceptingDao(PLAYER), sessions)) {
            assertEquals(MessageType.LOGIN_SUCCESS, first.login().getType());
            assertFailure(second.login(), "USER_ALREADY_ONLINE");
            // Kết nối bị từ chối không thể đăng xuất tài khoản của kết nối khác.
            Message unauthorized = second.request(new Message(MessageType.LOGOUT));
            assertEquals(MessageType.ACTION_FAILED, unauthorized.getType());
            assertEquals("NOT_LOGGED_IN", unauthorized.getData());
            assertFailure(second.login(), "USER_ALREADY_ONLINE");

            Message logout = first.request(new Message(MessageType.LOGOUT));
            assertEquals(MessageType.ACTION_SUCCESS, logout.getType());
            assertEquals("LOGOUT", logout.getData());
            assertEquals(MessageType.LOGIN_SUCCESS, second.login().getType());
            assertFailure(first.login(), "USER_ALREADY_ONLINE");
        }
    }

    @Test
    void disconnectReleasesSession() throws Exception {
        SessionManager sessions = new SessionManager();
        try (ConnectionPair first = new ConnectionPair(acceptingDao(PLAYER), sessions);
             ConnectionPair second = new ConnectionPair(acceptingDao(PLAYER), sessions)) {
            assertEquals(MessageType.LOGIN_SUCCESS, first.login().getType());
            first.disconnectAndWait();
            assertEquals(MessageType.LOGIN_SUCCESS, second.login().getType());
        }
    }

    @Test
    void simultaneousLoginsForSameAccountHaveExactlyOneWinner() throws Exception {
        SessionManager sessions = new SessionManager();
        CountDownLatch gate = new CountDownLatch(1);
        try (ConnectionPair first = new ConnectionPair(acceptingDao(PLAYER), sessions);
             ConnectionPair second = new ConnectionPair(acceptingDao(PLAYER), sessions)) {
            CompletableFuture<Message> a = CompletableFuture.supplyAsync(() -> loginAfterGate(first, gate));
            CompletableFuture<Message> b = CompletableFuture.supplyAsync(() -> loginAfterGate(second, gate));
            gate.countDown();
            Message ra = a.get(5, TimeUnit.SECONDS);
            Message rb = b.get(5, TimeUnit.SECONDS);
            assertEquals(1, Stream.of(ra, rb).filter(r -> r.getType() == MessageType.LOGIN_SUCCESS).count());
            Message rejected = ra.getType() == MessageType.LOGIN_FAILED ? ra : rb;
            assertFailure(rejected, "USER_ALREADY_ONLINE");
        }
    }

    private static Message loginAfterGate(ConnectionPair pair, CountDownLatch gate) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Login gate timed out");
            }
            return pair.login();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void assertFailure(Message response, String reason) {
        assertEquals(MessageType.LOGIN_FAILED, response.getType());
        assertEquals(reason, response.getData());
    }

    // Kiểm thử protocol trên TCP thật, không thay đổi MySQL của người dùng.
    private static class ConnectionPair implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread handler;
        private volatile Socket accepted;
        private final TCPClient client = new TCPClient();

        ConnectionPair(UserDAO dao, SessionManager sessions) throws IOException {
            this(dao, sessions, null);
        }

        ConnectionPair(UserDAO dao, SessionManager sessions, HistoryImageService images) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            handler = new Thread(() -> {
                try {
                    accepted = listener.accept();
                    if (images == null) {
                        new ClientHandler(accepted, 1, dao, sessions).run();
                    } else {
                        new ClientHandler(accepted, 1, dao, sessions, new RoomManager(), System::nanoTime, images).run();
                    }
                } catch (IOException e) {
                    if (!listener.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            }, "Login-Test-Server");
            handler.setDaemon(true);
            handler.start();
            assertTrue(client.connect(listener.getInetAddress().getHostAddress(), listener.getLocalPort()));
        }

        Message login() {
            return login(new LoginRequest("player", "secret"));
        }

        Message login(Object payload) {
            return request(new Message(MessageType.LOGIN, payload));
        }

        Message request(Message message) {
            assertTrue(client.sendMessage(message));
            Message response = client.receiveMessage();
            assertNotNull(response);
            return response;
        }

        void disconnectAndWait() throws InterruptedException {
            client.disconnect();
            handler.join(2000);
            assertFalse(handler.isAlive());
        }

        @Override
        public void close() throws Exception {
            client.disconnect();
            listener.close();
            if (accepted != null) {
                accepted.close();
            }
            handler.join(2000);
            assertFalse(handler.isAlive());
        }
    }
}
