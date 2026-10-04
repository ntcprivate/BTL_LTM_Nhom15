package com.nhom15.drawguess.server.network;

import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.MessageType;
import com.nhom15.drawguess.common.protocol.RegisterRequest;
import com.nhom15.drawguess.server.dao.UserDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationProtocolTest {

    @Test
    void successfulRegistrationPreservesPasswordAndTrimsUsername() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        UserDAO dao = new UserDAO() {
            @Override
            public boolean register(String username, String password) {
                assertEquals("người_chơi", username);
                assertEquals(" mật khẩu ", password);
                calls.incrementAndGet();
                return true;
            }
        };
        try (ConnectionPair pair = new ConnectionPair(dao)) {
            Message result = pair.request(new RegisterRequest(" người_chơi ", " mật khẩu "));
            assertEquals(MessageType.REGISTER_SUCCESS, result.getType());
            assertEquals(1, calls.get());
            // Giữ stream hoạt động sau đăng ký và vẫn hỗ trợ PING.
            assertTrue(pair.client.sendMessage(new Message(MessageType.PING, "Xin chào")));
            assertEquals(MessageType.PONG, pair.client.receiveMessage().getType());
        }
    }

    @Test
    void duplicateUsernameReturnsExpectedFailure() throws Exception {
        try (ConnectionPair pair = new ConnectionPair(new UserDAO() {
            @Override
            public boolean register(String username, String password) {
                return false;
            }
        })) {
            assertFailure(pair.request(new RegisterRequest("player", "secret")), "USERNAME_EXIST");
        }
    }

    @Test
    void databaseErrorDoesNotDropConnection() throws Exception {
        try (ConnectionPair pair = new ConnectionPair(new UserDAO() {
            @Override
            public boolean register(String username, String password) throws SQLException {
                throw new SQLException("Database unavailable", "08001");
            }
        })) {
            assertFailure(pair.request(new RegisterRequest("player", "secret")), "DATABASE_ERROR");
            assertTrue(pair.client.sendMessage(new Message(MessageType.PING)));
            assertEquals(MessageType.PONG, pair.client.receiveMessage().getType());
        }
    }

    static Stream<Object[]> invalidRequests() {
        return Stream.of(
                new Object[]{null, "INVALID_REQUEST"},
                new Object[]{"wrong payload", "INVALID_REQUEST"},
                new Object[]{new RegisterRequest(null, "secret"), "INVALID_USERNAME"},
                new Object[]{new RegisterRequest("   ", "secret"), "INVALID_USERNAME"},
                new Object[]{new RegisterRequest("a".repeat(51), "secret"), "INVALID_USERNAME"},
                new Object[]{new RegisterRequest("player", null), "INVALID_PASSWORD"},
                new Object[]{new RegisterRequest("player", "   "), "INVALID_PASSWORD"},
                new Object[]{new RegisterRequest("player", "a".repeat(256)), "INVALID_PASSWORD"});
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidRequestNeverReachesDatabase(Object payload, String reason) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ConnectionPair pair = new ConnectionPair(new UserDAO() {
            @Override
            public boolean register(String username, String password) {
                calls.incrementAndGet();
                return true;
            }
        })) {
            assertFailure(pair.request(payload), reason);
            assertEquals(0, calls.get());
        }
    }

    @Test
    void disconnectedClientFailsWithoutNullPointerException() {
        TCPClient client = new TCPClient();
        assertFalse(client.sendMessage(new Message(MessageType.PING)));
        assertNull(client.receiveMessage());
        client.disconnect();
        client.disconnect();
        assertFalse(client.isConnected());
    }

    private static void assertFailure(Message result, String reason) {
        assertNotNull(result);
        assertEquals(MessageType.REGISTER_FAILED, result.getType());
        assertEquals(reason, result.getData());
    }

    // Dùng socket thật, DAO giả: không thêm/xóa tài khoản trong MySQL của người dùng.
    private static class ConnectionPair implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread serverThread;
        private volatile Socket acceptedSocket;
        private final TCPClient client = new TCPClient();

        ConnectionPair(UserDAO dao) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverThread = new Thread(() -> {
                try {
                    acceptedSocket = listener.accept();
                    new ClientHandler(acceptedSocket, 1, dao).run();
                } catch (IOException e) {
                    if (!listener.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            }, "Registration-Test-Server");
            serverThread.setDaemon(true);
            serverThread.start();
            assertTrue(client.connect(listener.getInetAddress().getHostAddress(), listener.getLocalPort()));
        }

        Message request(Object payload) {
            assertTrue(client.sendMessage(new Message(MessageType.REGISTER, payload)));
            Message result = client.receiveMessage();
            assertNotNull(result);
            return result;
        }

        @Override
        public void close() throws Exception {
            client.disconnect();
            listener.close();
            if (acceptedSocket != null) {
                acceptedSocket.close();
            }
            serverThread.join(2000);
            assertFalse(serverThread.isAlive(), "Handler phải kết thúc khi client ngắt kết nối");
        }
    }
}
