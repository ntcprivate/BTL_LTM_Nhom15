package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Thông tin được gửi về client sau đăng nhập, không chứa mật khẩu.
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private final String username;
    private final String role;
    private final String avatarKey;

    public UserInfo(int id, String username, String role) {
        this(id, username, role, AvatarCatalog.DEFAULT);
    }

    public UserInfo(int id, String username, String role, String avatarKey) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.avatarKey = AvatarCatalog.normalize(avatarKey);
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

    public String getAvatarKey() {
        // Đối tượng được gửi từ server phiên bản cũ có thể chưa có trường này.
        return AvatarCatalog.normalize(avatarKey);
    }

    public UserInfo withAvatar(String key) {
        return new UserInfo(id, username, role, key);
    }
}
