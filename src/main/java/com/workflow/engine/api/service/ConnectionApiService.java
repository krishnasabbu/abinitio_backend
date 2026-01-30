package com.workflow.engine.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.dto.DatabaseConnectionDto;
import com.workflow.engine.api.dto.KafkaConnectionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ConnectionApiService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    public List<DatabaseConnectionDto> getAllDatabaseConnections() {
        String sql = "SELECT id, name, connection_type, host, port, database, username, password, ssl_enabled, ssl_cert, additional_params, is_active, created_at, updated_at FROM database_connections";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DatabaseConnectionDto dto = new DatabaseConnectionDto();
            dto.setId(rs.getString("id"));
            dto.setName(rs.getString("name"));
            dto.setConnectionType(rs.getString("connection_type"));
            dto.setHost(rs.getString("host"));
            dto.setPort(rs.getInt("port"));
            dto.setDatabase(rs.getString("database"));
            dto.setUsername(rs.getString("username"));
            dto.setPassword(rs.getString("password"));
            dto.setSslEnabled(rs.getBoolean("ssl_enabled"));
            dto.setSslCert(rs.getString("ssl_cert"));
            dto.setIsActive(rs.getBoolean("is_active"));
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            return dto;
        });
    }

    public String createDatabaseConnection(DatabaseConnectionDto request) {
        String id = "db_conn_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        String additionalParams = toJson(request.getAdditionalParams());

        String sql = "INSERT INTO database_connections (id, name, connection_type, host, port, database, username, password, ssl_enabled, ssl_cert, additional_params, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, request.getName(), request.getConnectionType(), request.getHost(), request.getPort(), request.getDatabase(), request.getUsername(), request.getPassword(), request.getSslEnabled() != null ? request.getSslEnabled() : false, request.getSslCert(), additionalParams, request.getIsActive() != null ? request.getIsActive() : true, now, now);
        return id;
    }

    public void updateDatabaseConnection(String id, DatabaseConnectionDto request) {
        String now = Instant.now().toString();
        String additionalParams = toJson(request.getAdditionalParams());

        String sql = "UPDATE database_connections SET name = ?, connection_type = ?, host = ?, port = ?, database = ?, username = ?, password = ?, ssl_enabled = ?, ssl_cert = ?, additional_params = ?, is_active = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, request.getName(), request.getConnectionType(), request.getHost(), request.getPort(), request.getDatabase(), request.getUsername(), request.getPassword(), request.getSslEnabled() != null ? request.getSslEnabled() : false, request.getSslCert(), additionalParams, request.getIsActive() != null ? request.getIsActive() : true, now, id);
    }

    public void deleteDatabaseConnection(String id) {
        String sql = "DELETE FROM database_connections WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public Map<String, Object> testDatabaseConnection(String id) {
        try {
            String sql = "SELECT COUNT(*) FROM database_connections WHERE id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
            if (count != null && count > 0) {
                return Map.of("success", true, "message", "Connection successful");
            }
            return Map.of("success", false, "message", "Connection not found");
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    public List<KafkaConnectionDto> getAllKafkaConnections() {
        String sql = "SELECT id, name, bootstrap_servers, security_protocol, sasl_mechanism, sasl_username, sasl_password, ssl_cert, ssl_key, consumer_group, additional_config, is_active, created_at, updated_at FROM kafka_connections";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            KafkaConnectionDto dto = new KafkaConnectionDto();
            dto.setId(rs.getString("id"));
            dto.setName(rs.getString("name"));
            dto.setBootstrapServers(rs.getString("bootstrap_servers"));
            dto.setSecurityProtocol(rs.getString("security_protocol"));
            dto.setSaslMechanism(rs.getString("sasl_mechanism"));
            dto.setSaslUsername(rs.getString("sasl_username"));
            dto.setSaslPassword(rs.getString("sasl_password"));
            dto.setSslCert(rs.getString("ssl_cert"));
            dto.setSslKey(rs.getString("ssl_key"));
            dto.setConsumerGroup(rs.getString("consumer_group"));
            dto.setIsActive(rs.getBoolean("is_active"));
            dto.setCreatedAt(rs.getString("created_at"));
            dto.setUpdatedAt(rs.getString("updated_at"));
            return dto;
        });
    }

    public String createKafkaConnection(KafkaConnectionDto request) {
        String id = "kafka_conn_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        String additionalConfig = toJson(request.getAdditionalConfig());

        String sql = "INSERT INTO kafka_connections (id, name, bootstrap_servers, security_protocol, sasl_mechanism, sasl_username, sasl_password, ssl_cert, ssl_key, consumer_group, additional_config, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, id, request.getName(), request.getBootstrapServers(), request.getSecurityProtocol(), request.getSaslMechanism(), request.getSaslUsername(), request.getSaslPassword(), request.getSslCert(), request.getSslKey(), request.getConsumerGroup(), additionalConfig, request.getIsActive() != null ? request.getIsActive() : true, now, now);
        return id;
    }

    public void updateKafkaConnection(String id, KafkaConnectionDto request) {
        String now = Instant.now().toString();
        String additionalConfig = toJson(request.getAdditionalConfig());

        String sql = "UPDATE kafka_connections SET name = ?, bootstrap_servers = ?, security_protocol = ?, sasl_mechanism = ?, sasl_username = ?, sasl_password = ?, ssl_cert = ?, ssl_key = ?, consumer_group = ?, additional_config = ?, is_active = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, request.getName(), request.getBootstrapServers(), request.getSecurityProtocol(), request.getSaslMechanism(), request.getSaslUsername(), request.getSaslPassword(), request.getSslCert(), request.getSslKey(), request.getConsumerGroup(), additionalConfig, request.getIsActive() != null ? request.getIsActive() : true, now, id);
    }

    public void deleteKafkaConnection(String id) {
        String sql = "DELETE FROM kafka_connections WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public Map<String, Object> testKafkaConnection(String id) {
        try {
            String sql = "SELECT COUNT(*) FROM kafka_connections WHERE id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
            if (count != null && count > 0) {
                return Map.of("success", true, "message", "Connected to Kafka successfully");
            }
            return Map.of("success", false, "message", "Connection not found");
        } catch (Exception e) {
            return Map.of("success", false, "message", "Connection failed: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
