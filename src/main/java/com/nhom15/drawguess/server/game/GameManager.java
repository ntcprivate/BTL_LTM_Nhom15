package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.WordDAO;
import com.nhom15.drawguess.server.dao.HistoryDAO;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    public static final int HINT_SECONDS = 15;
    // Điểm theo tốc độ: đúng trong 10 giây đầu được 3, 10 giây tiếp được 2, sau đó 1.
    public static final int SPEED_STEP_SECONDS = 10;
    public static final int MAX_GUESS_POINTS = 3;
    // Server nhận tối đa một bản nháp tranh mỗi giây cho mỗi người (client gửi 2 giây một lần).
    private static final long DRAFT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final HistoryDAO historyDAO;
    private final WordDAO wordDAO;
    private final DrawingStore drawings;
    private final LongSupplier clock;
    private final ScheduledExecutorService timers;
    private final Map<Integer, Match> matches = new ConcurrentHashMap<>();
    private final Random random = new Random();

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

    // Từ khóa cho từng vòng; `topics` là chủ đề chủ phòng chọn cho mỗi vòng ("" = ngẫu nhiên).
    public List<GamePlan> prepare(List<String> topics, int playerCount) throws SQLException {
        return wordDAO.selectStages(topics, playerCount);
    }

    // Các chủ đề chủ phòng có thể chọn.
    public List<String> topics() throws SQLException {
        return wordDAO.topics();
    }

    public void start(RoomInfo room, GamePlan plan, Map<Integer, Consumer<Message>> senders,
                      Object roomLock, Runnable finished) {
        start(room, List.of(plan), senders, roomLock, finished);
    }

    // Một trận gồm nhiều vòng, mỗi vòng một chủ đề: cả phòng vẽ rồi đoán lần lượt tranh của nhau, điểm cộng dồn.
    public void start(RoomInfo room, List<GamePlan> plans, Map<Integer, Consumer<Message>> senders,
                      Object roomLock, Runnable finished) {
        Match match = new Match(room, plans, senders, roomLock, finished);
        synchronized (roomLock) {
            matches.put(room.getRoomId(), match);
            if (!persist(match)) { abort(match, "HISTORY_STORAGE_ERROR"); return; }
            beginStage(match, 0);
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
            } else if (match.phase == GamePhase.GUESSING && !match.hintRevealed
                    && clock.getAsLong() >= match.roundStarted + seconds(HINT_SECONDS)) {
                revealHint(match);
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
            Turn turn = match.drawing(player);
            if (turn.image != null) { fail(reply, "ALREADY_UPLOADED"); return; }
            byte[] image;
            try { image = drawings.validate(upload.getImage()); }
            catch (IOException e) { fail(reply, "INVALID_PNG"); return; }
            try { turn.imagePath = drawings.save(match.id, userId, match.stage, image).toString(); }
            catch (IOException e) { abort(match, "DRAWING_STORAGE_ERROR"); return; }
            turn.image = image;
            persist(match);
            reply.accept(new Message(MessageType.ACTION_SUCCESS, "DRAW_UPLOAD"));
            if (allUploaded(match)) { beginGuessing(match); }
        }
    }

    // Bản nháp tranh đang vẽ. Người chơi mất kết nối thì tranh cuối cùng vẫn được dùng khi hết giờ,
    // và được gửi lại để vẽ tiếp nếu họ vào lại kịp. Bản nháp lỗi hoặc gửi quá dày thì bỏ qua, không báo lỗi.
    public void draft(int roomId, int userId, Object payload) {
        Match match = matches.get(roomId);
        if (match == null) { return; }
        synchronized (match.lock) {
            if (matches.get(roomId) != match || !(payload instanceof DrawUpload upload)
                    || !match.id.equals(upload.getMatchId())
                    || (match.phase != GamePhase.DRAWING && match.phase != GamePhase.UPLOADING)) { return; }
            Player player = match.players.get(userId);
            if (player == null || !player.online || match.drawing(player).image != null) { return; }
            long now = clock.getAsLong();
            if (player.draft != null && now - player.draftAt < DRAFT_INTERVAL_NANOS) { return; }
            try { player.draft = drawings.validate(upload.getImage()); }
            catch (IOException e) { return; }
            player.draftAt = now;
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
            Turn turn = match.current();
            Player drawer = turn.drawer;
            if (drawer == player) { guessReply(reply, request, false, player, true, "OWN_DRAWING"); return; }
            if (player.solved || player.attempts >= 3) {
                guessReply(reply, request, false, player, true, "GUESS_LOCKED"); return;
            }
            String answer = request.getAnswer();
            if (answer == null || answer.isBlank() || answer.length() > 100) {
                guessReply(reply, request, false, player, false, "INVALID_ANSWER"); return;
            }
            player.attempts++;
            int earned = 0;
            boolean correct = normalize(answer).equals(normalize(turn.word));
            if (correct) {
                player.solved = true;
                player.correctCount++;
                player.totalGuessNanos += receivedAt - match.roundStarted;
                earned = speedPoints(receivedAt - match.roundStarted);
                player.score += earned;
                if (match.firstCorrect == null) {
                    player.score++;
                    earned++;
                    match.firstCorrect = player;
                    match.firstReceivedAt = receivedAt;
                } else if (receivedAt < match.firstReceivedAt) {
                    // Thread nhận sớm hơn nhưng lấy khóa muộn hơn vẫn được điểm đầu tiên.
                    match.firstCorrect.score--;
                    player.score++;
                    earned++;
                    match.firstCorrect = player;
                    match.firstReceivedAt = receivedAt;
                }
                // Tranh đã bị quá nửa người đoán báo cáo thì người vẽ không được cộng thêm.
                if (drawer.online && !match.drawingRejected) {
                    drawer.score++;
                    match.drawerRoundPoints++;
                }
            }
            turn.guesses.add(new HistoryData.Guess(player.id, player.username, answer.trim(),
                    correct, match.roundWallStarted + TimeUnit.NANOSECONDS.toMillis(receivedAt - match.roundStarted),
                    (receivedAt - match.roundStarted) / 1_000_000_000.0));
            persist(match);
            reply.accept(new Message(MessageType.GUESS_RESULT, new GuessResult(request.getMatchId(),
                    request.getRoundIndex(), correct, Math.max(0, 3 - player.attempts),
                    player.solved || player.attempts >= 3, correct ? "CORRECT" : "WRONG_ANSWER", earned)));
            if (correct) { broadcastScores(match, false); }
            if (everyoneDone(match)) { endRound(match); }
        }
    }

    // Thời gian tính từ lúc bắt đầu vòng; mỗi 10 giây sớm hơn được thêm 1 điểm, thấp nhất 1 điểm.
    static int speedPoints(long elapsedNanos) {
        long steps = elapsedNanos / TimeUnit.SECONDS.toNanos(SPEED_STEP_SECONDS);
        return (int) Math.max(1, MAX_GUESS_POINTS - steps);
    }

    // Tin nhắn chat có để lộ từ khóa chưa công bố của trận đang chơi không (không phân biệt hoa thường, dấu).
    public boolean revealsWord(int roomId, String text) {
        Match match = matches.get(roomId);
        if (match == null) { return false; }
        String message = " " + chatForm(text) + " ";
        String joined = message.replace(" ", "");
        synchronized (match.lock) {
            for (int index = 0; index < match.turns.size(); index++) {
                // Lượt đang đoán còn bí mật; lượt trước đó đã công bố (kể cả khi đã sang vòng vẽ mới).
                boolean revealed = index < match.round || (index == match.round && match.phase != GamePhase.GUESSING);
                if (revealed) { continue; }
                String word = chatForm(match.turns.get(index).word);
                // Khớp nguyên từ ("cá" không chặn "các bạn"); từ nhiều tiếng thì chặn cả kiểu viết liền.
                if (message.contains(" " + word + " ") || (word.contains(" ") && joined.contains(word.replace(" ", "")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String chatForm(String text) {
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    // Báo cáo tranh đang đoán (cả lúc đang công bố đáp án). Mỗi người đoán chỉ báo cáo một lần mỗi vòng.
    public void report(int roomId, int userId, Object payload, Consumer<Message> reply) {
        Match match = matches.get(roomId);
        if (match == null) { fail(reply, "NOT_IN_MATCH"); return; }
        synchronized (match.lock) {
            if (!(payload instanceof ReportRequest request) || !match.id.equals(request.getMatchId())) {
                fail(reply, "INVALID_REQUEST"); return;
            }
            Player player = match.players.get(userId);
            if (matches.get(roomId) != match || player == null || !player.online) {
                fail(reply, "NOT_IN_MATCH"); return;
            }
            if ((match.phase != GamePhase.GUESSING && match.phase != GamePhase.ROUND_END)
                    || request.getRoundIndex() != match.round) {
                fail(reply, "REPORT_NOT_ACTIVE"); return;
            }
            if (match.current().drawer == player) { fail(reply, "OWN_DRAWING"); return; }
            if (!match.reports.add(userId)) { fail(reply, "ALREADY_REPORTED"); return; }
            reply.accept(new Message(MessageType.ACTION_SUCCESS, "REPORT_DRAWING"));
            applyReports(match);
        }
    }

    // Phiếu của người đã thoát không được tính; số phiếu cần tính theo người đoán còn online.
    private int validReports(Match match) {
        return (int) match.reports.stream().filter(id -> match.players.get(id).online).count();
    }

    private int reportsNeeded(Match match) {
        Player drawer = match.current().drawer;
        long guessers = match.players.values().stream().filter(p -> p.online && p != drawer).count();
        return (int) guessers / 2 + 1;
    }

    // Quá nửa người đoán báo cáo: người vẽ mất số điểm đã nhận ở vòng này và không được cộng thêm.
    private void applyReports(Match match) {
        if (!match.drawingRejected && validReports(match) >= reportsNeeded(match)) {
            match.drawingRejected = true;
            match.current().drawer.score -= match.drawerRoundPoints;
            match.drawerRoundPoints = 0;
            persist(match);
            broadcastScores(match, false);
        }
        broadcast(match, MessageType.REPORT_STATUS, new ReportStatus(match.id, match.round,
                match.current().drawer.id, validReports(match), reportsNeeded(match), match.drawingRejected));
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
            // Ít người đoán hơn thì số phiếu cần cũng giảm, có thể đủ để loại tranh.
            if ((match.phase == GamePhase.GUESSING || match.phase == GamePhase.ROUND_END) && !match.reports.isEmpty()) {
                applyReports(match);
            }
            if (match.phase == GamePhase.UPLOADING && allUploaded(match)) { beginGuessing(match); }
            else if (match.phase == GamePhase.GUESSING && everyoneDone(match)) { endRound(match); }
        }
    }

    // Người chơi vào lại trận sau khi mất kết nối: gửi lại trạng thái hiện tại để client dựng lại màn hình.
    public void reconnect(int roomId, int userId, Consumer<Message> sender) {
        Match match = matches.get(roomId);
        if (match == null) { return; }
        synchronized (match.lock) {
            Player player = match.players.get(userId);
            if (matches.get(roomId) != match || player == null) { return; }
            player.online = true;
            player.sender = sender;
            int remaining = remainingSeconds(match);
            send(player, MessageType.ROUND_CATEGORY, match.category());
            send(player, MessageType.DRAW_WORD, prompt(match, player, match.phase == GamePhase.DRAWING ? remaining : 0));
            // Trả lại tranh đang vẽ dở (trước SUBMIT_DRAW để client gửi đúng tranh đó chứ không phải tranh trắng).
            if ((match.phase == GamePhase.DRAWING || match.phase == GamePhase.UPLOADING)
                    && match.drawing(player).image == null && player.draft != null) {
                send(player, MessageType.DRAW_DRAFT, new DrawUpload(match.id, player.draft));
            }
            if (match.phase == GamePhase.UPLOADING) {
                // Client gửi ngay tranh đang có; đã gửi trước khi mất kết nối thì server báo ALREADY_UPLOADED.
                send(player, MessageType.SUBMIT_DRAW, match.id);
            } else if (match.phase == GamePhase.GUESSING || match.phase == GamePhase.ROUND_END) {
                Turn turn = match.current();
                Player drawer = turn.drawer;
                send(player, MessageType.ROUND_START, roundInfo(match, match.phase == GamePhase.GUESSING ? remaining : 0));
                if (player != drawer && (player.solved || player.attempts >= 3)) {
                    send(player, MessageType.GUESS_RESULT, new GuessResult(match.id, match.round, player.solved,
                            Math.max(0, 3 - player.attempts), true, player.solved ? "CORRECT" : "GUESS_LOCKED"));
                }
                if (match.reports.contains(userId)) { send(player, MessageType.ACTION_SUCCESS, "REPORT_DRAWING"); }
                if (match.phase == GamePhase.ROUND_END) {
                    send(player, MessageType.TIME_UP, new TimerInfo(match.id, GamePhase.ROUND_END, 0));
                    send(player, MessageType.ANSWER, new RoundAnswer(match.id, match.round, turn.word));
                }
                // Thêm một người đoán thì số phiếu báo cáo cần cũng thay đổi.
                if (!match.reports.isEmpty()) { applyReports(match); }
            }
            persist(match);
            broadcastScores(match, false);
            sendTimer(match);
        }
    }

    private int remainingSeconds(Match match) {
        long remaining = Math.max(0, match.deadline - clock.getAsLong());
        return (int) ((remaining + 999_999_999L) / 1_000_000_000L);
    }

    // Bắt đầu vòng `stage`: mỗi người nhận một từ của chủ đề vòng này và có 60 giây để vẽ.
    private void beginStage(Match match, int stage) {
        match.stage = stage;
        match.phase = GamePhase.DRAWING;
        match.deadline = clock.getAsLong() + seconds(DRAW_SECONDS);
        for (Player player : match.order) { player.draft = null; }
        broadcast(match, MessageType.ROUND_CATEGORY, match.category());
        for (Player player : match.order) { send(player, MessageType.DRAW_WORD, prompt(match, player, DRAW_SECONDS)); }
        broadcastScores(match, false);
        sendTimer(match);
    }

    private DrawingPrompt prompt(Match match, Player player, int seconds) {
        return new DrawingPrompt(match.id, match.roomId, match.category(), match.drawing(player).word, seconds,
                match.stage, match.plans.size());
    }

    private RoundInfo roundInfo(Match match, int seconds) {
        Turn turn = match.current();
        return new RoundInfo(match.id, match.round, match.turns.size(), turn.drawer.id, turn.drawer.username,
                match.category(), turn.image, seconds, match.hint, match.stage, match.plans.size());
    }

    private void beginUpload(Match match) {
        match.phase = GamePhase.UPLOADING;
        match.deadline = clock.getAsLong() + seconds(UPLOAD_SECONDS);
        broadcast(match, MessageType.SUBMIT_DRAW, match.id);
        if (allUploaded(match)) { beginGuessing(match); }
    }

    private boolean allUploaded(Match match) {
        return match.players.values().stream().allMatch(p -> !p.online || match.drawing(p).image != null);
    }

    private void beginGuessing(Match match) {
        try {
            for (Player player : match.order) {
                Turn turn = match.drawing(player);
                if (turn.image == null) {
                    // Người không gửi được tranh (thường do mất kết nối) dùng bản nháp cuối cùng, không có thì tranh trắng.
                    turn.image = player.draft != null ? player.draft : drawings.blank();
                    turn.imagePath = drawings.save(match.id, player.id, match.stage, turn.image).toString();
                }
            }
        } catch (IOException e) { abort(match, "DRAWING_STORAGE_ERROR"); return; }
        match.round = match.stage * match.order.size() - 1;
        nextRound(match);
    }

    private void nextRound(Match match) {
        match.round++;
        // Đoán xong tranh của vòng này: sang vòng (chủ đề) tiếp theo, hoặc kết thúc trận.
        if (match.round >= (match.stage + 1) * match.order.size()) {
            if (match.stage + 1 < match.plans.size()) { beginStage(match, match.stage + 1); }
            else { finish(match); }
            return;
        }
        match.phase = GamePhase.GUESSING;
        match.roundStarted = clock.getAsLong();
        match.roundWallStarted = System.currentTimeMillis();
        match.roundStarts.put(match.round, match.roundWallStarted);
        persist(match);
        match.deadline = match.roundStarted + seconds(GUESS_SECONDS);
        match.firstCorrect = null;
        match.hintRevealed = false;
        match.reports.clear();
        match.drawingRejected = false;
        match.drawerRoundPoints = 0;
        for (Player player : match.order) { player.attempts = 0; player.solved = false; }
        match.hint = WordHints.mask(match.current().word, Set.of());
        broadcast(match, MessageType.ROUND_START, roundInfo(match, GUESS_SECONDS));
        broadcastScores(match, false);
        sendTimer(match);
        if (everyoneDone(match)) { endRound(match); }
    }

    // Mở một chữ cái ngẫu nhiên; từ chỉ có một chữ cái thì không mở vì sẽ lộ đáp án.
    private void revealHint(Match match) {
        match.hintRevealed = true;
        String word = match.current().word;
        int letters = WordHints.letterCount(word);
        if (letters < 2) { return; }
        String hint = WordHints.mask(word, Set.of(random.nextInt(letters)));
        match.hint = hint;
        broadcast(match, MessageType.HINT, new RoundHint(match.id, match.round, hint));
    }

    private boolean everyoneDone(Match match) {
        Player drawer = match.current().drawer;
        return match.players.values().stream().filter(p -> p.online && p != drawer)
                .allMatch(p -> p.solved || p.attempts >= 3);
    }

    private void endRound(Match match) {
        match.roundEnds.put(match.round, System.currentTimeMillis());
        persist(match);
        match.phase = GamePhase.ROUND_END;
        match.deadline = clock.getAsLong() + seconds(REVEAL_SECONDS);
        broadcast(match, MessageType.TIME_UP, new TimerInfo(match.id, GamePhase.ROUND_END, 0));
        broadcast(match, MessageType.ANSWER, new RoundAnswer(match.id, match.round, match.current().word));
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
        if (match.round >= 0 && match.round < match.turns.size() && match.roundStarts.containsKey(match.round)) {
            match.roundEnds.putIfAbsent(match.round, match.endedAt);
        }
        persist(match);
        broadcast(match, MessageType.GAME_END, match.id);
        broadcast(match, MessageType.GAME_RESULT, new GameResult(match.id, scores(match, true)));
        match.finished.run();
    }

    private boolean persist(Match match) {
        List<HistoryData.Round> rounds = new ArrayList<>();
        // Chỉ lưu các lượt của những vòng đã bắt đầu, không để lộ từ khóa của vòng chưa chơi.
        for (int i=0; i<match.turns.size(); i++) {
            Turn t=match.turns.get(i);
            if (t.stage > match.stage) { break; }
            rounds.add(new HistoryData.Round(i,t.drawer.id,t.drawer.username,t.word,t.imagePath,
                    match.roundStarts.get(i),match.roundEnds.get(i),List.copyOf(t.guesses)));
        }
        try {
            historyDAO.save(new HistoryData.Detail(match.id,match.roomId,match.categories,match.startedAt,
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
        broadcast(match, MessageType.TIME_UPDATE, new TimerInfo(match.id, match.phase, remainingSeconds(match)));
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
            boolean solvedRound = player.solved
                    && (match.phase == GamePhase.GUESSING || match.phase == GamePhase.ROUND_END);
            result.add(new ScoreEntry(player.id, player.username, player.score, ranked ? rank : 0,
                    player.average(), player.online, solvedRound));
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
        final String id = UUID.randomUUID().toString();
        final int roomId;
        final List<GamePlan> plans;
        // Tên các chủ đề, lưu vào lịch sử trận (cột category_name dài tối đa 100 ký tự).
        final String categories;
        // Mọi lượt đoán của trận theo thứ tự: vòng 0 (người 1, 2, ...), vòng 1, ...
        final List<Turn> turns = new ArrayList<>();
        int stage;
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
        boolean hintRevealed;
        // Gợi ý đang hiện của vòng này, gửi lại cho người vào lại trận.
        String hint;
        // Báo cáo của vòng đang đoán.
        final Set<Integer> reports = new HashSet<>();
        boolean drawingRejected;
        int drawerRoundPoints;
        ScheduledFuture<?> timer;

        Match(RoomInfo room, List<GamePlan> plans, Map<Integer, Consumer<Message>> senders, Object lock, Runnable finished) {
            roomId = room.getRoomId();
            this.plans = List.copyOf(plans);
            String names = String.join(", ", plans.stream().map(GamePlan::getCategory).toList());
            categories = names.length() <= 100 ? names : names.substring(0, 99) + "…";
            this.lock = lock;
            this.finished = finished;
            for (int i = 0; i < room.getPlayers().size(); i++) {
                PlayerInfo info = room.getPlayers().get(i);
                Player player = new Player(info.getUserId(), info.getUsername(), i, senders.get(info.getUserId()));
                players.put(player.id, player);
                order.add(player);
            }
            for (int s = 0; s < plans.size(); s++) {
                for (Player player : order) { turns.add(new Turn(s, player, plans.get(s).getWords().get(player.index))); }
            }
        }

        String category() { return plans.get(stage).getCategory(); }
        // Tranh người chơi vẽ ở vòng hiện tại.
        Turn drawing(Player player) { return turns.get(stage * order.size() + player.index); }
        // Lượt đang được đoán.
        Turn current() { return turns.get(round); }
    }

    private static class Turn {
        final int stage;
        final Player drawer;
        final String word;
        byte[] image;
        String imagePath;
        final List<HistoryData.Guess> guesses = new ArrayList<>();

        Turn(int stage, Player drawer, String word) { this.stage = stage; this.drawer = drawer; this.word = word; }
    }

    private static class Player {
        final int id;
        final String username;
        // Vị trí trong thứ tự vẽ/đoán của phòng.
        final int index;
        Consumer<Message> sender;
        boolean online = true;
        // Bản nháp tranh đang vẽ ở vòng hiện tại.
        byte[] draft;
        long draftAt;
        int score;
        int attempts;
        boolean solved;
        int correctCount;
        long totalGuessNanos;

        Player(int id, String username, int index, Consumer<Message> sender) {
            this.id = id; this.username = username; this.index = index; this.sender = sender;
        }
        double average() { return correctCount == 0 ? Double.POSITIVE_INFINITY : totalGuessNanos / 1_000_000_000.0 / correctCount; }
    }
}
