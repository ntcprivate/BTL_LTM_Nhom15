package com.nhom15.drawguess.server.game;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WordHintsTest {
    @Test
    void masksEachLetterAndSeparatesWordsWithWideGaps() {
        assertEquals("_ _ _   _ _ _", WordHints.mask("Con mèo", Set.of()));
        assertEquals("_ _ _   m _ _", WordHints.mask("  Con   mèo ", Set.of(3)));
        assertEquals(6, WordHints.letterCount("Con mèo"));
    }

    @Test
    void keepsPunctuationVisibleAndDoesNotCountIt() {
        assertEquals("_ - _ _ _ _ _", WordHints.mask("T-shirt", Set.of()));
        assertEquals(6, WordHints.letterCount("T-shirt"));
    }

    @Test
    void decomposedVietnameseAccentsAreNotLeakedAsSeparateSymbols() {
        String decomposed = Normalizer.normalize("mèo", Normalizer.Form.NFD);
        assertEquals("_ _ _", WordHints.mask(decomposed, Set.of()));
        assertEquals("_ è _", WordHints.mask(decomposed, Set.of(1)));
        assertEquals(3, WordHints.letterCount(decomposed));
    }
}
