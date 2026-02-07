package com.workflow.engine.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcNodeOutputDataRepository implements NodeOutputDataRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcNodeOutputDataRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public JdbcNodeOutputDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(String executionId, String nodeId, List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO node_output_data (execution_id, node_id, row_index, record_data) VALUES (?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>(Math.min(records.size(), BATCH_SIZE));

        for (int i = 0; i < records.size(); i++) {
            try {
                String json = objectMapper.writeValueAsString(records.get(i));
                batchArgs.add(new Object[]{executionId, nodeId, i, json});
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize record at index {} for node {}: {}", i, nodeId, e.getMessage());
                batchArgs.add(new Object[]{executionId, nodeId, i, "{}"});
            }

            if (batchArgs.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }

        logger.info("Saved {} output records for node {} in execution {}", records.size(), nodeId, executionId);
    }

    @Override
    public List<Map<String, Object>> findByNode(String executionId, String nodeId, int offset, int limit) {
        String sql = "SELECT record_data FROM node_output_data WHERE execution_id = ? AND node_id = ? ORDER BY row_index ASC LIMIT ? OFFSET ?";
        List<String> jsonRows = jdbcTemplate.queryForList(sql, String.class, executionId, nodeId, limit, offset);

        List<Map<String, Object>> result = new ArrayList<>(jsonRows.size());
        for (String json : jsonRows) {
            try {
                Map<String, Object> record = objectMapper.readValue(json, new TypeReference<>() {});
                result.add(record);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to deserialize record for node {}: {}", nodeId, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public long countByNode(String executionId, String nodeId) {
        String sql = "SELECT COUNT(*) FROM node_output_data WHERE execution_id = ? AND node_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, executionId, nodeId);
        return count != null ? count : 0;
    }

    @Override
    public void deleteByExecution(String executionId) {
        String sql = "DELETE FROM node_output_data WHERE execution_id = ?";
        int deleted = jdbcTemplate.update(sql, executionId);
        logger.info("Deleted {} output records for execution {}", deleted, executionId);
    }

    @Override
    public void updateOutputSummary(String executionId, String nodeId, String summaryJson) {
        String sql = "UPDATE node_executions SET output_summary = ? WHERE execution_id = ? AND node_id = ?";
        jdbcTemplate.update(sql, summaryJson, executionId, nodeId);
    }
}
