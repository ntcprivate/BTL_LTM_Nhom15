package com.nhom15.drawguess.server.dao;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import java.sql.*;
import java.util.*;
public class CatalogDAO {
    protected Connection openConnection() throws SQLException { return DBConnection.getConnection(); }
    public Catalog list() throws SQLException {
        List<Category> categories=new ArrayList<>(); List<Word> words=new ArrayList<>();
        try(Connection c=openConnection(); Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT * FROM category ORDER BY name")) { while(r.next()) categories.add(new Category(r.getInt("id"),r.getString("name"),r.getString("description"))); }
            try(ResultSet r=s.executeQuery("SELECT * FROM word ORDER BY category_id,content")) { while(r.next()) words.add(new Word(r.getInt("id"),r.getInt("category_id"),r.getString("content"),r.getString("difficulty"))); }
        } return new Catalog(categories,words);
    }
    public void edit(Edit e) throws SQLException {
        validate(e);
        try(Connection c=openConnection()) {
            c.setAutoCommit(false);
            try {
                boolean category="CATEGORY".equals(e.entity());
                if(category && "DELETE".equals(e.action())) {
                    try(PreparedStatement s=c.prepareStatement("DELETE FROM word WHERE category_id=?")) { s.setInt(1,e.id()); s.executeUpdate(); }
                }
                String sql=switch(e.action()) {
                    case "CREATE" -> category ? "INSERT INTO category(name,description) VALUES(?,?)" : "INSERT INTO word(category_id,content,difficulty) VALUES(?,?,?)";
                    case "UPDATE" -> category ? "UPDATE category SET name=?,description=? WHERE id=?" : "UPDATE word SET category_id=?,content=?,difficulty=? WHERE id=?";
                    default -> category ? "DELETE FROM category WHERE id=?" : "DELETE FROM word WHERE id=?";
                };
                try(PreparedStatement s=c.prepareStatement(sql)) {
                    int i=1;
                    if(!"DELETE".equals(e.action())) {
                        if(!category) s.setInt(i++,e.categoryId());
                        s.setString(i++,e.name().trim()); s.setString(i++,category ? e.description() : e.difficulty());
                    }
                    if(!"CREATE".equals(e.action())) s.setInt(i,e.id());
                    if(s.executeUpdate()==0) throw new IllegalArgumentException("NOT_FOUND");
                } c.commit();
            } catch(SQLException | IllegalArgumentException ex) { c.rollback(); throw ex; }
        }
    }
    public static void validate(Edit e) {
        if(e==null || !Set.of("CATEGORY","WORD").contains(e.entity()==null ? "" : e.entity()) || !Set.of("CREATE","UPDATE","DELETE").contains(e.action()==null ? "" : e.action())) throw new IllegalArgumentException("INVALID_REQUEST");
        if(!"CREATE".equals(e.action()) && e.id()<=0) throw new IllegalArgumentException("INVALID_REQUEST");
        if("DELETE".equals(e.action())) return;
        if(e.name()==null || e.name().isBlank() || e.name().trim().length()>100 || (e.description()!=null && e.description().length()>255)) throw new IllegalArgumentException("INVALID_NAME");
        if("WORD".equals(e.entity()) && (e.categoryId()<=0 || e.difficulty()==null || !Set.of("EASY","MEDIUM","HARD").contains(e.difficulty()))) throw new IllegalArgumentException("INVALID_WORD");
    }
}
