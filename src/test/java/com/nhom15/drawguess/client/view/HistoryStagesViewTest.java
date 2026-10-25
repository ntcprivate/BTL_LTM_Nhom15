package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HistoryStagesViewTest {
    private static final long AT = 1_760_000_000_000L;

    private static HistoryData.Round turn(int index, int drawerId, String drawer, String word, int guesses) {
        List<HistoryData.Guess> list = new ArrayList<>();
        for (int i = 0; i < guesses; i++) {
            list.add(new HistoryData.Guess(9, "khách", i == 0 ? word : "Sai " + i, i == 0, AT + 2000 + i, 2.5 + i));
        }
        return new HistoryData.Round(index, drawerId, drawer, word, "server-data/drawings/x/" + drawerId + ".png",
                AT + index * 30_000L, AT + index * 30_000L + 20_000, list);
    }

    private static HistoryData.Detail detail(String id, String category, int stages) {
        List<ScoreEntry> players = List.of(new ScoreEntry(1, "vu", 12, 1, 4.0, true),
                new ScoreEntry(2, "lan", 9, 2, 6.0, true), new ScoreEntry(3, "minh", 7, 3, 8.0, true));
        String[][] words = {{"Con mèo", "Con chó", "Con voi"}, {"Phở", "Bún chả", "Cơm tấm"}};
        List<HistoryData.Round> rounds = new ArrayList<>();
        for (int s = 0; s < stages; s++) {
            for (int p = 0; p < 3; p++) {
                rounds.add(turn(s * 3 + p, p + 1, players.get(p).getUsername(), words[s][p], p + 1));
            }
        }
        return new HistoryData.Detail(id, 7, category, AT, AT + 300_000, "COMPLETED", players, rounds);
    }

    @Test void turnsAreGroupedIntoStagesWithTheirTopics() {
        HistoryData.Detail detail = detail(UUID.randomUUID().toString(), "Động vật, Đồ ăn", 2);
        List<HistoryData.Stage> stages = HistoryData.stages(detail);
        assertEquals(2, stages.size());
        assertEquals("Vòng 1: Động vật", stages.getFirst().label());
        assertEquals("Vòng 2: Đồ ăn", stages.get(1).label());
        assertEquals(List.of(3, 4, 5), stages.get(1).rounds().stream().map(HistoryData.Round::index).toList());
        assertEquals(2, HistoryData.turnNumber(detail, stages.get(1).rounds().get(1)));

        HistoryData.Detail single = detail(UUID.randomUUID().toString(), "Đồ chơi, búp bê", 1);
        assertEquals("Vòng 1: Đồ chơi, búp bê", HistoryData.stages(single).getFirst().label(),
                "Trận một vòng giữ nguyên tên chủ đề kể cả khi có dấu phẩy");
        HistoryData.Detail cut = detail(UUID.randomUUID().toString(), "Một chủ đề rất dài bị cắt…", 2);
        assertEquals("Vòng 2", HistoryData.stages(cut).get(1).label(), "Không tách được tên thì chỉ ghi số vòng");
    }

    @Nested
    @EnabledIfSystemProperty(named = "drawguess.ui", matches = "true")
    class View {
        @BeforeAll static void init() throws Exception {
            CompletableFuture<Void> ready = new CompletableFuture<>();
            try {
                Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
            } catch (IllegalStateException alreadyStarted) {
                ready.complete(null);
            }
            ready.get(15, TimeUnit.SECONDS);
        }

        @AfterEach void closeWindows() throws Exception {
            ui(() -> { for (var window : List.copyOf(javafx.stage.Window.getWindows())) { window.hide(); } });
        }

        @Test void eachStageListsItsOwnTurnsAndPickersFollowTheStage() throws Exception {
            ui(() -> {
                List<Message> sent = new ArrayList<>();
                HistoryView view = new HistoryView(sent::add, () -> {});
                show(view, 1280, 860);
                String id = UUID.randomUUID().toString();
                view.handleMessage(new Message(MessageType.HISTORY_LIST, List.of(
                        new HistoryData.Summary(id, 7, "Động vật, Đồ ăn", AT, AT + 300_000, "COMPLETED", 12, 1))));
                ((ListView<?>) view.lookup(".list-view")).getSelectionModel().select(0);
                view.handleMessage(new Message(MessageType.HISTORY_DETAIL, detail(id, "Động vật, Đồ ăn", 2)));
                view.applyCss(); view.layout();

                List<Node> groups = List.copyOf(view.lookupAll(".history-stage"));
                assertEquals(2, groups.size());
                assertEquals(List.of("Động vật", "Đồ ăn"), view.lookupAll(".history-stage-topic").stream()
                        .map(n -> ((Label) n).getText()).toList());
                assertEquals("3 lượt • 6 câu đoán", ((Label) groups.get(1).lookup(".history-stage-meta")).getText());
                assertEquals(3, ((Parent) groups.get(1)).lookupAll(".round-card").size(), "Mỗi vòng chỉ có lượt của riêng nó");
                assertEquals(List.of("L1", "L2", "L3"), ((Parent) groups.get(1)).lookupAll(".round-number").stream()
                        .map(n -> ((Label) n).getText()).toList());

                ComboBox<?> stages = (ComboBox<?>) view.lookup(".history-stages");
                ComboBox<?> turns = (ComboBox<?>) view.lookup(".history-rounds");
                assertEquals(2, stages.getItems().size());
                assertEquals(3, turns.getItems().size());
                assertEquals(new HistoryData.ImageRequest(id, 0), sent.getLast().getData());

                stages.getSelectionModel().select(1);
                assertEquals(3, turns.getItems().size());
                assertEquals(3, ((HistoryData.Round) turns.getValue()).index(), "Đổi vòng thì chọn lượt đầu của vòng đó");
                assertEquals(new HistoryData.ImageRequest(id, 3), sent.getLast().getData());

                Node secondTurn = List.copyOf(((Parent) groups.getFirst()).lookupAll(".round-card")).get(1);
                Event.fireEvent(secondTurn, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, true, null));
                assertEquals(0, ((HistoryData.Stage) stages.getValue()).index(), "Bấm lượt ở vòng khác thì đổi vòng theo");
                assertEquals(new HistoryData.ImageRequest(id, 1), sent.getLast().getData());
                assertTrue(secondTurn.getStyleClass().contains("round-card-selected"));
                assertEquals("Vòng 1: Động vật   •   Lượt 2   •   Người vẽ: lan   •   Từ khóa: Con chó",
                        ((Label) view.lookup(".round-summary")).getText());
                assertEquals(2, view.lookupAll(".guess-card").size());
                snapshot(view, "history-stages.png");
                javafx.scene.control.ScrollPane overview = (javafx.scene.control.ScrollPane) view.lookupAll(".scroll-pane")
                        .stream().filter(n -> n.lookup(".history-overview") != null).findFirst().orElseThrow();
                snapshot((Parent) overview.getContent(), "history-stages-groups.png");
            });
        }
    }

    private static void ui(Runnable task) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { task.run(); done.complete(null); } catch (Throwable e) { done.completeExceptionally(e); } });
        done.get(15, TimeUnit.SECONDS);
    }

    private static void show(Parent view, double width, double height) {
        var stage = new javafx.stage.Stage();
        stage.setOpacity(0);
        stage.setScene(new Scene(view, width, height));
        stage.show();
        view.applyCss();
        view.layout();
    }

    private static void snapshot(Parent view, String filename) {
        try {
            view.applyCss(); view.layout();
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#0b3478"));
            WritableImage image = view.snapshot(params, null);
            BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            Files.createDirectories(Path.of("target/qa"));
            ImageIO.write(png, "png", Path.of("target/qa", filename).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
