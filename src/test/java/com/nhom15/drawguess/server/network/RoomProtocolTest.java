package com.nhom15.drawguess.server.network;

import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.game.GameManager;
import com.nhom15.drawguess.server.game.GamePlan;
import com.nhom15.drawguess.server.game.DrawingStore;
import com.nhom15.drawguess.server.dao.WordDAO;
import com.nhom15.drawguess.server.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class RoomProtocolTest {

    @TempDir Path drawingsDirectory;

    @Test
    void fourClientsCompleteDrawingGuessingAndCanStartAnotherMatch() throws Exception {
        AtomicLong clock = new AtomicLong();
        WordDAO words = new WordDAO() {
            @Override public GamePlan selectWords(int count) {
                return new GamePlan("Động vật", List.of("Con mèo", "Con chó", "Con voi", "Con hổ").subList(0, count));
            }
        };
        DrawingStore store = new DrawingStore(drawingsDirectory);
        try (GameManager game = new GameManager(words, store, clock::get, false, new com.nhom15.drawguess.server.dao.HistoryDAO() { @Override public void save(com.nhom15.drawguess.common.protocol.HistoryData.Detail d) {} });
             TestServer server = new TestServer(game, clock::get)) {
            List<TCPClient> players = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                TCPClient client = server.client();
                players.add(client);
                login(client, id, "USER");
                send(client, MessageType.JOIN_ROOM, 1);
                receive(client, MessageType.JOIN_SUCCESS);
            }
            for (TCPClient client : players) { send(client, MessageType.READY, null); }
            List<DrawingPrompt> prompts = new ArrayList<>();
            for (TCPClient client : players) {
                prompts.add(assertInstanceOf(DrawingPrompt.class, receive(client, MessageType.DRAW_WORD).getData()));
            }
            String matchId = prompts.getFirst().getMatchId();
            assertEquals(4, prompts.stream().map(DrawingPrompt::getWord).distinct().count());
            clock.addAndGet(60_000_000_000L);
            game.tick(1);
            for (TCPClient client : players) {
                assertEquals(matchId, receive(client, MessageType.SUBMIT_DRAW).getData());
                send(client, MessageType.DRAW_UPLOAD, new DrawUpload(matchId, store.blank()));
                assertEquals("DRAW_UPLOAD", receive(client, MessageType.ACTION_SUCCESS).getData());
            }
            for (int roundIndex = 0; roundIndex < 4; roundIndex++) {
                for (TCPClient client : players) {
                    RoundInfo round = assertInstanceOf(RoundInfo.class, receive(client, MessageType.ROUND_START).getData());
                    assertEquals(roundIndex, round.getRoundIndex());
                    assertEquals(roundIndex + 1, round.getDrawerId());
                }
                for (int id = 1; id <= 4; id++) {
                    if (id == roundIndex + 1) { continue; }
                    clock.addAndGet(1_000_000_000L);
                    TCPClient client = players.get(id - 1);
                    send(client, MessageType.GUESS, new GuessRequest(matchId, roundIndex, prompts.get(roundIndex).getWord()));
                    GuessResult result = assertInstanceOf(GuessResult.class, receive(client, MessageType.GUESS_RESULT).getData());
                    assertTrue(result.isCorrect());
                    assertTrue(result.isLocked());
                }
                for (TCPClient client : players) {
                    RoundAnswer answer = assertInstanceOf(RoundAnswer.class, receive(client, MessageType.ANSWER).getData());
                    assertEquals(prompts.get(roundIndex).getWord(), answer.getWord());
                }
                clock.addAndGet(3_000_000_000L);
                game.tick(1);
            }
            for (TCPClient client : players) {
                GameResult result = assertInstanceOf(GameResult.class, receive(client, MessageType.GAME_RESULT).getData());
                assertEquals(matchId, result.getMatchId());
                assertEquals(4, result.getPlayers().size());
                assertEquals(List.of(9, 7, 6, 6), result.getPlayers().stream().map(ScoreEntry::getScore).toList());
            }
            RoomInfo lobby = server.rooms.getRooms().getFirst();
            assertEquals(RoomStatus.READY_CHECK, lobby.getStatus());
            assertTrue(lobby.getPlayers().stream().noneMatch(PlayerInfo::isReady));
            for (TCPClient client : players) { send(client, MessageType.READY, null); }
            for (TCPClient client : players) {
                DrawingPrompt next = assertInstanceOf(DrawingPrompt.class, receive(client, MessageType.DRAW_WORD).getData());
                assertNotEquals(matchId, next.getMatchId());
            }
        }
    }

    @Test
    void fourTcpClientsLoginJoinReadyAndReceiveStartGame() throws Exception {
        try (TestServer server = new TestServer()) {
            List<TCPClient> players = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                TCPClient client = server.client();
                players.add(client);
                login(client, id, "USER");
                send(client, MessageType.JOIN_ROOM, 1);
                RoomInfo joined = assertInstanceOf(RoomInfo.class, receive(client, MessageType.JOIN_SUCCESS).getData());
                assertEquals(id, joined.getPlayers().size());
            }
            for (TCPClient client : players) { send(client, MessageType.READY, null); }
            for (TCPClient client : players) {
                RoomInfo start = assertInstanceOf(RoomInfo.class, receive(client, MessageType.START_GAME).getData());
                assertEquals(RoomStatus.PLAYING, start.getStatus());
                assertEquals(List.of(1, 2, 3, 4), start.getPlayers().stream().map(PlayerInfo::getUserId).toList());
            }
            TCPClient outsider = server.client();
            login(outsider, 5, "USER");
            send(outsider, MessageType.JOIN_ROOM, 1);
            assertEquals("ROOM_PLAYING", receive(outsider, MessageType.JOIN_FAILED).getData());

            send(players.getFirst(), MessageType.LEAVE_ROOM, null);
            assertEquals("LEAVE_ROOM", receive(players.getFirst(), MessageType.ACTION_SUCCESS).getData());
            RoomInfo update = assertInstanceOf(RoomInfo.class, receive(players.get(1), MessageType.ROOM_INFO).getData());
            // Có thể còn ROOM_INFO từ các lần join: đọc tới cập nhật offline.
            while (update.getPlayers().getFirst().isOnline()) {
                update = assertInstanceOf(RoomInfo.class, receive(players.get(1), MessageType.ROOM_INFO).getData());
            }
            assertEquals(4, update.getPlayers().size());
        }
    }

    @Test
    void unauthenticatedAndAdminClientsCannotJoinRooms() throws Exception {
        try (TestServer server = new TestServer()) {
            TCPClient client = server.client();
            send(client, MessageType.GET_ROOMS, null);
            assertEquals("NOT_LOGGED_IN", receive(client, MessageType.ACTION_FAILED).getData());
            send(client, MessageType.JOIN_ROOM, 1);
            assertEquals("NOT_LOGGED_IN", receive(client, MessageType.JOIN_FAILED).getData());
            login(client, 1, "ADMIN");
            send(client, MessageType.JOIN_ROOM, 1);
            assertEquals("FORBIDDEN", receive(client, MessageType.JOIN_FAILED).getData());
        }
    }

    @Test
    void roomListInvalidJoinAndLeaveKeepAuthenticatedConnectionAlive() throws Exception {
        try (TestServer server = new TestServer()) {
            TCPClient client = server.client();
            login(client, 1, "USER");
            send(client, MessageType.GET_ROOMS, null);
            assertEquals(100, assertInstanceOf(List.class, receive(client, MessageType.ROOM_LIST).getData()).size());
            send(client, MessageType.JOIN_ROOM, "1");
            assertEquals("INVALID_REQUEST", receive(client, MessageType.JOIN_FAILED).getData());
            send(client, MessageType.JOIN_ROOM, 101);
            assertEquals("ROOM_NOT_FOUND", receive(client, MessageType.JOIN_FAILED).getData());
            send(client, MessageType.READY, null);
            assertEquals("NOT_IN_ROOM", receive(client, MessageType.ACTION_FAILED).getData());
            send(client, MessageType.JOIN_ROOM, 1);
            receive(client, MessageType.JOIN_SUCCESS);
            send(client, MessageType.LEAVE_ROOM, null);
            assertEquals("LEAVE_ROOM", receive(client, MessageType.ACTION_SUCCESS).getData());
            send(client, MessageType.JOIN_ROOM, 2);
            assertEquals(2, assertInstanceOf(RoomInfo.class, receive(client, MessageType.JOIN_SUCCESS).getData()).getRoomId());
            send(client, MessageType.LOGOUT, null);
            assertEquals("LOGOUT", receive(client, MessageType.ACTION_SUCCESS).getData());
            assertTrue(server.rooms.getRooms().get(1).getPlayers().isEmpty());
        }
    }

    private static void login(TCPClient client, int id, String role) {
        send(client, MessageType.LOGIN, new LoginRequest(Integer.toString(id), role));
        assertEquals(id, assertInstanceOf(UserInfo.class, receive(client, MessageType.LOGIN_SUCCESS).getData()).getId());
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

    private static class TestServer implements AutoCloseable {
        final RoomManager rooms;
        final LongSupplier clock;
        final SessionManager sessions = new SessionManager();
        final ServerSocket listener;
        final List<TCPClient> clients = new ArrayList<>();
        final List<Thread> handlers = new ArrayList<>();
        final List<Socket> sockets = new ArrayList<>();

        TestServer() throws IOException {
            this(null, System::nanoTime);
        }

        TestServer(GameManager gameManager, LongSupplier clock) throws IOException {
            this.clock = clock;
            rooms = new RoomManager(gameManager);
            listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        }

        TCPClient client() {
            UserDAO dao = new UserDAO() {
                @Override
                public UserInfo login(String username, String password) {
                    return new UserInfo(Integer.parseInt(username), "player" + username, password);
                }
            };
            // Một thread chấp nhận socket và xử lý đúng client vừa tạo.
            Thread handler = new Thread(() -> {
                try {
                    Socket socket = listener.accept();
                    synchronized (sockets) { sockets.add(socket); }
                    new ClientHandler(socket, 1, dao, sessions, rooms, clock).run();
                } catch (IOException e) {
                    if (!listener.isClosed()) { throw new RuntimeException(e); }
                }
            }, "Room-Test-Server");
            handler.setDaemon(true);
            handlers.add(handler);
            handler.start();
            TCPClient client = new TCPClient();
            clients.add(client);
            assertTrue(client.connect(listener.getInetAddress().getHostAddress(), listener.getLocalPort()));
            assertTrue(client.setReadTimeout(3000));
            return client;
        }

        @Override
        public void close() throws Exception {
            for (TCPClient client : clients) { client.disconnect(); }
            listener.close();
            synchronized (sockets) {
                for (Socket socket : sockets) { socket.close(); }
            }
            for (Thread handler : handlers) {
                handler.join(3000);
                assertFalse(handler.isAlive());
            }
        }
    }
}
