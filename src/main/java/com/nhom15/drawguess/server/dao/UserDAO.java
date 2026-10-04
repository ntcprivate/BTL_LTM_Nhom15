package com.nhom15.drawguess.server.dao;

import com.nhom15.drawguess.common.protocol.UserInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    public UserInfo login(String username, String password) throws SQLException {
        // So sánh mật khẩu chính xác, kể cả chữ hoa và khoảng trắng cuối.
        String sql = "SELECT id, username, role FROM user_account WHERE username = ? "
                + "AND CAST(password AS BINARY) = CAST(? AS BINARY)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new UserInfo(result.getInt("id"), result.getString("username"),
                            result.getString("role"));
                }
                return null;
            }
        }
    }

    public boolean usernameExists(String username)
            throws SQLException {

        String sql =
                "SELECT id FROM user_account "
                + "WHERE username = ?";

        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, username);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    public boolean register(
            String username,
            String password
    ) throws SQLException {

        if (usernameExists(username)) {
            return false;
        }

        String sql =
                "INSERT INTO user_account "
                + "(username, password, role) "
                + "VALUES (?, ?, 'USER')";

        try (
                Connection connection =
                        DBConnection.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setString(1, username);
            statement.setString(2, password);

            int rows = statement.executeUpdate();

            return rows == 1;
        } catch (SQLException e) {
            // UNIQUE(username) ngăn hai client tạo cùng một tên tài khoản.
            if (e.getErrorCode() == 1062 && "23000".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }
}
