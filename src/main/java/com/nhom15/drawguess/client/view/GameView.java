package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameView extends BorderPane {
    private static final int UNDO_LIMIT = 20;
    private static final int URGENT_SECONDS = 10;
    private static final KeyCombination UNDO_KEY = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private final int userId;
    private final Map<Integer, String> avatars;
    private final Canvas canvas = new Canvas(500, 500);
    private final ImageView picture = new ImageView();
    private final VBox resultPanel = new VBox(14);
    private final Label category = new Label();
    private final Label word = new Label();
    private final Label hint = new Label();
    private final Label timer = new Label();
    private final Label status = new Label();
    private final ColorPicker color = new ColorPicker(Color.BLACK);
    private final Slider width = new Slider(2, 24, 5);
    private final ToggleButton eraser = new ToggleButton("Tẩy");
    private final Button clear = new Button("Xóa tranh");
    private final Button undo = Icons.attach(new Button("Hoàn tác"), Icons.Glyph.UNDO, 16);
    // Lịch sử vẽ dưới dạng các nét (không chụp ảnh canvas mỗi lần nhấn chuột vì rất tốn GPU).
    // Hoàn tác = bỏ thao tác cuối rồi vẽ lại các nét còn lại.
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke;
    private int undoableSteps;
    // Tranh đang vẽ dở do server gửi lại khi vào lại trận; là nền của các nét vẽ cho tới lần "Xóa tranh".
    private Image base;
    // Có nét mới kể từ lần gửi bản nháp trước.
    private boolean changed;
    private final Button report = Icons.attach(new Button("Báo cáo tranh"), Icons.Glyph.FLAG, 15);
    private final Label reportStatus = new Label();
    private final HBox reportBar = new HBox(10, report, reportStatus);
    private Runnable onReport = () -> {};
    private String drawerName = "";
    private final TextField answer = new TextField();
    private final Button guess = new Button("Đoán");
    private final HBox guessRow = new HBox(8, answer, guess);
    private final Button back = new Button("Về phòng chờ");
    private final Button playAgain = Icons.attach(new Button("Chơi lại"), Icons.Glyph.REPLAY, 16);
    private final Label rematch = Icons.attach(new Label(), Icons.Glyph.REPLAY, 15);
    private Runnable onPlayAgain = () -> {};
    // Khung chat: cửa sổ đủ rộng thì là cột bên trái ô vẽ (cân với cột bảng điểm bên phải);
    // cửa sổ hẹp thì gộp vào cột phải, chuyển qua lại bằng hai thẻ "Bảng điểm" / "Trò chuyện".
    private static final double SIDE_WIDTH = 290;
    private static final double COLUMN_GAP = 20;
    static final double WIDE_LAYOUT_WIDTH = 2 * SIDE_WIDTH + 500 + 2 * COLUMN_GAP + 20;
    private final ChatPanel chat;
    private final VBox chatColumn = new VBox(12);
    private final ArtworkCard chatPainting;
    private final HBox center = new HBox(COLUMN_GAP);
    private VBox canvasColumn;
    private VBox right;
    private final Label scoreTitle = new Label("Bảng điểm");
    private final ToggleButton scoreTab = new ToggleButton("Bảng điểm");
    private final ToggleButton chatTab = new ToggleButton("Trò chuyện");
    private final HBox sideTabs = new HBox(6, scoreTab, chatTab);
    private final StackPane sideSlot = new StackPane();
    private Boolean wideLayout;
    private final Button leave = new Button("Rời phòng");
    private final Button logout = new Button("Đăng xuất");
    private final ListView<ScoreEntry> scores = new ListView<>();
    private final ScaleTransition timerPulse = new ScaleTransition(Duration.millis(220), timer);
    private final VBox tools = new VBox(6);
    private boolean drawing;
    private boolean finished;
    private int drawerId = -1;
    // Pha đang hiển thị, để nhãn trên bảng điểm đúng ngữ cảnh (đang vẽ / đang đoán).
    private GamePhase scorePhase;
    private double lastX;
    private double lastY;

    public GameView(Runnable sendGuess, Runnable returnToLobby, Runnable leaveRoom, Runnable signOut) {
        this(0, Map.of(), sendGuess, returnToLobby, leaveRoom, signOut);
    }

    public GameView(int userId, Map<Integer, String> avatars, Runnable sendGuess, Runnable returnToLobby,
                    Runnable leaveRoom, Runnable signOut) {
        this.userId = userId;
        this.avatars = Map.copyOf(avatars);
        UiTheme.apply(this);
        setPadding(new Insets(10));
        category.getStyleClass().add("section-title");
        word.getStyleClass().add("game-word");
        hint.getStyleClass().add("game-hint");
        hint.setVisible(false);
        hint.setManaged(false);
        timer.getStyleClass().add("timer-chip");
        timer.setMinWidth(Region.USE_PREF_SIZE);
        timerPulse.setFromX(1.12);
        timerPulse.setFromY(1.12);
        timerPulse.setToX(1);
        timerPulse.setToY(1);

        // Nút rời trận nằm ở đầu trang, tách xa ô đoán để tránh bấm nhầm.
        back.setOnAction(event -> returnToLobby.run());
        leave.setOnAction(event -> confirmLeave("Rời phòng", Icons.Glyph.LOG_OUT, leaveRoom));
        logout.setOnAction(event -> confirmLeave("Đăng xuất", Icons.Glyph.POWER, signOut));
        back.setVisible(false);
        back.setManaged(false);
        back.getStyleClass().add("ghost-button");
        playAgain.setOnAction(event -> {
            playAgain.setDisable(true);
            onPlayAgain.run();
        });
        playAgain.setVisible(false);
        playAgain.setManaged(false);
        playAgain.getStyleClass().add("primary-button");
        playAgain.setTooltip(new Tooltip("Về phòng chờ và báo sẵn sàng cho ván mới"));
        leave.getStyleClass().add("ghost-button");
        logout.getStyleClass().add("danger-button");
        HBox navigation = new HBox(8, playAgain, back, leave, logout);
        navigation.setAlignment(Pos.CENTER_RIGHT);
        navigation.getChildren().forEach(node -> ((Region) node).setMinWidth(Region.USE_PREF_SIZE));

        // Chủ đề và từ khóa / tên tranh chung một dòng để thanh tiêu đề thấp hơn.
        Label separator = new Label("•");
        separator.getStyleClass().add("section-title");
        HBox titleLine = new HBox(10, category, separator, word);
        titleLine.setAlignment(Pos.BASELINE_LEFT);
        VBox headingText = new VBox(2, titleLine, hint);
        Region headingSpacer = new Region();
        HBox.setHgrow(headingSpacer, Priority.ALWAYS);
        HBox heading = new HBox(14, headingText, headingSpacer, timer, navigation);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().addAll("header-card", "game-header");
        BorderPane.setMargin(heading, new Insets(0, 0, 10, 0));
        setTop(heading);

        clearCanvas();
        picture.setFitWidth(500);
        picture.setFitHeight(500);
        picture.setPreserveRatio(true);
        picture.setVisible(false);
        resultPanel.getStyleClass().add("result-panel");
        resultPanel.setAlignment(Pos.TOP_CENTER);
        resultPanel.setVisible(false);
        // Khung viền và bóng đổ là một lớp riêng nằm dưới canvas và được cache thành ảnh,
        // nên mỗi nét vẽ không bắt GPU tính lại hiệu ứng bóng đổ.
        Region frame = new Region();
        frame.getStyleClass().add("canvas-frame");
        frame.setCache(true);
        StackPane board = new StackPane(frame, canvas, picture, resultPanel);
        board.setMinSize(500, 500);
        board.setMaxSize(500, 500);

        // Hai hàng công cụ để chữ trên nút không bị cắt ở cửa sổ nhỏ.
        Label strokeLabel = new Label("Nét");
        strokeLabel.setMinWidth(Region.USE_PREF_SIZE);
        strokeLabel.setLabelFor(width);
        width.setAccessibleText("Độ dày nét vẽ");
        color.setAccessibleText("Màu bút");
        color.setTooltip(new Tooltip("Màu bút"));
        width.setPrefWidth(160);
        HBox.setHgrow(width, Priority.ALWAYS);
        color.getStyleClass().add("drawing-color");
        HBox brushRow = new HBox(10, color, strokeLabel, width);
        brushRow.setAlignment(Pos.CENTER_LEFT);
        eraser.getStyleClass().add("tool-toggle");
        javafx.scene.Node eraserOn = Icons.icon(Icons.Glyph.CHECK, 14, eraser.textFillProperty());
        eraser.setGraphicTextGap(6);
        eraser.selectedProperty().addListener((observable, wasSelected, selected) -> {
            eraser.setText(selected ? "Đang tẩy" : "Tẩy");
            eraser.setGraphic(selected ? eraserOn : null);
        });
        eraser.setTooltip(new Tooltip("Bật để tẩy, bấm lần nữa để vẽ tiếp"));
        undo.getStyleClass().add("secondary-button");
        undo.setTooltip(new Tooltip("Quay lại nét vẽ trước (Ctrl+Z)"));
        undo.setDisable(true);
        clear.getStyleClass().add("ghost-button");
        HBox actionRow = new HBox(8, eraser, undo, clear);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.getChildren().forEach(node -> ((Region) node).setMinWidth(Region.USE_PREF_SIZE));
        tools.getChildren().addAll(brushRow, actionRow);
        tools.getStyleClass().add("toolbar");
        tools.setMaxWidth(500);
        // Trong phiên đoán, thanh báo cáo thay chỗ thanh công cụ vẽ (không dùng tới khi đoán).
        report.getStyleClass().addAll("ghost-button", "report-button");
        report.setMinWidth(Region.USE_PREF_SIZE);
        report.setTooltip(new Tooltip("Báo cáo khi tranh không liên quan tới từ khóa hoặc không phù hợp. "
                + "Quá nửa người đoán báo cáo thì người vẽ không được điểm vòng này."));
        report.setOnAction(event -> confirmReport());
        reportStatus.getStyleClass().add("report-status");
        reportStatus.setWrapText(true);
        reportStatus.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(reportStatus, Priority.ALWAYS);
        reportBar.getStyleClass().addAll("toolbar", "report-bar");
        reportBar.setAlignment(Pos.CENTER_LEFT);
        reportBar.setMaxWidth(500);
        showReportBar(false);
        VBox left = new VBox(8, board, tools, reportBar);
        left.setAlignment(Pos.TOP_CENTER);

        answer.setPromptText("Nhập đáp án...");
        // Ô đoán không có nhãn hiển thị nên cần tên riêng cho trình đọc màn hình.
        answer.setAccessibleText("Đáp án của bạn");
        guess.setOnAction(event -> sendGuess.run());
        answer.setOnAction(event -> sendGuess.run());
        guess.getStyleClass().add("primary-button");
        guess.setMinWidth(Region.USE_PREF_SIZE);
        guessRow.getStyleClass().add("guess-row");
        HBox.setHgrow(answer, Priority.ALWAYS);
        scores.setPrefHeight(300);
        scores.getStyleClass().add("score-list");
        scores.setCellFactory(list -> new ScoreCell());
        scores.setFocusTraversable(false);
        VBox.setVgrow(scores, Priority.ALWAYS);
        status.setWrapText(true);
        status.setMinHeight(Region.USE_PREF_SIZE);
        UiTheme.status(status, "info");
        scoreTitle.getStyleClass().add("section-title");
        ArtworkCard painting = new ArtworkCard(
                "/images/Wheatfield-with-Crows-1890.jpg", "Wheatfield with Crows • 1890", SIDE_WIDTH - 40, 86);
        ToggleGroup tabs = new ToggleGroup();
        for (ToggleButton tab : List.of(scoreTab, chatTab)) {
            tab.setToggleGroup(tabs);
            tab.getStyleClass().add("side-tab");
            tab.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tab, Priority.ALWAYS);
        }
        scoreTab.setSelected(true);
        // Luôn có một thẻ được chọn; bấm lại thẻ đang chọn không bỏ chọn nó.
        tabs.selectedToggleProperty().addListener((observable, before, selected) -> {
            if (selected == null) { before.setSelected(true); } else { showSideTab(); }
        });
        sideTabs.getStyleClass().add("side-tabs");
        StackPane sideHeader = new StackPane(scoreTitle, sideTabs);
        StackPane.setAlignment(scoreTitle, Pos.CENTER_LEFT);
        VBox.setVgrow(sideSlot, Priority.ALWAYS);
        right = new VBox(12, painting, sideHeader, sideSlot, guessRow, status);
        right.getStyleClass().add("sidebar-card");
        right.setPrefWidth(SIDE_WIDTH);
        right.setMinWidth(SIDE_WIDTH - 30);

        chat = new ChatPanel(userId);
        chat.unreadProperty().addListener((observable, before, count) -> chatTab.setText(
                count.intValue() == 0 ? "Trò chuyện" : "Trò chuyện (" + count + ")"));
        chatPainting = new ArtworkCard("/images/The-Starry-Night-1889.jpg", "The Starry Night • 1889", SIDE_WIDTH - 40, 86);
        VBox.setVgrow(chat, Priority.ALWAYS);
        chatColumn.getStyleClass().add("sidebar-card");
        chatColumn.setPrefWidth(SIDE_WIDTH);
        chatColumn.setMinWidth(SIDE_WIDTH - 30);

        canvasColumn = left;
        center.setAlignment(Pos.TOP_CENTER);
        setCenter(center);
        applyLayout(true);
        widthProperty().addListener((observable, before, now) -> applyLayout(now.doubleValue() >= WIDE_LAYOUT_WIDTH));

        clear.setOnAction(event -> {
            if (!drawing) { return; }
            record(Stroke.CLEAR);
            clearCanvas();
        });
        undo.setOnAction(event -> undo());
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (UNDO_KEY.match(event) && drawing) {
                undo();
                event.consume();
            }
        });
        canvas.setOnMousePressed(event -> {
            if (!drawing) { return; }
            currentStroke = new Stroke(eraser.isSelected() ? Color.WHITE : color.getValue(), width.getValue(),
                    new ArrayList<>(List.of(new double[]{event.getX(), event.getY()})));
            record(currentStroke);
            paintDot(currentStroke, event.getX(), event.getY());
        });
        canvas.setOnMouseDragged(event -> {
            if (!drawing || currentStroke == null) { return; }
            double[] last = currentStroke.points().getLast();
            currentStroke.points().add(new double[]{event.getX(), event.getY()});
            paintSegment(currentStroke, last[0], last[1], event.getX(), event.getY());
        });
        canvas.setOnMouseReleased(event -> currentStroke = null);
    }

    // Cửa sổ rộng: [chat | ô vẽ | bảng điểm]. Cửa sổ hẹp: [ô vẽ | bảng điểm/chat theo thẻ đang chọn].
    private void applyLayout(boolean wide) {
        if (Boolean.valueOf(wide).equals(wideLayout)) { return; }
        wideLayout = wide;
        if (wide) {
            sideSlot.getChildren().setAll(scores);
            chatColumn.getChildren().setAll(chatPainting, chat);
            center.getChildren().setAll(chatColumn, canvasColumn, right);
        } else {
            chatColumn.getChildren().clear();
            sideSlot.getChildren().setAll(scores, chat);
            center.getChildren().setAll(canvasColumn, right);
        }
        chat.setTitleVisible(wide);
        scoreTitle.setVisible(wide);
        sideTabs.setVisible(!wide);
        sideTabs.setManaged(!wide);
        showSideTab();
    }

    private void showSideTab() {
        boolean chatOpen = !wideLayout && chatTab.isSelected();
        scores.setVisible(wideLayout || !chatOpen);
        chat.setVisible(wideLayout || chatOpen);
        chat.setShown(wideLayout || chatOpen);
    }

    public boolean isWideLayout() {
        return Boolean.TRUE.equals(wideLayout);
    }

    public void setOnChat(java.util.function.Consumer<String> send) {
        chat.setOnSend(send);
    }

    public void addChat(ChatMessage message) {
        chat.add(message);
    }

    public void showChatNotice(String text) {
        chat.showNotice(text);
    }

    public ChatPanel getChat() {
        return chat;
    }

    public void setOnPlayAgain(Runnable action) {
        onPlayAgain = action;
    }

    // Sau trận: bao nhiêu người đã bấm "Chơi lại" (đã sẵn sàng trong phòng) và còn chờ ai.
    public void showRematch(RoomInfo room) {
        if (!finished || room.getStatus() == RoomStatus.PLAYING || room.getStatus() == RoomStatus.FINISHED) { return; }
        List<PlayerInfo> players = room.getPlayers();
        long ready = players.stream().filter(PlayerInfo::isReady).count();
        String waiting = players.stream().filter(player -> !player.isReady())
                .map(player -> player.getUserId() == userId ? "bạn" : player.getUsername())
                .reduce((a, b) -> a + ", " + b).orElse("");
        rematch.setText(players.size() < 2 ? "Cần ít nhất 2 người để chơi lại. Hãy mời thêm bạn từ phòng chờ."
                : ready + "/" + players.size() + " người muốn chơi lại" + (waiting.isEmpty() ? "" : " • Đang chờ: " + waiting));
        rematch.setVisible(true);
        rematch.setManaged(true);
    }

    private void confirmLeave(String action, Icons.Glyph glyph, Runnable confirmed) {
        if (finished || ThemedDialog.confirm(this, ThemedDialog.Tone.DANGER, glyph, action + " khi đang chơi?",
                "Trận đấu chưa kết thúc. Nếu " + action.toLowerCase() + " bây giờ, bạn sẽ bỏ dở trận "
                        + "và không thể quay lại.", action, "Ở lại chơi")) {
            confirmed.run();
        }
    }

    private void confirmReport() {
        if (ThemedDialog.confirm(this, ThemedDialog.Tone.WARNING, Icons.Glyph.FLAG,
                "Báo cáo tranh của " + drawerName + "?",
                "Chỉ báo cáo khi tranh không liên quan tới từ khóa hoặc có nội dung không phù hợp. "
                        + "Nếu quá nửa người đoán cùng báo cáo, người vẽ sẽ không được điểm ở vòng này.",
                "Báo cáo", "Hủy")) {
            report.setDisable(true);
            onReport.run();
        }
    }

    private void showReportBar(boolean guessing) {
        tools.setVisible(!guessing);
        tools.setManaged(!guessing);
        reportBar.setVisible(guessing);
        reportBar.setManaged(guessing);
    }

    public void setOnReport(Runnable action) {
        onReport = action;
    }

    // Đã gửi báo cáo thành công (hoặc server cho biết đã báo cáo trước đó).
    public void markReported() {
        report.setText("Đã báo cáo");
        report.setDisable(true);
    }

    public void showReportStatus(ReportStatus status) {
        reportStatus.getStyleClass().remove("report-rejected");
        if (status.isRejected()) {
            reportStatus.setText("Tranh đã bị báo cáo: " + drawerName + " không được điểm ở vòng này.");
            reportStatus.getStyleClass().add("report-rejected");
            report.setDisable(true);
        } else {
            reportStatus.setText("Báo cáo: " + status.getReports() + "/" + status.getNeeded()
                    + " (cần quá nửa người đoán)");
        }
    }

    // Một nét vẽ (màu, độ dày, các điểm); CLEAR đánh dấu lần bấm "Xóa tranh".
    private record Stroke(Color color, double size, List<double[]> points) {
        static final Stroke CLEAR = new Stroke(Color.WHITE, 0, List.of());
    }

    private GraphicsContext brush(Stroke stroke) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setStroke(stroke.color());
        graphics.setFill(stroke.color());
        graphics.setLineWidth(stroke.size());
        graphics.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        return graphics;
    }

    private void paintDot(Stroke stroke, double x, double y) {
        brush(stroke).fillOval(x - stroke.size() / 2, y - stroke.size() / 2, stroke.size(), stroke.size());
    }

    private void paintSegment(Stroke stroke, double fromX, double fromY, double toX, double toY) {
        brush(stroke).strokeLine(fromX, fromY, toX, toY);
    }

    private void clearCanvas() {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.WHITE);
        graphics.fillRect(0, 0, 500, 500);
    }

    private void record(Stroke stroke) {
        changed = true;
        strokes.add(stroke);
        undoableSteps = Math.min(undoableSteps + 1, UNDO_LIMIT);
        undo.setDisable(!drawing);
    }

    private void undo() {
        if (!drawing || undoableSteps == 0) { return; }
        strokes.removeLast();
        undoableSteps--;
        changed = true;
        currentStroke = null;
        redraw();
        undo.setDisable(undoableSteps == 0);
    }

    // Vẽ lại từ lần "Xóa tranh" gần nhất còn lại trong lịch sử.
    private void redraw() {
        clearCanvas();
        int start = strokes.lastIndexOf(Stroke.CLEAR) + 1;
        if (start == 0 && base != null) { canvas.getGraphicsContext2D().drawImage(base, 0, 0, 500, 500); }
        for (Stroke stroke : strokes.subList(start, strokes.size())) {
            double[] first = stroke.points().getFirst();
            paintDot(stroke, first[0], first[1]);
            for (int i = 1; i < stroke.points().size(); i++) {
                double[] from = stroke.points().get(i - 1), to = stroke.points().get(i);
                paintSegment(stroke, from[0], from[1], to[0], to[1]);
            }
        }
    }

    // Trận một vòng: "Chủ đề: X"; nhiều vòng: "Vòng 2/3: X".
    private static String topicTitle(String topic, int stage, int stageCount) {
        return stageCount > 1 ? "Vòng " + (stage + 1) + "/" + stageCount + ": " + topic : "Chủ đề: " + topic;
    }

    // Bắt đầu (hoặc sang vòng mới): ô vẽ trắng, bỏ tranh đang đoán của vòng trước.
    public void showDrawing(DrawingPrompt prompt) {
        category.setText(topicTitle(prompt.getCategory(), prompt.getStage(), prompt.getStageCount()));
        word.setText("Từ khóa của bạn: " + prompt.getWord());
        timer.setText("Vẽ: " + prompt.getSeconds() + " giây");
        timer.getStyleClass().remove("timer-urgent");
        status.setText(prompt.getStage() > 0
                ? "Vòng " + (prompt.getStage() + 1) + " bắt đầu! Vẽ từ khóa mới của bạn. Tranh sẽ được gửi khi hết giờ."
                : "Vẽ từ khóa của bạn. Tranh sẽ được gửi khi hết giờ.");
        picture.setVisible(false);
        picture.setImage(null);
        canvas.setVisible(true);
        base = null;
        strokes.clear();
        currentStroke = null;
        undoableSteps = 0;
        changed = false;
        clearCanvas();
        drawerId = -1;
        showHint(null, false);
        scorePhase = GamePhase.DRAWING;
        scores.refresh();
        UiTheme.status(status, "info");
        showReportBar(false);
        setDrawing(true);
        lockGuess();
    }

    public void setDrawing(boolean enabled) {
        drawing = enabled;
        color.setDisable(!enabled);
        width.setDisable(!enabled);
        eraser.setDisable(!enabled);
        clear.setDisable(!enabled);
        undo.setDisable(!enabled || undoableSteps == 0);
    }

    // Dựng lại tranh đang vẽ dở từ ảnh PNG server gửi về; các nét vẽ tiếp theo nằm trên ảnh này.
    public void restoreDrawing(byte[] png) {
        Image image = new Image(new ByteArrayInputStream(png));
        if (image.isError() || image.getWidth() != 500 || image.getHeight() != 500) { return; }
        base = image;
        strokes.clear();
        currentStroke = null;
        undoableSteps = 0;
        undo.setDisable(true);
        redraw();
    }

    // Trả về true nếu tranh đã đổi kể từ lần gọi trước (để chỉ gửi bản nháp khi có nét mới).
    public boolean takeChanges() {
        boolean result = changed;
        changed = false;
        return result;
    }

    public int[] captureDrawing() {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.WHITE);
        WritableImage image = canvas.snapshot(parameters, null);
        int[] pixels = new int[500 * 500];
        image.getPixelReader().getPixels(0, 0, 500, 500, PixelFormat.getIntArgbInstance(), pixels, 0, 500);
        return pixels;
    }

    public void showRound(RoundInfo round, int userId) {
        setDrawing(false);
        canvas.setVisible(false);
        picture.setVisible(true);
        picture.setImage(new Image(new ByteArrayInputStream(round.getImage())));
        drawerId = round.getDrawerId();
        scorePhase = GamePhase.GUESSING;
        scores.refresh();
        // Lượt đếm trong vòng hiện tại (mỗi vòng ai cũng có một tranh).
        int perStage = Math.max(1, round.getTotalRounds() / round.getStageCount());
        category.setText(topicTitle(round.getCategory(), round.getStage(), round.getStageCount()));
        word.setText("Tranh của " + round.getDrawerName() + " — lượt "
                + (round.getRoundIndex() % perStage + 1) + "/" + perStage);
        showHint(round.getHint(), false);
        answer.clear();
        boolean owner = round.getDrawerId() == userId;
        drawerName = round.getDrawerName();
        showReportBar(true);
        report.setText("Báo cáo tranh");
        report.setDisable(owner);
        report.setVisible(!owner);
        report.setManaged(!owner);
        reportStatus.getStyleClass().remove("report-rejected");
        reportStatus.setText(owner ? "Người đoán có thể báo cáo nếu tranh không đúng từ khóa."
                : "Tranh không đúng từ khóa? Bạn có thể báo cáo.");
        answer.setDisable(owner);
        guess.setDisable(owner);
        status.setText(owner ? "Bạn không được đoán tranh của chính mình." : "Bạn có tối đa 3 lần đoán.");
        UiTheme.status(status, "info");
        timer.setText("Đoán: " + round.getSeconds() + " giây");
    }

    public void showHint(String mask, boolean letterRevealed) {
        boolean visible = mask != null && !mask.isBlank();
        hint.setVisible(visible);
        hint.setManaged(visible);
        if (visible) { hint.setText("Gợi ý: " + mask + (letterRevealed ? "   (đã mở 1 chữ)" : "")); }
    }

    public void showAnswer(String answerWord) {
        hint.setVisible(true);
        hint.setManaged(true);
        hint.setText("Đáp án: " + answerWord);
    }

    public String getAnswer() { return answer.getText(); }
    public void setGuessPending() { answer.setDisable(true); guess.setDisable(true); }
    public void lockGuess() { setGuessPending(); }

    public void showGuessResult(GuessResult result) {
        answer.clear();
        answer.setDisable(result.isLocked());
        guess.setDisable(result.isLocked());
        String message = switch (result.getReason()) {
            case "CORRECT" -> result.getPoints() > 0 ? "Đúng rồi! +" + result.getPoints() + " điểm" : "Đúng rồi!";
            case "WRONG_ANSWER" -> "Chưa đúng. Còn " + result.getAttemptsLeft() + " lần đoán.";
            case "INVALID_ANSWER" -> "Đáp án phải có từ 1 đến 100 ký tự.";
            case "OWN_DRAWING" -> "Bạn không được đoán tranh của mình.";
            default -> "Lượt đoán đã kết thúc hoặc bạn đã hết lượt đoán.";
        };
        status.setText(message);
        UiTheme.status(status, "CORRECT".equals(result.getReason()) ? "success"
                : result.isLocked() ? "error" : "info");
        if ("CORRECT".equals(result.getReason())) { flashGuess("guess-row-correct", false); }
        else if ("WRONG_ANSWER".equals(result.getReason())) { flashGuess("guess-row-wrong", true); }
        if (!result.isLocked()) { answer.requestFocus(); }
    }

    // Ô đoán nháy xanh khi đúng, nháy đỏ và rung nhẹ khi sai.
    private void flashGuess(String styleClass, boolean shake) {
        guessRow.getStyleClass().removeAll("guess-row-correct", "guess-row-wrong");
        guessRow.getStyleClass().add(styleClass);
        PauseTransition restore = new PauseTransition(Duration.millis(900));
        restore.setOnFinished(event -> guessRow.getStyleClass().remove(styleClass));
        restore.play();
        if (shake) {
            TranslateTransition wiggle = new TranslateTransition(Duration.millis(50), guessRow);
            wiggle.setFromX(0);
            wiggle.setByX(6);
            wiggle.setCycleCount(6);
            wiggle.setAutoReverse(true);
            wiggle.setOnFinished(event -> guessRow.setTranslateX(0));
            wiggle.play();
        }
    }

    public void updateTimer(TimerInfo info) {
        String phase = switch (info.getPhase()) {
            case DRAWING -> "Vẽ";
            case UPLOADING -> "Chờ gửi tranh";
            case GUESSING -> "Đoán";
            case ROUND_END -> "Lượt tiếp theo";
            case FINISHED -> "Kết thúc";
        };
        timer.setText(phase + ": " + info.getSeconds() + " giây");
        boolean urgent = (info.getPhase() == GamePhase.DRAWING || info.getPhase() == GamePhase.GUESSING)
                && info.getSeconds() > 0 && info.getSeconds() <= URGENT_SECONDS;
        timer.getStyleClass().remove("timer-urgent");
        if (urgent) {
            timer.getStyleClass().add("timer-urgent");
            timerPulse.playFromStart();
        }
        if (info.getPhase() != GamePhase.DRAWING || info.getSeconds() == 0) { setDrawing(false); }
        if (info.getPhase() == GamePhase.ROUND_END || info.getPhase() == GamePhase.FINISHED) { lockGuess(); }
    }

    public void updateScores(List<ScoreEntry> players) {
        scores.getItems().setAll(players);
    }

    public void showResult(GameResult result) {
        finished = true;
        setDrawing(false);
        lockGuess();
        drawerId = -1;
        scorePhase = GamePhase.FINISHED;
        updateScores(result.getPlayers());
        timer.getStyleClass().remove("timer-urgent");
        timer.setText("Trận đấu kết thúc");
        status.setText("Kết quả đã được xếp hạng. Bấm Chơi lại để vào ván mới cùng cả phòng.");
        UiTheme.status(status, "success");
        renderPodium(result.getPlayers());
        canvas.setVisible(false);
        picture.setVisible(false);
        resultPanel.setVisible(true);
        tools.setVisible(false);
        reportBar.setVisible(false);
        reportBar.setManaged(false);
        back.setVisible(true);
        back.setManaged(true);
        playAgain.setDisable(false);
        playAgain.setVisible(true);
        playAgain.setManaged(true);
    }

    // Bục 1-2-3 cho ba người đứng đầu, những người còn lại liệt kê bên dưới.
    private void renderPodium(List<ScoreEntry> players) {
        Label title = new Label("Kết quả trận đấu");
        title.getStyleClass().add("result-title");
        String winners = players.stream().filter(p -> p.getRank() == 1).map(ScoreEntry::getUsername)
                .reduce((a, b) -> a + ", " + b).orElse("");
        Label winner = new Label(winners.isEmpty() ? "" : "Người thắng: " + winners);
        winner.getStyleClass().add("result-winner");
        if (!winners.isEmpty()) { Icons.attach(winner, Icons.Glyph.STAR, 16); }
        winner.setWrapText(true);
        HBox podium = new HBox(14);
        podium.setAlignment(Pos.BOTTOM_CENTER);
        int[] order = {1, 0, 2};
        for (int index : order) {
            if (index < players.size()) { podium.getChildren().add(podiumColumn(players.get(index), index)); }
        }
        VBox others = new VBox(6);
        others.setAlignment(Pos.CENTER);
        for (int index = 3; index < players.size(); index++) {
            ScoreEntry player = players.get(index);
            Label row = new Label(player.getRank() + ".  " + player.getUsername() + "  —  " + player.getScore() + " điểm");
            row.getStyleClass().add("result-other");
            others.getChildren().add(row);
        }
        rematch.getStyleClass().add("rematch-status");
        rematch.setWrapText(true);
        rematch.setVisible(false);
        rematch.setManaged(false);
        resultPanel.getChildren().setAll(title, winner, rematch, podium, others);
    }

    private VBox podiumColumn(ScoreEntry player, int place) {
        AvatarView avatar = new AvatarView(avatars.get(player.getUserId()), place == 0 ? 72 : 58);
        Label name = new Label(player.getUsername() + (player.getUserId() == userId ? " (Bạn)" : ""));
        name.getStyleClass().add("podium-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setAlignment(Pos.CENTER);
        Label points = new Label(player.getScore() + " điểm");
        points.getStyleClass().add("podium-score");
        Label rank = new Label(String.valueOf(player.getRank()));
        rank.getStyleClass().add("podium-rank");
        StackPane step = new StackPane(rank);
        step.getStyleClass().addAll("podium-step", "podium-step-" + (place + 1));
        step.setPrefSize(130, switch (place) { case 0 -> 130; case 1 -> 96; default -> 72; });
        step.setMaxHeight(Region.USE_PREF_SIZE);
        VBox column = new VBox(6, avatar, name, points, step);
        column.setAlignment(Pos.BOTTOM_CENTER);
        column.setPrefWidth(130);
        return column;
    }

    public void showMessage(String message) {
        status.setText(message);
        UiTheme.status(status, "info");
    }

    public void setLeaving() {
        setDrawing(false);
        lockGuess();
        leave.setDisable(true);
        logout.setDisable(true);
        back.setDisable(true);
        playAgain.setDisable(true);
        status.setText("Đang rời trận đấu...");
        UiTheme.status(status, "info");
    }

    // Một dòng bảng điểm: thứ tự, avatar, tên, trạng thái trong vòng và điểm.
    private class ScoreCell extends ListCell<ScoreEntry> {
        private final Label position = new Label();
        private final AvatarView avatar = new AvatarView(null, 30);
        private final Label name = new Label();
        private final Label badge = new Label();
        private final javafx.scene.Node solvedIcon = Icons.icon(Icons.Glyph.CHECK, 12, badge.textFillProperty());
        private final Label points = new Label();
        private final HBox row;

        ScoreCell() {
            position.getStyleClass().add("score-position");
            name.getStyleClass().add("score-name");
            badge.getStyleClass().add("score-badge");
            points.getStyleClass().add("score-points");
            points.setMinWidth(Region.USE_PREF_SIZE);
            VBox text = new VBox(1, name, badge);
            text.setAlignment(Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row = new HBox(9, position, avatar, text, spacer, points);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("score-row");
            // Ô không tự đòi rộng hơn danh sách, tránh hiện thanh cuộn ngang.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ScoreEntry player, boolean empty) {
            super.updateItem(player, empty);
            setText(null);
            if (empty || player == null) {
                setGraphic(null);
                return;
            }
            int place = player.getRank() > 0 ? player.getRank() : getIndex() + 1;
            position.setText(String.valueOf(place));
            avatar.setAvatar(avatars.get(player.getUserId()));
            boolean self = player.getUserId() == userId;
            name.setText(player.getUsername() + (self ? "  • Bạn" : ""));
            String state;
            String style;
            if (!player.isOnline()) {
                state = "Mất kết nối"; style = "score-badge-offline";
            } else if (scorePhase == GamePhase.DRAWING) {
                state = "Đang vẽ"; style = "score-badge-drawing";
            } else if (scorePhase == GamePhase.GUESSING && player.getUserId() == drawerId) {
                // Tác giả của bức tranh đang được đoán, không phải người đang vẽ.
                state = "Người vẽ"; style = "score-badge-drawer";
            } else if (scorePhase == GamePhase.GUESSING && player.isSolvedRound()) {
                state = "Đoán đúng"; style = "score-badge-solved";
            } else if (scorePhase == GamePhase.GUESSING) {
                state = "Đang đoán"; style = "score-badge-guessing";
            } else {
                state = ""; style = null;
            }
            badge.setText(state);
            badge.setGraphic("score-badge-solved".equals(style) ? solvedIcon : null);
            badge.setVisible(!state.isEmpty());
            badge.setManaged(!state.isEmpty());
            badge.getStyleClass().removeAll("score-badge-solved", "score-badge-drawer", "score-badge-offline",
                    "score-badge-drawing", "score-badge-guessing");
            if (style != null) { badge.getStyleClass().add(style); }
            points.setText(player.getScore() + " điểm");
            row.getStyleClass().remove("score-row-self");
            if (self) { row.getStyleClass().add("score-row-self"); }
            setGraphic(row);
        }
    }
}
