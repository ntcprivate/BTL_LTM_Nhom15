package com.nhom15.drawguess.server.dao;

import com.nhom15.drawguess.server.game.GamePlan;
import com.nhom15.drawguess.server.game.RoomManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class WordDAO {
    private final Random random = new Random();

    // Một vòng với chủ đề ngẫu nhiên có đủ từ khác nhau cho số người chơi.
    public GamePlan selectWords(int playerCount) throws SQLException {
        List<GamePlan> plans = selectStages(List.of(""), playerCount);
        return plans == null ? null : plans.getFirst();
    }

    // Từ khóa cho từng vòng theo chủ đề chủ phòng chọn ("" = ngẫu nhiên); null nếu có vòng không đủ từ.
    public List<GamePlan> selectStages(List<String> stages, int playerCount) throws SQLException {
        return GamePlan.choose(loadTopics(), stages, playerCount, random);
    }

    // Tên các chủ đề chơi được (đủ từ cho ít nhất số người tối thiểu), theo thứ tự trong cơ sở dữ liệu.
    public List<String> topics() throws SQLException {
        List<String> names = new ArrayList<>();
        loadTopics().forEach((name, words) -> { if (words.size() >= RoomManager.MIN_PLAYERS) { names.add(name); } });
        return names;
    }

    // Tên chủ đề -> các từ khóa khác nhau (không phân biệt hoa thường) của chủ đề đó.
    protected Map<String, List<String>> loadTopics() throws SQLException {
        String sql = "SELECT c.id, c.name, w.content FROM category c "
                + "JOIN word w ON w.category_id = c.id ORDER BY c.id, w.id";
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Map<String, String>> words = new LinkedHashMap<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String content = rows.getString("content");
                if (content == null || content.isBlank() || content.trim().length() > 100) { continue; }
                int id = rows.getInt("id");
                names.put(id, rows.getString("name"));
                words.computeIfAbsent(id, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(content.trim().toLowerCase(Locale.ROOT), content.trim());
            }
        }
        Map<String, List<String>> topics = new LinkedHashMap<>();
        words.forEach((id, entries) -> topics.put(names.get(id), List.copyOf(entries.values())));
        return topics;
    }
}
