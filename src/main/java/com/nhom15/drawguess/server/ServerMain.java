package com.nhom15.drawguess.server;

import com.nhom15.drawguess.server.network.ClientHandler;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.session.SessionManager;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.game.GameManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {

    public static final int PORT = 5555;

    public static void main(String[] args) {

        int clientCounter = 0;
        SessionManager sessionManager = new SessionManager();
        GameManager gameManager = new GameManager();
        Runtime.getRuntime().addShutdownHook(new Thread(gameManager::close, "Game-Shutdown"));
        RoomManager roomManager = new RoomManager(gameManager);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("=================================");
            System.out.println(" DRAW AND GUESS SERVER");
            System.out.println("=================================");
            System.out.println("Server đang chạy tại cổng: " + PORT);
            System.out.println("Đang chờ Client kết nối...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                clientCounter++;

                System.out.println(
                        "Client #" + clientCounter
                        + " đã kết nối: "
                        + clientSocket.getRemoteSocketAddress()
                );

                ClientHandler handler =
                        new ClientHandler(
                                clientSocket,
                                clientCounter,
                                new UserDAO(),
                                sessionManager,
                                roomManager
                        );

                Thread clientThread =
                        new Thread(handler);

                clientThread.setName(
                        "ClientHandler-" + clientCounter
                );

                clientThread.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            gameManager.close();
        }
    }
}
