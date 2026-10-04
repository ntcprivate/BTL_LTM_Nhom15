package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.network.TCPClient;
import com.nhom15.drawguess.client.view.AdminView;
import com.nhom15.drawguess.client.view.LoginView;
import com.nhom15.drawguess.client.view.RegisterView;
import com.nhom15.drawguess.common.protocol.LoginRequest;
import com.nhom15.drawguess.common.protocol.Message;
import com.nhom15.drawguess.common.protocol.MessageType;
import com.nhom15.drawguess.common.protocol.RegisterRequest;
import com.nhom15.drawguess.common.protocol.UserInfo;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

public class AuthController {
    private final StackPane root;
    private final LoginView loginView = new LoginView();
    private final RegisterView registerView = new RegisterView();
    private final TCPClient tcpClient;
    private AdminView accountView;
    private RoomController roomController;
    private volatile boolean loggingOut;
    private volatile boolean closed;

    public AuthController(StackPane root, TCPClient tcpClient) {
        this.root = root;
        this.tcpClient = tcpClient;
        loginView.setOnLogin(this::login);
        loginView.setOnRegister(() -> {
            loginView.clearPassword();
            root.getChildren().setAll(registerView);
        });
        registerView.setOnRegister(this::register);
        registerView.setOnLogin(() -> {
            registerView.clearPasswords();
            showLogin("Nhập tài khoản để đăng nhập.", true);
        });
        root.getChildren().setAll(loginView);
    }

    private String validate(String username, String password) {
        if (username.isBlank() || username.length() > 50) {
            return "Tên đăng nhập phải có từ 1 đến 50 ký tự.";
        }
        if (password.isBlank() || password.length() > 255) {
            return "Mật khẩu phải có từ 1 đến 255 ký tự.";
        }
        return null;
    }

    private void login() {
        String username = loginView.getUsername();
        String password = loginView.getPassword();
        String error = validate(username, password);
        if (error != null) {
            loginView.showMessage(error, false);
            return;
        }
        loginView.setBusy(true);
        loginView.showMessage("Đang đăng nhập...", true);
        startNetworkThread("Login-Network", () -> {
            boolean authenticated = false;
            boolean loggedOut = false;
            Message response = null;
            try {
                response = request(new Message(MessageType.LOGIN, new LoginRequest(username, password)));
                if (response != null && response.getType() == MessageType.LOGIN_SUCCESS
                        && response.getData() instanceof UserInfo user && !closed
                        && tcpClient.setReadTimeout(0)) {
                    authenticated = true;
                    runOnUi(() -> {
                        loginView.clearPassword();
                        loginView.setBusy(false);
                        loggingOut = false;
                        if ("USER".equals(user.getRole())) {
                            roomController = new RoomController(root, user, this::sendAuthenticated, this::logout);
                            roomController.show();
                        } else {
                            accountView = new AdminView(user, this::sendAuthenticated, this::logout);
                            root.getChildren().setAll(accountView);
                        }
                    });
                    // Chỉ thread này đọc socket sau login. Không timeout khi phiên đang chờ.
                    loggedOut = listenForLogout();
                }
            } finally {
                tcpClient.disconnect();
                if (authenticated) {
                    boolean success = loggedOut;
                    runOnUi(() -> showLogin(success ? "Đã đăng xuất."
                            : "Mất kết nối tới server. Hãy đăng nhập lại.", success));
                } else {
                    Message result = response;
                    runOnUi(() -> {
                        loginView.clearPassword();
                        loginView.setBusy(false);
                        loginView.showMessage(failureMessage(result, MessageType.LOGIN_FAILED), false);
                    });
                }
            }
        });
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
        String error = validate(username, password);
        if (error == null && !password.equals(registerView.getConfirmPassword())) {
            error = "Mật khẩu nhập lại không khớp.";
        }
        if (error != null) {
            registerView.showMessage(error, false);
            return;
        }
        registerView.setBusy(true);
        registerView.showMessage("Đang đăng ký...", true);
        startNetworkThread("Register-Network", () -> {
            Message response = null;
            try {
                response = request(new Message(MessageType.REGISTER, new RegisterRequest(username, password)));
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
                        registerView.showMessage(failureMessage(result, MessageType.REGISTER_FAILED), false);
                    }
                });
            }
        });
    }

    private Message request(Message message) {
        if (!closed && tcpClient.connect("localhost", 5555) && !closed && tcpClient.sendMessage(message)) {
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
        accountView = null;
        if (roomController != null) { roomController.close(); }
        roomController = null;
        loggingOut = false;
        loginView.clearPassword();
        loginView.setBusy(false);
        loginView.showMessage(message, success);
        root.getChildren().setAll(loginView);
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
