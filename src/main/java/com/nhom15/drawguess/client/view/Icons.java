package com.nhom15.drawguess.client.view;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

// Bộ icon vector dùng chung, vẽ theo lưới 24x24 với nét 2px đầu tròn (phong cách Lucide/Feather).
// Thay cho các ký tự ★ ✓ ↶ ✦ → vốn to nhỏ, đậm nhạt khác nhau tùy font của máy.
// Icon chức năng đều là dạng viền; chỉ ngôi sao trang trí của logo (SPARKLE) được tô đặc.
public final class Icons {
    public enum Glyph {
        CHECK("M20 6L9 17l-5-5", false),
        STAR("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z", false),
        UNDO("M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11", false),
        ARROW_RIGHT("M5 12h14M12 5l7 7-7 7", false),
        ALERT("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 8v4M12 16h.01", false),
        FLAG("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1zM4 22v-7", false),
        USER_PLUS("M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M8.5 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM20 8v6M23 11h-6", false),
        USER_MINUS("M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M8.5 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM23 11h-6", false),
        LOG_OUT("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9", false),
        POWER("M18.36 6.64a9 9 0 1 1-12.73 0M12 2v10", false),
        MAIL("M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM22 6l-10 7L2 6", false),
        USERS("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z"
                + "M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75", false),
        WIFI_OFF("M1 1l22 22M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39"
                + "M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88M8.53 16.11a6 6 0 0 1 6.95 0M12 20h.01", false),
        SEND("M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z", false),
        MESSAGE("M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7"
                + "a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z", false),
        REPLAY("M1 4v6h6M3.51 15a9 9 0 1 0 2.13-9.36L1 10", false),
        TRASH("M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", false),
        CLOSE("M18 6L6 18M6 6l12 12", false),
        PLUS("M12 5v14M5 12h14", false),
        LAYERS("M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5", false),
        CHEVRON_DOWN("M6 9l6 6 6-6", false),
        SPARKLE("M12 3l1.9 5.8a2 2 0 0 0 1.3 1.3L21 12l-5.8 1.9a2 2 0 0 0-1.3 1.3L12 21l-1.9-5.8"
                + "a2 2 0 0 0-1.3-1.3L3 12l5.8-1.9a2 2 0 0 0 1.3-1.3L12 3z", true),
        EYE("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", false),
        EYE_OFF("M17.94 17.94A10.07 10.07 0 0 1 12 19c-7 0-11-7-11-7a18.45 18.45 0 0 1 5.06-5.94"
                + "M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"
                + "M14.12 14.12a3 3 0 1 1-4.24-4.24M1 1l22 22", false);

        private final String path;
        private final boolean filled;

        Glyph(String path, boolean filled) {
            this.path = path;
            this.filled = filled;
        }
    }

    private Icons() {
    }

    // Icon cùng màu với `color` (thường là màu chữ của nút/nhãn chứa nó).
    public static Pane icon(Glyph glyph, double size, ObservableValue<? extends Paint> color) {
        SVGPath path = new SVGPath();
        path.setContent(glyph.path);
        path.setStrokeWidth(2);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.strokeProperty().bind(color);
        if (glyph.filled) {
            path.fillProperty().bind(color);
        } else {
            path.setFill(null);
        }
        path.getTransforms().add(new Scale(size / 24, size / 24));
        Pane box = new Pane(path);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        box.getStyleClass().addAll("icon", "icon-" + glyph.name().toLowerCase().replace('_', '-'));
        return box;
    }

    // Gắn icon vào trước chữ của nút/nhãn; icon tự đổi màu theo màu chữ do CSS đặt.
    public static <T extends Labeled> T attach(T labeled, Glyph glyph, double size) {
        labeled.setGraphic(icon(glyph, size, labeled.textFillProperty()));
        labeled.setGraphicTextGap(6);
        return labeled;
    }
}
