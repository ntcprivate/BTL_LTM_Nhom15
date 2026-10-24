package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

// Công cụ đo tạm thời: thời gian giữa các khung hình (pulse) khi ở menu và khi vẽ.
@EnabledIfSystemProperty(named = "drawguess.perf", matches = "true")
class PerfProbeTest {
    private static final int FRAMES = 240;

    @BeforeAll static void init() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
        catch (IllegalStateException alreadyStarted) { ready.complete(null); }
        ready.get(15, TimeUnit.SECONDS);
    }

    private record Page(Stage stage, StackPane root, StackPane content, AnimatedStarryBackdrop backdrop) {}

    // Dựng cảnh giống ClientMain: nền động + nội dung, cửa sổ cỡ màn hình (như khi phóng to).
    private static Page page(Parent view) {
        StackPane root = new StackPane();
        root.getStyleClass().add("app-screen");
        UiTheme.apply(root);
        AnimatedStarryBackdrop backdrop = new AnimatedStarryBackdrop(root);
        StackPane content = new StackPane(view);
        content.setPadding(new javafx.geometry.Insets(24));
        root.getChildren().addAll(backdrop, content);
        backdrop.followContent(content);
        var bounds = Screen.getPrimary().getVisualBounds();
        Stage stage = new Stage();
        stage.setScene(new Scene(root, bounds.getWidth(), bounds.getHeight()));
        stage.show();
        return new Page(stage, root, content, backdrop);
    }

    private static String measure(String label, IntConsumer perFrame) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        Platform.runLater(() -> new AnimationTimer() {
            final List<Long> stamps = new ArrayList<>();
            @Override public void handle(long now) {
                stamps.add(now);
                if (stamps.size() > 20) { perFrame.accept(stamps.size()); }
                if (stamps.size() >= FRAMES + 20) {
                    stop();
                    List<Double> gaps = new ArrayList<>();
                    for (int i = 21; i < stamps.size(); i++) { gaps.add((stamps.get(i) - stamps.get(i - 1)) / 1e6); }
                    gaps.sort(Double::compare);
                    double avg = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    long slow = gaps.stream().filter(g -> g > 25).count();
                    result.complete(String.format("%-28s trung bình %5.1f ms (%4.1f FPS) | p95 %5.1f ms | max %5.1f ms | khung > 25ms: %d/%d",
                            label, avg, 1000 / avg, gaps.get((int) (gaps.size() * 0.95)), gaps.getLast(), slow, gaps.size()));
                }
            }
        }.start());
        return result.get(60, TimeUnit.SECONDS);
    }

    private static <T> T fx(java.util.concurrent.Callable<T> task) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { done.complete(task.call()); } catch (Throwable e) { done.completeExceptionally(e); } });
        return done.get(20, TimeUnit.SECONDS);
    }

    private static Canvas findCanvas(Parent parent) {
        for (var child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Canvas canvas) { return canvas; }
            if (child instanceof Parent nested) { Canvas found = findCanvas(nested); if (found != null) { return found; } }
        }
        return null;
    }

    private static void mouse(javafx.scene.Node target, javafx.event.EventType<MouseEvent> type, double x, double y) {
        var scene = target.localToScene(x, y);
        target.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    // Mỗi lần chạy JVM chỉ đo một tình huống (-Dperf.scenario=...), tránh nhiễu giữa các lần đo.
    @Test void report() throws Exception {
        String scenario = System.getProperty("perf.scenario", "draw-short");
        String line;
        if (scenario.startsWith("menu")) {
            Page menu = fx(() -> page(new RoomListView(new UserInfo(1, "vu", "USER"), () -> {}, () -> {}, () -> {})));
            Thread.sleep(1500);
            line = measure(scenario, frame -> {
                if (scenario.equals("menu-move")) {
                    mouse(menu.root(), MouseEvent.MOUSE_MOVED, 200 + (frame * 7) % 900, 150 + (frame * 5) % 500);
                }
            });
            fx(() -> { menu.stage().hide(); return null; });
        } else {
            GameView game = new GameView(1, Map.of(), () -> {}, () -> {}, () -> {}, () -> {});
            Page play = fx(() -> {
                Page p = page(game);
                game.showDrawing(new DrawingPrompt("00000000-0000-0000-0000-000000000003", 1, "Động vật", "Con mèo", 60));
                return p;
            });
            Thread.sleep(1500);
            Canvas canvas = fx(() -> findCanvas(game));
            int strokeLength = scenario.equals("draw-long") ? 40 : 4;
            line = measure(scenario, frame -> {
                double x = 50 + (frame * 3) % 400, y = 50 + (frame * 11) % 400;
                if (frame % strokeLength == 0) { mouse(canvas, MouseEvent.MOUSE_PRESSED, x, y); }
                else { mouse(canvas, MouseEvent.MOUSE_DRAGGED, x, y); }
            });
            fx(() -> { play.stage().hide(); return null; });
        }
        System.out.println("@@RESULT " + line);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target", "perf.txt"), line + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
