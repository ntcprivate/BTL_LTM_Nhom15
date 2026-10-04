package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.view.GameView;
import com.nhom15.drawguess.common.protocol.*;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class GameController {
    private final int userId;
    private final Consumer<Message> send;
    private final GameView view;
    private final String matchId;
    private int round = -1;
    private boolean uploadRequested;
    private volatile boolean closed;

    public GameController(StackPane root, int userId, DrawingPrompt prompt, Consumer<Message> send,
                          Runnable back, Runnable leave, Runnable logout) {
        this.userId = userId;
        this.send = send;
        matchId = prompt.getMatchId();
        view = new GameView(this::guess, back, leave, logout);
        view.showDrawing(prompt);
        root.getChildren().setAll(view);
    }

    private void guess() {
        String answer = view.getAnswer();
        if (answer.isBlank() || answer.length() > 100) {
            view.showMessage("Đáp án phải có từ 1 đến 100 ký tự.");
            return;
        }
        view.setGuessPending();
        send.accept(new Message(MessageType.GUESS, new GuessRequest(matchId, round, answer)));
    }

    private void upload() {
        if (uploadRequested || closed) { return; }
        uploadRequested = true;
        view.setDrawing(false);
        int[] pixels = view.captureDrawing();
        view.showMessage("Đang gửi tranh...");
        Thread encoder = new Thread(() -> {
            try {
                BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, 500, 500, pixels, 0, 500);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output) || output.size() >= 200 * 1024) {
                    throw new IOException("PNG_TOO_LARGE");
                }
                if (!closed) {
                    send.accept(new Message(MessageType.DRAW_UPLOAD, new DrawUpload(matchId, output.toByteArray())));
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    if (!closed) { view.showMessage("Không gửi được tranh. Server sẽ dùng tranh trắng nếu hết hạn chờ."); }
                });
            }
        }, "Drawing-PNG-Encoder");
        encoder.setDaemon(true);
        encoder.start();
    }

    public void handleMessage(Message message) {
        if (closed) { return; }
        switch (message.getType()) {
            case HISTORY_FAILED -> view.showMessage("Không lưu được lịch sử trận. Kiểm tra MySQL và history.sql trên server.");
            case SUBMIT_DRAW -> { if (matchId.equals(message.getData())) { upload(); } }
            case TIME_UPDATE -> {
                if (message.getData() instanceof TimerInfo timer && matchId.equals(timer.getMatchId())) { view.updateTimer(timer); }
            }
            case ROUND_START -> {
                if (message.getData() instanceof RoundInfo info && matchId.equals(info.getMatchId())) {
                    round = info.getRoundIndex();
                    view.showRound(info, userId);
                }
            }
            case GUESS_RESULT -> {
                if (message.getData() instanceof GuessResult result && matchId.equals(result.getMatchId())
                        && result.getRoundIndex() == round) { view.showGuessResult(result); }
            }
            case SCORE_UPDATE -> {
                if (message.getData() instanceof List<?> scores && scores.stream().allMatch(ScoreEntry.class::isInstance)) {
                    view.updateScores(scores.stream().map(ScoreEntry.class::cast).toList());
                }
            }
            case TIME_UP, GAME_END -> view.lockGuess();
            case ANSWER -> {
                if (message.getData() instanceof RoundAnswer answer && matchId.equals(answer.getMatchId())
                        && answer.getRoundIndex() == round) { view.showMessage("Đáp án: " + answer.getWord()); }
            }
            case GAME_RESULT -> {
                if (message.getData() instanceof GameResult result && matchId.equals(result.getMatchId())) { view.showResult(result); }
            }
            case ACTION_SUCCESS -> {
                if ("DRAW_UPLOAD".equals(message.getData())) { view.showMessage("Đã gửi tranh. Đang chờ các người chơi khác."); }
            }
            case ACTION_FAILED, GAME_ERROR -> view.showMessage(errorMessage(String.valueOf(message.getData())));
            default -> { }
        }
    }

    public static String errorMessage(String reason) {
        return switch (reason) {
            case "GAME_DATABASE_ERROR" -> "Không tải được chủ đề và từ khóa. Hãy kiểm tra dữ liệu game trên server.";
            case "NOT_ENOUGH_WORDS" -> "Chưa có chủ đề đủ từ khóa cho số người chơi.";
            case "INVALID_PNG" -> "Ảnh phải là PNG 500×500 và nhỏ hơn 200 KB.";
            case "UPLOAD_NOT_ACTIVE" -> "Đã hết thời gian nhận tranh.";
            case "HISTORY_STORAGE_ERROR" -> "Không lưu được lịch sử. Hãy chạy history.sql và kiểm tra MySQL.";
            case "DRAWING_STORAGE_ERROR" -> "Server không lưu được tranh. Trận đấu phải kết thúc.";
            case "ALREADY_UPLOADED" -> "Tranh của bạn đã được gửi.";
            default -> "Không thực hiện được yêu cầu trong trận đấu.";
        };
    }

    public void setLeaving() { view.setLeaving(); }
    public void close() { closed = true; }
}
