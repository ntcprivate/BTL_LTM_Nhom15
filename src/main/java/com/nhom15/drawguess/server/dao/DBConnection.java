package com.nhom15.drawguess.server.dao;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/drawguess_db"
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh";

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Properties local = new Properties();
        Path config = Path.of("config", "db.properties");
        if (Files.exists(config)) {
            try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                local.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                throw new SQLException("Cannot read config/db.properties", e);
            }
        }
        return DriverManager.getConnection(
                setting(local, "db.url", "DRAWGUESS_DB_URL", DEFAULT_URL),
                setting(local, "db.user", "DRAWGUESS_DB_USER", "root"),
                setting(local, "db.password", "DRAWGUESS_DB_PASSWORD", ""));
    }

    private static String setting(Properties local, String key, String environment, String fallback) {
        String value = System.getenv(environment);
        return value != null ? value : local.getProperty(key, fallback);
    }
}
