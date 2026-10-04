package com.nhom15.drawguess.common.protocol;
import java.io.Serializable;
import java.util.List;
public final class HistoryData {
    private HistoryData() {}
    public static String formatTime(Long value) {
        return value==null ? "Chưa có" : java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.Instant.ofEpochMilli(value));
    }
    public static String statusLabel(String status) {
        return switch(status) {
            case "COMPLETED" -> "Hoàn tất";
            case "ABANDONED" -> "Mọi người đã rời trận";
            case "ABORTED" -> "Bị gián đoạn";
            default -> "Đang chơi";
        };
    }
    public record Summary(String id, int roomId, String category, long startedAt, Long endedAt,
                          String status, int score, int rank) implements Serializable {
        @Override public String toString() { return formatTime(startedAt) + " | " + category + " | " + statusLabel(status) + " | Điểm: " + score + " | Hạng: " + rank; }
    }
    public record Guess(int userId, String username, String answer, boolean correct, long at, double elapsedSeconds) implements Serializable {}
    public record Round(int index, int drawerId, String drawer, String word, String imagePath,
                        Long startedAt, Long endedAt, List<Guess> guesses) implements Serializable {}
    public record Detail(String id, int roomId, String category, long startedAt, Long endedAt,
                         String status, List<ScoreEntry> players, List<Round> rounds) implements Serializable {}
    public record ImageRequest(String matchId, int roundIndex) implements Serializable {}
    public record DrawingImage(String matchId, int roundIndex, byte[] png) implements Serializable {}
    public record ImageFailure(String matchId, int roundIndex, String reason) implements Serializable {}
}
