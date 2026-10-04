package com.nhom15.drawguess.server.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DrawingStoreTest {
    @TempDir Path directory;

    @Test
    void validPngIsConvertedToRgbAndKeptAtFiveHundredPixels() throws Exception {
        DrawingStore store = new DrawingStore(directory);
        byte[] png = encode(new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB), "png");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(store.validate(png)));
        assertEquals(500, image.getWidth());
        assertEquals(500, image.getHeight());
        assertFalse(image.getColorModel().hasAlpha());
        assertEquals(0xFFFFFF, image.getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void wrongDimensionsFormatAndOversizedPayloadAreRejected() throws Exception {
        DrawingStore store = new DrawingStore(directory);
        assertThrows(IOException.class, () -> store.validate(null));
        assertThrows(IOException.class, () -> store.validate(new byte[]{1,2,3}));
        assertThrows(IOException.class, () -> store.validate(new byte[DrawingStore.MAX_BYTES]));
        assertThrows(IOException.class, () -> store.validate(encode(new BufferedImage(501,500,BufferedImage.TYPE_INT_RGB), "png")));
        assertThrows(IOException.class, () -> store.validate(encode(new BufferedImage(500,500,BufferedImage.TYPE_INT_RGB), "jpeg")));
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
