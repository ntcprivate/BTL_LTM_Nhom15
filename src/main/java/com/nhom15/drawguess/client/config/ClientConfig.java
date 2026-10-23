package com.nhom15.drawguess.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public record ClientConfig(String host, int port) {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5555;
    private static final Path DEFAULT_FILE = Path.of("config", "client.properties");

    public ClientConfig {
        host = validateHost(host);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Cổng server phải nằm trong khoảng 1 đến 65535.");
        }
    }

    public static ClientConfig load() {
        return load(DEFAULT_FILE, System.getenv());
    }

    static ClientConfig load(Path path, Map<String, String> environment) {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Không đọc được cấu hình client: " + e.getMessage());
            }
        }

        String host = firstNonBlank(environment.get("DRAWGUESS_SERVER_HOST"),
                properties.getProperty("server.host"), DEFAULT_HOST);
        String port = firstNonBlank(environment.get("DRAWGUESS_SERVER_PORT"),
                properties.getProperty("server.port"), String.valueOf(DEFAULT_PORT));
        try {
            return fromInput(host, port);
        } catch (IllegalArgumentException e) {
            System.err.println("Cấu hình server không hợp lệ, dùng localhost:5555: " + e.getMessage());
            return new ClientConfig(DEFAULT_HOST, DEFAULT_PORT);
        }
    }

    public static ClientConfig fromInput(String host, String portText) {
        String value = portText == null ? "" : portText.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Hãy nhập cổng server.");
        }
        try {
            return new ClientConfig(host, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cổng server phải là số từ 1 đến 65535.");
        }
    }

    private static String validateHost(String host) {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Hãy nhập địa chỉ server.");
        }
        if (value.length() > 253 || value.chars().anyMatch(Character::isWhitespace)
                || value.contains("://")) {
            throw new IllegalArgumentException("Địa chỉ server không hợp lệ.");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
