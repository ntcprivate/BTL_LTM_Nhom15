package com.nhom15.drawguess.common.protocol;
import java.io.Serializable;
import java.util.List;
public final class CatalogData {
    private CatalogData() {}
    public record Category(int id, String name, String description) implements Serializable {
        @Override public String toString() { return name; }
    }
    public record Word(int id, int categoryId, String content, String difficulty) implements Serializable {
        @Override public String toString() { return content + " (" + difficulty + ")"; }
    }
    public record Catalog(List<Category> categories, List<Word> words) implements Serializable {}
    public record Edit(String entity, String action, int id, int categoryId, String name, String description, String difficulty) implements Serializable {}
}
