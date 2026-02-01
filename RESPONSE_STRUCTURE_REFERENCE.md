# Response Structure Reference - Quick Comparison Guide

This document shows side-by-side comparisons of documented vs current implementation for quick reference during development.

---

## 1. GET /executions - List Executions

### Documented Response
```json
[
  {
    "execution_id": "exec_abc123",
    "workflow_name": "ETL_Pipeline",
    "status": "running",
    "start_time": "2026-01-26T12:47:18.240+00:00",
    "end_time": "2026-01-26T12:49:18.240+00:00",
    "total_execution_time_ms": 120000,
    "total_nodes": 5,
    "completed_nodes": 3,
    "failed_nodes": 0,
    "total_records_processed": 1000,
    "execution_mode": "parallel",
    "planning_start_time": "2026-01-26T12:47:10.240+00:00",
    "max_parallel_nodes": 4,
    "peak_workers": 8,
    "total_input_records": 1000,
    "total_output_records": 950,
    "total_bytes_read": 5242880,
    "total_bytes_written": 4980736,
    "error": null
  }
]
```

### Current Implementation ❌
```json
[
  {
    "id": "record_id",
    "execution_id": "exec_abc123",
    "workflow_name": "ETL_Pipeline",
    "status": "running",
    "start_time": 1674749238000,
    "end_time": 1674749338000,
    "total_nodes": 5,
    "completed_nodes": 3,
    "successful_nodes": 3,
    "failed_nodes": 0,
    "total_records": 1000,
    "total_execution_time_ms": 120000,
    "error_message": null
  }
]
```

### Changes Required
```diff
- Remove: id field (internal)
- Remove: successful_nodes (renamed to completed_nodes)
+ Change: start_time/end_time to ISO 8601 string
+ Change: total_records → total_records_processed
+ Add: execution_mode (from DB)
+ Add: planning_start_time (from DB)
+ Add: max_parallel_nodes (from DB)
+ Add: peak_workers (from DB)
+ Add: total_input_records (from DB)
+ Add: total_output_records (from DB)
+ Add: total_bytes_read (from DB)
+ Add: total_bytes_written (from DB)
+ Change: error_message → error
```

---

## 2. GET /execution/{executionId} - Single Execution

### Documented Response
```json
{
  "execution_id": "exec_abc123",
  "workflow_name": "ETL_Pipeline",
  "status": "success",
  "start_time": "2026-01-26T12:47:18.240+00:00",
  "end_time": "2026-01-26T12:49:18.240+00:00",
  "total_execution_time_ms": 120000,
  "total_nodes": 5,
  "completed_nodes": 5,
  "failed_nodes": 0,
  "total_records_processed": 1000,
  "execution_mode": "parallel",
  "planning_start_time": "2026-01-26T12:47:10.240+00:00",
  "max_parallel_nodes": 4,
  "peak_workers": 8,
  "total_input_records": 1000,
  "total_output_records": 950,
  "total_bytes_read": 5242880,
  "total_bytes_written": 4980736,
  "error": null
}
```

### Changes Required
**Same as List Executions above**

---

## 3. GET /executions/{executionId}/nodes - Execution Nodes

### Documented Response
```json
[
  {
    "execution_id": "exec_abc123",
    "node_id": "node_1",
    "node_label": "Load Data",
    "node_type": "source",
    "status": "success",
    "start_time": "2026-01-26T12:47:18.240+00:00",
    "end_time": "2026-01-26T12:47:28.240+00:00",
    "execution_time_ms": 10000,
    "records_processed": 1000,
    "input_records": null,
    "output_records": 1000,
    "input_bytes": null,
    "output_bytes": 5242880,
    "records_per_second": 100.0,
    "bytes_per_second": 524288.0,
    "queue_wait_time_ms": 500,
    "depth_in_dag": 0,
    "retry_count": 0,
    "error_message": null,
    "output_summary": null,
    "logs": null
  }
]
```

### Current Implementation ❌
```json
[
  {
    "id": "node_exec_id",
    "execution_id": "exec_abc123",
    "node_id": "node_1",
    "node_label": "Load Data",
    "node_type": "source",
    "status": "success",
    "start_time": 1674749238000,
    "end_time": 1674749248000,
    "execution_time_ms": 10000,
    "records_processed": 1000,
    "retry_count": 0,
    "error_message": null
  }
]
```

### Changes Required
```diff
- Remove: id field
+ Change: start_time/end_time to ISO 8601
+ Add: input_records
+ Add: output_records
+ Add: input_bytes
+ Add: output_bytes
+ Add: records_per_second (calculated)
+ Add: bytes_per_second (calculated)
+ Add: queue_wait_time_ms
+ Add: depth_in_dag
+ Add: output_summary
+ Add: logs
```

---

## 4. GET /executions/{executionId}/bottlenecks

### Documented Response
```json
{
  "bottlenecks": [
    {
      "node_id": "node_3",
      "node_label": "Process Data",
      "execution_time_ms": 45000,
      "records_processed": 950,
      "status": "success"
    },
    {
      "node_id": "node_2",
      "node_label": "Transform",
      "execution_time_ms": 35000,
      "records_processed": 1000,
      "status": "success"
    }
  ]
}
```

### Current Implementation ❌
May be missing `status` field

### Changes Required
```diff
+ Add: status field to each bottleneck
```

---

## 5. GET /executions/{executionId}/timeline

### Documented Response
```json
{
  "execution_id": "exec_abc123",
  "workflow_status": "success",
  "workflow_start_time": "2026-01-26T12:47:18.240+00:00",
  "workflow_end_time": "2026-01-26T12:49:18.240+00:00",
  "nodes": [
    {
      "node_id": "node_1",
      "node_label": "Load Data",
      "start_time": "2026-01-26T12:47:18.240+00:00",
      "end_time": "2026-01-26T12:47:28.240+00:00",
      "execution_time_ms": 10000,
      "status": "success"
    }
  ]
}
```

### Changes Required
```diff
+ Change: All timestamps to ISO 8601 format
+ Change: workflow_status instead of execution_status
+ Change: workflow_start_time/workflow_end_time
```

---

## 6. GET /executions/{executionId}/metrics

### Documented Response
```json
{
  "workflow_metrics": {
    "execution_id": "exec_abc123",
    "workflow_name": "ETL_Pipeline",
    "status": "success",
    "start_time": "2026-01-26T12:47:18.240+00:00",
    "end_time": "2026-01-26T12:49:18.240+00:00",
    "total_execution_time_ms": 120000,
    "total_nodes": 5,
    "completed_nodes": 5,
    "failed_nodes": 0,
    "total_records_processed": 1000,
    "execution_mode": "parallel",
    "planning_start_time": "2026-01-26T12:47:10.240+00:00",
    "max_parallel_nodes": 4,
    "peak_workers": 8,
    "total_input_records": 1000,
    "total_output_records": 950,
    "total_bytes_read": 5242880,
    "total_bytes_written": 4980736,
    "error": null
  },
  "node_metrics": [
    {
      "node_id": "node_1",
      "node_label": "Load Data",
      "records_processed": 1000,
      "execution_time_ms": 10000,
      "input_records": null,
      "output_records": 1000,
      "input_bytes": null,
      "output_bytes": 5242880,
      "records_per_second": 100.0,
      "bytes_per_second": 524288.0
    }
  ]
}
```

### Changes Required
```diff
- Restructure response to include both workflow_metrics and node_metrics
+ workflow_metrics: Use updated WorkflowExecutionDto structure
+ node_metrics: Use updated NodeExecutionDto structure (metrics only)
```

---

## 7. GET /logs/executions/{executionId}

### Documented Response
```json
{
  "execution_id": "exec_abc123",
  "logs": [
    {
      "timestamp": 1674749238000,
      "datetime": "2026-01-26T12:47:18.240+00:00",
      "level": "INFO",
      "execution_id": "exec_abc123",
      "workflow_id": "wf_workflow1",
      "node_id": "node_1",
      "message": "Starting node execution",
      "metadata": {},
      "stack_trace": null
    }
  ],
  "total": 150,
  "summary": {
    "total": 150,
    "levels": {
      "INFO": 100,
      "ERROR": 30,
      "WARNING": 20
    },
    "nodes": ["node_1", "node_2", "node_3"],
    "first_timestamp": 1674749238000,
    "last_timestamp": 1674749338000
  }
}
```

### Current Implementation ✓
**Appears correct** - Verify:
- [ ] datetime field always populated
- [ ] timestamp in milliseconds (correct)
- [ ] All fields present

---

## 8. GET /logs/summary/{executionId}

### Documented Response
```json
{
  "total": 150,
  "levels": {
    "INFO": 100,
    "ERROR": 30,
    "WARNING": 20,
    "DEBUG": 0
  },
  "nodes": ["node_1", "node_2", "node_3"],
  "first_timestamp": 1674749238000,
  "last_timestamp": 1674749338000
}
```

### Changes Required
```diff
+ Add: DEBUG level to levels map (if missing)
```

---

## 9. GET /workflows/{workflowId}/analytics

### Documented Response
```json
{
  "total_executions": 42,
  "successful_executions": 40,
  "success_rate": 0.952,
  "avg_duration": 120000,
  "min_duration": 95000,
  "max_duration": 180000
}
```

### Verification Needed
- [ ] success_rate is decimal (0-1), not percentage
- [ ] All durations in milliseconds
- [ ] min_duration and max_duration included

---

## 10. GET /analytics/global

### Documented Response
```json
{
  "total_workflows": 15,
  "total_executions": 450,
  "today_executions": 42,
  "failed_today": 3,
  "success_rate": 0.933,
  "avg_duration_ms": 120000,
  "top_workflows": [
    {
      "workflow_name": "ETL_Pipeline",
      "execution_count": 85
    }
  ],
  "most_failing_workflows": [
    {
      "workflow_name": "DataValidation",
      "failed_count": 12
    }
  ],
  "slowest_nodes": [
    {
      "node_label": "Process Large Dataset",
      "total_time": 540000
    }
  ]
}
```

### Verification Needed
- [ ] success_rate is decimal
- [ ] All durations in milliseconds
- [ ] Field names match exactly

---

## 11. GET /analytics/trends

### Documented Response
```json
{
  "trends": [
    {
      "count": 42,
      "date": "2026-01-26T00:00:00.000+00:00",
      "success_rate": 0.952,
      "avg_duration": 120000
    }
  ]
}
```

### Changes Required
```diff
+ Ensure date is ISO 8601 timestamp (start of day)
+ Ensure success_rate is decimal (0-1)
+ Apply days default (7) if not provided
```

---

## 12. GET /analytics/node-types

### Documented Response
```json
{
  "node_types": [
    {
      "node_type": "source",
      "usage_count": 45,
      "successful": 44,
      "failed": 1,
      "avg_execution_time": 10000,
      "avg_records_processed": 1000
    }
  ]
}
```

### Verification Needed
- [ ] All fields present
- [ ] Calculations correct

---

## 13. POST /executions/{executionId}/rerun

### Documented Response
```json
{
  "status": "queued",
  "original_execution_id": "exec_abc123",
  "new_execution_id": "exec_xyz789",
  "from_node_id": null
}
```

### Verification Needed
- [ ] All fields present
- [ ] from_node_id included when provided as query param

---

## 14. POST /executions/{executionId}/cancel

### Documented Response
```json
{
  "status": "success",
  "execution_id": "exec_abc123"
}
```

### Changes Required
```diff
+ Ensure status field present (success/error/already_completed)
```

---

## Data Type Reference

| Field | Type | Format/Values | Example |
|-------|------|---------------|---------|
| execution_id | string | "exec_" prefix | "exec_abc123" |
| workflow_id | string | "wf_" prefix | "wf_workflow1" |
| node_id | string | Custom IDs | "node_1" |
| status | string | pending\|running\|success\|failed\|cancelled\|skipped | "success" |
| node_status | string | pending\|running\|success\|failed\|skipped\|retrying | "success" |
| start_time | string | ISO 8601 | "2026-01-26T12:47:18.240+00:00" |
| end_time | string | ISO 8601 | "2026-01-26T12:49:18.240+00:00" |
| timestamp (logs) | number | Unix ms | 1674749238000 |
| duration_ms | number | Milliseconds | 120000 |
| records_processed | number | Integer count | 1000 |
| bytes_read | number | Integer bytes | 5242880 |
| success_rate | number | Decimal 0-1 | 0.952 |
| execution_mode | string | python\|parallel\|pyspark | "parallel" |

---

## Key Transformation Rules

### 1. Timestamps in Execution APIs
- **Input:** Unix milliseconds from database
- **Output:** ISO 8601 string "2026-01-26T12:47:18.240+00:00"
- **Location:** All execution endpoints

### 2. Success Rate
- **Input:** percentage (0-100) or count-based calculation
- **Output:** decimal (0.0-1.0)
- **Location:** All analytics endpoints, workflow analytics

### 3. Optional Fields
- **Rule:** Use @JsonInclude(JsonInclude.Include.NON_NULL)
- **Result:** Null fields omitted from JSON response
- **Location:** All DTOs

### 4. Durations
- **Rule:** Always in milliseconds
- **Exception:** Never use seconds for duration

### 5. Field Naming
- **Rule:** snake_case in JSON (via @JsonProperty)
- **Class:** camelCase in Java

---

## Testing Checklist per Endpoint

### For Each Execution Endpoint
- [ ] All timestamps in ISO 8601 format
- [ ] execution_mode lowercase if present
- [ ] Optional fields omitted if null
- [ ] Status values valid (pending|running|success|failed|cancelled|skipped)
- [ ] Node status values valid (pending|running|success|failed|skipped|retrying)

### For Each Analytics Endpoint
- [ ] success_rate in decimal format
- [ ] All dates in ISO 8601 format
- [ ] Array fields are arrays (not null)
- [ ] Counts are integers

### For Log Endpoints
- [ ] timestamp in milliseconds
- [ ] datetime in ISO 8601
- [ ] level values valid (INFO|ERROR|WARNING|DEBUG)
- [ ] Summary includes all levels

---

## Common Pitfalls to Avoid

❌ **Wrong:** Returning milliseconds for timestamps
```json
{ "start_time": 1674749238000 }
```

✓ **Correct:** ISO 8601 format
```json
{ "start_time": "2026-01-26T12:47:18.240+00:00" }
```

---

❌ **Wrong:** success_rate as percentage
```json
{ "success_rate": 95.2 }
```

✓ **Correct:** decimal 0-1
```json
{ "success_rate": 0.952 }
```

---

❌ **Wrong:** Uppercase execution modes
```json
{ "execution_mode": "PARALLEL" }
```

✓ **Correct:** lowercase
```json
{ "execution_mode": "parallel" }
```

---

❌ **Wrong:** Including null fields
```json
{ "max_parallel_nodes": null, "error": null }
```

✓ **Correct:** Omit null fields
```json
{ "max_parallel_nodes": 4 }
```

---

## Frontend Integration Notes

The frontend will:
1. Parse ISO 8601 timestamps and format them
2. Multiply success_rate by 100 for display percentage
3. Skip optional fields if not present
4. Expect exact field names from JSON
5. Handle nested objects (workflow_metrics, node_metrics)

---

## Validation Commands

```bash
# Verify timestamp format
curl http://localhost:8999/api/executions | jq '.[] | .start_time'

# Should output:
# "2026-01-26T12:47:18.240+00:00"

# Verify success_rate format
curl http://localhost:8999/api/analytics/global | jq '.success_rate'

# Should output:
# 0.952

# Verify optional fields omitted
curl http://localhost:8999/api/executions | jq '.[] | keys'

# Should NOT include null fields like: "error": null
```
