package com.nhom15.drawguess.server.game;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

public class DrawingStore {
    public static final int SIZE = 500;
    public static final int MAX_BYTES = 200 * 1024;
    private final Path root;

    public DrawingStore(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public byte[] validate(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length >= MAX_BYTES) {
            throw new IOException("INVALID_IMAGE_SIZE");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) { throw new IOException("INVALID_PNG"); }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw new IOException("INVALID_PNG"); }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (!"png".equalsIgnoreCase(reader.getFormatName())
                        || reader.getWidth(0) != SIZE || reader.getHeight(0) != SIZE) {
                    throw new IOException("INVALID_PNG");
                }
                BufferedImage decoded = reader.read(0);
                BufferedImage rgb = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, SIZE, SIZE);
                    graphics.drawImage(decoded, 0, 0, null);
                } finally { graphics.dispose(); }
                byte[] result = encode(rgb);
                if (result.length >= MAX_BYTES) { throw new IOException("INVALID_IMAGE_SIZE"); }
                return result;
            } finally { reader.dispose(); }
        }
    }

    public byte[] blank() throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIZE, SIZE);
        } finally { graphics.dispose(); }
        return encode(image);
    }

    public Path save(String matchId, int userId, byte[] png) throws IOException {
        // matchId do server sinh, luôn phải là UUID trước khi sử dụng làm đường dẫn.
        UUID.fromString(matchId);
        Path directory = root.resolve(matchId).normalize();
        if (!directory.startsWith(root)) { throw new IOException("INVALID_PATH"); }
        Files.createDirectories(directory);
        Path file = directory.resolve(userId + ".png");
        Files.write(file, png);
        return file;
    }

    public byte[] load(String matchId, int drawerId) throws IOException {
        UUID.fromString(matchId);
        if (drawerId <= 0) { throw new IOException("INVALID_DRAWER"); }
        // Chỉ dựng đường dẫn từ ID đã được server xác thực, không nhận đường dẫn từ client.
        Path file = root.resolve(matchId).resolve(drawerId + ".png").toRealPath();
        if (!file.startsWith(root.toRealPath())) { throw new IOException("INVALID_PATH"); }
        try (var input = Files.newInputStream(file)) {
            return validate(input.readNBytes(MAX_BYTES));
        }
    }

    private byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) { throw new IOException("PNG_ENCODER_UNAVAILABLE"); }
        return output.toByteArray();
    }
}
