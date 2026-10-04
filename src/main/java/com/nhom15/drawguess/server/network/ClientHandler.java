package com.nhom15.drawguess.server.network;

import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.MessageType;
import com.nhom15.drawguess.common.protocol.RegisterRequest;
import com.nhom15.drawguess.common.protocol.LoginRequest;
import com.nhom15.drawguess.common.protocol.UserInfo;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.dao.HistoryDAO;
import com.nhom15.drawguess.server.dao.CatalogDAO;
import com.nhom15.drawguess.common.protocol.CatalogData;
import com.nhom15.drawguess.server.session.SessionManager;
import com.nhom15.drawguess.server.game.RoomManager;
import com.nhom15.drawguess.server.game.HistoryImageService;
import com.nhom15.drawguess.common.protocol.HistoryData;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.function.LongSupplier;


public class ClientHandler implements Runnable {

    private static final SessionManager DEFAULT_SESSIONS = new SessionManager();
    private static final RoomManager DEFAULT_ROOMS = new RoomManager();

    private final Socket socket;
    private final int clientId;
    private final UserDAO userDAO;
    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final LongSupplier clock;
    private final HistoryImageService historyImages;
    private UserInfo currentUser;

    private ObjectOutputStream output;
    private ObjectInputStream input;

    public ClientHandler(
            Socket socket,
            int clientId
    ) {
        this(socket, clientId, new UserDAO());
    }

    public ClientHandler(Socket socket, int clientId, UserDAO userDAO) {
        this(socket, clientId, userDAO, DEFAULT_SESSIONS);
    }

    public ClientHandler(Socket socket, int clientId, UserDAO userDAO,
                         SessionManager sessionManager) {
        this(socket, clientId, userDAO, sessionManager, DEFAULT_ROOMS);
    }

    public ClientHandler(Socket socket, int clientId, UserDAO userDAO,
                         SessionManager sessionManager, RoomManager roomManager) {
        this(socket, clientId, userDAO, sessionManager, roomManager, System::nanoTime);
    }

    public ClientHandler(Socket socket, int clientId, UserDAO userDAO,
                         SessionManager sessionManager, RoomManager roomManager, LongSupplier clock) {
        this(socket, clientId, userDAO, sessionManager, roomManager, clock, new HistoryImageService());
    }

    public ClientHandler(Socket socket, int clientId, UserDAO userDAO,
                         SessionManager sessionManager, RoomManager roomManager, LongSupplier clock,
                         HistoryImageService historyImages) {
        this.socket = socket;
        this.clientId = clientId;
        this.userDAO = userDAO;
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
        this.clock = clock;
        this.historyImages = historyImages;
    }

    @Override
    public void run() {

        System.out.println(
                "Client #" + clientId
                + " đang được xử lý bởi "
                + Thread.currentThread().getName()
        );

        try {

            /*
             * Phải tạo Output trước,
             * giống phía Client.
             */
            output =
                    new ObjectOutputStream(
                            socket.getOutputStream()
                    );

            output.flush();

            input =
                    new ObjectInputStream(
                            socket.getInputStream()
                    );

            while (true) {

                Object object = input.readObject();

                if (!(object instanceof Message)) {
                    continue;
                }

                Message message =
                        (Message) object;

                long receivedAt = clock.getAsLong();
                handleMessage(message, receivedAt);
            }

        } catch (IOException e) {

            System.out.println(
                    "Client #" + clientId
                    + " mất kết nối."
            );

        } catch (ClassNotFoundException e) {

            e.printStackTrace();

        } finally {

            clearSession();

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(
                    "Client #" + clientId
                    + " đã ngắt kết nối."
            );
        }
    }

    private void handleMessage(Message message, long receivedAt) {

        if (message.getType() == null) {
            sendMessage(new Message(MessageType.ACTION_FAILED, "INVALID_REQUEST"));
            return;
        }
        switch (message.getType()) {
            case GET_HISTORY_IMAGE -> {
                if (currentUser == null) {
                    HistoryData.ImageRequest request = message.getData() instanceof HistoryData.ImageRequest r ? r : null;
                    sendMessage(HistoryImageService.failed(request == null ? null : request.matchId(),
                            request == null ? -1 : request.roundIndex(), "NOT_LOGGED_IN"));
                } else {
                    sendMessage(historyImages.getImage(currentUser.getId(), message.getData()));
                }
                return;
            }
            case GET_HISTORY, GET_HISTORY_DETAIL, GET_CATALOG, EDIT_CATALOG -> {
                handleDataMessage(message); return;
            }
            case GET_ROOMS, JOIN_ROOM, LEAVE_ROOM, READY, CANCEL_READY, DRAW_UPLOAD, GUESS -> {
                handleRoomMessage(message, receivedAt);
                return;
            }
            default -> { }
        }

        if (message.getType() == MessageType.LOGIN) {
            handleLogin(message.getData());
            return;
        }
        if (message.getType() == MessageType.LOGOUT) {
            if (currentUser == null) {
                sendMessage(new Message(MessageType.ACTION_FAILED, "NOT_LOGGED_IN"));
            } else {
                clearSession();
                sendMessage(new Message(MessageType.ACTION_SUCCESS, "LOGOUT"));
            }
            return;
        }

        if (message.getType() == MessageType.REGISTER) {
            handleRegister(message.getData());
            return;
        }

        System.out.println(
                "Client #" + clientId
                + " gửi: "
                + message.getType()
        );

        if (message.getType() == MessageType.PING) {

            System.out.println(
                    "Dữ liệu: "
                    + message.getData()
            );

            sendMessage(
                    new Message(
                            MessageType.PONG,
                            "Hello Client #" + clientId
                    )
            );
        }
    }

    private void handleDataMessage(Message message) {
        boolean catalog = message.getType() == MessageType.GET_CATALOG || message.getType() == MessageType.EDIT_CATALOG;
        MessageType failure = catalog ? MessageType.CATALOG_FAILED : MessageType.HISTORY_FAILED;
        if (currentUser == null) { sendMessage(new Message(failure,"NOT_LOGGED_IN")); return; }
        if (catalog && !"ADMIN".equals(currentUser.getRole())) { sendMessage(new Message(failure,"FORBIDDEN")); return; }
        try {
            switch (message.getType()) {
                case GET_HISTORY -> sendMessage(new Message(MessageType.HISTORY_LIST,new HistoryDAO().list(currentUser.getId())));
                case GET_HISTORY_DETAIL -> {
                    if (!(message.getData() instanceof String id)) throw new IllegalArgumentException("INVALID_REQUEST");
                    try { java.util.UUID.fromString(id); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("INVALID_REQUEST"); }
                    var detail = new HistoryDAO().detail(currentUser.getId(),id);
                    sendMessage(new Message(detail==null ? failure : MessageType.HISTORY_DETAIL,detail==null ? "NOT_FOUND_OR_UNFINISHED" : detail));
                }
                case GET_CATALOG -> sendMessage(new Message(MessageType.CATALOG,new CatalogDAO().list()));
                case EDIT_CATALOG -> {
                    if (!(message.getData() instanceof CatalogData.Edit edit)) throw new IllegalArgumentException("INVALID_REQUEST");
                    new CatalogDAO().edit(edit);
                    sendMessage(new Message(MessageType.CATALOG_SAVED));
                    sendMessage(new Message(MessageType.CATALOG,new CatalogDAO().list()));
                }
                default -> { }
            }
        } catch (IllegalArgumentException e) {
            sendMessage(new Message(failure,e.getMessage()));
        } catch (SQLException e) {
            System.err.println("Lỗi dữ liệu Client #" + clientId + ": SQLState=" + e.getSQLState());
            String state=e.getSQLState();
            sendMessage(new Message(failure,state!=null && state.startsWith("23") ? "DUPLICATE_OR_INVALID_REFERENCE" : "DATABASE_ERROR"));
        }
    }

    private void handleRoomMessage(Message message, long receivedAt) {
        MessageType failureType = message.getType() == MessageType.JOIN_ROOM
                ? MessageType.JOIN_FAILED : MessageType.ACTION_FAILED;
        if (currentUser == null) {
            sendMessage(new Message(failureType, "NOT_LOGGED_IN"));
            return;
        }
        if (!"USER".equals(currentUser.getRole())) {
            sendMessage(new Message(failureType, "FORBIDDEN"));
            return;
        }
        switch (message.getType()) {
            case GET_ROOMS -> sendMessage(new Message(MessageType.ROOM_LIST, roomManager.getRooms()));
            case JOIN_ROOM -> {
                if (!(message.getData() instanceof Integer roomId)) {
                    sendMessage(new Message(MessageType.JOIN_FAILED, "INVALID_REQUEST"));
                } else {
                    roomManager.join(currentUser, roomId, this::sendMessage);
                }
            }
            case LEAVE_ROOM -> roomManager.leave(currentUser.getId(), this::sendMessage);
            case READY -> roomManager.ready(currentUser.getId(), true, this::sendMessage);
            case CANCEL_READY -> roomManager.ready(currentUser.getId(), false, this::sendMessage);
            case DRAW_UPLOAD, GUESS -> roomManager.handleGameMessage(currentUser.getId(), message, receivedAt, this::sendMessage);
            default -> { }
        }
    }

    private void handleLogin(Object data) {
        if (currentUser != null) {
            loginFailed("ALREADY_LOGGED_IN");
            return;
        }
        if (!(data instanceof LoginRequest request)) {
            loginFailed("INVALID_REQUEST");
            return;
        }
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || username.trim().length() > 50) {
            loginFailed("INVALID_USERNAME");
            return;
        }
        if (password == null || password.isBlank() || password.length() > 255) {
            loginFailed("INVALID_PASSWORD");
            return;
        }
        try {
            UserInfo user = userDAO.login(username.trim(), password);
            if (user == null) {
                loginFailed("INVALID_CREDENTIALS");
            } else if (!("USER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
                loginFailed("INVALID_ROLE");
            } else if (!sessionManager.login(user.getId(), socket)) {
                loginFailed("USER_ALREADY_ONLINE");
            } else {
                currentUser = user;
                sendMessage(new Message(MessageType.LOGIN_SUCCESS, user));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng nhập của Client #" + clientId
                    + ": SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode());
            loginFailed("DATABASE_ERROR");
        }
    }

    private void loginFailed(String reason) {
        sendMessage(new Message(MessageType.LOGIN_FAILED, reason));
    }

    private void clearSession() {
        if (currentUser != null) {
            roomManager.disconnect(currentUser.getId());
            sessionManager.logout(currentUser.getId(), socket);
            currentUser = null;
        }
    }

    private void handleRegister(Object data) {
        if (!(data instanceof RegisterRequest request)) {
            registrationFailed("INVALID_REQUEST");
            return;
        }
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank() || username.trim().length() > 50) {
            registrationFailed("INVALID_USERNAME");
            return;
        }
        if (password == null || password.isBlank() || password.length() > 255) {
            registrationFailed("INVALID_PASSWORD");
            return;
        }
        try {
            if (userDAO.register(username.trim(), password)) {
                sendMessage(new Message(MessageType.REGISTER_SUCCESS));
            } else {
                registrationFailed("USERNAME_EXIST");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký của Client #" + clientId
                    + ": SQLState=" + e.getSQLState() + ", errorCode=" + e.getErrorCode());
            registrationFailed("DATABASE_ERROR");
        }
    }

    private void registrationFailed(String reason) {
        sendMessage(new Message(MessageType.REGISTER_FAILED, reason));
    }

    private synchronized void sendMessage(Message message) {

        try {

            // Giải phóng các tham chiếu của lần gửi trước trên kết nối lâu dài.
            output.reset();
            output.writeObject(message);
            output.flush();

        } catch (IOException e) {

            System.out.println(
                    "Không gửi được cho Client #"
                    + clientId
            );
            try {
                socket.close();
            } catch (IOException closeError) {
                System.err.println("Không đóng được socket của Client #" + clientId);
            }
        }
    }
}
