package com.nhom15.drawguess.server.dao;

import com.nhom15.drawguess.server.game.GamePlan;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WordDAO {
    public GamePlan selectWords(int playerCount) throws SQLException {
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
        List<Integer> candidates = new ArrayList<>();
        words.forEach((id, entries) -> {
            if (entries.size() >= playerCount) { candidates.add(id); }
        });
        if (candidates.isEmpty()) { return null; }
        Collections.shuffle(candidates);
        int categoryId = candidates.getFirst();
        List<String> selected = new ArrayList<>(words.get(categoryId).values());
        Collections.shuffle(selected);
        return new GamePlan(names.get(categoryId), selected.subList(0, playerCount));
    }
}
