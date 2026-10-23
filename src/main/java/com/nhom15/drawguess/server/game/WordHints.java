package com.nhom15.drawguess.server.game;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class WordHints {
    private WordHints() {
    }

    // Mỗi chữ cái thành "_", các ký tự cách nhau một dấu cách, các từ cách nhau ba dấu cách.
    // Ví dụ "Con mèo" -> "_ _ _   _ _ _"; mở chữ thứ 4 -> "_ _ _   m _ _".
    public static String mask(String word, Set<Integer> revealedLetters) {
        List<String> words = new ArrayList<>();
        int letter = 0;
        for (String token : normalize(word).split(" ")) {
            List<String> symbols = new ArrayList<>();
            for (int codePoint : token.codePoints().toArray()) {
                if (Character.isLetterOrDigit(codePoint)) {
                    symbols.add(revealedLetters.contains(letter) ? Character.toString(codePoint) : "_");
                    letter++;
                } else {
                    symbols.add(Character.toString(codePoint));
                }
            }
            words.add(String.join(" ", symbols));
        }
        return String.join("   ", words);
    }

    public static int letterCount(String word) {
        return (int) normalize(word).codePoints().filter(Character::isLetterOrDigit).count();
    }

    // NFC gộp chữ và dấu tiếng Việt thành một ký tự, để dấu không bị tách ra khỏi "_".
    private static String normalize(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
    }
}
