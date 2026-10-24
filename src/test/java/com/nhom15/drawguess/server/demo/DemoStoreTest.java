package com.nhom15.drawguess.server.demo;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import com.nhom15.drawguess.server.game.GamePlan;
import com.nhom15.drawguess.server.game.HistoryImageService;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.network.ClientHandler;
import com.nhom15.drawguess.server.session.SessionManager;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoStoreTest {
    private final DemoStore store = new DemoStore();

    @Test
    void seededAccountsLogInAndNewAccountsCanRegister() throws Exception {
        UserInfo demo = store.userDAO().login("demo1", DemoStore.DEMO_PASSWORD);
        assertNotNull(demo);
        assertEquals("USER", demo.getRole());
        assertNull(store.userDAO().login("demo1", "sai"));
        assertEquals("ADMIN", store.userDAO().login("admin", DemoStore.ADMIN_PASSWORD).getRole());
        assertTrue(store.userDAO().register("lan", "abc"));
        assertFalse(store.userDAO().register("lan", "khac"));
        assertNotNull(store.userDAO().login("lan", "abc"));
        assertTrue(store.userDAO().updateAvatar(demo.getId(), "irises"));
        assertEquals("irises", store.userDAO().login("demo1", DemoStore.DEMO_PASSWORD).getAvatarKey());
    }

    @Test
    void everyCategoryCanHostAFullRoom() throws Exception {
        assertEquals(3, store.catalogDAO().list().categories().size());
        GamePlan plan = store.wordDAO().selectWords(RoomManager.MAX_PLAYERS);
        assertNotNull(plan);
        assertEquals(RoomManager.MAX_PLAYERS, plan.getWords().stream().distinct().count());
        assertNull(store.wordDAO().selectWords(11));
    }

    @Test
    void catalogEditsRejectDuplicatesAndDeleteCategoryWords() throws Exception {
        Category animals = store.catalogDAO().list().categories().stream()
                .filter(c -> c.name().equals("Động vật")).findFirst().orElseThrow();
        SQLException duplicate = assertThrows(SQLException.class, () -> store.catalogDAO()
                .edit(new Edit("WORD", "CREATE", 0, animals.id(), "con mèo", null, "EASY")));
        assertEquals("23000", duplicate.getSQLState());
        store.catalogDAO().edit(new Edit("WORD", "CREATE", 0, animals.id(), "Con sư tử", null, "HARD"));
        assertTrue(store.catalogDAO().list().words().stream().anyMatch(w -> w.content().equals("Con sư tử")));
        store.catalogDAO().edit(new Edit("CATEGORY", "DELETE", animals.id(), 0, null, null, null));
        assertTrue(store.catalogDAO().list().words().stream().noneMatch(w -> w.categoryId() == animals.id()));
    }

    @Test
    void historyIsOnlyVisibleToPlayersAfterTheMatchEnds() throws Exception {
        var round = new HistoryData.Round(0, 1, "demo1", "Con mèo", "x.png", 1L, 2L, List.of());
        var players = List.of(new ScoreEntry(1, "demo1", 3, 1, 1.0, true), new ScoreEntry(2, "demo2", 1, 2, 2.0, true));
        String id = "00000000-0000-0000-0000-000000000009";
        store.historyDAO().save(new HistoryData.Detail(id, 1, "Động vật", 1L, null, "IN_PROGRESS", players, List.of(round)));
        assertNull(store.historyDAO().detail(1, id), "Trận chưa kết thúc không được xem chi tiết");
        store.historyDAO().save(new HistoryData.Detail(id, 1, "Động vật", 1L, 5L, "COMPLETED", players, List.of(round)));
        assertEquals(3, store.historyDAO().list(1).getFirst().score());
        assertNotNull(store.historyDAO().detail(2, id));
        assertNull(store.historyDAO().detail(3, id), "Người không tham gia không được xem");
        assertTrue(store.historyDAO().list(3).isEmpty());
        assertEquals(1, store.historyDAO().drawing(2, id, 0).drawerId());
    }

    @Test
    void clientLogsInAndLoadsHistoryOverTcpWithoutMySql() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
            Socket accepted = server.accept();
            Thread handler = new Thread(new ClientHandler(accepted, 1, store.userDAO(), new SessionManager(),
                    new RoomManager(), System::nanoTime, new HistoryImageService(store.historyDAO(), null),
                    store.historyDAO(), store.catalogDAO()));
            handler.setDaemon(true);
            handler.start();
            client.setSoTimeout(5000);
            ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream());
            output.flush();
            ObjectInputStream input = new ObjectInputStream(client.getInputStream());

            output.writeObject(new Message(MessageType.LOGIN, new LoginRequest("demo1", DemoStore.DEMO_PASSWORD)));
            output.flush();
            Message login = (Message) input.readObject();
            assertEquals(MessageType.LOGIN_SUCCESS, login.getType());

            output.writeObject(new Message(MessageType.GET_HISTORY));
            output.flush();
            Message history = (Message) input.readObject();
            assertEquals(MessageType.HISTORY_LIST, history.getType());
            assertEquals(List.of(), history.getData());

            output.writeObject(new Message(MessageType.GET_CATALOG));
            output.flush();
            Message catalog = (Message) input.readObject();
            assertEquals(MessageType.CATALOG_FAILED, catalog.getType());
            assertEquals("FORBIDDEN", catalog.getData());
        }
    }
}
