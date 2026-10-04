package com.nhom15.drawguess.server.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "drawguess.integration", matches = "true")
class AdminSetupIntegrationTest {
    @Test
    void adminScriptRequiresPasswordIsRepeatableAndPreservesExistingAccounts() throws Exception {
        String schema = "drawguess_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DBConnection.getConnection()) {
            try (Statement create = connection.createStatement()) {
                create.execute("CREATE DATABASE " + schema);
            }
            try {
                connection.setCatalog(schema);
                try (Statement sql = connection.createStatement()) {
                runScript(sql, "registration.sql");
                sql.execute("SET @admin_username = NULL, @admin_password = NULL");
                runScript(sql, "admin.sql");
                try (var result = sql.executeQuery("SELECT COUNT(*) FROM user_account")) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
                sql.execute("SET @admin_username = 'admin', @admin_password = 'test-private-password'");
                runScript(sql, "admin.sql");
                sql.execute("SET @admin_password = 'different-password'");
                runScript(sql, "admin.sql");
                try (var result = sql.executeQuery("SELECT role, password FROM user_account WHERE username='admin'")) {
                    assertTrue(result.next());
                    assertEquals("ADMIN", result.getString(1));
                    assertEquals("test-private-password", result.getString(2));
                    assertFalse(result.next());
                }
                sql.execute("INSERT INTO user_account(username,password,role) VALUES('player','original','USER')");
                sql.execute("SET @admin_username='player', @admin_password='new-password'");
                runScript(sql, "admin.sql");
                try (var result = sql.executeQuery("SELECT role,password FROM user_account WHERE username='player'")) {
                    assertTrue(result.next());
                    assertEquals("USER", result.getString(1));
                    assertEquals("original", result.getString(2));
                }
                }
            } finally {
                try (Connection cleanup = DBConnection.getConnection(); Statement sql = cleanup.createStatement()) {
                    sql.execute("DROP DATABASE " + schema);
                }
            }
        }
    }

    private void runScript(Statement sql, String file) throws Exception {
        String script = Files.readString(Path.of("src/main/resources/db", file))
                .replaceAll("(?m)^\\s*--.*$", "");
        for (String command : script.split(";")) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("USE ") && !trimmed.startsWith("CREATE DATABASE")) {
                sql.execute(trimmed);
            }
        }
    }
}
