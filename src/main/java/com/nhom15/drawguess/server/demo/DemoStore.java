package com.nhom15.drawguess.server.demo;

import com.nhom15.drawguess.common.protocol.AvatarCatalog;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import com.nhom15.drawguess.common.protocol.HistoryData;
import com.nhom15.drawguess.common.protocol.UserInfo;
import com.nhom15.drawguess.server.dao.CatalogDAO;
import com.nhom15.drawguess.server.dao.HistoryDAO;
import com.nhom15.drawguess.server.dao.UserDAO;
import com.nhom15.drawguess.server.dao.WordDAO;
import com.nhom15.drawguess.server.game.GamePlan;

import java.sql.SQLException;
import java.util.*;

// Dữ liệu chạy thử nằm hoàn toàn trong bộ nhớ: không cần MySQL, mất hết khi tắt server.
public final class DemoStore {
    public static final String DEMO_PASSWORD = "123";
    public static final String ADMIN_PASSWORD = "admin";

    private record Account(int id, String username, String password, String role, String avatarKey) {}

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Category> categories = new ArrayList<>();
    private final List<Word> words = new ArrayList<>();
    private final Map<String, HistoryData.Detail> matches = new LinkedHashMap<>();
    private int nextUserId = 1;
    private int nextCategoryId = 1;
    private int nextWordId = 1;

    public DemoStore() {
        for (int i = 1; i <= 4; i++) { addAccount("demo" + i, DEMO_PASSWORD, "USER"); }
        addAccount("admin", ADMIN_PASSWORD, "ADMIN");
        seed("Động vật", "Các con vật quen thuộc", "Con mèo", "Con chó", "Con voi", "Con hổ",
                "Con gà", "Con cá", "Con thỏ", "Con khỉ", "Con rùa", "Con bướm");
        seed("Đồ ăn", "Món ăn và trái cây", "Bánh mì", "Phở", "Quả táo", "Quả chuối",
                "Kem", "Bánh pizza", "Quả dưa hấu", "Trứng ốp la", "Bánh sinh nhật", "Cà rốt");
        seed("Đồ vật", "Đồ dùng hằng ngày", "Cái ô", "Đồng hồ", "Kính mắt", "Cái ghế",
                "Bóng đèn", "Chìa khóa", "Điện thoại", "Cây bút", "Cái kéo", "Xe đạp");
    }

    private void addAccount(String username, String password, String role) {
        accounts.put(username, new Account(nextUserId++, username, password, role, AvatarCatalog.DEFAULT));
    }

    private void seed(String category, String description, String... contents) {
        int id = nextCategoryId++;
        categories.add(new Category(id, category, description));
        for (String content : contents) { words.add(new Word(nextWordId++, id, content, "EASY")); }
    }

    private static UserInfo info(Account account) {
        return new UserInfo(account.id(), account.username(), account.role(), account.avatarKey());
    }

    // ---- Tài khoản ----

    synchronized UserInfo login(String username, String password) {
        Account account = accounts.get(username);
        return account != null && account.password().equals(password) ? info(account) : null;
    }

    synchronized boolean register(String username, String password) {
        if (accounts.containsKey(username)) { return false; }
        addAccount(username, password, "USER");
        return true;
    }

    synchronized boolean updateAvatar(int userId, String avatarKey) {
        for (Account account : accounts.values()) {
            if (account.id() == userId) {
                accounts.put(account.username(), new Account(account.id(), account.username(), account.password(),
                        account.role(), avatarKey));
                return true;
            }
        }
        return false;
    }

    // ---- Chủ đề và từ khóa ----

    synchronized Catalog catalog() {
        List<Category> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort(Comparator.comparing(Category::name));
        List<Word> sortedWords = new ArrayList<>(words);
        sortedWords.sort(Comparator.comparingInt(Word::categoryId).thenComparing(Word::content));
        return new Catalog(sortedCategories, sortedWords);
    }

    synchronized void edit(Edit edit) throws SQLException {
        CatalogDAO.validate(edit);
        boolean category = "CATEGORY".equals(edit.entity());
        String name = edit.name() == null ? null : edit.name().trim();
        switch (edit.action()) {
            case "CREATE" -> {
                if (category) {
                    requireUniqueCategory(name, 0);
                    categories.add(new Category(nextCategoryId++, name, edit.description()));
                } else {
                    requireCategory(edit.categoryId());
                    requireUniqueWord(edit.categoryId(), name, 0);
                    words.add(new Word(nextWordId++, edit.categoryId(), name, edit.difficulty()));
                }
            }
            case "UPDATE" -> {
                if (category) {
                    int index = indexOf(categories, c -> c.id() == edit.id());
                    requireUniqueCategory(name, edit.id());
                    categories.set(index, new Category(edit.id(), name, edit.description()));
                } else {
                    int index = indexOf(words, w -> w.id() == edit.id());
                    requireCategory(edit.categoryId());
                    requireUniqueWord(edit.categoryId(), name, edit.id());
                    words.set(index, new Word(edit.id(), edit.categoryId(), name, edit.difficulty()));
                }
            }
            default -> {
                if (category) {
                    categories.remove(indexOf(categories, c -> c.id() == edit.id()));
                    words.removeIf(w -> w.categoryId() == edit.id());
                } else {
                    words.remove(indexOf(words, w -> w.id() == edit.id()));
                }
            }
        }
    }

    private static <T> int indexOf(List<T> items, java.util.function.Predicate<T> match) {
        for (int i = 0; i < items.size(); i++) { if (match.test(items.get(i))) { return i; } }
        throw new IllegalArgumentException("NOT_FOUND");
    }

    private void requireCategory(int id) throws SQLException {
        if (categories.stream().noneMatch(c -> c.id() == id)) { throw new SQLException("Chủ đề không tồn tại", "23000"); }
    }

    private void requireUniqueCategory(String name, int exceptId) throws SQLException {
        if (categories.stream().anyMatch(c -> c.id() != exceptId && c.name().equalsIgnoreCase(name))) {
            throw new SQLException("Trùng tên chủ đề", "23000");
        }
    }

    private void requireUniqueWord(int categoryId, String content, int exceptId) throws SQLException {
        if (words.stream().anyMatch(w -> w.id() != exceptId && w.categoryId() == categoryId
                && w.content().equalsIgnoreCase(content))) {
            throw new SQLException("Trùng từ khóa", "23000");
        }
    }

    // Giống WordDAO: chọn ngẫu nhiên một chủ đề có đủ từ khác nhau cho số người chơi.
    synchronized GamePlan selectWords(int playerCount) {
        List<Category> candidates = new ArrayList<>();
        for (Category category : categories) {
            long distinct = words.stream().filter(w -> w.categoryId() == category.id())
                    .map(w -> w.content().trim().toLowerCase(Locale.ROOT)).distinct().count();
            if (distinct >= playerCount) { candidates.add(category); }
        }
        if (candidates.isEmpty()) { return null; }
        Collections.shuffle(candidates);
        Category chosen = candidates.getFirst();
        Map<String, String> unique = new LinkedHashMap<>();
        words.stream().filter(w -> w.categoryId() == chosen.id())
                .forEach(w -> unique.putIfAbsent(w.content().trim().toLowerCase(Locale.ROOT), w.content().trim()));
        List<String> selected = new ArrayList<>(unique.values());
        Collections.shuffle(selected);
        return new GamePlan(chosen.name(), selected.subList(0, playerCount));
    }

    // ---- Lịch sử trận ----

    synchronized void save(HistoryData.Detail detail) { matches.put(detail.id(), detail); }

    synchronized List<HistoryData.Summary> history(int userId) {
        List<HistoryData.Summary> result = new ArrayList<>();
        for (HistoryData.Detail d : matches.values()) {
            d.players().stream().filter(p -> p.getUserId() == userId).findFirst().ifPresent(p ->
                    result.add(new HistoryData.Summary(d.id(), d.roomId(), d.category(), d.startedAt(), d.endedAt(),
                            d.status(), p.getScore(), p.getRank())));
        }
        result.sort(Comparator.comparingLong(HistoryData.Summary::startedAt).reversed());
        return result.size() > 200 ? result.subList(0, 200) : result;
    }

    synchronized HistoryData.Detail detail(int userId, String matchId) {
        HistoryData.Detail d = matches.get(matchId);
        if (d == null || d.endedAt() == null || d.players().stream().noneMatch(p -> p.getUserId() == userId)) { return null; }
        return d;
    }

    synchronized HistoryDAO.DrawingReference drawing(int userId, String matchId, int roundIndex) {
        HistoryData.Detail d = detail(userId, matchId);
        if (d == null) { return null; }
        return d.rounds().stream().filter(r -> r.index() == roundIndex).findFirst()
                .map(r -> new HistoryDAO.DrawingReference(r.drawerId(), r.imagePath())).orElse(null);
    }

    // ---- DAO thay thế cho bản MySQL ----

    public UserDAO userDAO() {
        return new UserDAO() {
            @Override public UserInfo login(String username, String password) { return DemoStore.this.login(username, password); }
            @Override public boolean usernameExists(String username) { synchronized (DemoStore.this) { return accounts.containsKey(username); } }
            @Override public boolean register(String username, String password) { return DemoStore.this.register(username, password); }
            @Override public boolean updateAvatar(int userId, String avatarKey) { return DemoStore.this.updateAvatar(userId, avatarKey); }
        };
    }

    public WordDAO wordDAO() {
        return new WordDAO() {
            @Override public GamePlan selectWords(int playerCount) { return DemoStore.this.selectWords(playerCount); }
        };
    }

    public CatalogDAO catalogDAO() {
        return new CatalogDAO() {
            @Override public Catalog list() { return catalog(); }
            @Override public void edit(Edit edit) throws SQLException { DemoStore.this.edit(edit); }
        };
    }

    public HistoryDAO historyDAO() {
        return new HistoryDAO() {
            @Override public void save(HistoryData.Detail d) { DemoStore.this.save(d); }
            @Override public List<HistoryData.Summary> list(int userId) { return history(userId); }
            @Override public HistoryData.Detail detail(int userId, String id) { return DemoStore.this.detail(userId, id); }
            @Override public DrawingReference drawing(int userId, String matchId, int roundIndex) {
                return DemoStore.this.drawing(userId, matchId, roundIndex);
            }
        };
    }
}
