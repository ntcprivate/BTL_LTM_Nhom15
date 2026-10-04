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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.ByteArrayInputStream;
import java.util.List;

public class GameView extends BorderPane {
    private final Canvas canvas = new Canvas(500, 500);
    private final ImageView picture = new ImageView();
    private final Label category = new Label();
    private final Label word = new Label();
    private final Label timer = new Label();
    private final Label status = new Label();
    private final ColorPicker color = new ColorPicker(Color.BLACK);
    private final Slider width = new Slider(2, 24, 5);
    private final ToggleButton eraser = new ToggleButton("Tẩy");
    private final Button clear = new Button("Xóa tranh");
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
        setPadding(new Insets(20));
        VBox heading = new VBox(8, category, word, timer);
        heading.setPadding(new Insets(0, 0, 12, 0));
        setTop(heading);
        clearCanvas();
        picture.setFitWidth(500);
        picture.setFitHeight(500);
        picture.setPreserveRatio(true);
        picture.setVisible(false);
        StackPane board = new StackPane(canvas, picture);
        board.setMinSize(500, 500);
        board.setMaxSize(500, 500);
        HBox tools = new HBox(8, color, new Label("Nét"), width, eraser, clear);
        tools.setAlignment(Pos.CENTER);
        width.setPrefWidth(100);
        VBox left = new VBox(10, board, tools);
        answer.setPromptText("Nhập đáp án (tối đa 3 lần/lượt)");
        guess.setOnAction(event -> sendGuess.run());
        answer.setOnAction(event -> sendGuess.run());
        back.setOnAction(event -> returnToLobby.run());
        leave.setOnAction(event -> leaveRoom.run());
        logout.setOnAction(event -> signOut.run());
        back.setVisible(false);
        back.setManaged(false);
        scores.setPrefHeight(270);
        status.setWrapText(true);
        VBox right = new VBox(12, new Label("Bảng điểm"), scores, answer, guess, status, back, leave, logout);
        right.setPrefWidth(280);
        HBox center = new HBox(22, left, right);
        setCenter(center);
        clear.setOnAction(event -> clearCanvas());
        canvas.setOnMousePressed(event -> {
            if (!drawing) { return; }
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

    public void showDrawing(DrawingPrompt prompt) {
        category.setText("Chủ đề: " + prompt.getCategory());
        word.setText("Từ khóa của bạn: " + prompt.getWord());
        timer.setText("Vẽ: " + prompt.getSeconds() + " giây");
        status.setText("Vẽ từ khóa của bạn. Tranh sẽ được gửi khi hết giờ.");
        setDrawing(true);
        lockGuess();
    }

    public void setDrawing(boolean enabled) {
        drawing = enabled;
        color.setDisable(!enabled);
        width.setDisable(!enabled);
        eraser.setDisable(!enabled);
        clear.setDisable(!enabled);
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
        answer.clear();
        boolean owner = round.getDrawerId() == userId;
        answer.setDisable(owner);
        guess.setDisable(owner);
        status.setText(owner ? "Bạn không được đoán tranh của chính mình." : "Bạn có tối đa 3 lần đoán.");
        timer.setText("Đoán: " + round.getSeconds() + " giây");
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
        back.setVisible(true);
        back.setManaged(true);
    }

    public void showMessage(String message) { status.setText(message); }

    public void setLeaving() {
        setDrawing(false);
        lockGuess();
        leave.setDisable(true);
        logout.setDisable(true);
        back.setDisable(true);
        status.setText("Đang rời trận đấu...");
    }
}
