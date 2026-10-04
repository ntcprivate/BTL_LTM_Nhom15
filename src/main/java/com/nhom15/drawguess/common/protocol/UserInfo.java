package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Thông tin được gửi về client sau đăng nhập, không chứa mật khẩu.
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private final String username;
    private final String role;

    public UserInfo(int id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
