package com.nhom15.drawguess.common.protocol;

import java.io.Serializable;

// Một tin nhắn trong phòng, server gửi cho mọi người đang online trong phòng.
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int userId;
    private final String username;
    private final String text;
    private final long sentAt;

    public ChatMessage(int userId, String username, String text, long sentAt) {
        this.userId = userId;
        this.username = username;
        this.text = text;
        this.sentAt = sentAt;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getText() { return text; }
    public long getSentAt() { return sentAt; }
}
