package com.nhom15.drawguess.server;

import com.nhom15.drawguess.server.dao.DBConnection;

import java.sql.Connection;

public class DatabaseTest {

    public static void main(String[] args) {

        try (
            Connection connection =
                    DBConnection.getConnection()
        ) {

            System.out.println(
                    "Kết nối MySQL thành công!"
            );

            System.out.println(
                    "Database: "
                    + connection.getCatalog()
            );

        } catch (Exception e) {

            System.out.println(
                    "Kết nối MySQL thất bại!"
            );

            e.printStackTrace();
        }
    }
}