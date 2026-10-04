package com.nhom15.drawguess.server.session;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SessionManager {
    private final Map<Integer, Socket> sessions = new HashMap<>();

    // Kiểm tra và tạo phiên trong cùng một khóa để tránh hai login đồng thời.
    public synchronized boolean login(int userId, Socket socket) {
        if (sessions.containsKey(userId)) {
            return false;
        }
        sessions.put(userId, socket);
        return true;
    }

    public synchronized void logout(int userId, Socket socket) {
        // Một kết nối chỉ được xóa phiên mà chính nó đang sở hữu.
        sessions.remove(userId, socket);
    }
}
