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
            assertEquals(2, f.score(2));
            assertEquals(1, f.score(1));
            f.guess(3, "con mèo", 3);
            assertEquals(1, f.score(3));
            assertEquals(2, f.score(1));
            f.guess(2, "con mèo", 4);
            assertEquals("GUESS_LOCKED", f.guessResult(2).getReason());
            assertEquals(2, f.score(2));
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
            assertEquals(1, f.score(2));
            assertEquals(2, f.score(3));
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
            assertEquals(2, f.score(2));
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
            assertEquals(5, result.getPlayers().getFirst().getScore());
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
            @Override public GamePlan selectWords(int playerCount) { return null; }
        };
        try (GameManager game = new GameManager(dao, new DrawingStore(directory), System::nanoTime, false, new com.nhom15.drawguess.server.dao.HistoryDAO() { @Override public void save(HistoryData.Detail d) {} })) {
            RoomManager rooms = new RoomManager(game);
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
            List<PlayerInfo> players = new ArrayList<>();
            Map<Integer, Consumer<Message>> senders = new LinkedHashMap<>();
            for (int id = 1; id <= 4; id++) {
                players.add(new PlayerInfo(id, "player" + id, true, true));
                messages.put(id, new ArrayList<>());
                senders.put(id, sender(id));
            }
            game.start(new RoomInfo(1, RoomStatus.PLAYING, players),
                    new GamePlan("Động vật", List.of("Con mèo", "Con chó", "Con voi", "Con hổ")),
                    senders, new Object(), finished::incrementAndGet);
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
        int score(int id) {
            List<?> scores = (List<?>) last(2, MessageType.SCORE_UPDATE).getData();
            return scores.stream().map(ScoreEntry.class::cast).filter(s -> s.getUserId() == id).findFirst().orElseThrow().getScore();
        }
        void advance(int seconds) { clock.addAndGet(seconds * 1_000_000_000L); game.tick(1); }
        void upload(int id, byte[] image) { game.upload(1, id, new DrawUpload(matchId, image), sender(id)); }
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
