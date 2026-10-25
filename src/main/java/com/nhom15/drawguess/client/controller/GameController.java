package com.nhom15.drawguess.client.controller;

import com.nhom15.drawguess.client.view.GameView;
import com.nhom15.drawguess.client.view.UiTheme;
import com.nhom15.drawguess.common.protocol.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GameController {
    // Cứ 2 giây gửi bản nháp tranh một lần (nếu có nét mới), để mất kết nối vẫn giữ được tranh.
    private static final double DRAFT_SECONDS = 2;
    private final int userId;
    private final Consumer<Message> send;
    private final GameView view;
    private final String matchId;
    private int round = -1;
    private boolean uploadRequested;
    private volatile boolean closed;
    private final Timeline drafts = new Timeline(new KeyFrame(Duration.seconds(DRAFT_SECONDS), event -> sendDraft()));

    public GameController(StackPane root, int userId, DrawingPrompt prompt, Map<Integer, String> avatars,
                          Consumer<Message> send, Runnable back, Runnable leave, Runnable logout) {
        this.userId = userId;
        this.send = send;
        matchId = prompt.getMatchId();
        view = new GameView(userId, avatars, this::guess, back, leave, logout);
        view.setOnReport(() -> send.accept(new Message(MessageType.REPORT_DRAWING, new ReportRequest(matchId, round))));
        view.setOnChat(text -> send.accept(new Message(MessageType.CHAT_SEND, text)));
        view.showDrawing(prompt);
        UiTheme.show(root, view);
        drafts.setCycleCount(Animation.INDEFINITE);
        drafts.play();
    }

    public void showMessage(String message) {
        view.showMessage(message);
    }

    // Server gửi từ khóa mới của cùng trận: sang vòng (chủ đề) tiếp theo trên chính màn hình này.
    public boolean isFor(String id) {
        return !closed && matchId.equals(id);
    }

    public void nextStage(DrawingPrompt prompt) {
        uploadRequested = false;
        round = -1;
        view.showDrawing(prompt);
        drafts.playFromStart();
    }

    // Tin nhắn ở phòng chờ trước khi vào trận.
    public void loadChat(List<ChatMessage> history) {
        view.getChat().addHistory(history);
    }

    public void setOnPlayAgain(Runnable action) {
        view.setOnPlayAgain(action);
    }

    public void showRematch(RoomInfo room) {
        if (!closed) { view.showRematch(room); }
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

    private void sendDraft() {
        if (uploadRequested || closed || !view.takeChanges()) { return; }
        int[] pixels = view.captureDrawing();
        Thread encoder = new Thread(() -> {
            try {
                byte[] png = encode(pixels);
                if (!closed && !uploadRequested) { send.accept(new Message(MessageType.DRAW_DRAFT, new DrawUpload(matchId, png))); }
            } catch (IOException ignored) {
                // Bản nháp chỉ để dự phòng; lần sau có nét mới sẽ gửi lại.
            }
        }, "Drawing-Draft-Encoder");
        encoder.setDaemon(true);
        encoder.start();
    }

    // Viền xám nhạt bao quanh tranh, ảnh luôn đúng 500x500.
    private static byte[] encode(int[] pixels) throws IOException {
        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 500, 500, pixels, 0, 500);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(0xd3d3d3));
        graphics.drawRect(0, 0, 499, 499);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output) || output.size() >= 200 * 1024) {
            throw new IOException("PNG_TOO_LARGE");
        }
        return output.toByteArray();
    }

    private void upload() {
        if (uploadRequested || closed) { return; }
        uploadRequested = true;
        drafts.stop();
        view.setDrawing(false);
        int[] pixels = view.captureDrawing();
        view.showMessage("Đang gửi tranh...");
        Thread encoder = new Thread(() -> {
            try {
                byte[] png = encode(pixels);
                if (!closed) {
                    send.accept(new Message(MessageType.DRAW_UPLOAD, new DrawUpload(matchId, png)));
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
            case DRAW_DRAFT -> {
                if (message.getData() instanceof DrawUpload draft && matchId.equals(draft.getMatchId()) && !uploadRequested) {
                    view.restoreDrawing(draft.getImage());
                }
            }
            case TIME_UPDATE -> {
                if (message.getData() instanceof TimerInfo timer && matchId.equals(timer.getMatchId())) { view.updateTimer(timer); }
            }
            case ROUND_START -> {
                if (message.getData() instanceof RoundInfo info && matchId.equals(info.getMatchId())) {
                    drafts.stop();
                    round = info.getRoundIndex();
                    view.showRound(info, userId);
                }
            }
            case HINT -> {
                if (message.getData() instanceof RoundHint hint && matchId.equals(hint.getMatchId())
                        && hint.getRoundIndex() == round) { view.showHint(hint.getHint(), true); }
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
                        && answer.getRoundIndex() == round) {
                    view.showAnswer(answer.getWord());
                    view.showMessage("Đáp án: " + answer.getWord());
                }
            }
            case REPORT_STATUS -> {
                if (message.getData() instanceof ReportStatus status && matchId.equals(status.getMatchId())
                        && status.getRoundIndex() == round) { view.showReportStatus(status); }
            }
            case GAME_RESULT -> {
                if (message.getData() instanceof GameResult result && matchId.equals(result.getMatchId())) { view.showResult(result); }
            }
            case ACTION_SUCCESS -> {
                if ("DRAW_UPLOAD".equals(message.getData())) { view.showMessage("Đã gửi tranh. Đang chờ các người chơi khác."); }
                if ("REPORT_DRAWING".equals(message.getData())) { view.markReported(); }
            }
            case CHAT_MESSAGE -> {
                if (message.getData() instanceof ChatMessage chat) { view.addChat(chat); }
            }
            case ACTION_FAILED, GAME_ERROR -> {
                String chatError = chatError(String.valueOf(message.getData()));
                if (chatError != null) {
                    view.showChatNotice(chatError);
                    return;
                }
                if ("ALREADY_REPORTED".equals(message.getData())) { view.markReported(); }
                view.showMessage(errorMessage(String.valueOf(message.getData())));
            }
            default -> { }
        }
    }

    public static String chatError(String reason) {
        return switch (reason) {
            case "CHAT_HAS_ANSWER" -> "Tin nhắn có chứa từ khóa đang được đoán nên không gửi được.";
            case "CHAT_TOO_FAST" -> "Bạn nhắn hơi nhanh, chờ một chút rồi gửi tiếp nhé.";
            case "INVALID_CHAT" -> "Tin nhắn cần từ 1 đến " + com.nhom15.drawguess.client.view.ChatPanel.MAX_LENGTH + " ký tự.";
            default -> null;
        };
    }

    public static String errorMessage(String reason) {
        return switch (reason) {
            case "GAME_DATABASE_ERROR" -> "Không tải được chủ đề và từ khóa. Hãy kiểm tra dữ liệu game trên server.";
            case "NOT_ENOUGH_WORDS" -> "Chưa có chủ đề đủ từ khóa cho số người chơi.";
            case "TOPIC_UNAVAILABLE" -> "Có chủ đề trong vòng đấu không còn hoặc không đủ từ khóa cho số người chơi. "
                    + "Chủ phòng hãy chọn chủ đề khác.";
            case "INVALID_PNG" -> "Ảnh phải là PNG 500×500 và nhỏ hơn 200 KB.";
            case "UPLOAD_NOT_ACTIVE" -> "Đã hết thời gian nhận tranh.";
            case "HISTORY_STORAGE_ERROR" -> "Không lưu được lịch sử. Hãy chạy history.sql và kiểm tra MySQL.";
            case "DRAWING_STORAGE_ERROR" -> "Server không lưu được tranh. Trận đấu phải kết thúc.";
            case "ALREADY_UPLOADED" -> "Tranh của bạn đã được gửi.";
            case "REPORT_NOT_ACTIVE" -> "Vòng này đã kết thúc nên không báo cáo được nữa.";
            case "ALREADY_REPORTED" -> "Bạn đã báo cáo tranh này rồi.";
            case "OWN_DRAWING" -> "Bạn không thể báo cáo tranh của chính mình.";
            default -> "Không thực hiện được yêu cầu trong trận đấu.";
        };
    }

    public void setLeaving() { view.setLeaving(); }
    public void close() {
        closed = true;
        drafts.stop();
    }
}
