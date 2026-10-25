package com.nhom15.drawguess.server.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GamePlan {
    private final String category;
    private final List<String> words;

    public GamePlan(String category, List<String> words) {
        this.category = category;
        this.words = List.copyOf(words);
    }

    public String getCategory() { return category; }
    public List<String> getWords() { return words; }

    // Chọn từ khóa cho từng vòng. `topics`: tên chủ đề -> các từ khác nhau; `stages`: chủ đề của mỗi vòng,
    // để trống là ngẫu nhiên (ưu tiên chủ đề chưa dùng ở vòng khác). Trả về null nếu có vòng không đủ từ.
    public static List<GamePlan> choose(Map<String, List<String>> topics, List<String> stages, int playerCount,
                                        Random random) {
        List<String> playable = new ArrayList<>();
        topics.forEach((name, words) -> { if (words.size() >= playerCount) { playable.add(name); } });
        Set<String> used = new HashSet<>();
        for (String stage : stages) { if (!stage.isBlank()) { used.add(stage); } }
        List<GamePlan> result = new ArrayList<>();
        for (String stage : stages) {
            String name;
            if (!stage.isBlank()) {
                if (!playable.contains(stage)) { return null; }
                name = stage;
            } else {
                List<String> fresh = playable.stream().filter(topic -> !used.contains(topic)).toList();
                List<String> candidates = fresh.isEmpty() ? playable : fresh;
                if (candidates.isEmpty()) { return null; }
                name = candidates.get(random.nextInt(candidates.size()));
                used.add(name);
            }
            List<String> words = new ArrayList<>(topics.get(name));
            java.util.Collections.shuffle(words, random);
            result.add(new GamePlan(name, words.subList(0, playerCount)));
        }
        return result;
    }
}
