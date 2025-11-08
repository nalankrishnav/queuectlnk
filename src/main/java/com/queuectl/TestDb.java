package com.queuectl;

import com.queuectl.util.DatabaseUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDb {
    public static void main(String[] args) throws Exception {
        DataSource ds = DatabaseUtil.getDataSource();
        try (Connection c = ds.getConnection()) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM jobs");
            if (rs.next()) {
                System.out.println("jobs count = " + rs.getInt(1));
            } else {
                System.out.println("jobs table exists but count unreadable");
            }
        }
        DatabaseUtil.close();
    }
}
