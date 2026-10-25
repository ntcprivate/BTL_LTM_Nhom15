package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.WordDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GameManagerTest {
    @TempDir Path directory;

    @Test
    void historyContainsSnapshotsImagesGuessesAndFinalRanks() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals("IN_PROGRESS", f.saved.getFirst().status());
            assertEquals(4,f.saved.getFirst().players().size());
            f.startGuessing();
            f.guess(2,"sai",1);
            f.guess(2,"Con mèo",2);
            f.guess(1,"Con mèo",2); // Own drawing must not be recorded.
            for (int i=0;i<4;i++) { f.advance(30); f.advance(3); }
            HistoryData.Detail d=f.saved.getLast();
            assertEquals("COMPLETED",d.status()); assertNotNull(d.endedAt());
            assertTrue(d.endedAt()>=d.startedAt()); assertEquals(4,d.rounds().size());
            assertEquals(2,d.rounds().getFirst().guesses().size());
            assertFalse(d.rounds().getFirst().guesses().getFirst().correct());
            assertTrue(d.rounds().getFirst().guesses().getLast().correct());
            assertEquals(2.0,d.rounds().getFirst().guesses().getLast().elapsedSeconds());
            assertEquals("Con mèo",d.rounds().getFirst().word());
            for (HistoryData.Round r:d.rounds()) { assertNotNull(r.startedAt()); assertNotNull(r.endedAt()); assertTrue(Files.exists(Path.of(r.imagePath()))); }
            assertEquals(1,d.players().stream().filter(p->p.getUserId()==2).findFirst().orElseThrow().getRank());
        }
    }

    @Test
    void storageFailureCancelsStartAndReleasesRoom() throws Exception {
        List<Message> replies=new ArrayList<>(); AtomicInteger finished=new AtomicInteger();
        try(GameManager game=new GameManager(new WordDAO(),new DrawingStore(directory),System::nanoTime,false,
                new com.nhom15.drawguess.server.dao.HistoryDAO() {
                    @Override public void save(HistoryData.Detail d) throws java.sql.SQLException { throw new java.sql.SQLException("offline","08001"); }
                })) {
            game.start(new RoomInfo(1,RoomStatus.PLAYING,List.of(new PlayerInfo(1,"p1",true,true))),
                    new GamePlan("category",List.of("word")),Map.of(1,replies::add),new Object(),finished::incrementAndGet);
            assertEquals(1,finished.get());
            assertTrue(replies.stream().anyMatch(m->m.getType()==MessageType.GAME_ERROR && "HISTORY_STORAGE_ERROR".equals(m.getData())));
            assertFalse(replies.stream().anyMatch(m->m.getType()==MessageType.DRAW_WORD));
        }
    }

    @Test
    void abandoningMatchAndShutdownAreRecorded() throws Exception {
        try(Fixture f=new Fixture()) {
            for(int id=1;id<=4;id++) f.game.disconnect(1,id);
            assertEquals("ABANDONED",f.saved.getLast().status()); assertNotNull(f.saved.getLast().endedAt());
        }
        try(Fixture f=new Fixture()) {
            f.game.close(); assertEquals("ABORTED",f.saved.getLast().status()); assertNotNull(f.saved.getLast().endedAt());
        }
    }

    @Test
    void eachPlayerReceivesOnlyTheirOwnWordAndDrawingLastsSixtySeconds() throws Exception {
        try (Fixture f = new Fixture()) {
            List<String> assigned = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                DrawingPrompt prompt = f.prompt(id);
                assigned.add(prompt.getWord());
                assertEquals(60, prompt.getSeconds());
                assertEquals("Động vật", prompt.getCategory());
                assertEquals(1, f.messages.get(id).stream().filter(m -> m.getType() == MessageType.DRAW_WORD).count());
            }
            assertEquals(4, assigned.stream().distinct().count());
            f.advance(59);
            assertFalse(f.messages.get(1).stream().anyMatch(m -> m.getType() == MessageType.SUBMIT_DRAW));
            f.advance(1);
            assertEquals(f.matchId, f.last(1, MessageType.SUBMIT_DRAW).getData());
        }
    }

    @Test
    void uploadsAreRestrictedToUploadPhaseAndValidatedBeforeSaving() throws Exception {
        try (Fixture f = new Fixture()) {
            f.upload(1, f.png);
            assertEquals("UPLOAD_NOT_ACTIVE", f.last(1, MessageType.ACTION_FAILED).getData());
            f.advance(60);
            f.upload(1, new byte[]{1, 2, 3});
            assertEquals("INVALID_PNG", f.last(1, MessageType.ACTION_FAILED).getData());
            f.upload(1, f.png);
            f.upload(1, f.png);
            assertEquals("ALREADY_UPLOADED", f.last(1, MessageType.ACTION_FAILED).getData());
            assertTrue(Files.exists(directory.resolve(f.matchId).resolve("1.png")));
            for (int id = 2; id <= 4; id++) { f.upload(id, f.png); }
            RoundInfo round = f.round();
            assertEquals(1, round.getDrawerId());
            assertEquals(4, round.getTotalRounds());
            assertEquals(30, round.getSeconds());
            assertNotNull(round.getImage());
        }
    }

    @Test
    void roundShowsMaskedHintAndRevealsOneLetterAfterFifteenSeconds() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            assertEquals("_ _ _   _ _ _", f.round().getHint());
            f.advance(14);
            assertTrue(f.messages.get(2).stream().noneMatch(m -> m.getType() == MessageType.HINT));
            f.advance(1);
            RoundHint hint = assertInstanceOf(RoundHint.class, f.last(2, MessageType.HINT).getData());
            assertEquals(f.matchId, hint.getMatchId());
            assertEquals(0, hint.getRoundIndex());
            String symbols = hint.getHint().replace(" ", "");
            assertEquals(6, symbols.length());
            assertEquals(5, symbols.chars().filter(c -> c == '_').count());
            for (int i = 0; i < symbols.length(); i++) {
                if (symbols.charAt(i) != '_') { assertEquals("Conmèo".charAt(i), symbols.charAt(i)); }
            }
            f.advance(5);
            assertEquals(1, f.messages.get(2).stream().filter(m -> m.getType() == MessageType.HINT).count());

            f.advance(10);
            f.advance(3);
            assertEquals(1, f.round().getRoundIndex());
            assertEquals("_ _ _   _ _ _", f.round().getHint());
            f.advance(15);
            assertEquals(1, ((RoundHint) f.last(2, MessageType.HINT).getData()).getRoundIndex());
        }
    }

    @Test
    void scoresMarkPlayersWhoSolvedTheCurrentRoundOnly() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "con mèo", 1);
            assertTrue(f.entry(2).isSolvedRound());
            assertFalse(f.entry(3).isSolvedRound());
            f.advance(30);
            assertTrue(f.entry(2).isSolvedRound(), "Dấu ✓ vẫn hiện trong lúc công bố đáp án");
            f.advance(3);
            assertEquals(1, f.round().getRoundIndex());
            assertFalse(f.entry(2).isSolvedRound(), "Vòng mới phải xóa dấu ✓ của vòng trước");
        }
    }

    @Test
    void uploadTimeoutUsesBlankDrawingsAndContinuesMatch() throws Exception {
        try (Fixture f = new Fixture()) {
            f.advance(60);
            f.upload(1, f.png);
            f.advance(10);
            assertEquals(0, f.round().getRoundIndex());
            for (int id = 1; id <= 4; id++) {
                assertTrue(Files.exists(directory.resolve(f.matchId).resolve(id + ".png")));
            }
        }
    }

    @Test
    void answersPreserveVietnameseAccentsAndScoringLocksCorrectGuesses() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(1, "con mèo", 1);
            assertEquals("OWN_DRAWING", f.guessResult(1).getReason());
            f.guess(2, "con meo", 1);
            assertFalse(f.guessResult(2).isCorrect());
            assertEquals(2, f.guessResult(2).getAttemptsLeft());
            f.guess(2, "  CON MÈO  ", 2);
            assertTrue(f.guessResult(2).isCorrect());
            assertEquals(4, f.score(2));
            assertEquals(1, f.score(1));
            f.guess(3, "con mèo", 3);
            assertEquals(3, f.score(3));
            assertEquals(2, f.score(1));
            f.guess(2, "con mèo", 4);
            assertEquals("GUESS_LOCKED", f.guessResult(2).getReason());
            assertEquals(4, f.score(2));
        }
    }

    @Test
    void onlyThreeWrongAttemptsAreAllowedAndOldRoundCannotScore() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            for (int i = 1; i <= 3; i++) { f.guess(2, "sai", i); }
            assertTrue(f.guessResult(2).isLocked());
            assertEquals(0, f.guessResult(2).getAttemptsLeft());
            f.guess(2, "con mèo", 4);
            assertEquals("GUESS_LOCKED", f.guessResult(2).getReason());
            assertEquals(0, f.score(2));
            f.advance(30);
            f.advance(3);
            f.game.guess(1, 2, new GuessRequest(f.matchId, 0, "con mèo"), f.clock.get(), f.sender(2));
            assertEquals("ROUND_NOT_ACTIVE", f.guessResult(2).getReason());
        }
    }

    @Test
    void guessAtDeadlineIsRejectedEvenBeforeTimerThreadRuns() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            long receivedAt = f.roundStarted + 30_000_000_000L;
            f.game.guess(1, 2, new GuessRequest(f.matchId, 0, "con mèo"), receivedAt, f.sender(2));
            assertEquals("ROUND_NOT_ACTIVE", f.guessResult(2).getReason());
            assertEquals(0, f.score(2));
        }
    }

    @Test
    void firstCorrectUsesReceiveTimeEvenWhenThreadGetsLockLater() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "con mèo", 3);
            f.guess(3, "con mèo", 1);
            assertEquals(3, f.score(2));
            assertEquals(4, f.score(3));
            assertEquals(2, f.score(1));
        }
    }

    @Test
    void offlineDrawerReceivesNoNewPointsAndNoMessages() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.game.disconnect(1, 1);
            int count = f.messages.get(1).size();
            f.guess(2, "con mèo", 1);
            assertEquals(0, f.score(1));
            assertEquals(4, f.score(2), "Đúng trong 10 giây đầu: 3 điểm + 1 thưởng đoán đầu tiên");
            assertEquals(count, f.messages.get(1).size());
        }
    }

    @Test
    void resultsUseScoreThenAverageCorrectTimeAndCompetitionRanks() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "con mèo", 1);
            f.guess(3, "con mèo", 2);
            f.guess(4, "con mèo", 3);
            f.advance(3);
            f.roundStarted = f.clock.get();
            f.guess(1, "con chó", 1);
            f.guess(3, "con chó", 2);
            f.guess(4, "con chó", 3);
            f.advance(3);
            f.advance(30); f.advance(3);
            f.advance(30); f.advance(3);
            GameResult result = assertInstanceOf(GameResult.class, f.last(1, MessageType.GAME_RESULT).getData());
            assertEquals(List.of(1, 1, 3, 4), result.getPlayers().stream().map(ScoreEntry::getRank).toList());
            assertEquals(7, result.getPlayers().getFirst().getScore());
            assertEquals(1.0, result.getPlayers().getFirst().getAverageGuessTime());
            assertEquals(1, f.finished.get());
            f.game.tick(1);
            assertEquals(1, f.finished.get());
        }
    }

    @Test
    void allOfflinePlayersFinishMatchWithoutWaitingForTimers() throws Exception {
        try (Fixture f = new Fixture()) {
            for (int id = 1; id <= 4; id++) { f.game.disconnect(1, id); }
            assertEquals(1, f.finished.get());
        }
    }

    @Test
    void noPlayableCategoryLeavesRoomWaitingAndReportsError() throws Exception {
        WordDAO dao = new WordDAO() {
            @Override protected Map<String, List<String>> loadTopics() { return Map.of("Ít từ", List.of("Một", "Hai")); }
        };
        try (GameManager game = new GameManager(dao, new DrawingStore(directory), System::nanoTime, false, new com.nhom15.drawguess.server.dao.HistoryDAO() { @Override public void save(HistoryData.Detail d) {} })) {
            RoomManager rooms = new RoomManager(game, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);
            List<Message> messages = new ArrayList<>();
            for (int id = 1; id <= 4; id++) {
                rooms.join(new UserInfo(id, "player" + id, "USER"), 1, messages::add);
            }
            for (int id = 1; id <= 4; id++) {
                rooms.ready(id, true, messages::add);
            }
            RoomInfo room = rooms.getRooms().getFirst();
            assertEquals(RoomStatus.READY_CHECK, room.getStatus());
            assertTrue(room.getPlayers().stream().noneMatch(PlayerInfo::isReady));
            assertEquals("NOT_ENOUGH_WORDS", messages.stream().filter(m -> m.getType() == MessageType.GAME_ERROR).findFirst().orElseThrow().getData());
            assertTrue(messages.stream().noneMatch(m -> m.getType() == MessageType.START_GAME));
        }
    }

    @Test
    void moreThanHalfOfGuessersReportingRemovesDrawerPointsForThatRoundOnly() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "Con mèo", 1);
            assertEquals(1, f.score(1), "Người vẽ được +1 khi có người đoán đúng");

            f.report(3, 0);
            assertEquals("REPORT_DRAWING", f.last(3, MessageType.ACTION_SUCCESS).getData());
            ReportStatus status = f.reportStatus(4);
            assertEquals(1, status.getReports());
            assertEquals(2, status.getNeeded(), "3 người đoán: cần ít nhất 2 phiếu (quá nửa)");
            assertFalse(status.isRejected());
            assertEquals(1, f.score(1), "Chưa đủ phiếu thì người vẽ vẫn giữ điểm");

            f.report(3, 0);
            assertEquals("ALREADY_REPORTED", f.last(3, MessageType.ACTION_FAILED).getData());
            f.report(1, 0);
            assertEquals("OWN_DRAWING", f.last(1, MessageType.ACTION_FAILED).getData());

            int guesserBefore = f.score(2);
            f.report(4, 0);
            assertTrue(f.reportStatus(2).isRejected());
            assertEquals(0, f.score(1), "Đủ phiếu thì người vẽ mất điểm đã nhận ở vòng này");
            assertEquals(guesserBefore, f.score(2), "Người đoán giữ nguyên điểm của mình");

            // Người 2 đã đúng, 3 và 4 đã báo cáo: lượt kết thúc ngay, không chờ hết 30 giây.
            f.advance(3);
            assertEquals(1, f.round().getRoundIndex());
            f.roundStarted = f.clock.get();
            assertEquals(0, f.reportStatuses(4, 1), "Vòng mới bắt đầu với 0 báo cáo");
            f.report(3, 1);
            assertFalse(f.reportStatus(4).isRejected(), "Vòng mới không còn bị ảnh hưởng bởi báo cáo cũ");
            f.report(4, 1);
            assertTrue(f.reportStatus(1).isRejected());
            int drawerBefore = f.score(2);
            int guesser = f.score(1);
            f.guess(1, "Con chó", 1);
            assertEquals(drawerBefore, f.score(2), "Tranh bị loại thì người vẽ không được cộng thêm");
            assertEquals(guesser + 4, f.score(1), "Người đoán đúng vẫn được điểm");
            f.report(3, 0);
            assertEquals("REPORT_NOT_ACTIVE", f.last(3, MessageType.ACTION_FAILED).getData());
        }
    }

    @Test
    void reportsCanBeSentDuringAnswerRevealAndCountOnlyOnlineGuessers() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(3, "Con mèo", 1);
            f.guess(4, "Con mèo", 2);
            for (int attempt = 0; attempt < 3; attempt++) { f.guess(2, "Con cá", 3 + attempt); }
            assertEquals(2, f.score(1));
            assertTrue(f.messages.get(1).stream().anyMatch(m -> m.getType() == MessageType.ANSWER));
            // Mọi người đã đoán xong: vòng chuyển sang công bố đáp án nhưng người chưa đoán đúng vẫn báo cáo được.
            f.report(2, 0);
            assertEquals(2, f.reportStatus(2).getNeeded());
            f.game.disconnect(1, 4);
            f.game.disconnect(1, 3);
            ReportStatus status = f.reportStatus(2);
            assertEquals(1, status.getNeeded(), "Chỉ còn 1 người đoán online");
            assertTrue(status.isRejected());
            assertEquals(0, f.score(1));
        }
    }

    @Test
    void reconnectingGuesserGetsCurrentRoundHintTimeAndOwnLockedState() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "Con mèo", 3);
            f.game.disconnect(1, 2);
            f.advance(16); // 19 giây sau khi vòng bắt đầu: đã mở gợi ý.
            String hint = ((RoundHint) f.last(1, MessageType.HINT).getData()).getHint();
            f.messages.get(2).clear();

            f.game.reconnect(1, 2, f.sender(2));
            assertEquals("Con chó", f.prompt(2).getWord(), "Người vào lại vẫn thấy từ của chính mình");
            RoundInfo round = (RoundInfo) f.last(2, MessageType.ROUND_START).getData();
            assertEquals(0, round.getRoundIndex());
            assertEquals(1, round.getDrawerId());
            assertEquals(11, round.getSeconds());
            assertEquals(hint, round.getHint());
            assertTrue(f.guessResult(2).isCorrect() && f.guessResult(2).isLocked(), "Đã đoán đúng trước khi rớt mạng");
            assertTrue(f.entry(2).isOnline());
            assertEquals(4, f.score(2), "Điểm đã có được giữ nguyên");
        }
    }

    @Test
    void reconnectingDuringUploadAsksForTheDrawingAgain() throws Exception {
        try (Fixture f = new Fixture()) {
            f.advance(60);
            f.upload(1, f.png);
            f.game.disconnect(1, 3);
            f.messages.get(3).clear();
            f.game.reconnect(1, 3, f.sender(3));
            assertEquals(0, f.prompt(3).getSeconds());
            assertEquals(f.matchId, f.last(3, MessageType.SUBMIT_DRAW).getData());
            f.upload(3, f.png);
            assertEquals("DRAW_UPLOAD", f.last(3, MessageType.ACTION_SUCCESS).getData());
        }
    }

    @Test
    void reconnectingDuringDrawingKeepsTheRemainingTime() throws Exception {
        try (Fixture f = new Fixture()) {
            f.advance(20);
            f.game.disconnect(1, 4);
            f.messages.get(4).clear();
            f.game.reconnect(1, 4, f.sender(4));
            assertEquals("Con hổ", f.prompt(4).getWord());
            assertEquals(40, f.prompt(4).getSeconds());
            assertTrue(f.messages.get(4).stream().noneMatch(m -> m.getType() == MessageType.ROUND_START));
        }
    }

    @Test
    void fasterCorrectGuessesEarnOneMorePointForEveryTenSecondsSaved() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "Con mèo", 5);
            assertEquals(4, f.guessResult(2).getPoints(), "0–10 giây: 3 điểm + 1 thưởng đoán đầu tiên");
            f.guess(3, "Con mèo", 15);
            assertEquals(2, f.guessResult(3).getPoints(), "10–20 giây: 2 điểm");
            f.guess(4, "Con mèo", 25);
            assertEquals(1, f.guessResult(4).getPoints(), "20–30 giây: 1 điểm");
            assertEquals(List.of(4, 2, 1), List.of(f.score(2), f.score(3), f.score(4)));
            assertEquals(3, f.score(1), "Người vẽ vẫn được 1 điểm cho mỗi người đoán đúng");
        }
    }

    @Test
    void speedPointsStepDownEveryTenSecondsButNeverBelowOne() {
        long second = 1_000_000_000L;
        assertEquals(3, GameManager.speedPoints(0));
        assertEquals(3, GameManager.speedPoints(10 * second - 1));
        assertEquals(2, GameManager.speedPoints(10 * second));
        assertEquals(1, GameManager.speedPoints(20 * second));
        assertEquals(1, GameManager.speedPoints(29 * second));
    }

    @Test
    void chatCannotRevealWordsThatAreStillSecret() throws Exception {
        try (Fixture f = new Fixture()) {
            assertTrue(f.game.revealsWord(1, "đáp án là CON MÈO nhé"));
            assertTrue(f.game.revealsWord(1, "con meo"), "Không dấu vẫn bị chặn");
            assertTrue(f.game.revealsWord(1, "conchó!!"), "Viết liền vẫn bị chặn");
            assertFalse(f.game.revealsWord(1, "các bạn vẽ đẹp quá"));
            assertFalse(f.game.revealsWord(1, "con gì thế nhỉ"));
            f.startGuessing();
            f.advance(30); // Hết vòng 1: đáp án "Con mèo" đã công bố.
            assertFalse(f.game.revealsWord(1, "hóa ra là con mèo"));
            assertTrue(f.game.revealsWord(1, "con voi"), "Từ của các vòng sau vẫn bí mật");
            assertFalse(f.game.revealsWord(2, "con voi"), "Phòng không có trận thì không chặn");
        }
    }

    @Test
    void drawerWhoDisconnectsKeepsTheirLastDraftForGuessing() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] early = f.store.validate(sketch(100)), latest = f.store.validate(sketch(200));
            f.draft(3, early);
            f.advance(2);
            f.draft(3, latest);
            f.game.disconnect(1, 3);
            f.advance(58);
            for (int id : new int[]{1, 2, 4}) { f.upload(id, f.png); }
            assertArrayEquals(f.png, f.round().getImage(), "Vòng 1 là tranh đã gửi bình thường");
            f.advance(30); f.advance(3); f.advance(30); f.advance(3);
            assertEquals(3, f.round().getDrawerId());
            assertArrayEquals(latest, f.round().getImage(), "Người mất kết nối được dùng bản nháp cuối cùng, không phải tranh trắng");
            assertTrue(f.saved.getLast().rounds().get(2).imagePath().endsWith("3.png"));
        }
    }

    @Test
    void reconnectingDrawerGetsTheirDraftBackBeforeBeingAskedToSubmit() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] drawing = f.store.validate(sketch(150));
            f.draft(4, drawing);
            f.advance(20);
            f.game.disconnect(1, 4);
            f.messages.get(4).clear();
            f.game.reconnect(1, 4, f.sender(4));
            assertArrayEquals(drawing, ((DrawUpload) f.last(4, MessageType.DRAW_DRAFT).getData()).getImage());

            f.advance(40); // Hết giờ vẽ.
            f.game.disconnect(1, 4);
            f.messages.get(4).clear();
            f.game.reconnect(1, 4, f.sender(4));
            List<MessageType> order = f.messages.get(4).stream().map(Message::getType)
                    .filter(t -> t == MessageType.DRAW_DRAFT || t == MessageType.SUBMIT_DRAW).toList();
            assertEquals(List.of(MessageType.DRAW_DRAFT, MessageType.SUBMIT_DRAW), order,
                    "Client dựng lại tranh rồi mới gửi, không gửi tranh trắng");
        }
    }

    @Test
    void draftsAreIgnoredWhenInvalidTooFrequentOrAfterTheFinalUpload() throws Exception {
        try (Fixture f = new Fixture()) {
            byte[] first = f.store.validate(sketch(100));
            f.draft(2, first);
            f.draft(2, sketch(200)); // Chưa đủ 1 giây.
            f.advance(1);
            f.draft(2, new byte[]{1, 2, 3});
            draftFor(f, "matchId-sai", 2, sketch(250));
            f.game.disconnect(1, 2);
            f.messages.get(2).clear();
            f.game.reconnect(1, 2, f.sender(2));
            assertArrayEquals(first, ((DrawUpload) f.last(2, MessageType.DRAW_DRAFT).getData()).getImage());
            assertTrue(f.messages.get(2).stream().noneMatch(m -> m.getType() == MessageType.ACTION_FAILED),
                    "Bản nháp lỗi bị bỏ qua lặng lẽ");

            f.advance(59);
            f.upload(2, f.png);
            f.advance(1);
            f.draft(2, sketch(300));
            for (int id : new int[]{1, 3, 4}) { f.upload(id, f.png); }
            f.advance(30); f.advance(3);
            assertEquals(2, f.round().getDrawerId());
            assertArrayEquals(f.png, f.round().getImage(), "Tranh đã gửi chính thức không bị bản nháp thay thế");
        }
    }

    @Test
    void matchPlaysEveryStageInOrderAndKeepsScoresAcrossStages() throws Exception {
        try (Fixture f = new Fixture(List.of(
                new GamePlan("Động vật", List.of("Con mèo", "Con chó", "Con voi", "Con hổ")),
                new GamePlan("Đồ ăn", List.of("Bánh mì", "Phở", "Cơm tấm", "Bún chả"))))) {
            assertEquals(0, f.prompt(1).getStage());
            assertEquals(2, f.prompt(1).getStageCount());
            assertEquals(4, f.saved.getLast().rounds().size(), "Lịch sử chưa có từ khóa của vòng chưa chơi");
            f.startGuessing();
            f.guess(2, "Con mèo", 5); // +4
            for (int turn = 0; turn < 4; turn++) { f.advance(30); f.advance(3); }
            assertEquals(4, f.score(2));
            assertEquals(0, f.finished.get(), "Chưa kết thúc sau vòng đầu");

            DrawingPrompt second = f.prompt(2);
            assertEquals(1, second.getStage());
            assertEquals("Đồ ăn", second.getCategory());
            assertEquals("Phở", second.getWord());
            assertEquals(60, second.getSeconds());
            assertFalse(f.game.revealsWord(1, "con hổ"), "Từ của vòng trước đã công bố");
            assertTrue(f.game.revealsWord(1, "phở"), "Từ của vòng đang vẽ vẫn bí mật");

            f.startGuessing();
            RoundInfo round = f.round();
            assertEquals(4, round.getRoundIndex(), "Lượt đếm tiếp trên cả trận");
            assertEquals(8, round.getTotalRounds());
            assertEquals(1, round.getStage());
            assertEquals("Đồ ăn", round.getCategory());
            f.guess(3, "Bánh mì", 5);
            assertTrue(f.guessResult(3).isCorrect());
            assertEquals(4, f.score(2), "Điểm vòng trước được giữ");
            for (int turn = 0; turn < 4; turn++) { f.advance(30); f.advance(3); }
            assertEquals(1, f.finished.get());
            HistoryData.Detail history = f.saved.getLast();
            assertEquals("Động vật, Đồ ăn", history.category());
            assertEquals(8, history.rounds().size());
            assertEquals("Phở", history.rounds().get(5).word());
            assertEquals("2-2.png", Path.of(history.rounds().get(5).imagePath()).getFileName().toString(), "Tranh mỗi vòng là một file riêng");
            assertEquals("2.png", Path.of(history.rounds().get(1).imagePath()).getFileName().toString());
        }
    }

    @Test
    void playerWhoGuessedCorrectlyCannotReportTheDrawing() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(2, "Con mèo", 5);
            f.report(2, 0);
            assertEquals("ALREADY_SOLVED", f.last(2, MessageType.ACTION_FAILED).getData());
            assertTrue(f.messages.get(3).stream().noneMatch(m -> m.getType() == MessageType.REPORT_STATUS),
                    "Báo cáo bị từ chối thì không được tính");
            f.report(3, 0);
            assertEquals(1, f.reportStatus(3).getReports(), "Người chưa đoán đúng vẫn báo cáo được");
        }
    }

    @Test
    void playerWhoReportedCannotGuessAndNoLongerHoldsUpTheRound() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.report(2, 0);
            f.guess(2, "Con mèo", 3);
            GuessResult blocked = f.guessResult(2);
            assertFalse(blocked.isCorrect());
            assertTrue(blocked.isLocked());
            assertEquals("REPORTED", blocked.getReason());
            assertEquals(0, f.score(2), "Báo cáo rồi thì không ăn điểm từ tranh đó");
            assertEquals(3, blocked.getAttemptsLeft(), "Lượt bị chặn không tính là một lần đoán");

            f.guess(3, "Con mèo", 4);
            f.guess(4, "Con mèo", 5);
            assertEquals("Con mèo", ((RoundAnswer) f.last(1, MessageType.ANSWER).getData()).getWord(),
                    "Hai người còn lại đoán xong thì lượt kết thúc, không chờ người đã báo cáo");
        }
    }

    @Test
    void roundEndsAsSoonAsEveryRemainingGuesserHasReported() throws Exception {
        try (Fixture f = new Fixture()) {
            f.startGuessing();
            f.guess(4, "Sai rồi", 2);
            f.report(2, 0);
            f.report(3, 0);
            assertTrue(f.messages.get(1).stream().noneMatch(m -> m.getType() == MessageType.ANSWER), "Người 4 vẫn đang đoán");
            f.report(4, 0);
            assertTrue(f.messages.get(1).stream().anyMatch(m -> m.getType() == MessageType.ANSWER));
        }
    }

    @Test
    void stagesUseTheChosenTopicsAndRandomStagesAvoidRepeats() {
        Map<String, List<String>> topics = new LinkedHashMap<>();
        topics.put("Động vật", List.of("Mèo", "Chó", "Voi"));
        topics.put("Đồ ăn", List.of("Phở", "Bún", "Cơm"));
        topics.put("Nhạc cụ", List.of("Đàn", "Trống"));
        java.util.Random random = new java.util.Random(7);
        for (int attempt = 0; attempt < 20; attempt++) {
            List<GamePlan> plans = GamePlan.choose(topics, List.of("", "Động vật"), 3, random);
            assertEquals("Đồ ăn", plans.getFirst().getCategory(), "Ngẫu nhiên tránh chủ đề đã chọn và chủ đề thiếu từ");
            assertEquals("Động vật", plans.get(1).getCategory());
            assertEquals(3, java.util.Set.copyOf(plans.get(1).getWords()).size());
        }
        assertNull(GamePlan.choose(topics, List.of("Nhạc cụ"), 3, random), "Chủ đề không đủ từ cho số người");
        assertNull(GamePlan.choose(topics, List.of("Không có"), 2, random));
        assertEquals(3, GamePlan.choose(topics, List.of("", "", ""), 2, random).stream()
                .map(GamePlan::getCategory).distinct().count());
    }

    private static void draftFor(Fixture f, String matchId, int id, byte[] image) {
        f.game.draft(1, id, new DrawUpload(matchId, image));
    }

    // Tranh 500x500 có một hình tròn đen, đường kính tùy chọn để các tranh khác nhau.
    private static byte[] sketch(int diameter) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(500, 500, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 500, 500);
        graphics.setColor(java.awt.Color.BLACK); graphics.fillOval(250 - diameter / 2, 250 - diameter / 2, diameter, diameter);
        graphics.dispose();
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private class Fixture implements AutoCloseable {
        final AtomicLong clock = new AtomicLong();
        final AtomicInteger finished = new AtomicInteger();
        final Map<Integer, List<Message>> messages = new LinkedHashMap<>();
        final DrawingStore store = new DrawingStore(directory);
        final List<HistoryData.Detail> saved = new ArrayList<>();
        final GameManager game = new GameManager(new WordDAO(), store, clock::get, false,
                new com.nhom15.drawguess.server.dao.HistoryDAO() {
                    @Override public void save(HistoryData.Detail d) { saved.add(d); }
                });
        final byte[] png;
        final String matchId;
        long roundStarted;

        Fixture() throws Exception {
            this(List.of(new GamePlan("Động vật", List.of("Con mèo", "Con chó", "Con voi", "Con hổ"))));
        }

        Fixture(List<GamePlan> plans) throws Exception {
            List<PlayerInfo> players = new ArrayList<>();
            Map<Integer, Consumer<Message>> senders = new LinkedHashMap<>();
            for (int id = 1; id <= 4; id++) {
                players.add(new PlayerInfo(id, "player" + id, true, true));
                messages.put(id, new ArrayList<>());
                senders.put(id, sender(id));
            }
            game.start(new RoomInfo(1, RoomStatus.PLAYING, players), plans, senders, new Object(), finished::incrementAndGet);
            matchId = prompt(1).getMatchId();
            png = store.blank();
        }

        Consumer<Message> sender(int id) { return messages.get(id)::add; }
        Message last(int id, MessageType type) {
            return messages.get(id).stream().filter(m -> m.getType() == type).reduce((a,b) -> b).orElseThrow();
        }
        DrawingPrompt prompt(int id) { return (DrawingPrompt) last(id, MessageType.DRAW_WORD).getData(); }
        RoundInfo round() { return (RoundInfo) last(1, MessageType.ROUND_START).getData(); }
        GuessResult guessResult(int id) { return (GuessResult) last(id, MessageType.GUESS_RESULT).getData(); }
        int score(int id) { return entry(id).getScore(); }
        ScoreEntry entry(int id) {
            List<?> scores = (List<?>) last(2, MessageType.SCORE_UPDATE).getData();
            return scores.stream().map(ScoreEntry.class::cast).filter(s -> s.getUserId() == id).findFirst().orElseThrow();
        }
        void advance(int seconds) { clock.addAndGet(seconds * 1_000_000_000L); game.tick(1); }
        void report(int id, int round) { game.report(1, id, new ReportRequest(matchId, round), sender(id)); }
        ReportStatus reportStatus(int id) { return (ReportStatus) last(id, MessageType.REPORT_STATUS).getData(); }
        long reportStatuses(int id, int round) {
            return messages.get(id).stream().filter(m -> m.getType() == MessageType.REPORT_STATUS
                    && ((ReportStatus) m.getData()).getRoundIndex() == round).count();
        }
        void upload(int id, byte[] image) { game.upload(1, id, new DrawUpload(matchId, image), sender(id)); }
        void draft(int id, byte[] image) { game.draft(1, id, new DrawUpload(matchId, image)); }
        void startGuessing() {
            advance(60);
            for (int id = 1; id <= 4; id++) { upload(id, png); }
            roundStarted = clock.get();
        }
        void guess(int id, String answer, int afterSeconds) {
            long received = roundStarted + afterSeconds * 1_000_000_000L;
            clock.accumulateAndGet(received, Math::max);
            game.guess(1, id, new GuessRequest(matchId, round().getRoundIndex(), answer), received, sender(id));
        }
        @Override public void close() { game.close(); }
    }
}
