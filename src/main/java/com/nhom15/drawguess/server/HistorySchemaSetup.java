package com.nhom15.drawguess.server;
import com.nhom15.drawguess.server.dao.DBConnection;
import java.nio.charset.StandardCharsets;
import java.sql.*;
public final class HistorySchemaSetup {
    private HistorySchemaSetup() {}
    public static void main(String[] args) throws Exception {
        try (var input=HistorySchemaSetup.class.getResourceAsStream("/db/history.sql")) {
            if(input==null) throw new IllegalStateException("Không tìm thấy db/history.sql");
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$","");
            try(Connection c=DBConnection.getConnection(); Statement s=c.createStatement()) {
                for(String command:sql.split(";")) if(!command.isBlank()) s.execute(command);
            }
            System.out.println("Đã tạo/kiểm tra các bảng lịch sử trận đấu.");
        }
    }
}
