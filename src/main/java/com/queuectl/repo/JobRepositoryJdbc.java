package com.queuectl.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import com.queuectl.model.Job;
import com.queuectl.util.DatabaseUtil;
import java.util.List;

public class JobRepositoryJdbc {
    private final DataSource ds;

    public JobRepositoryJdbc() {
        this.ds = DatabaseUtil.getDataSource();
    }

    /**
     * Insert or update a job (upsert-like using ON DUPLICATE KEY UPDATE).
     */
    public void save(Job job) {
        String sql = "INSERT INTO jobs (id, command, state, attempts, max_retries, next_try_at, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                     "ON DUPLICATE KEY UPDATE command = VALUES(command), state = VALUES(state), attempts = VALUES(attempts), " +
                     "max_retries = VALUES(max_retries), next_try_at = VALUES(next_try_at), updated_at = NOW()";

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(true);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, job.getId());
                ps.setString(2, job.getCommand());
                ps.setString(3, job.getState() == null ? "pending" : job.getState());
                ps.setInt(4, job.getAttempts());
                ps.setInt(5, job.getMaxRetries() > 0 ? job.getMaxRetries() : 3);

                if (job.getNextTryAt() == null) {
                    // explicitly set NULL so DB will treat it as immediately eligible (or use condition in select)
                    ps.setNull(6, java.sql.Types.TIMESTAMP);
                } else {
                    ps.setTimestamp(6, Timestamp.from(job.getNextTryAt().atZone(java.time.ZoneOffset.UTC).toInstant()));
                }

                int rows = ps.executeUpdate();
                if (rows > 0) {
                    System.out.println("✅ Job saved: " + job.getId());
                } else {
                    System.out.println("⚠️ No rows inserted (check primary key)");
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to save job: " + job.getId(), ex);
        }
    }



    /**
     * Simple find by id.
     */
    public Optional<Job> findById(String id) {
        String sql = "SELECT * FROM jobs WHERE id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rowToJob(rs));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to find job: " + id, ex);
        }
        return Optional.empty();
    }

    /**
     * Find a single pending job whose next_try_at <= NOW().
     * NOTE: This method does NOT claim or lock the job; claiming must be done in worker with FOR UPDATE SKIP LOCKED.
     */
 // Claim next pending job safely
    public Job findAndClaimNext(String workerId, int leaseSeconds) {
        String select = "SELECT id, command, attempts, max_retries FROM jobs " +
                        "WHERE state = 'pending' AND (next_try_at IS NULL OR next_try_at <= NOW()) " +
                        "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED";
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(select);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    c.commit();
                    return null;
                }
                String id = rs.getString("id");
                String command = rs.getString("command");
                int attempts = rs.getInt("attempts");
                int maxRetries = rs.getInt("max_retries");

                Timestamp expiresAt = Timestamp.valueOf(LocalDateTime.now().plusSeconds(leaseSeconds));


                String upd = "UPDATE jobs SET state='processing', worker_id=?, processing_expires_at=?, updated_at=NOW() WHERE id=?";
                try (PreparedStatement up = c.prepareStatement(upd)) {
                    up.setString(1, workerId);
                    up.setTimestamp(2, expiresAt);
                    up.setString(3, id);
                    int u = up.executeUpdate();
                    System.out.println("findAndClaimNext: claimed id=" + id + " updatedRows=" + u);
                }

                c.commit(); // important: persist claim BEFORE executing external command
                Job j = new Job();
                j.setId(id);
                j.setCommand(command);
                j.setAttempts(attempts);
                j.setMaxRetries(maxRetries);
                return j;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }



    // Mark job completed
 // ---------- markCompleted ----------
 // markCompleted
    public void markCompleted(String id, int exitCode, String stdout, String stderr) {
        String sql = "UPDATE jobs SET state='completed', exit_code=?, stdout=?, stderr=?, updated_at=NOW() WHERE id=?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(true);
            ps.setInt(1, exitCode);
            ps.setString(2, stdout);
            ps.setString(3, stderr);
            ps.setString(4, id);
            int updated = ps.executeUpdate();
            System.out.println("markCompleted: rows=" + updated + " id=" + id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // markRetry (persist incremented attempts and next_try_at)
 // change signature to accept Instant for clarity
    public void markRetry(String id, int attempts, LocalDateTime nextTry,
            int exitCode, String stdout, String stderr) {
			String sql = "UPDATE jobs SET state='pending', attempts=?, next_try_at=?, exit_code=?, stdout=?, stderr=?, updated_at=NOW() WHERE id=?";
			try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
			c.setAutoCommit(true);
			ps.setInt(1, attempts);
			ps.setTimestamp(2, Timestamp.valueOf(nextTry));
			ps.setInt(3, exitCode);
			ps.setString(4, stdout);
			ps.setString(5, stderr);
			ps.setString(6, id);
			int updated = ps.executeUpdate();
			System.out.println("markRetry: rows=" + updated + " id=" + id + " attempts=" + attempts + " next=" + nextTry);
			} catch (SQLException e) {
			System.err.println("markRetry FAILED for id=" + id);
			e.printStackTrace();
			throw new RuntimeException(e);
			}
}




    // markDead (persist attempts, set dead, clear next_try_at)
 // markDead: persist attempts, set dead, clear next_try_at
    public void markDead(String id, int attempts, int exitCode, String stdout, String stderr) {
        String sql = "UPDATE jobs SET state='dead', attempts=?, next_try_at=NULL, exit_code=?, stdout=?, stderr=?, updated_at=NOW() WHERE id=?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(true);
            ps.setInt(1, attempts);
            ps.setInt(2, exitCode);
            ps.setString(3, stdout);
            ps.setString(4, stderr);
            ps.setString(5, id);
            int updated = ps.executeUpdate();
            System.out.println("markDead: rows=" + updated + " id=" + id + " attempts=" + attempts);
        } catch (SQLException e) {
            System.err.println("markDead FAILED for id=" + id);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }




    public Optional<Job> findNextPending() {
        String sql = "SELECT * FROM jobs WHERE state = 'pending' AND next_try_at <= NOW() ORDER BY created_at LIMIT 1";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(rowToJob(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to fetch next pending job", ex);
        }
        return Optional.empty();
    }

    private Job rowToJob(ResultSet rs) throws SQLException {
        Job j = new Job();
        j.setId(rs.getString("id"));
        j.setCommand(rs.getString("command"));
        j.setState(rs.getString("state"));
        j.setAttempts(rs.getInt("attempts"));
        j.setMaxRetries(rs.getInt("max_retries"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) j.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) j.setUpdatedAt(updated.toLocalDateTime());
        Timestamp nextTry = rs.getTimestamp("next_try_at");
        if (nextTry != null) j.setNextTryAt(nextTry.toLocalDateTime());

        j.setWorkerId(rs.getString("worker_id"));
        int exitCode = rs.getInt("exit_code");
        if (!rs.wasNull()) j.setExitCode(exitCode);
        j.setStdout(rs.getString("stdout"));
        j.setStderr(rs.getString("stderr"));

        return j;
    }
 // return jobs for a state (null or "all" returns all rows)
    public List<Job> listByState(String state) {
        String sql = (state == null || state.isBlank() || "all".equalsIgnoreCase(state))
                     ? "SELECT * FROM jobs ORDER BY created_at DESC"
                     : "SELECT * FROM jobs WHERE state = ? ORDER BY created_at DESC";
        List<Job> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (!sql.contains("WHERE")) { /* nothing */ }
            else ps.setString(1, state);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rowToJob(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<Job> listDead() {
        return listByState("dead");
    }

    // Move a dead job back to pending and reset attempts (retry once more)
 // Robust move from dead -> pending; sets attempts=0 and next_try_at = NOW()
    public boolean moveDeadToPending(String id) {
        final String schema = "queuecli";
        final String table = schema + ".jobs";

        String updateSql = "UPDATE " + table + " " +
                           "SET state = 'pending', attempts = 0, next_try_at = NOW(), exit_code = NULL, stdout = NULL, stderr = NULL, updated_at = NOW() " +
                           "WHERE id = ? AND LOWER(TRIM(state)) = 'dead'";
        String verifySql = "SELECT id, CONCAT('<', state, '>') AS state_raw, CHAR_LENGTH(state) AS len, attempts, next_try_at, updated_at " +
                           "FROM " + table + " WHERE id = ?";

        try (Connection c = ds.getConnection()) {
            // ensure this method runs in auto-commit mode for a single-statement update,
            // or commit manually after update. We'll use explicit commit below.
            boolean previousAuto = c.getAutoCommit();
            try {
                c.setAutoCommit(false); // transaction block
                try (PreparedStatement ps = c.prepareStatement(updateSql)) {
                    ps.setString(1, id);
                    int u = ps.executeUpdate();
                    System.out.println("moveDeadToPending: rowsUpdated=" + u + " id=" + id);
                }

                // commit so other connections see the change
                c.commit();

                // verify from same connection
                try (PreparedStatement pv = c.prepareStatement(verifySql)) {
                    pv.setString(1, id);
                    try (ResultSet rs = pv.executeQuery()) {
                        if (rs.next()) {
                            System.out.println("VERIFY: id=" + rs.getString("id")
                                    + " state=" + rs.getString("state_raw")
                                    + " len=" + rs.getInt("len")
                                    + " attempts=" + rs.getInt("attempts")
                                    + " next_try_at=" + rs.getTimestamp("next_try_at")
                                    + " updated_at=" + rs.getTimestamp("updated_at"));
                        } else {
                            System.out.println("VERIFY: no row found for id=" + id + " in schema " + schema);
                        }
                    }
                }

                return true;
            } catch (SQLException ex) {
                try { c.rollback(); } catch (SQLException _ex) {}
                throw ex;
            } finally {
                try { c.setAutoCommit(previousAuto); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            System.err.println("moveDeadToPending FAILED for id=" + id + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
    }



    // Count jobs grouped by state
    public Map<String, Integer> countByState() {
        String sql = "SELECT state, COUNT(*) cnt FROM jobs GROUP BY state";
        Map<String, Integer> map = new HashMap<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("state"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

}
