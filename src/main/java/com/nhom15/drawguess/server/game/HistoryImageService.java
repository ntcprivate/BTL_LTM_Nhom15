package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.HistoryDAO;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

public class HistoryImageService {
    private final HistoryDAO history;
    private final DrawingStore drawings;

    public HistoryImageService() {
        this(new HistoryDAO(), new DrawingStore(Path.of("server-data", "drawings")));
    }

    public HistoryImageService(HistoryDAO history, DrawingStore drawings) {
        this.history = history;
        this.drawings = drawings;
    }

    public Message getImage(int userId, Object payload) {
        if (!(payload instanceof HistoryData.ImageRequest request)) {
            return failed(null, -1, "INVALID_REQUEST");
        }
        String id = request.matchId();
        int round = request.roundIndex();
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id) || round < 0 || round >= RoomManager.MAX_PLAYERS * RoomInfo.MAX_STAGES) {
                return failed(id, round, "INVALID_REQUEST");
            }
        } catch (IllegalArgumentException e) { return failed(id, round, "INVALID_REQUEST"); }
        try {
            HistoryDAO.DrawingReference reference = history.drawing(userId, id, round);
            if (reference == null) { return failed(id, round, "NOT_FOUND_OR_UNFINISHED"); }
            if (reference.imagePath() == null || reference.imagePath().isBlank()) {
                return failed(id, round, "IMAGE_NOT_AVAILABLE");
            }
            byte[] png = drawings.load(id, reference.drawerId(), stageOf(reference));
            return new Message(MessageType.HISTORY_IMAGE, new HistoryData.DrawingImage(id, round, png));
        } catch (SQLException e) {
            System.err.println("Lỗi đọc ảnh lịch sử: SQLState=" + e.getSQLState());
            return failed(id, round, "DATABASE_ERROR");
        } catch (IOException e) { return failed(id, round, "IMAGE_NOT_AVAILABLE"); }
    }

    // Tên file "<drawerId>-<vòng>.png" cho biết tranh thuộc vòng nào; tên khác là tranh vòng đầu.
    private static int stageOf(HistoryDAO.DrawingReference reference) {
        String path = reference.imagePath();
        String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(reference.drawerId() + "-(\\d)\\.png").matcher(name);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) - 1 : 0;
    }

    public static Message failed(String id, int round, String reason) {
        return new Message(MessageType.HISTORY_IMAGE_FAILED, new HistoryData.ImageFailure(id, round, reason));
    }
}
