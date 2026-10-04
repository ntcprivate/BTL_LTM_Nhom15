package com.nhom15.drawguess.server.dao;
import com.nhom15.drawguess.common.protocol.*;
import java.sql.*;
import java.util.*;
public class HistoryDAO {
    public record DrawingReference(int drawerId, String imagePath) {}

    public DrawingReference drawing(int userId, String matchId, int roundIndex) throws SQLException {
        String sql = "SELECT r.drawer_id,r.image_path FROM match_round r "
                + "JOIN game_match m ON m.id=r.match_id "
                + "JOIN match_player p ON p.match_id=m.id "
                + "WHERE r.match_id=? AND r.round_index=? AND p.user_id=? AND m.ended_at IS NOT NULL";
        try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, matchId);
            s.setInt(2, roundIndex);
            s.setInt(3, userId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new DrawingReference(r.getInt("drawer_id"), r.getString("image_path")) : null;
            }
        }
    }

    protected Connection openConnection() throws SQLException { return DBConnection.getConnection(); }
    public void save(HistoryData.Detail d) throws SQLException {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO game_match VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE ended_at=VALUES(ended_at),status=VALUES(status)")) {
                    s.setString(1,d.id()); s.setInt(2,d.roomId()); s.setString(3,d.category()); s.setLong(4,d.startedAt()); s.setObject(5,d.endedAt()); s.setString(6,d.status()); s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO match_player VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE score=VALUES(score),player_rank=VALUES(player_rank),average_seconds=VALUES(average_seconds),online=VALUES(online)")) {
                    for (ScoreEntry p : d.players()) {
                        s.setString(1,d.id()); s.setInt(2,p.getUserId()); s.setString(3,p.getUsername()); s.setInt(4,p.getScore()); s.setInt(5,p.getRank()); s.setObject(6,Double.isFinite(p.getAverageGuessTime()) ? p.getAverageGuessTime() : null); s.setBoolean(7,p.isOnline()); s.addBatch();
                    } s.executeBatch();
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO match_round VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE image_path=VALUES(image_path),started_at=VALUES(started_at),ended_at=VALUES(ended_at)")) {
                    for (HistoryData.Round r : d.rounds()) {
                        s.setString(1,d.id()); s.setInt(2,r.index()); s.setInt(3,r.drawerId()); s.setString(4,r.drawer()); s.setString(5,r.word()); s.setString(6,r.imagePath()); s.setObject(7,r.startedAt()); s.setObject(8,r.endedAt()); s.addBatch();
                    } s.executeBatch();
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO round_guess VALUES(?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE guess_index=VALUES(guess_index)")) {
                    for (HistoryData.Round r : d.rounds()) for (int i=0;i<r.guesses().size();i++) {
                        HistoryData.Guess g=r.guesses().get(i);
                        s.setString(1,d.id()); s.setInt(2,r.index()); s.setInt(3,i); s.setInt(4,g.userId()); s.setString(5,g.username()); s.setString(6,g.answer()); s.setBoolean(7,g.correct()); s.setLong(8,g.at()); s.setDouble(9,g.elapsedSeconds()); s.addBatch();
                    } s.executeBatch();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }
    public List<HistoryData.Summary> list(int userId) throws SQLException {
        List<HistoryData.Summary> result=new ArrayList<>();
        try (Connection c=openConnection(); PreparedStatement s=c.prepareStatement("SELECT m.*,p.score,p.player_rank FROM game_match m JOIN match_player p ON p.match_id=m.id WHERE p.user_id=? ORDER BY m.started_at DESC LIMIT 200")) {
            s.setInt(1,userId); try (ResultSet r=s.executeQuery()) { while(r.next()) result.add(new HistoryData.Summary(r.getString("id"),r.getInt("room_id"),r.getString("category_name"),r.getLong("started_at"),(Long)r.getObject("ended_at"),r.getString("status"),r.getInt("score"),r.getInt("player_rank"))); }
        } return result;
    }
    public HistoryData.Detail detail(int userId,String id) throws SQLException {
        try (Connection c=openConnection()) {
            c.setAutoCommit(false);
            try {
                HistoryData.Summary summary;
                try (PreparedStatement s=c.prepareStatement("SELECT m.*,p.score,p.player_rank FROM game_match m JOIN match_player p ON p.match_id=m.id WHERE m.id=? AND p.user_id=? AND m.ended_at IS NOT NULL")) {
                    s.setString(1,id); s.setInt(2,userId); try(ResultSet r=s.executeQuery()) {
                        if(!r.next()) { c.commit(); return null; }
                        summary=new HistoryData.Summary(id,r.getInt("room_id"),r.getString("category_name"),r.getLong("started_at"),(Long)r.getObject("ended_at"),r.getString("status"),r.getInt("score"),r.getInt("player_rank"));
                    }
                }
                List<ScoreEntry> players=new ArrayList<>(); List<HistoryData.Round> rounds=new ArrayList<>();
                try(PreparedStatement s=c.prepareStatement("SELECT * FROM match_player WHERE match_id=? ORDER BY player_rank,user_id")) {
                    s.setString(1,id); try(ResultSet r=s.executeQuery()) { while(r.next()) players.add(new ScoreEntry(r.getInt("user_id"),r.getString("username"),r.getInt("score"),r.getInt("player_rank"),r.getObject("average_seconds")==null ? Double.POSITIVE_INFINITY : r.getDouble("average_seconds"),r.getBoolean("online"))); }
                }
                try(PreparedStatement s=c.prepareStatement("SELECT * FROM match_round WHERE match_id=? ORDER BY round_index")) {
                    s.setString(1,id); try(ResultSet r=s.executeQuery()) { while(r.next()) {
                        List<HistoryData.Guess> guesses=new ArrayList<>();
                        try(PreparedStatement gs=c.prepareStatement("SELECT * FROM round_guess WHERE match_id=? AND round_index=? ORDER BY guessed_at,guess_index")) {
                            gs.setString(1,id); gs.setInt(2,r.getInt("round_index")); try(ResultSet g=gs.executeQuery()) { while(g.next()) guesses.add(new HistoryData.Guess(g.getInt("user_id"),g.getString("username"),g.getString("answer"),g.getBoolean("correct"),g.getLong("guessed_at"),g.getDouble("elapsed_seconds"))); }
                        }
                        rounds.add(new HistoryData.Round(r.getInt("round_index"),r.getInt("drawer_id"),r.getString("drawer_name"),r.getString("word_content"),r.getString("image_path"),(Long)r.getObject("started_at"),(Long)r.getObject("ended_at"),guesses));
                    } }
                }
                c.commit(); return new HistoryData.Detail(id,summary.roomId(),summary.category(),summary.startedAt(),summary.endedAt(),summary.status(),players,rounds);
            } catch(SQLException e) { c.rollback(); throw e; }
        }
    }
}
