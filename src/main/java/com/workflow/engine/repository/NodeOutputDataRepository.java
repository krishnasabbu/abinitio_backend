package com.workflow.engine.repository;

import java.util.List;
import java.util.Map;

public interface NodeOutputDataRepository {

    void saveAll(String executionId, String nodeId, List<Map<String, Object>> records);

    List<Map<String, Object>> findByNode(String executionId, String nodeId, int offset, int limit);

    long countByNode(String executionId, String nodeId);

    void deleteByExecution(String executionId);

    void updateOutputSummary(String executionId, String nodeId, String summaryJson);
}
