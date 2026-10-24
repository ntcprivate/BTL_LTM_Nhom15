package com.nhom15.drawguess.server.session;

import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.UserInfo;

import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class SessionManager {
    // Mỗi tài khoản một phiên; user và sender dùng để liệt kê người online và gửi lời mời.
    // `resumeToken` cho phép chính máy vừa mất mạng lấy lại phiên cũ khi server chưa kịp nhận ra kết nối đã chết.
    public record Session(Socket socket, UserInfo user, Consumer<Message> sender, String resumeToken) { }

    // Phiên vừa mở và phiên cũ bị thay thế (null nếu không có) để đóng kết nối cũ.
    public record Opened(Session session, Session replaced) { }

    private final Map<Integer, Session> sessions = new HashMap<>();

    public synchronized boolean login(int userId, Socket socket) {
        return open(userId, socket, null, null, null) != null;
    }

    public synchronized boolean login(UserInfo user, Socket socket, Consumer<Message> sender) {
        return open(user, socket, sender, null) != null;
    }

    // Tài khoản đang có phiên thì chỉ đăng nhập được khi gửi đúng mã nối lại của phiên đó; trả về null nếu bị từ chối.
    public synchronized Opened open(UserInfo user, Socket socket, Consumer<Message> sender, String resumeToken) {
        return open(user.getId(), socket, user, sender, resumeToken);
    }

    // Kiểm tra và tạo phiên trong cùng một khóa để tránh hai login đồng thời.
    private Opened open(int userId, Socket socket, UserInfo user, Consumer<Message> sender, String resumeToken) {
        Session current = sessions.get(userId);
        if (current != null && (resumeToken == null || !resumeToken.equals(current.resumeToken()))) {
            return null;
        }
        Session session = new Session(socket, user, sender, UUID.randomUUID().toString());
        sessions.put(userId, session);
        return new Opened(session, current);
    }

    // Mã nối lại chỉ trả cho chính kết nối đang giữ phiên.
    public synchronized String resumeTokenOf(int userId, Socket socket) {
        Session session = sessions.get(userId);
        return session != null && session.socket() == socket ? session.resumeToken() : null;
    }

    public synchronized int onlineCount() {
        return sessions.size();
    }

    public synchronized List<UserInfo> onlineUsers() {
        return sessions.values().stream().map(Session::user).filter(Objects::nonNull).toList();
    }

    public synchronized UserInfo userOf(int userId) {
        Session session = sessions.get(userId);
        return session == null ? null : session.user();
    }

    public synchronized Consumer<Message> senderOf(int userId) {
        Session session = sessions.get(userId);
        return session == null ? null : session.sender();
    }

    // Cập nhật thông tin hiển thị (ví dụ avatar mới) của phiên đang mở.
    public synchronized void updateUser(UserInfo user) {
        sessions.computeIfPresent(user.getId(),
                (id, session) -> new Session(session.socket(), user, session.sender(), session.resumeToken()));
    }

    public synchronized void logout(int userId, Socket socket) {
        // Một kết nối chỉ được xóa phiên mà chính nó đang sở hữu.
        Session session = sessions.get(userId);
        if (session != null && session.socket() == socket) {
            sessions.remove(userId);
        }
    }
}
