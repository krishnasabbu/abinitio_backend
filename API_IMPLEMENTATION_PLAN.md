# API Implementation Plan - Detailed Guide

**Status:** Ready for Implementation
**Priority:** CRITICAL → IMPORTANT → MINOR
**Estimated Total Effort:** 16-21 hours

---

## PHASE 1: CRITICAL FIXES (Days 1-2)

### Task 1.1: Timestamp Conversion Utility

**File:** `src/main/java/com/workflow/engine/api/util/TimestampConverter.java` (NEW)

**Purpose:** Centralized timestamp conversion to ensure consistency across all APIs

**Implementation:**
```java
package com.workflow.engine.api.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimestampConverter {

    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .withZone(ZoneOffset.UTC);

    /**
     * Convert Unix timestamp in milliseconds to ISO 8601 format
     * Example: 1674749238000 -> "2026-01-26T12:47:18.240+00:00"
     */
    public static String toISO8601(Long timestampMs) {
        if (timestampMs == null || timestampMs == 0) {
            return null;
        }
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneOffset.UTC)
            .format(ISO_FORMATTER);
    }

    /**
     * Convert ISO 8601 timestamp to Unix milliseconds (reverse operation)
     */
    public static Long fromISO8601(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return null;
        }
        return Instant.from(ISO_FORMATTER.parse(isoString))
            .toEpochMilli();
    }
}
```

**Testing:**
```java
// Test timestamps
Long ms = 1674749238000L;
String iso = TimestampConverter.toISO8601(ms);
// Expected: "2026-01-26T12:47:18.240+00:00"
Long msBack = TimestampConverter.fromISO8601(iso);
// Expected: 1674749238000L (should match original)
```

---

### Task 1.2: Update WorkflowExecutionDto

**File:** `src/main/java/com/workflow/engine/api/dto/WorkflowExecutionDto.java`

**Current Issues:**
- Timestamps are in milliseconds (need ISO 8601)
- Missing optional fields
- `total_records` should be `total_records_processed`

**Changes Required:**

```java
package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.workflow.engine.api.util.TimestampConverter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowExecutionDto {

    // Existing fields
    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("workflow_name")
    private String workflowName;

    @JsonProperty("status")
    private String status;

    // CHANGED: String instead of Long for ISO 8601
    @JsonProperty("start_time")
    private String startTime;

    // CHANGED: String instead of Long for ISO 8601
    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("total_nodes")
    private Integer totalNodes;

    @JsonProperty("completed_nodes")
    private Integer completedNodes;

    @JsonProperty("failed_nodes")
    private Integer failedNodes;

    @JsonProperty("total_execution_time_ms")
    private Long totalExecutionTimeMs;

    // RENAMED: total_records_processed instead of total_records
    @JsonProperty("total_records_processed")
    private Long totalRecordsProcessed;

    @JsonProperty("error_message")
    private String errorMessage;

    // NEW OPTIONAL FIELDS
    @JsonProperty("execution_mode")
    private String executionMode;

    @JsonProperty("planning_start_time")
    private String planningStartTime;

    @JsonProperty("max_parallel_nodes")
    private Integer maxParallelNodes;

    @JsonProperty("peak_workers")
    private Integer peakWorkers;

    @JsonProperty("total_input_records")
    private Long totalInputRecords;

    @JsonProperty("total_output_records")
    private Long totalOutputRecords;

    @JsonProperty("total_bytes_read")
    private Long totalBytesRead;

    @JsonProperty("total_bytes_written")
    private Long totalBytesWritten;

    @JsonProperty("error")
    private String error;

    // Getters and Setters
    // NOTE: For timestamp fields, convert to/from ISO 8601

    public String getStartTime() { return startTime; }

    public void setStartTime(Long startTimeMs) {
        this.startTime = TimestampConverter.toISO8601(startTimeMs);
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() { return endTime; }

    public void setEndTime(Long endTimeMs) {
        this.endTime = TimestampConverter.toISO8601(endTimeMs);
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getPlanningStartTime() { return planningStartTime; }

    public void setPlanningStartTime(Long planningStartTimeMs) {
        this.planningStartTime = TimestampConverter.toISO8601(planningStartTimeMs);
    }

    public void setPlanningStartTime(String planningStartTime) {
        this.planningStartTime = planningStartTime;
    }

    // ... rest of getters/setters for new fields

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public Integer getMaxParallelNodes() { return maxParallelNodes; }
    public void setMaxParallelNodes(Integer maxParallelNodes) { this.maxParallelNodes = maxParallelNodes; }

    public Integer getPeakWorkers() { return peakWorkers; }
    public void setPeakWorkers(Integer peakWorkers) { this.peakWorkers = peakWorkers; }

    public Long getTotalInputRecords() { return totalInputRecords; }
    public void setTotalInputRecords(Long totalInputRecords) { this.totalInputRecords = totalInputRecords; }

    public Long getTotalOutputRecords() { return totalOutputRecords; }
    public void setTotalOutputRecords(Long totalOutputRecords) { this.totalOutputRecords = totalOutputRecords; }

    public Long getTotalBytesRead() { return totalBytesRead; }
    public void setTotalBytesRead(Long totalBytesRead) { this.totalBytesRead = totalBytesRead; }

    public Long getTotalBytesWritten() { return totalBytesWritten; }
    public void setTotalBytesWritten(Long totalBytesWritten) { this.totalBytesWritten = totalBytesWritten; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getTotalRecordsProcessed() { return totalRecordsProcessed; }
    public void setTotalRecordsProcessed(Long totalRecordsProcessed) { this.totalRecordsProcessed = totalRecordsProcessed; }

    // ... keep all other existing getters/setters unchanged
}
```

---

### Task 1.3: Update NodeExecutionDto

**File:** `src/main/java/com/workflow/engine/api/dto/NodeExecutionDto.java`

**Changes Required:**
- Convert timestamps to ISO 8601
- Add missing optional fields

```java
package com.workflow.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.workflow.engine.api.util.TimestampConverter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeExecutionDto {

    @JsonProperty("execution_id")
    private String executionId;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("node_label")
    private String nodeLabel;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("status")
    private String status;

    // CHANGED: String for ISO 8601
    @JsonProperty("start_time")
    private String startTime;

    // CHANGED: String for ISO 8601
    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("records_processed")
    private Long recordsProcessed;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("error_message")
    private String errorMessage;

    // NEW OPTIONAL FIELDS
    @JsonProperty("input_records")
    private Long inputRecords;

    @JsonProperty("output_records")
    private Long outputRecords;

    @JsonProperty("input_bytes")
    private Long inputBytes;

    @JsonProperty("output_bytes")
    private Long outputBytes;

    @JsonProperty("records_per_second")
    private Double recordsPerSecond;

    @JsonProperty("bytes_per_second")
    private Double bytesPerSecond;

    @JsonProperty("queue_wait_time_ms")
    private Long queueWaitTimeMs;

    @JsonProperty("depth_in_dag")
    private Integer depthInDag;

    @JsonProperty("output_summary")
    private Object outputSummary;

    @JsonProperty("logs")
    private Object logs;

    // Getters and Setters with ISO 8601 conversion
    public String getStartTime() { return startTime; }

    public void setStartTime(Long startTimeMs) {
        this.startTime = TimestampConverter.toISO8601(startTimeMs);
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() { return endTime; }

    public void setEndTime(Long endTimeMs) {
        this.endTime = TimestampConverter.toISO8601(endTimeMs);
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    // ... rest of getters/setters for all fields
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    // ... continue for all new fields
}
```

---

### Task 1.4: Database Schema Migration

**File:** `src/main/resources/schema_v2_metrics.sql` (NEW MIGRATION)

**Purpose:** Add new metrics columns to track execution performance

```sql
/*
  # Add execution metrics columns

  1. New Columns
    - `execution_mode` (if not exists)
    - `planning_start_time` - when execution planning started
    - `max_parallel_nodes` - maximum nodes executed in parallel
    - `peak_workers` - peak worker threads used
    - `total_input_records` - input records to workflow
    - `total_output_records` - output records from workflow
    - `total_bytes_read` - total data read
    - `total_bytes_written` - total data written

  2. Modified Tables
    - `workflow_executions` - added metrics columns
    - `node_executions` - added optional metrics
*/

-- Add columns to workflow_executions if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'planning_start_time'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN planning_start_time BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'max_parallel_nodes'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN max_parallel_nodes INTEGER DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'peak_workers'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN peak_workers INTEGER DEFAULT 0;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'total_input_records'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN total_input_records BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'total_output_records'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN total_output_records BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'total_bytes_read'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN total_bytes_read BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'workflow_executions' AND column_name = 'total_bytes_written'
    ) THEN
        ALTER TABLE workflow_executions ADD COLUMN total_bytes_written BIGINT;
    END IF;
END $$;

-- Add optional columns to node_executions
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'input_records'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN input_records BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'output_records'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN output_records BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'input_bytes'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN input_bytes BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'output_bytes'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN output_bytes BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'queue_wait_time_ms'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN queue_wait_time_ms BIGINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'node_executions' AND column_name = 'depth_in_dag'
    ) THEN
        ALTER TABLE node_executions ADD COLUMN depth_in_dag INTEGER;
    END IF;
END $$;
```

---

### Task 1.5: Update ExecutionApiService - Timestamp Conversion

**File:** `src/main/java/com/workflow/engine/api/service/ExecutionApiService.java`

**Changes:** All methods that return execution data need to convert timestamps

**Example for getExecutionHistory method:**

```java
public List<WorkflowExecutionDto> getExecutionHistory(String workflowId) {
    String sql = "SELECT id, execution_id, workflow_id, workflow_name, status, " +
                 "start_time, end_time, total_nodes, completed_nodes, successful_nodes, " +
                 "failed_nodes, total_execution_time_ms, execution_mode, planning_start_time, " +
                 "max_parallel_nodes, peak_workers, total_input_records, total_output_records, " +
                 "total_bytes_read, total_bytes_written, error_message " +
                 "FROM workflow_executions ";

    if (workflowId != null && !workflowId.isEmpty()) {
        sql += "WHERE workflow_id = ? ";
    }
    sql += "ORDER BY start_time DESC";

    List<WorkflowExecutionDto> executions = new ArrayList<>();

    List<Map<String, Object>> rows;
    if (workflowId != null && !workflowId.isEmpty()) {
        rows = jdbcTemplate.queryForList(sql, workflowId);
    } else {
        rows = jdbcTemplate.queryForList(sql);
    }

    for (Map<String, Object> row : rows) {
        WorkflowExecutionDto dto = new WorkflowExecutionDto();
        dto.setExecutionId((String) row.get("execution_id"));
        dto.setWorkflowName((String) row.get("workflow_name"));
        dto.setStatus((String) row.get("status"));

        // Convert timestamps to ISO 8601
        dto.setStartTime((Long) row.get("start_time"));
        dto.setEndTime((Long) row.get("end_time"));
        dto.setPlanningStartTime((Long) row.get("planning_start_time"));

        dto.setTotalNodes(((Number) row.get("total_nodes")).intValue());
        dto.setCompletedNodes(((Number) row.get("completed_nodes")).intValue());
        dto.setFailedNodes(((Number) row.get("failed_nodes")).intValue());
        dto.setTotalExecutionTimeMs(((Number) row.get("total_execution_time_ms")).longValue());
        dto.setTotalRecordsProcessed((Long) row.get("total_records"));

        // Set optional fields
        dto.setExecutionMode((String) row.get("execution_mode"));
        dto.setMaxParallelNodes(row.get("max_parallel_nodes") != null ?
            ((Number) row.get("max_parallel_nodes")).intValue() : null);
        dto.setPeakWorkers(row.get("peak_workers") != null ?
            ((Number) row.get("peak_workers")).intValue() : null);
        dto.setTotalInputRecords((Long) row.get("total_input_records"));
        dto.setTotalOutputRecords((Long) row.get("total_output_records"));
        dto.setTotalBytesRead((Long) row.get("total_bytes_read"));
        dto.setTotalBytesWritten((Long) row.get("total_bytes_written"));
        dto.setErrorMessage((String) row.get("error_message"));

        executions.add(dto);
    }

    return executions;
}
```

---

## PHASE 2: IMPORTANT FIXES (Days 2-3)

### Task 2.1: Add Missing Fields to Bottlenecks Response

**File:** `src/main/java/com/workflow/engine/api/service/ExecutionApiService.java`

**Method:** `getExecutionBottlenecks`

**Change:**
```java
public Map<String, Object> getExecutionBottlenecks(String executionId, Integer topN) {
    if (topN == null) {
        topN = 5; // Apply default
    }

    String sql = "SELECT node_id, node_label, execution_time_ms, records_processed, status " +
                 "FROM node_executions " +
                 "WHERE execution_id = ? " +
                 "ORDER BY execution_time_ms DESC " +
                 "LIMIT ?";

    List<Map<String, Object>> bottlenecks = jdbcTemplate.queryForList(sql, executionId, topN);

    return Map.of("bottlenecks", bottlenecks);
}
```

---

### Task 2.2: Fix Analytics Trends Response

**File:** `src/main/java/com/workflow/engine/api/service/AnalyticsApiService.java`

**Method:** `getAnalyticsTrends`

**Current Issue:** `date` field format unclear

**Fix:**
```java
public Map<String, Object> getAnalyticsTrends(Integer days) {
    if (days == null) {
        days = 7; // Apply default
    }

    // Query implementation...
    List<Map<String, Object>> trends = new ArrayList<>();

    // When building response, ensure date is ISO 8601
    for (Map<String, Object> trend : rows) {
        Map<String, Object> enrichedTrend = new HashMap<>(trend);

        Long timestamp = (Long) trend.get("date");
        if (timestamp != null) {
            enrichedTrend.put("date", TimestampConverter.toISO8601(timestamp));
        }

        trends.add(enrichedTrend);
    }

    return Map.of("trends", trends);
}
```

---

### Task 2.3: Update Log Summary Response

**File:** `src/main/java/com/workflow/engine/api/service/LogApiService.java`

**Method:** `getLogSummary`

**Change:**
```java
public Map<String, Object> getLogSummary(String executionId) {
    String sql = "SELECT level, COUNT(*) as count FROM execution_logs " +
                 "WHERE execution_id = ? GROUP BY level";

    List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, executionId);

    Map<String, Integer> levels = new HashMap<>();
    levels.put("INFO", 0);
    levels.put("ERROR", 0);
    levels.put("WARNING", 0);
    levels.put("DEBUG", 0);

    for (Map<String, Object> row : results) {
        String level = (String) row.get("level");
        int count = ((Number) row.get("count")).intValue();
        levels.put(level, count);
    }

    // Get unique nodes
    String nodesSql = "SELECT DISTINCT node_id FROM execution_logs " +
                     "WHERE execution_id = ? AND node_id IS NOT NULL";
    List<String> nodes = jdbcTemplate.queryForList(nodesSql, String.class, executionId);

    // Get timestamp range
    String timestampSql = "SELECT MIN(timestamp) as first_ts, MAX(timestamp) as last_ts " +
                         "FROM execution_logs WHERE execution_id = ?";
    Map<String, Object> timestamps =
        jdbcTemplate.queryForMap(timestampSql, executionId);

    return Map.of(
        "total", getTotalLogCount(executionId),
        "levels", levels,
        "nodes", nodes,
        "first_timestamp", timestamps.get("first_ts"),
        "last_timestamp", timestamps.get("last_ts")
    );
}
```

---

### Task 2.4: Complete Node Execution Optional Fields

**File:** `src/main/java/com/workflow/engine/api/service/ExecutionApiService.java`

**Method:** `getNodeExecutions`

**Enhancement:**
```java
public List<NodeExecutionDto> getNodeExecutions(String executionId) {
    String sql = "SELECT id, execution_id, node_id, node_label, node_type, status, " +
                 "start_time, end_time, execution_time_ms, records_processed, " +
                 "input_records, output_records, input_bytes, output_bytes, " +
                 "queue_wait_time_ms, depth_in_dag, retry_count, error_message " +
                 "FROM node_executions " +
                 "WHERE execution_id = ? " +
                 "ORDER BY start_time ASC";

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, executionId);
    List<NodeExecutionDto> nodes = new ArrayList<>();

    for (Map<String, Object> row : rows) {
        NodeExecutionDto dto = new NodeExecutionDto();
        dto.setExecutionId((String) row.get("execution_id"));
        dto.setNodeId((String) row.get("node_id"));
        dto.setNodeLabel((String) row.get("node_label"));
        dto.setNodeType((String) row.get("node_type"));
        dto.setStatus((String) row.get("status"));

        // Convert timestamps
        dto.setStartTime((Long) row.get("start_time"));
        dto.setEndTime((Long) row.get("end_time"));

        dto.setExecutionTimeMs(((Number) row.get("execution_time_ms")).longValue());
        dto.setRecordsProcessed((Long) row.get("records_processed"));
        dto.setRetryCount(((Number) row.get("retry_count")).intValue());
        dto.setErrorMessage((String) row.get("error_message"));

        // Add optional fields
        dto.setInputRecords((Long) row.get("input_records"));
        dto.setOutputRecords((Long) row.get("output_records"));
        dto.setInputBytes((Long) row.get("input_bytes"));
        dto.setOutputBytes((Long) row.get("output_bytes"));
        dto.setQueueWaitTimeMs((Long) row.get("queue_wait_time_ms"));
        dto.setDepthInDag(row.get("depth_in_dag") != null ?
            ((Number) row.get("depth_in_dag")).intValue() : null);

        // Calculate derived metrics
        long execTimeMs = dto.getExecutionTimeMs();
        long recordsProcessed = dto.getRecordsProcessed() != null ?
            dto.getRecordsProcessed() : 0;
        long bytesProcessed = 0;
        if (dto.getInputBytes() != null) bytesProcessed += dto.getInputBytes();
        if (dto.getOutputBytes() != null) bytesProcessed += dto.getOutputBytes();

        if (execTimeMs > 0) {
            if (recordsProcessed > 0) {
                dto.setRecordsPerSecond((double) recordsProcessed / (execTimeMs / 1000.0));
            }
            if (bytesProcessed > 0) {
                dto.setBytesPerSecond((double) bytesProcessed / (execTimeMs / 1000.0));
            }
        }

        nodes.add(dto);
    }

    return nodes;
}
```

---

### Task 2.5: Fix Execution Metrics Response Structure

**File:** `src/main/java/com/workflow/engine/api/service/ExecutionApiService.java`

**Method:** `getExecutionMetrics`

**Current Issue:** May not be returning both workflow_metrics and node_metrics

**Fix:**
```java
public Map<String, Object> getExecutionMetrics(String executionId) {
    // Get workflow metrics
    WorkflowExecutionDto workflowMetrics = getExecutionById(executionId);

    // Get node metrics
    List<NodeExecutionDto> nodeMetrics = getNodeExecutions(executionId);

    // Return both
    return Map.of(
        "workflow_metrics", workflowMetrics,
        "node_metrics", nodeMetrics
    );
}
```

---

### Task 2.6: Add Parameter Defaults to Controllers

**File:** `src/main/java/com/workflow/engine/api/controller/ExecutionApiController.java`

**Changes:**
```java
@GetMapping("/executions/{executionId}/bottlenecks")
public ResponseEntity<?> getExecutionBottlenecks(
    @PathVariable String executionId,
    @RequestParam(required = false) Integer topN) {

    // Apply default
    if (topN == null) {
        topN = 5;
    }

    return ResponseEntity.ok(executionApiService.getExecutionBottlenecks(executionId, topN));
}
```

**File:** `src/main/java/com/workflow/engine/api/controller/AnalyticsApiController.java`

**Changes:**
```java
@GetMapping("/analytics/trends")
public ResponseEntity<?> getAnalyticsTrends(
    @RequestParam(required = false) Integer days) {

    // Apply default
    if (days == null) {
        days = 7;
    }

    return ResponseEntity.ok(analyticsApiService.getAnalyticsTrends(days));
}
```

---

## PHASE 3: MINOR REFINEMENTS (Day 3)

### Task 3.1: Verify Error Response Format

**File:** `src/main/java/com/workflow/engine/config/GlobalExceptionHandler.java` (Check if exists)

**Required Format:**
```json
{
  "detail": "Error message describing what went wrong"
}
```

**If using Spring's default error handling, create custom handler:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("detail", e.getMessage()));
    }
}
```

---

### Task 3.2: Verify Execution Mode Values

**Check all places where execution_mode is set/returned**

**Expected Values (lowercase):**
- `python`
- `parallel`
- `pyspark`

---

### Task 3.3: Verify Success Rate Format

**All success_rate fields should be decimal 0-1**

**Example:**
```json
{
  "success_rate": 0.95
}
```

NOT:
```json
{
  "success_rate": 95
}
```

---

## IMPLEMENTATION CHECKLIST

### Phase 1: Critical
- [ ] Create TimestampConverter utility
- [ ] Update WorkflowExecutionDto (add fields + timestamp conversion)
- [ ] Update NodeExecutionDto (add fields + timestamp conversion)
- [ ] Create database migration for new metrics columns
- [ ] Update ExecutionApiService.getExecutionHistory() with timestamp conversion
- [ ] Update ExecutionApiService.getExecutionById() with timestamp conversion
- [ ] Update ExecutionApiService.getNodeExecutions() with timestamp conversion
- [ ] Test all timestamp conversions work correctly
- [ ] Verify ISO 8601 format matches frontend expectations

### Phase 2: Important
- [ ] Add status field to getExecutionBottlenecks()
- [ ] Add parameter defaults in controllers
- [ ] Update AnalyticsApiService.getAnalyticsTrends() for date format
- [ ] Update LogApiService.getLogSummary() for DEBUG level
- [ ] Update ExecutionApiService.getExecutionMetrics() response structure
- [ ] Complete NodeExecutionDto with all optional fields
- [ ] Test all optional fields are properly serialized (null fields omitted)

### Phase 3: Minor
- [ ] Verify error response format consistency
- [ ] Check execution_mode values are lowercase
- [ ] Verify success_rate format (0-1 decimal)
- [ ] Standardize ISO 8601 timezone format

---

## TESTING STRATEGY

### Unit Tests to Add

1. **TimestampConverter Tests**
```java
@Test
public void testToISO8601() {
    Long ms = 1674749238000L;
    String iso = TimestampConverter.toISO8601(ms);
    assertTrue(iso.endsWith("+00:00"));
    assertTrue(iso.contains("T"));
}

@Test
public void testFromISO8601() {
    String iso = "2026-01-26T12:47:18.240+00:00";
    Long ms = TimestampConverter.fromISO8601(iso);
    assertNotNull(ms);
}
```

2. **ExecutionApiService Tests**
```java
@Test
public void testExecutionResponseHasISO8601Timestamps() {
    WorkflowExecutionDto execution = executionApiService.getExecutionById("exec_123");
    assertTrue(execution.getStartTime().contains("T"));
    assertTrue(execution.getStartTime().contains("+"));
}

@Test
public void testExecutionResponseHasOptionalFields() {
    WorkflowExecutionDto execution = executionApiService.getExecutionById("exec_123");
    // At least one optional field should be present
    assertTrue(execution.getExecutionMode() != null ||
               execution.getMaxParallelNodes() != null);
}
```

### Integration Tests

1. **API Endpoint Tests**
```
GET /api/executions
- Verify all fields present
- Verify timestamps are ISO 8601
- Verify execution_mode is lowercase

GET /api/executions/{executionId}/bottlenecks
- Verify status field present
- Verify topN default applied (5)

GET /api/analytics/trends
- Verify days default applied (7)
- Verify date fields are ISO 8601
```

### Frontend Integration Tests

1. **Date Parsing**
```javascript
const response = await fetch('/api/executions');
const execution = response.data[0];
const date = new Date(execution.start_time);
// Should parse correctly
expect(date.getTime()).toBeGreaterThan(0);
```

2. **Optional Fields**
```javascript
// Fields should be omitted if null/undefined
if (execution.max_parallel_nodes) {
    // Display metric
} else {
    // Skip metric
}
```

---

## DEPLOYMENT CHECKLIST

1. **Before Deployment:**
   - [ ] All tests pass
   - [ ] Database migrations verified on test database
   - [ ] Frontend tested with new response formats
   - [ ] Backward compatibility verified (old clients)

2. **During Deployment:**
   - [ ] Run database migrations in order
   - [ ] Deploy new service code
   - [ ] Monitor API response times
   - [ ] Check error logs

3. **After Deployment:**
   - [ ] Smoke test all endpoints
   - [ ] Verify timestamps in responses
   - [ ] Check frontend dashboard displays correctly
   - [ ] Monitor for any client errors

---

## Rollback Plan

If issues occur:

1. **Timestamp Issues:** Roll back DTOs to milliseconds, create adapter
2. **Missing Fields:** Are optional with @JsonInclude(NON_NULL), safe to rollback
3. **Database:** Migrations are additive (new columns), safe to rollback
4. **API Changes:** All new fields are optional, existing fields unchanged

---

## Success Criteria

- [ ] All 13 documented APIs return exact response structure
- [ ] All timestamps are ISO 8601 format
- [ ] All optional fields are included
- [ ] Frontend dashboard displays all metrics correctly
- [ ] No breaking changes for existing clients
- [ ] Error responses use consistent format
- [ ] All parameter defaults applied correctly

