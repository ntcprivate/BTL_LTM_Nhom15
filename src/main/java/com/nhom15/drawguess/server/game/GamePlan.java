package com.nhom15.drawguess.server.game;

import java.util.List;

public class GamePlan {
    private final String category;
    private final List<String> words;

    public GamePlan(String category, List<String> words) {
        this.category = category;
        this.words = List.copyOf(words);
    }

    public String getCategory() { return category; }
    public List<String> getWords() { return words; }
}
