package com.nhom15.drawguess.client.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

// Âm báo ngắn của game (đếm ngược, đoán đúng/sai, hết giờ, tin nhắn...). Có nút tắt tiếng dùng chung
// cho mọi màn hình; lựa chọn được nhớ cho lần mở game sau. Máy không phát được âm thanh thì bỏ qua.
public final class Sounds {
    public enum Effect {
        TICK("tick"), START("start"), CORRECT("correct"), WRONG("wrong"), TIME_UP("time_up"),
        MESSAGE("message"), INVITE("invite"), RESULT("result");

        private final String file;

        Effect(String file) { this.file = file; }
    }

    private static final double VOLUME = 0.6;
    private static final String MUTED_KEY = "muted";
    private static final Map<Effect, AudioClip> clips = new ConcurrentHashMap<>();
    private static final BooleanProperty muted = new SimpleBooleanProperty(loadMuted());
    // Cách phát âm thanh; test thay bằng hàm ghi lại để kiểm tra mà không phát ra loa.
    static Consumer<Effect> player = Sounds::playClip;
    // Cách lưu lựa chọn tắt tiếng; test thay để không ghi vào máy.
    static Consumer<Boolean> saveMuted = Sounds::storeMuted;

    private Sounds() {
    }

    public static void play(Effect effect) {
        if (!muted.get()) { player.accept(effect); }
    }

    public static boolean isMuted() {
        return muted.get();
    }

    public static void setMuted(boolean value) {
        muted.set(value);
        saveMuted.accept(value);
    }

    public static BooleanProperty mutedProperty() {
        return muted;
    }

    // Nút loa (chỉ có icon) để tắt/bật âm thanh; mọi nút trên các màn hình cùng đổi theo.
    public static Button toggleButton() {
        Button button = new Button();
        button.getStyleClass().addAll("ghost-button", "sound-toggle");
        Tooltip tooltip = new Tooltip();
        button.setTooltip(tooltip);
        Runnable render = () -> {
            boolean off = muted.get();
            Icons.attach(button, off ? Icons.Glyph.VOLUME_OFF : Icons.Glyph.VOLUME, 18);
            button.setAccessibleText(off ? "Bật âm thanh" : "Tắt âm thanh");
            tooltip.setText(off ? "Đang tắt tiếng. Bấm để bật âm thanh" : "Bấm để tắt âm thanh");
        };
        render.run();
        // Giữ listener theo nút (weak) để nút của màn hình đã đóng không bị giữ lại mãi.
        javafx.beans.value.ChangeListener<Boolean> listener = (observable, before, value) -> render.run();
        button.getProperties().put("sound-listener", listener);
        muted.addListener(new javafx.beans.value.WeakChangeListener<>(listener));
        button.setOnAction(event -> {
            setMuted(!muted.get());
            if (!muted.get()) { play(Effect.MESSAGE); }
        });
        return button;
    }

    // Nạp sẵn mọi âm thanh ở luồng nền khi mở game, để tiếng đầu tiên (ví dụ tích tắc đếm ngược) không bị trễ.
    public static void preload() {
        if (Boolean.getBoolean("drawguess.silent")) { return; }
        Thread loader = new Thread(() -> {
            for (Effect effect : Effect.values()) {
                try { clips.computeIfAbsent(effect, Sounds::load); }
                catch (RuntimeException | LinkageError unavailable) { return; }
            }
        }, "Sound-Preload");
        loader.setDaemon(true);
        loader.start();
    }

    private static void playClip(Effect effect) {
        if (Boolean.getBoolean("drawguess.silent")) { return; }
        try {
            AudioClip clip = clips.computeIfAbsent(effect, Sounds::load);
            if (clip != null) { clip.play(VOLUME); }
        } catch (RuntimeException | LinkageError unavailable) {
            // Không có thiết bị âm thanh hoặc thiếu thư viện media: game vẫn chạy bình thường.
        }
    }

    private static AudioClip load(Effect effect) {
        URL url = Sounds.class.getResource("/sounds/" + effect.file + ".wav");
        return url == null ? null : new AudioClip(url.toExternalForm());
    }

    private static boolean loadMuted() {
        try {
            return Preferences.userNodeForPackage(Sounds.class).getBoolean(MUTED_KEY, false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void storeMuted(boolean value) {
        try {
            Preferences.userNodeForPackage(Sounds.class).putBoolean(MUTED_KEY, value);
        } catch (RuntimeException ignored) {
            // Không lưu được thì lựa chọn chỉ có hiệu lực tới khi tắt game.
        }
    }
}
