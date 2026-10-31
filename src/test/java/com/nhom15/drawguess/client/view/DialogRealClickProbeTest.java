package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.RoundInfo;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Bấm chuột THẬT (qua hệ điều hành) ra ngoài các hộp xác nhận ở màn chơi: hộp phải đóng như bấm nút hủy.
// Test giả lập sự kiện trong JavaFX không bắt được lỗi Windows bỏ cú bấm, nên cần test này.
// Di chuyển chuột trên màn hình nên chỉ chạy khi bật -Ddrawguess.robot=true.
@EnabledIfSystemProperty(named = "drawguess.robot", matches = "true")
class DialogRealClickProbeTest {
    @Test void realClickOutsideClosesTheInGameConfirmations() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); } catch (IllegalStateException e) { ready.complete(null); }
        ready.get(15, TimeUnit.SECONDS);
        UiTheme.motionPreference = () -> true;
        for (String action : List.of("Rời phòng", "Đăng xuất", "Báo cáo tranh")) {
            assertEquals(List.of(), clickOutside(action), action + ": bấm ra ngoài phải đóng hộp và không thực hiện hành động");
        }
    }

    // Mở hộp xác nhận của nút `action`, bấm chuột thật vào vùng tối bên ngoài; trả về các hành động đã chạy.
    private static List<String> clickOutside(String action) throws Exception {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        Platform.runLater(() -> { try {
            List<String> done = new ArrayList<>();
            GameView view = new GameView(1, Map.of(), () -> {}, () -> {}, () -> done.add("leave"), () -> done.add("logout"));
            view.setOnReport(() -> done.add("report"));
            StackPane root = new StackPane(view);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 1200, 820));
            stage.setX(60);
            stage.setY(60);
            // Luôn nằm trên cùng để cú bấm không rơi vào cửa sổ khác đang che.
            stage.setAlwaysOnTop(true);
            stage.show();
            view.showRound(new RoundInfo("00000000-0000-0000-0000-000000000001", 0, 3, 2, "lan", "Động vật",
                    new byte[0], 30, "_ _"), 1);
            Robot robot = new Robot();
            PauseTransition open = new PauseTransition(Duration.millis(500));
            open.setOnFinished(e -> Platform.runLater(() -> { try {
                PauseTransition click = new PauseTransition(Duration.millis(700));
                click.setOnFinished(ev -> {
                    robot.mouseMove(stage.getX() + 160, stage.getY() + 260);
                    robot.mouseClick(MouseButton.PRIMARY);
                });
                PauseTransition giveUp = new PauseTransition(Duration.millis(3000));
                giveUp.setOnFinished(ev -> {
                    for (var w : List.copyOf(javafx.stage.Window.getWindows())) {
                        if (w != stage && w.isShowing()) { done.add("still open"); w.hide(); }
                    }
                });
                click.play();
                giveUp.play();
                Button button = (Button) view.lookupAll(".button").stream()
                        .filter(n -> n instanceof Button b && action.equals(b.getText())).findFirst().orElseThrow();
                button.fire();
                stage.hide();
                result.complete(done);
            } catch (Throwable t) { result.completeExceptionally(t); } }));
            open.play();
        } catch (Throwable t) { result.completeExceptionally(t); } });
        return result.get(20, TimeUnit.SECONDS);
    }
}
