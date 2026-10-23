package com.nhom15.drawguess.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfigTest {
    @TempDir
    Path tempDirectory;

    @Test
    void usesDefaultsWhenFileDoesNotExist() {
        ClientConfig config = ClientConfig.load(tempDirectory.resolve("missing.properties"), Map.of());
        assertEquals("localhost", config.host());
        assertEquals(5555, config.port());
    }

    @Test
    void loadsPropertiesAndLetsEnvironmentOverrideThem() throws Exception {
        Path file = tempDirectory.resolve("client.properties");
        Files.writeString(file, "server.host=10.0.0.5\nserver.port=6000\n");

        ClientConfig fromFile = ClientConfig.load(file, Map.of());
        assertEquals("10.0.0.5", fromFile.host());
        assertEquals(6000, fromFile.port());

        ClientConfig fromEnvironment = ClientConfig.load(file, Map.of(
                "DRAWGUESS_SERVER_HOST", "192.168.1.25",
                "DRAWGUESS_SERVER_PORT", "7000"));
        assertEquals("192.168.1.25", fromEnvironment.host());
        assertEquals(7000, fromEnvironment.port());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromInput("", "5555"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromInput("localhost", "0"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromInput("localhost", "abc"));
        assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromInput("http://localhost", "5555"));
    }
}
