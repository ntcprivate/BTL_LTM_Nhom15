package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.WordDAO;
import com.nhom15.drawguess.server.dao.HistoryDAO;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class GameManager implements AutoCloseable {
    public static final int DRAW_SECONDS = 60;
    public static final int UPLOAD_SECONDS = 10;
    public static final int GUESS_SECONDS = 30;
    public static final int REVEAL_SECONDS = 3;
    private final HistoryDAO historyDAO;
    private final WordDAO wordDAO;
    private final DrawingStore drawings;
    private final LongSupplier clock;
    private final ScheduledExecutorService timers;
    private final Map<Integer, Match> matches = new ConcurrentHashMap<>();

    public GameManager() {
        this(new WordDAO(), new DrawingStore(Path.of("server-data", "drawings")), System::nanoTime, true, new HistoryDAO());
    }

    public GameManager(WordDAO wordDAO, DrawingStore drawings, LongSupplier clock, boolean automaticTimers) {
        this(wordDAO, drawings, clock, automaticTimers, new HistoryDAO());
    }

    public GameManager(WordDAO wordDAO, DrawingStore drawings, LongSupplier clock, boolean automaticTimers, HistoryDAO historyDAO) {
        this.historyDAO = historyDAO;
        this.wordDAO = wordDAO;
        this.drawings = drawings;
        this.clock = clock;
        timers = automaticTimers ? Executors.newScheduledThreadPool(4, task -> {
            Thread thread = new Thread(task, "Game-Timer");
            thread.setDaemon(true);
            return thread;
        }) : null;
    }

    public GamePlan prepare(int playerCount) throws SQLException {
        return wordDAO.selectWords(playerCount);
    }

    public void start(RoomInfo room, GamePlan plan, Map<Integer, Consumer<Message>> senders,
                      Object roomLock, Runnable finished) {
        Match match = new Match(room, plan, senders, roomLock, finished);
        synchronized (roomLock) {
            matches.put(room.getRoomId(), match);
            if (!persist(match)) { abort(match, "HISTORY_STORAGE_ERROR"); return; }
            match.deadline = clock.getAsLong() + seconds(DRAW_SECONDS);
            broadcast(match, MessageType.ROUND_CATEGORY, plan.getCategory());
            for (Player player : match.players.values()) {
                send(player, MessageType.DRAW_WORD, new DrawingPrompt(match.id, room.getRoomId(),
                        plan.getCategory(), player.word, DRAW_SECONDS));
            }
            broadcastScores(match, false);
            sendTimer(match);
            if (timers != null) {
                match.timer = timers.scheduleAtFixedRate(() -> {
                    try { tick(room.getRoomId()); }
                    catch (RuntimeException e) {
                        System.err.println("Lỗi điều khiển trận " + match.id + ": " + e.getClass().getSimpleName());
                        synchronized (roomLock) {
                            if (matches.get(room.getRoomId()) == match) { abort(match, "GAME_SERVER_ERROR"); }
                        }
                    }
                }, 1, 1, TimeUnit.SECONDS);
            }
        }
    }

    // Timer và ClientHandler đều sử dụng cùng khóa phòng, tránh hai trạng thái bị lệch nhau.
    public void tick(int roomId) {
        Match match = matches.get(roomId);
        if (match == null) { return; }
        synchronized (match.lock) {
            if (matches.get(roomId) != match) { return; }
            if (clock.getAsLong() >= match.deadline) {
                switch (match.phase) {
                    case DRAWING -> beginUpload(match);
                    case UPLOADING -> beginGuessing(match);
                    case GUESSING -> endRound(match);
                    case ROUND_END -> nextRound(match);
                    default -> { }
                }
            }
            if (matches.get(roomId) == match) { sendTimer(match); }
        }
    }

    public void upload(int roomId, int userId, Object payload, Consumer<Message> reply) {
        Match match = matches.get(roomId);
        if (match == null) { fail(reply, "NOT_IN_MATCH"); return; }
        synchronized (match.lock) {
            if (!(payload instanceof DrawUpload upload) || !match.id.equals(upload.getMatchId())) {
                fail(reply, "INVALID_REQUEST"); return;
            }
            Player player = match.players.get(userId);
            if (matches.get(roomId) != match || match.phase != GamePhase.UPLOADING
                    || clock.getAsLong() >= match.deadline) {
                fail(reply, "UPLOAD_NOT_ACTIVE"); return;
            }
            if (player == null || !player.online) { fail(reply, "NOT_IN_MATCH"); return; }
            if (player.image != null) { fail(reply, "ALREADY_UPLOADED"); return; }
            byte[] image;
            try { image = drawings.validate(upload.getImage()); }
            catch (IOException e) { fail(reply, "INVALID_PNG"); return; }
            try { player.imagePath = drawings.save(match.id, userId, image).toString(); }
            catch (IOException e) { abort(match, "DRAWING_STORAGE_ERROR"); return; }
            player.image = image;
            persist(match);
            reply.accept(new Message(MessageType.ACTION_SUCCESS, "DRAW_UPLOAD"));
            if (allUploaded(match)) { beginGuessing(match); }
        }
    }

    public void guess(int roomId, int userId, Object payload, long receivedAt, Consumer<Message> reply) {
        Match match = matches.get(roomId);
        if (match == null) { fail(reply, "NOT_IN_MATCH"); return; }
        synchronized (match.lock) {
            if (!(payload instanceof GuessRequest request) || !match.id.equals(request.getMatchId())) {
                fail(reply, "INVALID_REQUEST"); return;
            }
            Player player = match.players.get(userId);
            if (matches.get(roomId) != match || player == null || !player.online) {
                fail(reply, "NOT_IN_MATCH"); return;
            }
            if (match.phase != GamePhase.GUESSING || request.getRoundIndex() != match.round
                    || receivedAt < match.roundStarted || receivedAt >= match.deadline) {
                guessReply(reply, request, false, player, true, "ROUND_NOT_ACTIVE"); return;
            }
            Player drawer = match.order.get(match.round);
            if (drawer == player) { guessReply(reply, request, false, player, true, "OWN_DRAWING"); return; }
            if (player.solved || player.attempts >= 3) {
                guessReply(reply, request, false, player, true, "GUESS_LOCKED"); return;
            }
            String answer = request.getAnswer();
            if (answer == null || answer.isBlank() || answer.length() > 100) {
                guessReply(reply, request, false, player, false, "INVALID_ANSWER"); return;
            }
            player.attempts++;
            boolean correct = normalize(answer).equals(normalize(drawer.word));
            if (correct) {
                player.solved = true;
                player.correctCount++;
                player.totalGuessNanos += receivedAt - match.roundStarted;
                player.score++;
                if (match.firstCorrect == null) {
                    player.score++;
                    match.firstCorrect = player;
                    match.firstReceivedAt = receivedAt;
                } else if (receivedAt < match.firstReceivedAt) {
                    // Thread nhận sớm hơn nhưng lấy khóa muộn hơn vẫn được điểm đầu tiên.
                    match.firstCorrect.score--;
                    player.score++;
                    match.firstCorrect = player;
                    match.firstReceivedAt = receivedAt;
                }
                if (drawer.online) { drawer.score++; }
            }
            match.guesses.get(match.round).add(new HistoryData.Guess(player.id, player.username, answer.trim(),
                    correct, match.roundWallStarted + TimeUnit.NANOSECONDS.toMillis(receivedAt - match.roundStarted),
                    (receivedAt - match.roundStarted) / 1_000_000_000.0));
            persist(match);
            guessReply(reply, request, correct, player, player.solved || player.attempts >= 3,
                    correct ? "CORRECT" : "WRONG_ANSWER");
            if (correct) { broadcastScores(match, false); }
            if (everyoneDone(match)) { endRound(match); }
        }
    }

    public void disconnect(int roomId, int userId) {
        Match match = matches.get(roomId);
        if (match == null) { return; }
        synchronized (match.lock) {
            Player player = match.players.get(userId);
            if (player == null) { return; }
            player.online = false;
            player.sender = null;
            if (match.players.values().stream().noneMatch(p -> p.online)) { match.status = "ABANDONED"; finish(match); return; }
            persist(match);
            broadcastScores(match, false);
            if (match.phase == GamePhase.UPLOADING && allUploaded(match)) { beginGuessing(match); }
            else if (match.phase == GamePhase.GUESSING && everyoneDone(match)) { endRound(match); }
        }
    }

    private void beginUpload(Match match) {
        match.phase = GamePhase.UPLOADING;
        match.deadline = clock.getAsLong() + seconds(UPLOAD_SECONDS);
        broadcast(match, MessageType.SUBMIT_DRAW, match.id);
        if (allUploaded(match)) { beginGuessing(match); }
    }

    private boolean allUploaded(Match match) {
        return match.players.values().stream().allMatch(p -> !p.online || p.image != null);
    }

    private void beginGuessing(Match match) {
        try {
            for (Player player : match.order) {
                if (player.image == null) {
                    player.image = drawings.blank();
                    player.imagePath = drawings.save(match.id, player.id, player.image).toString();
                }
            }
        } catch (IOException e) { abort(match, "DRAWING_STORAGE_ERROR"); return; }
        match.round = -1;
        nextRound(match);
    }

    private void nextRound(Match match) {
        match.round++;
        if (match.round >= match.order.size()) { finish(match); return; }
        match.phase = GamePhase.GUESSING;
        match.roundStarted = clock.getAsLong();
        match.roundWallStarted = System.currentTimeMillis();
        match.roundStarts.put(match.round, match.roundWallStarted);
        persist(match);
        match.deadline = match.roundStarted + seconds(GUESS_SECONDS);
        match.firstCorrect = null;
        for (Player player : match.order) { player.attempts = 0; player.solved = false; }
        Player drawer = match.order.get(match.round);
        broadcast(match, MessageType.ROUND_START, new RoundInfo(match.id, match.round, match.order.size(),
                drawer.id, drawer.username, match.category, drawer.image, GUESS_SECONDS));
        sendTimer(match);
        if (everyoneDone(match)) { endRound(match); }
    }

    private boolean everyoneDone(Match match) {
        Player drawer = match.order.get(match.round);
        return match.players.values().stream().filter(p -> p.online && p != drawer)
                .allMatch(p -> p.solved || p.attempts >= 3);
    }

    private void endRound(Match match) {
        match.roundEnds.put(match.round, System.currentTimeMillis());
        persist(match);
        match.phase = GamePhase.ROUND_END;
        match.deadline = clock.getAsLong() + seconds(REVEAL_SECONDS);
        broadcast(match, MessageType.TIME_UP, new TimerInfo(match.id, GamePhase.ROUND_END, 0));
        broadcast(match, MessageType.ANSWER, new RoundAnswer(match.id, match.round, match.order.get(match.round).word));
        broadcastScores(match, false);
    }

    private void abort(Match match, String reason) {
        match.status = "ABORTED";
        broadcast(match, MessageType.GAME_ERROR, reason);
        finish(match);
    }

    private void finish(Match match) {
        match.phase = GamePhase.FINISHED;
        if (!matches.remove(match.roomId, match)) { return; }
        if (match.timer != null) { match.timer.cancel(false); }
        match.endedAt = System.currentTimeMillis();
        if ("IN_PROGRESS".equals(match.status)) { match.status = "COMPLETED"; }
        if (match.round >= 0 && match.round < match.order.size() && match.roundStarts.containsKey(match.round)) {
            match.roundEnds.putIfAbsent(match.round, match.endedAt);
        }
        persist(match);
        broadcast(match, MessageType.GAME_END, match.id);
        broadcast(match, MessageType.GAME_RESULT, new GameResult(match.id, scores(match, true)));
        match.finished.run();
    }

    private boolean persist(Match match) {
        List<HistoryData.Round> rounds = new ArrayList<>();
        for (int i=0; i<match.order.size(); i++) {
            Player p=match.order.get(i);
            rounds.add(new HistoryData.Round(i,p.id,p.username,p.word,p.imagePath,
                    match.roundStarts.get(i),match.roundEnds.get(i),List.copyOf(match.guesses.get(i))));
        }
        try {
            historyDAO.save(new HistoryData.Detail(match.id,match.roomId,match.category,match.startedAt,
                    match.endedAt,match.status,scores(match,match.endedAt != null),rounds));
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu lịch sử trận " + match.id + ": SQLState=" + e.getSQLState());
            broadcast(match,MessageType.HISTORY_FAILED,"HISTORY_STORAGE_ERROR");
            return false;
        }
    }

    private void guessReply(Consumer<Message> reply, GuessRequest request, boolean correct,
                            Player player, boolean locked, String reason) {
        reply.accept(new Message(MessageType.GUESS_RESULT, new GuessResult(request.getMatchId(),
                request.getRoundIndex(), correct, Math.max(0, 3 - player.attempts), locked, reason)));
    }

    private void sendTimer(Match match) {
        long remaining = Math.max(0, match.deadline - clock.getAsLong());
        int seconds = (int) ((remaining + 999_999_999L) / 1_000_000_000L);
        broadcast(match, MessageType.TIME_UPDATE, new TimerInfo(match.id, match.phase, seconds));
    }

    private List<ScoreEntry> scores(Match match, boolean ranked) {
        List<Player> ordered = new ArrayList<>(match.order);
        ordered.sort(Comparator.comparingInt((Player p) -> p.score).reversed().thenComparingDouble(Player::average));
        List<ScoreEntry> result = new ArrayList<>();
        int rank = 0;
        Player previous = null;
        for (int i = 0; i < ordered.size(); i++) {
            Player player = ordered.get(i);
            if (previous == null || player.score != previous.score
                    || Double.compare(player.average(), previous.average()) != 0) { rank = i + 1; }
            result.add(new ScoreEntry(player.id, player.username, player.score, ranked ? rank : 0,
                    player.average(), player.online));
            previous = player;
        }
        return result;
    }

    private void broadcastScores(Match match, boolean ranked) { broadcast(match, MessageType.SCORE_UPDATE, scores(match, ranked)); }
    private void broadcast(Match match, MessageType type, Object data) {
        for (Player player : match.order) { send(player, type, data); }
    }
    private void send(Player player, MessageType type, Object data) {
        if (player.online && player.sender != null) { player.sender.accept(new Message(type, data)); }
    }
    private void fail(Consumer<Message> reply, String reason) { reply.accept(new Message(MessageType.ACTION_FAILED, reason)); }
    private static String normalize(String answer) { return answer.trim().toLowerCase(Locale.ROOT); }
    private static long seconds(int value) { return TimeUnit.SECONDS.toNanos(value); }

    @Override
    public void close() {
        if (timers != null) { timers.shutdownNow(); }
        for (Match match : new ArrayList<>(matches.values())) {
            synchronized (match.lock) { if (matches.get(match.roomId) == match) { abort(match,"SERVER_SHUTDOWN"); } }
        }
    }

    private static class Match {
        final long startedAt = System.currentTimeMillis();
        Long endedAt;
        String status = "IN_PROGRESS";
        long roundWallStarted;
        final Map<Integer,Long> roundStarts = new LinkedHashMap<>();
        final Map<Integer,Long> roundEnds = new LinkedHashMap<>();
        final List<List<HistoryData.Guess>> guesses = new ArrayList<>();
        final String id = UUID.randomUUID().toString();
        final int roomId;
        final String category;
        final Object lock;
        final Runnable finished;
        final Map<Integer, Player> players = new LinkedHashMap<>();
        final List<Player> order = new ArrayList<>();
        GamePhase phase = GamePhase.DRAWING;
        long deadline;
        long roundStarted;
        int round = -1;
        Player firstCorrect;
        long firstReceivedAt;
        ScheduledFuture<?> timer;

        Match(RoomInfo room, GamePlan plan, Map<Integer, Consumer<Message>> senders, Object lock, Runnable finished) {
            roomId = room.getRoomId();
            category = plan.getCategory();
            this.lock = lock;
            this.finished = finished;
            for (int i = 0; i < room.getPlayers().size(); i++) {
                PlayerInfo info = room.getPlayers().get(i);
                Player player = new Player(info.getUserId(), info.getUsername(), plan.getWords().get(i), senders.get(info.getUserId()));
                players.put(player.id, player);
                order.add(player);
                guesses.add(new ArrayList<>());
            }
        }
    }

    private static class Player {
        final int id;
        final String username;
        final String word;
        Consumer<Message> sender;
        boolean online = true;
        byte[] image;
        String imagePath;
        int score;
        int attempts;
        boolean solved;
        int correctCount;
        long totalGuessNanos;

        Player(int id, String username, String word, Consumer<Message> sender) {
            this.id = id; this.username = username; this.word = word; this.sender = sender;
        }
        double average() { return correctCount == 0 ? Double.POSITIVE_INFINITY : totalGuessNanos / 1_000_000_000.0 / correctCount; }
    }
}
