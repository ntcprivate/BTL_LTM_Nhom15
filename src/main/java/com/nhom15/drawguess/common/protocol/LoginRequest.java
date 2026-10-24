package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

public class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private final String password;
    // Mã nối lại của phiên trước (null khi đăng nhập bình thường).
    private final String resumeToken;

    public LoginRequest(String username, String password) {
        this(username, password, null);
    }

    public LoginRequest(String username, String password, String resumeToken) {
        this.username = username;
        this.password = password;
        this.resumeToken = resumeToken;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
