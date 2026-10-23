package com.nhom15.drawguess.client.view;

import com.nhom15.drawguess.common.protocol.*;
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

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class GameView extends BorderPane {
    private static final int UNDO_LIMIT = 20;
    private static final KeyCombination UNDO_KEY = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private final Canvas canvas = new Canvas(500, 500);
    private final ImageView picture = new ImageView();
    private final Label category = new Label();
    private final Label word = new Label();
    private final Label hint = new Label();
    private final Label timer = new Label();
    private final Label status = new Label();
    private final ColorPicker color = new ColorPicker(Color.BLACK);
    private final Slider width = new Slider(2, 24, 5);
    private final ToggleButton eraser = new ToggleButton("Tẩy");
    private final Button clear = new Button("Xóa tranh");
    private final Button undo = new Button("↶ Hoàn tác");
    // Ảnh canvas trước mỗi nét vẽ hoặc lần xóa, để người chơi quay lại khi vẽ nhầm.
    private final Deque<WritableImage> undoStack = new ArrayDeque<>();
    private final TextField answer = new TextField();
    private final Button guess = new Button("Đoán");
    private final Button back = new Button("Về phòng chờ");
    private final Button leave = new Button("Rời phòng");
    private final Button logout = new Button("Đăng xuất");
    private final ListView<String> scores = new ListView<>();
    private boolean drawing;
    private double lastX;
    private double lastY;

    public GameView(Runnable sendGuess, Runnable returnToLobby, Runnable leaveRoom, Runnable signOut) {
        UiTheme.apply(this);
        setPadding(new Insets(18));
        category.getStyleClass().add("section-title");
        word.getStyleClass().add("game-word");
        hint.getStyleClass().add("game-hint");
        hint.setVisible(false);
        hint.setManaged(false);
        timer.getStyleClass().add("timer-chip");
        VBox headingText = new VBox(5, category, word, hint);
        Region headingSpacer = new Region();
        HBox.setHgrow(headingSpacer, Priority.ALWAYS);
        HBox heading = new HBox(14, headingText, headingSpacer, timer);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("header-card");
        BorderPane.setMargin(heading, new Insets(0, 0, 16, 0));
        setTop(heading);
        clearCanvas();
        picture.setFitWidth(500);
        picture.setFitHeight(500);
        picture.setPreserveRatio(true);
        picture.setVisible(false);
        StackPane board = new StackPane(canvas, picture);
        board.setMinSize(500, 500);
        board.setMaxSize(500, 500);
        board.getStyleClass().add("canvas-frame");
        HBox tools = new HBox(8, color, new Label("Nét"), width, eraser, undo, clear);
        tools.setAlignment(Pos.CENTER);
        tools.getStyleClass().add("toolbar");
        width.setPrefWidth(100);
        eraser.getStyleClass().add("secondary-button");
        clear.getStyleClass().add("ghost-button");
        undo.getStyleClass().add("secondary-button");
        undo.setTooltip(new Tooltip("Quay lại nét vẽ trước (Ctrl+Z)"));
        undo.setDisable(true);
        VBox left = new VBox(10, board, tools);
        left.setAlignment(Pos.TOP_CENTER);
        answer.setPromptText("Nhập đáp án (tối đa 3 lần/lượt)");
        guess.setOnAction(event -> sendGuess.run());
        answer.setOnAction(event -> sendGuess.run());
        back.setOnAction(event -> returnToLobby.run());
        leave.setOnAction(event -> leaveRoom.run());
        logout.setOnAction(event -> signOut.run());
        back.setVisible(false);
        back.setManaged(false);
        guess.getStyleClass().add("primary-button");
        back.getStyleClass().add("primary-button");
        leave.getStyleClass().add("ghost-button");
        logout.getStyleClass().add("danger-button");
        scores.setPrefHeight(300);
        VBox.setVgrow(scores, Priority.ALWAYS);
        status.setWrapText(true);
        UiTheme.status(status, "info");
        Label scoreTitle = new Label("Bảng điểm");
        scoreTitle.getStyleClass().add("section-title");
        ArtworkCard painting = new ArtworkCard(
                "/images/Wheatfield-with-Crows-1890.jpg", "Wheatfield with Crows • 1890", 270, 86);
        HBox.setHgrow(answer, Priority.ALWAYS);
        HBox guessRow = new HBox(8, answer, guess);
        HBox navigation = new HBox(8, back, leave, logout);
        VBox right = new VBox(12, painting, scoreTitle, scores, guessRow, status, navigation);
        right.getStyleClass().add("sidebar-card");
        right.setPrefWidth(310);
        HBox center = new HBox(24, left, right);
        center.setAlignment(Pos.TOP_CENTER);
        setCenter(center);
        clear.setOnAction(event -> {
            if (!drawing) { return; }
            saveUndoState();
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
            saveUndoState();
            lastX = event.getX(); lastY = event.getY();
            GraphicsContext graphics = brush();
            double size = width.getValue();
            graphics.fillOval(lastX - size / 2, lastY - size / 2, size, size);
        });
        canvas.setOnMouseDragged(event -> {
            if (!drawing) { return; }
            brush().strokeLine(lastX, lastY, event.getX(), event.getY());
            lastX = event.getX(); lastY = event.getY();
        });
    }

    private GraphicsContext brush() {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Color paint = eraser.isSelected() ? Color.WHITE : color.getValue();
        graphics.setStroke(paint);
        graphics.setFill(paint);
        graphics.setLineWidth(width.getValue());
        graphics.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        return graphics;
    }

    private void clearCanvas() {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setFill(Color.WHITE);
        graphics.fillRect(0, 0, 500, 500);
    }

    private void saveUndoState() {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.WHITE);
        undoStack.push(canvas.snapshot(parameters, null));
        if (undoStack.size() > UNDO_LIMIT) { undoStack.removeLast(); }
        undo.setDisable(!drawing);
    }

    private void undo() {
        if (!drawing || undoStack.isEmpty()) { return; }
        canvas.getGraphicsContext2D().drawImage(undoStack.pop(), 0, 0, 500, 500);
        undo.setDisable(undoStack.isEmpty());
    }

    public void showDrawing(DrawingPrompt prompt) {
        category.setText("Chủ đề: " + prompt.getCategory());
        word.setText("Từ khóa của bạn: " + prompt.getWord());
        timer.setText("Vẽ: " + prompt.getSeconds() + " giây");
        status.setText("Vẽ từ khóa của bạn. Tranh sẽ được gửi khi hết giờ.");
        UiTheme.status(status, "info");
        setDrawing(true);
        lockGuess();
    }

    public void setDrawing(boolean enabled) {
        drawing = enabled;
        color.setDisable(!enabled);
        width.setDisable(!enabled);
        eraser.setDisable(!enabled);
        clear.setDisable(!enabled);
        undo.setDisable(!enabled || undoStack.isEmpty());
    }

    public int[] captureDrawing() {
        // Viền được vẽ bên trong canvas nên ảnh xuất vẫn đúng 500x500.
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setStroke(Color.LIGHTGRAY);
        graphics.setLineWidth(1);
        graphics.strokeRect(0.5, 0.5, 499, 499);
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
        word.setText("Tranh của " + round.getDrawerName() + " — lượt "
                + (round.getRoundIndex() + 1) + "/" + round.getTotalRounds());
        showHint(round.getHint(), false);
        answer.clear();
        boolean owner = round.getDrawerId() == userId;
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
            case "CORRECT" -> "Đúng rồi!";
            case "WRONG_ANSWER" -> "Chưa đúng. Còn " + result.getAttemptsLeft() + " lần đoán.";
            case "INVALID_ANSWER" -> "Đáp án phải có từ 1 đến 100 ký tự.";
            case "OWN_DRAWING" -> "Bạn không được đoán tranh của mình.";
            default -> "Lượt đoán đã kết thúc hoặc bạn đã hết lượt đoán.";
        };
        status.setText(message);
        UiTheme.status(status, "CORRECT".equals(result.getReason()) ? "success"
                : result.isLocked() ? "error" : "info");
        if (!result.isLocked()) { answer.requestFocus(); }
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
        if (info.getPhase() != GamePhase.DRAWING || info.getSeconds() == 0) { setDrawing(false); }
        if (info.getPhase() == GamePhase.ROUND_END || info.getPhase() == GamePhase.FINISHED) { lockGuess(); }
    }

    public void updateScores(List<ScoreEntry> players) {
        scores.getItems().setAll(players.stream().map(p -> (p.getRank() > 0 ? p.getRank() + ". " : "")
                + p.getUsername() + ": " + p.getScore() + " điểm" + (!p.isOnline() ? " (offline)" : "")).toList());
    }

    public void showResult(GameResult result) {
        setDrawing(false);
        lockGuess();
        updateScores(result.getPlayers());
        timer.setText("Trận đấu kết thúc");
        status.setText("Kết quả đã được xếp hạng. Bạn có thể trở về phòng chờ để chơi tiếp.");
        UiTheme.status(status, "success");
        back.setVisible(true);
        back.setManaged(true);
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
        status.setText("Đang rời trận đấu...");
        UiTheme.status(status, "info");
    }
}
