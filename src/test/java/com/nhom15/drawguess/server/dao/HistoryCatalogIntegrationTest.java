package com.nhom15.drawguess.server.dao;
import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.common.protocol.CatalogData.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="drawguess.integration",matches="true")
class HistoryCatalogIntegrationTest {
    private String schema;
    private boolean created;
    private Connection open() throws SQLException { Connection c=DBConnection.getConnection(); c.setCatalog(schema); return c; }
    @BeforeEach void setup() throws Exception {
        schema="drawguess_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=DBConnection.getConnection()) {
            try(Statement create=c.createStatement()) { create.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
            created=true; c.setCatalog(schema);
            try(Statement s=c.createStatement()) {
            s.execute("CREATE TABLE category(id INT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) UNIQUE NOT NULL,description VARCHAR(255))");
            s.execute("CREATE TABLE word(id INT AUTO_INCREMENT PRIMARY KEY,category_id INT NOT NULL,content VARCHAR(100) NOT NULL,difficulty VARCHAR(20) NOT NULL,UNIQUE(category_id,content),FOREIGN KEY(category_id) REFERENCES category(id))");
            String sql=Files.readString(Path.of("src/main/resources/db/history.sql")).replaceAll("(?m)^\\s*--.*$","");
            for(String command:sql.split(";")) if(!command.isBlank() && !command.trim().startsWith("USE ")) s.execute(command);
            }
        }
    }
    @AfterEach void cleanup() throws Exception {
        if(created && schema.matches("drawguess_test_[a-f0-9]{32}")) try(Connection c=DBConnection.getConnection(); Statement s=c.createStatement()) { s.execute("DROP DATABASE "+schema); }
    }
    private HistoryDAO history() { return new HistoryDAO() { @Override protected Connection openConnection() throws SQLException { return open(); } }; }
    private CatalogDAO catalog() { return new CatalogDAO() { @Override protected Connection openConnection() throws SQLException { return open(); } }; }
    private HistoryData.Detail match(String id,Long end,String status,List<HistoryData.Round> rounds) {
        return new HistoryData.Detail(id,1,"Động vật",1000L,end,status,List.of(new ScoreEntry(1,"người chơi",3,end==null ? 0 : 1,Double.POSITIVE_INFINITY,false)),rounds);
    }
    @Test void historyRoundTripsAndChecksMembershipAndHidesLiveAnswers() throws Exception {
        HistoryDAO h=history(); String id=UUID.randomUUID().toString();
        var guess=new HistoryData.Guess(1,"người chơi","Con mèo",true,1500L,0.5);
        var round=new HistoryData.Round(0,2,"người vẽ","Con mèo","server-data/drawings/test/2.png",1200L,1800L,List.of(guess));
        h.save(match(id,null,"IN_PROGRESS",List.of(round)));
        assertNull(h.drawing(1, id, 0));
        assertNull(h.detail(1,id)); assertEquals(1,h.list(1).size());
        h.save(match(id,2000L,"COMPLETED",List.of(round))); h.save(match(id,2000L,"COMPLETED",List.of(round)));
        var detail=h.detail(1,id); assertNotNull(detail); assertEquals("Con mèo",detail.rounds().getFirst().word()); assertEquals(1,detail.rounds().getFirst().guesses().size());
        assertEquals(Double.POSITIVE_INFINITY,detail.players().getFirst().getAverageGuessTime()); assertEquals(1,detail.players().getFirst().getRank());
        assertNull(h.detail(2,id)); assertTrue(h.list(2).isEmpty());
        assertEquals(2, h.drawing(1, id, 0).drawerId());
        assertEquals(round.imagePath(), h.drawing(1, id, 0).imagePath());
        assertNull(h.drawing(2, id, 0));
        assertNull(h.drawing(1, id, 1));
    }
    @Test void failedSnapshotRollsBackAllTables() throws Exception {
        HistoryDAO h=history(); String id=UUID.randomUUID().toString();
        h.save(match(id,null,"IN_PROGRESS",List.of()));
        var invalid=new HistoryData.Round(0,2,"drawer",null,null,null,null,List.of());
        assertThrows(SQLException.class,()->h.save(match(id,2000L,"COMPLETED",List.of(invalid))));
        assertNull(h.detail(1,id)); assertEquals("IN_PROGRESS",h.list(1).getFirst().status());
    }
    @Test void catalogCrudKeepsHistorySnapshotsAndRejectsDuplicates() throws Exception {
        CatalogDAO c=catalog();
        c.edit(new Edit("CATEGORY","CREATE",0,0,"Động vật","Mô tả",null));
        int categoryId=c.list().categories().getFirst().id();
        c.edit(new Edit("WORD","CREATE",0,categoryId,"Con mèo",null,"EASY"));
        int wordId=c.list().words().getFirst().id();
        assertThrows(SQLException.class,()->c.edit(new Edit("WORD","CREATE",0,categoryId,"Con mèo",null,"EASY")));
        c.edit(new Edit("WORD","UPDATE",wordId,categoryId,"Con chó",null,"HARD"));
        assertEquals("Con chó",c.list().words().getFirst().content());
        HistoryDAO h=history(); String id=UUID.randomUUID().toString(); h.save(match(id,2000L,"COMPLETED",List.of(new HistoryData.Round(0,1,"người chơi","Con mèo",null,null,null,List.of()))));
        c.edit(new Edit("CATEGORY","UPDATE",categoryId,0,"Thú cưng","",null));
        c.edit(new Edit("CATEGORY","DELETE",categoryId,0,null,null,null));
        assertTrue(c.list().categories().isEmpty()); assertTrue(c.list().words().isEmpty());
        assertEquals("Động vật",h.detail(1,id).category()); assertEquals("Con mèo",h.detail(1,id).rounds().getFirst().word());
    }
    @Test void invalidCatalogRequestsAreRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,()->CatalogDAO.validate(new Edit("WORD","CREATE",0,0,"Mèo",null,"EASY")));
        assertThrows(IllegalArgumentException.class,()->CatalogDAO.validate(new Edit("CATEGORY","UPDATE",1,0," ","",null)));
        assertThrows(IllegalArgumentException.class,()->CatalogDAO.validate(new Edit("WORD","CREATE",0,1,"Mèo",null,"INVALID")));
    }
}
