package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.config.ClientConfig;
import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.client.view.AdminView;
import com.nhom15.drawguess.client.view.AuthField;
import com.nhom15.drawguess.client.view.LoginView;
import com.nhom15.drawguess.client.view.ReconnectOverlay;
import com.nhom15.drawguess.client.view.RegisterView;
import com.nhom15.drawguess.client.view.UiTheme;
import com.nhom15.drawguess.common.protocol.LoginRequest;
import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.MessageType;
import com.nhom15.drawguess.common.protocol.RegisterRequest;
import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;

public class AuthController {
    private final StackPane root;
    private final LoginView loginView;
    private final RegisterView registerView = new RegisterView();
    private final TCPClient tcpClient;
    private AdminView accountView;
    private RoomController roomController;
    private volatile boolean loggingOut;
    private volatile boolean closed;
    // Tự đăng nhập lại khi mất kết nối giữa chừng.
    static final int RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 2000;
    // Mã nối lại do server cấp: giúp lấy lại phiên cũ khi server chưa nhận ra kết nối cũ đã chết.
    private volatile String resumeToken;
    private volatile boolean reconnectCancelled;
    private ReconnectOverlay reconnectOverlay;

    public AuthController(StackPane root, TCPClient tcpClient) {
        this.root = root;
        this.tcpClient = tcpClient;
        ClientConfig config = ClientConfig.load();
        loginView = new LoginView(config.host(), config.port());
        loginView.setOnLogin(this::login);
        loginView.setOnRegister(() -> {
            loginView.clearPassword();
            registerView.setServerAddress(loginView.getServerAddress());
            UiTheme.show(root, registerView);
        });
        registerView.setOnRegister(this::register);
        registerView.setOnLogin(() -> {
            registerView.clearPasswords();
            showLogin("Nhập tài khoản để đăng nhập.", true);
        });
        UiTheme.show(root, loginView);
    }

    // Kiểm tra tất cả các ô cùng lúc để báo lỗi ngay dưới từng ô sai.
    private Map<AuthField, String> validate(String username, String password) {
        Map<AuthField, String> errors = new EnumMap<>(AuthField.class);
        if (username.isBlank() || username.length() > 50) {
            errors.put(AuthField.USERNAME, "Tên đăng nhập phải có từ 1 đến 50 ký tự.");
        }
        if (password.isBlank() || password.length() > 255) {
            errors.put(AuthField.PASSWORD, "Mật khẩu phải có từ 1 đến 255 ký tự.");
        }
        return errors;
    }

    private static String inputError(Runnable check) {
        try {
            check.run();
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private void login() {
        String username = loginView.getUsername();
        String password = loginView.getPassword();
        Map<AuthField, String> errors = validate(username, password);
        // Kiểm tra riêng địa chỉ và cổng để biết lỗi thuộc ô nào.
        String hostError = inputError(() -> new ClientConfig(loginView.getServerHost(), ClientConfig.DEFAULT_PORT));
        String portError = inputError(() -> ClientConfig.fromInput(ClientConfig.DEFAULT_HOST, loginView.getServerPort()));
        if (hostError != null) { errors.put(AuthField.SERVER_HOST, hostError); }
        if (portError != null) { errors.put(AuthField.SERVER_PORT, portError); }
        if (!errors.isEmpty()) {
            loginView.showFieldErrors(errors);
            return;
        }
        ClientConfig server = ClientConfig.fromInput(loginView.getServerHost(), loginView.getServerPort());
        loginView.clearFieldErrors();
        loginView.setBusy(true);
        loginView.showMessage("Đang đăng nhập...", true);
        ClientConfig targetServer = server;
        startNetworkThread("Login-Network", () -> runSession(targetServer, username, password));
    }

    // Đăng nhập rồi giữ kết nối. Mất kết nối giữa chừng (không phải do đăng xuất) thì tự đăng nhập lại
    // vài lần; server sẽ đưa người chơi về đúng phòng hoặc trận đang chơi.
    private void runSession(ClientConfig server, String username, String password) {
        resumeToken = null;
        Message response = request(server, new Message(MessageType.LOGIN, new LoginRequest(username, password)));
        if (!startSession(response)) {
            tcpClient.disconnect();
            runOnUi(() -> {
                loginView.clearPassword();
                loginView.setBusy(false);
                loginView.showMessage(failureMessage(response, MessageType.LOGIN_FAILED), false);
                // Không kết nối được thì mở sẵn phần cài đặt máy chủ để người chơi kiểm tra.
                if (response == null) { loginView.showServerSettings(true); }
            });
            return;
        }
        while (true) {
            // Chỉ thread này đọc socket sau login. Không timeout khi phiên đang chờ.
            boolean loggedOut = listenForLogout();
            tcpClient.disconnect();
            if (loggedOut || loggingOut || closed) {
                runOnUi(() -> showLogin(loggedOut ? "Đã đăng xuất."
                        : "Mất kết nối tới server. Hãy đăng nhập lại.", loggedOut));
                return;
            }
            if (!reconnect(server, username, password)) {
                String reason = reconnectCancelled ? "Đã dừng kết nối lại. Hãy đăng nhập lại."
                        : "Không kết nối lại được tới server. Hãy kiểm tra mạng rồi đăng nhập lại.";
                runOnUi(() -> showLogin(reason, false));
                return;
            }
        }
    }

    // Đăng nhập thành công thì dựng màn hình theo vai trò và xin mã nối lại cho lần mất kết nối sau.
    private boolean startSession(Message response) {
        if (closed || response == null || response.getType() != MessageType.LOGIN_SUCCESS
                || !(response.getData() instanceof UserInfo user) || !tcpClient.setReadTimeout(0)) {
            return false;
        }
        runOnUi(() -> {
            loginView.clearPassword();
            loginView.setBusy(false);
            loggingOut = false;
            reconnectOverlay = null;
            if (roomController != null) { roomController.close(); }
            roomController = null;
            accountView = null;
            if ("USER".equals(user.getRole())) {
                roomController = new RoomController(root, user, this::sendAuthenticated, this::logout);
                roomController.show();
            } else {
                accountView = new AdminView(user, this::sendAuthenticated, this::logout);
                UiTheme.show(root, accountView);
            }
        });
        return tcpClient.sendMessage(new Message(MessageType.GET_RESUME_TOKEN));
    }

    private boolean reconnect(ClientConfig server, String username, String password) {
        reconnectCancelled = false;
        for (int attempt = 1; attempt <= RECONNECT_ATTEMPTS && !closed && !reconnectCancelled; attempt++) {
            int shown = attempt;
            runOnUi(() -> showReconnecting(shown));
            if (!pause(RECONNECT_DELAY_MS) || reconnectCancelled || closed) { return false; }
            Message response = request(server,
                    new Message(MessageType.LOGIN, new LoginRequest(username, password, resumeToken)));
            if (!reconnectCancelled && startSession(response)) { return true; }
            tcpClient.disconnect();
            // Server từ chối hẳn (đổi mật khẩu, tài khoản đang dùng ở máy khác...) thì không thử tiếp.
            if (response != null && response.getType() == MessageType.LOGIN_FAILED
                    && !"DATABASE_ERROR".equals(response.getData())) {
                return false;
            }
        }
        return false;
    }

    // Giữ nguyên màn hình đang chơi và phủ thông báo đang kết nối lại lên trên.
    private void showReconnecting(int attempt) {
        if (reconnectOverlay == null) {
            if (roomController != null) { roomController.close(); }
            reconnectOverlay = new ReconnectOverlay(() -> {
                reconnectCancelled = true;
                tcpClient.disconnect();
            });
            root.getChildren().add(reconnectOverlay);
            reconnectOverlay.focusAction();
        }
        reconnectOverlay.showAttempt(attempt, RECONNECT_ATTEMPTS);
    }

    private static boolean pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean listenForLogout() {
        while (!closed) {
            Message message = tcpClient.receiveMessage();
            if (message == null) {
                return false;
            }
            if (message.getType() == MessageType.ACTION_SUCCESS
                    && "LOGOUT".equals(message.getData())) {
                return true;
            }
            if (message.getType() == MessageType.ACTION_FAILED && loggingOut) {
                return false;
            }
            if (message.getType() == MessageType.RESUME_TOKEN && message.getData() instanceof String token) {
                resumeToken = token;
                continue;
            }
            runOnUi(() -> {
                if (roomController != null) {
                    roomController.handleMessage(message);
                }
                if (accountView != null) { accountView.handleMessage(message); }
            });
        }
        return false;
    }

    private void logout() {
        loggingOut = true;
        if (roomController != null) { roomController.setLoggingOut(); }
        if (accountView != null) { accountView.setLoggingOut(); }
        startNetworkThread("Logout-Network", () -> {
            if (!tcpClient.sendMessage(new Message(MessageType.LOGOUT))) {
                tcpClient.disconnect();
            }
        });
    }

    private void sendAuthenticated(Message message) {
        startNetworkThread("Room-Send", () -> {
            if (!closed && !loggingOut && !tcpClient.sendMessage(message)) {
                tcpClient.disconnect();
            }
        });
    }

    private void register() {
        String username = registerView.getUsername();
        String password = registerView.getPassword();
        Map<AuthField, String> errors = validate(username, password);
        if (!errors.containsKey(AuthField.PASSWORD) && !password.equals(registerView.getConfirmPassword())) {
            errors.put(AuthField.CONFIRM_PASSWORD, "Mật khẩu nhập lại không khớp.");
        }
        if (!errors.isEmpty()) {
            registerView.showFieldErrors(errors);
            return;
        }
        registerView.clearFieldErrors();
        ClientConfig server;
        try {
            server = ClientConfig.fromInput(loginView.getServerHost(), loginView.getServerPort());
        } catch (IllegalArgumentException e) {
            // Máy chủ được nhập ở màn hình đăng nhập nên lỗi này hiện ở ô thông báo chung.
            registerView.showMessage(e.getMessage(), false);
            return;
        }
        registerView.setBusy(true);
        registerView.showMessage("Đang đăng ký...", true);
        ClientConfig targetServer = server;
        startNetworkThread("Register-Network", () -> {
            Message response = null;
            try {
                response = request(targetServer,
                        new Message(MessageType.REGISTER, new RegisterRequest(username, password)));
            } finally {
                tcpClient.disconnect();
                Message result = response;
                runOnUi(() -> {
                    registerView.setBusy(false);
                    if (result != null && result.getType() == MessageType.REGISTER_SUCCESS) {
                        registerView.clearPasswords();
                        loginView.setUsername(username);
                        showLogin("Đăng ký thành công! Hãy đăng nhập.", true);
                    } else {
                        String message = failureMessage(result, MessageType.REGISTER_FAILED);
                        AuthField field = result == null || result.getType() != MessageType.REGISTER_FAILED ? null
                                : switch (String.valueOf(result.getData())) {
                                    case "USERNAME_EXIST", "INVALID_USERNAME" -> AuthField.USERNAME;
                                    case "INVALID_PASSWORD" -> AuthField.PASSWORD;
                                    default -> null;
                                };
                        if (field != null) {
                            registerView.showFieldErrors(Map.of(field, message));
                        } else {
                            registerView.showMessage(message, false);
                        }
                    }
                });
            }
        });
    }

    private Message request(ClientConfig server, Message message) {
        if (!closed && tcpClient.connect(server.host(), server.port())
                && !closed && tcpClient.sendMessage(message)) {
            return tcpClient.receiveMessage();
        }
        return null;
    }

    private String failureMessage(Message response, MessageType expectedFailure) {
        if (response == null) {
            return "Không nhận được phản hồi. Hãy kiểm tra server và thử lại.";
        }
        if (response.getType() != expectedFailure) {
            return "Server trả về phản hồi không phù hợp.";
        }
        return switch (String.valueOf(response.getData())) {
            case "USERNAME_EXIST" -> "Tên đăng nhập đã tồn tại. Hãy chọn tên khác.";
            case "INVALID_USERNAME" -> "Tên đăng nhập phải có từ 1 đến 50 ký tự.";
            case "INVALID_PASSWORD" -> "Mật khẩu phải có từ 1 đến 255 ký tự.";
            case "INVALID_CREDENTIALS" -> "Tên đăng nhập hoặc mật khẩu không đúng.";
            case "USER_ALREADY_ONLINE" -> "Tài khoản đang đăng nhập trên một kết nối khác.";
            case "ALREADY_LOGGED_IN" -> "Kết nối này đã đăng nhập.";
            case "INVALID_ROLE" -> "Tài khoản chưa có quyền truy cập hợp lệ.";
            case "DATABASE_ERROR" -> "Server chưa thể truy cập cơ sở dữ liệu. Hãy thử lại sau.";
            default -> "Yêu cầu không hợp lệ.";
        };
    }

    private void showLogin(String message, boolean success) {
        reconnectOverlay = null;
        accountView = null;
        if (roomController != null) { roomController.close(); }
        roomController = null;
        loggingOut = false;
        loginView.clearPassword();
        loginView.setBusy(false);
        loginView.showMessage(message, success);
        UiTheme.show(root, loginView);
    }

    private void runOnUi(Runnable action) {
        if (!closed) {
            Platform.runLater(() -> {
                if (!closed) {
                    action.run();
                }
            });
        }
    }

    private void startNetworkThread(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
    }

    public void close() {
        closed = true;
        if (roomController != null) { roomController.close(); }
        tcpClient.disconnect();
    }
}
