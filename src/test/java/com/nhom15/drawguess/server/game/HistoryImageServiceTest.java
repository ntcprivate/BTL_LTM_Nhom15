package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.HistoryDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HistoryImageServiceTest {
    @TempDir Path directory;
    final String matchId = UUID.randomUUID().toString();

    private HistoryDAO dao(HistoryDAO.DrawingReference reference) {
        return new HistoryDAO() {
            @Override public DrawingReference drawing(int userId, String id, int round) {
                assertEquals(15, userId);
                assertEquals(matchId, id);
                assertEquals(0, round);
                return reference;
            }
        };
    }
    private void failure(Message reply, String reason) {
        assertEquals(MessageType.HISTORY_IMAGE_FAILED, reply.getType());
        assertEquals(reason, assertInstanceOf(HistoryData.ImageFailure.class, reply.getData()).reason());
    }

    @Test void authorizedImageIsSentAsPngAndRequestIsCorrelated() throws Exception {
        DrawingStore store = new DrawingStore(directory);
        store.save(matchId, 2, store.blank());
        var service = new HistoryImageService(dao(new HistoryDAO.DrawingReference(2, "stored-path")), store);
        Message reply = service.getImage(15, new HistoryData.ImageRequest(matchId, 0));
        assertEquals(MessageType.HISTORY_IMAGE, reply.getType());
        var image = assertInstanceOf(HistoryData.DrawingImage.class, reply.getData());
        assertEquals(matchId, image.matchId()); assertEquals(0, image.roundIndex());
        assertArrayEquals(store.blank(), image.png());
    }
    @Test void unauthorizedOrUnfinishedMatchCannotReadAnExistingFile() throws Exception {
        DrawingStore store = new DrawingStore(directory);
        store.save(matchId, 2, store.blank());
        failure(new HistoryImageService(dao(null), store).getImage(15,
                new HistoryData.ImageRequest(matchId, 0)), "NOT_FOUND_OR_UNFINISHED");
    }
    @Test void missingCorruptAndOversizedImagesReturnRecoverableFailure() throws Exception {
        DrawingStore store = new DrawingStore(directory);
        var service = new HistoryImageService(dao(new HistoryDAO.DrawingReference(2, "stored-path")), store);
        var request = new HistoryData.ImageRequest(matchId, 0);
        failure(service.getImage(15, request), "IMAGE_NOT_AVAILABLE");
        store.save(matchId, 2, new byte[]{1, 2, 3});
        failure(service.getImage(15, request), "IMAGE_NOT_AVAILABLE");
        store.save(matchId, 2, new byte[DrawingStore.MAX_BYTES]);
        failure(service.getImage(15, request), "IMAGE_NOT_AVAILABLE");
    }
    @Test void absentUploadAndDatabaseFailureReturnSeparateReasons() {
        DrawingStore store = new DrawingStore(directory);
        failure(new HistoryImageService(dao(new HistoryDAO.DrawingReference(2, null)), store)
                .getImage(15, new HistoryData.ImageRequest(matchId, 0)), "IMAGE_NOT_AVAILABLE");
        HistoryDAO offline = new HistoryDAO() {
            @Override public DrawingReference drawing(int userId, String id, int round) throws SQLException {
                throw new SQLException("offline", "08001");
            }
        };
        failure(new HistoryImageService(offline, store).getImage(15,
                new HistoryData.ImageRequest(matchId, 0)), "DATABASE_ERROR");
    }
    @Test void malformedIdsPayloadAndRoundNumbersNeverReachDatabase() {
        AtomicInteger calls = new AtomicInteger();
        HistoryDAO history = new HistoryDAO() {
            @Override public DrawingReference drawing(int userId, String id, int round) {
                calls.incrementAndGet(); return null;
            }
        };
        var service = new HistoryImageService(history, new DrawingStore(directory));
        failure(service.getImage(15, "path.png"), "INVALID_REQUEST");
        failure(service.getImage(15, new HistoryData.ImageRequest(null, 0)), "INVALID_REQUEST");
        failure(service.getImage(15, new HistoryData.ImageRequest("../../file", 0)), "INVALID_REQUEST");
        failure(service.getImage(15, new HistoryData.ImageRequest(matchId, -1)), "INVALID_REQUEST");
        failure(service.getImage(15, new HistoryData.ImageRequest(matchId, 8)), "INVALID_REQUEST");
        assertEquals(0, calls.get());
    }
}
