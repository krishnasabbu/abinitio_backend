package com.workflow.engine.repository;

import com.workflow.engine.model.DbConnectionConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDbConnectionRepository implements DbConnectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDbConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DbConnectionConfig> findById(String connectionId) {
        String sql = "SELECT id, name, connection_type, jdbc_url, username, password, driver_class, created_at " +
                     "FROM DB_CONNECTION WHERE id = ?";

        List<DbConnectionConfig> results = jdbcTemplate.query(sql, new DbConnectionConfigRowMapper(), connectionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<DbConnectionConfig> findAll() {
        String sql = "SELECT id, name, connection_type, jdbc_url, username, password, driver_class, created_at " +
                     "FROM DB_CONNECTION ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new DbConnectionConfigRowMapper());
    }

    @Override
    public void save(DbConnectionConfig config) {
        String sql = "INSERT INTO DB_CONNECTION (id, name, connection_type, jdbc_url, username, password, driver_class, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDateTime createdAt = config.createdAt() != null ? config.createdAt() : LocalDateTime.now();

        jdbcTemplate.update(sql,
            config.id(),
            config.name(),
            config.connectionType(),
            config.jdbcUrl(),
            config.username(),
            config.password(),
            config.driverClass(),
            Timestamp.valueOf(createdAt)
        );
    }

    @Override
    public void update(DbConnectionConfig config) {
        String sql = "UPDATE DB_CONNECTION SET name = ?, connection_type = ?, jdbc_url = ?, username = ?, " +
                     "password = ?, driver_class = ? WHERE id = ?";

        jdbcTemplate.update(sql,
            config.name(),
            config.connectionType(),
            config.jdbcUrl(),
            config.username(),
            config.password(),
            config.driverClass(),
            config.id()
        );
    }

    @Override
    public void delete(String connectionId) {
        String sql = "DELETE FROM DB_CONNECTION WHERE id = ?";
        jdbcTemplate.update(sql, connectionId);
    }

    @Override
    public boolean existsById(String connectionId) {
        String sql = "SELECT COUNT(*) FROM DB_CONNECTION WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, connectionId);
        return count != null && count > 0;
    }

    private static class DbConnectionConfigRowMapper implements RowMapper<DbConnectionConfig> {
        @Override
        public DbConnectionConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp timestamp = rs.getTimestamp("created_at");
            LocalDateTime createdAt = timestamp != null ? timestamp.toLocalDateTime() : null;

            return new DbConnectionConfig(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("connection_type"),
                rs.getString("jdbc_url"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("driver_class"),
                createdAt
            );
        }
    }
}
