package com.nhom15.drawguess.server.demo;

import com.nhom15.drawguess.server.ServerMain;
import com.nhom15.drawguess.server.game.DrawingStore;
import com.nhom15.drawguess.server.game.GameManager;
import com.nhom15.drawguess.server.game.HistoryImageService;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.network.ClientHandler;
import com.nhom15.drawguess.server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

// Server chạy thử không cần MySQL: tài khoản, từ khóa và lịch sử nằm trong bộ nhớ.
// Chạy: mvn -q compile exec:java -Dexec.mainClass=com.nhom15.drawguess.server.demo.DemoServerMain
public class DemoServerMain {

    public static void main(String[] args) {
        DemoStore store = new DemoStore();
        DrawingStore drawings = new DrawingStore(Path.of("server-data", "demo-drawings"));
        GameManager gameManager = new GameManager(store.wordDAO(), drawings, System::nanoTime, true, store.historyDAO());
        Runtime.getRuntime().addShutdownHook(new Thread(gameManager::close, "Game-Shutdown"));
        RoomManager roomManager = new RoomManager(gameManager);
        SessionManager sessionManager = new SessionManager();
        HistoryImageService historyImages = new HistoryImageService(store.historyDAO(), drawings);
        int clientCounter = 0;

        try (ServerSocket serverSocket = new ServerSocket(ServerMain.PORT)) {
            System.out.println("=================================");
            System.out.println(" DRAW AND GUESS — SERVER CHẠY THỬ");
            System.out.println("=================================");
            System.out.println("Không dùng MySQL. Dữ liệu sẽ mất khi tắt server.");
            System.out.println("Cổng: " + ServerMain.PORT);
            System.out.println("Tài khoản người chơi: demo1, demo2, demo3, demo4 / mật khẩu " + DemoStore.DEMO_PASSWORD);
            System.out.println("Tài khoản quản trị:   admin / mật khẩu " + DemoStore.ADMIN_PASSWORD);
            System.out.println("Đang chờ Client kết nối...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientCounter++;
                System.out.println("Client #" + clientCounter + " đã kết nối: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, clientCounter, store.userDAO(), sessionManager,
                        roomManager, System::nanoTime, historyImages, store.historyDAO(), store.catalogDAO());
                Thread clientThread = new Thread(handler, "ClientHandler-" + clientCounter);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Không mở được cổng " + ServerMain.PORT + ": " + e.getMessage()
                    + ". Có thể server khác đang chạy.");
        } finally {
            gameManager.close();
        }
    }
}
