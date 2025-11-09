package com.queuectl.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DatabaseUtil {
    private static HikariDataSource ds;

    private DatabaseUtil() {}

    public static synchronized DataSource getDataSource() {
        if (ds == null) {
            Properties p = new Properties();
            try (InputStream in = DatabaseUtil.class.getResourceAsStream("/application.properties")) {
                if (in == null) throw new RuntimeException("application.properties not found on classpath");
                p.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load application.properties", e);
            }

            HikariConfig cfg = new HikariConfig();
            String jdbcUrl = p.getProperty("db.url");
            if (jdbcUrl == null) throw new RuntimeException("db.url not set in application.properties");

            cfg.setJdbcUrl(jdbcUrl);
            cfg.setUsername(p.getProperty("db.user"));
            cfg.setPassword(p.getProperty("db.password"));
            cfg.setMaximumPoolSize(Integer.parseInt(p.getProperty("db.pool.size", "10")));
            cfg.setAutoCommit(false);
            cfg.setPoolName("queuectl-pool");

            ds = new HikariDataSource(cfg);

            // --- DEBUG: print the actual DB/catalog and user this app connected to ---
            try (java.sql.Connection c = ds.getConnection();
                 java.sql.PreparedStatement ps = c.prepareStatement("SELECT DATABASE() AS db, USER() AS user, @@session.time_zone AS tz");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("DEBUG: app connected to DB=" + rs.getString("db")
                            + " as user=" + rs.getString("user")
                            + " tz=" + rs.getString("tz"));
                }
            } catch (Exception ex) {
                System.err.println("DEBUG: failed to query DB info: " + ex.getMessage());
                // don't throw — debug should not break startup
            }
            // --- end debug ---
        }
        return ds;
    }


    public static synchronized void close() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
}
