package com.nhom15.drawguess.common.protocol;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
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

    // Một vòng đấu (một chủ đề) của trận: các lượt liên tiếp, mỗi người chơi vẽ một tranh.
    public record Stage(int index, String topic, List<Round> rounds) {
        public String label() {
            return topic == null || topic.isBlank() ? "Vòng " + (index + 1) : "Vòng " + (index + 1) + ": " + topic;
        }
    }

    // Trận lưu các lượt nối tiếp nhau (vòng 1: người 1..n, vòng 2: người 1..n, ...) và tên các chủ đề nối bằng ", ".
    // Không tách được tên chủ đề (tên quá dài bị cắt) thì vòng chỉ có số thứ tự.
    public static List<Stage> stages(Detail detail) {
        int perStage = turnsPerStage(detail);
        List<Round> rounds = detail.rounds().stream().sorted(Comparator.comparingInt(Round::index)).toList();
        int count = rounds.isEmpty() ? 0 : rounds.getLast().index() / perStage + 1;
        String[] topics = detail.category() == null ? new String[0] : detail.category().split(", ");
        List<Stage> result = new ArrayList<>();
        for (int stage = 0; stage < count; stage++) {
            int current = stage;
            List<Round> own = rounds.stream().filter(r -> r.index() / perStage == current).toList();
            if (own.isEmpty()) { continue; }
            String topic = count == 1 ? detail.category() : topics.length == count ? topics[stage] : null;
            result.add(new Stage(stage, topic, own));
        }
        return result;
    }

    // Số thứ tự của lượt trong vòng của nó (từ 1).
    public static int turnNumber(Detail detail, Round round) {
        return round.index() % turnsPerStage(detail) + 1;
    }

    // Mỗi vòng ai cũng vẽ một tranh; không có danh sách người chơi thì coi cả trận là một vòng.
    private static int turnsPerStage(Detail detail) {
        int players = detail.players().isEmpty() ? detail.rounds().size() : detail.players().size();
        return Math.max(1, players);
    }
    public record DrawingImage(String matchId, int roundIndex, byte[] png) implements Serializable {}
    public record ImageFailure(String matchId, int roundIndex, String reason) implements Serializable {}
}
