# Data Persistence and Analytics Guide

## Complete End-to-End Data Capture

All workflow execution data is now captured and persisted across the complete execution lifecycle.

## Data Persistence Flow

### 1. Workflow Execution Initiated
**Endpoint:** `POST /api/execute?execution_mode=sequential`

**Data Stored (workflow_executions table):**
```sql
INSERT INTO workflow_executions (
  id, execution_id, workflow_id, workflow_name,
  status, start_time, execution_mode, parameters
)
```

- `id`: Unique record identifier (UUID)
- `execution_id`: Execution identifier used throughout lifecycle
- `workflow_id`: Workflow identifier from definition
- `workflow_name`: Name of the workflow
- `status`: Initially "running"
- `start_time`: Execution start timestamp
- `execution_mode`: "sequential", "parallel", or "distributed"
- `parameters`: Complete workflow definition JSON

### 2. Each Node Execution Started
**Triggered by:** Spring Batch StepExecution.beforeStep()
**Listener:** PersistenceStepListener.beforeStep()

**Data Stored (node_executions table):**
```sql
INSERT INTO node_executions (
  id, execution_id, node_id, node_label, node_type,
  status, start_time
)
```

- `id`: Unique node execution record identifier
- `execution_id`: Links to workflow execution
- `node_id`: Identifier of the node being executed
- `node_label`: Display name of the node
- `node_type`: Type of node (e.g., DBSource, Compute, Filter, etc.)
- `status`: Initially "running"
- `start_time`: Node execution start time

### 3. Each Node Execution Completed
**Triggered by:** Spring Batch StepExecution.afterStep()
**Listener:** PersistenceStepListener.afterStep()

**Data Updated (node_executions table):**
```sql
UPDATE node_executions SET
  status = ?, end_time = ?, execution_time_ms = ?,
  records_processed = ?, error_message = ?
WHERE id = ?
```

- `status`: "success", "failed", or "stopped"
- `end_time`: Node execution completion time
- `execution_time_ms`: Total execution duration in milliseconds
- `records_processed`: Number of records processed by this node
- `error_message`: Error details if node failed

### 4. Workflow Execution Completed
**Triggered by:** Spring Batch JobExecution.afterJob()
**Listener:** PersistenceJobListener.afterJob()

**Data Updated (workflow_executions table):**
```sql
UPDATE workflow_executions SET
  status = ?, end_time = ?, error_message = ?,
  total_nodes = ?, completed_nodes = ?,
  successful_nodes = ?, failed_nodes = ?,
  total_records = ?, total_execution_time_ms = ?
WHERE execution_id = ?
```

Aggregates from all node_executions:
- `total_nodes`: Total number of nodes executed
- `successful_nodes`: Count of successfully completed nodes
- `failed_nodes`: Count of failed nodes
- `completed_nodes`: Total completed (successful + failed)
- `total_records`: Sum of all records_processed across all nodes
- `total_execution_time_ms`: Sum of all execution_time_ms across all nodes

## Database Tables

### workflow_executions
Main execution record with aggregated metrics.

| Column | Type | Purpose |
|--------|------|---------|
| id | VARCHAR(64) | Primary key |
| execution_id | VARCHAR(64) UNIQUE | Execution identifier |
| workflow_id | VARCHAR(64) | Workflow reference |
| workflow_name | VARCHAR(255) | Workflow name |
| status | VARCHAR(20) | Current status |
| start_time | BIGINT | Start timestamp |
| end_time | BIGINT | End timestamp (after completion) |
| total_nodes | INTEGER | Total nodes in workflow |
| successful_nodes | INTEGER | Successful executions |
| failed_nodes | INTEGER | Failed executions |
| completed_nodes | INTEGER | Total completed |
| total_records | BIGINT | Total records processed |
| total_execution_time_ms | BIGINT | Total duration |
| error_message | TEXT | Error details if failed |
| execution_mode | VARCHAR(50) | Sequential/Parallel/Distributed |
| parameters | TEXT | Workflow definition JSON |

### node_executions
Individual node execution records.

| Column | Type | Purpose |
|--------|------|---------|
| id | VARCHAR(64) | Primary key |
| execution_id | VARCHAR(64) | Workflow execution reference |
| node_id | VARCHAR(64) | Node identifier |
| node_label | VARCHAR(255) | Node display name |
| node_type | VARCHAR(100) | Node type (DBSource, Compute, etc.) |
| status | VARCHAR(20) | Node status |
| start_time | BIGINT | Start timestamp |
| end_time | BIGINT | End timestamp |
| execution_time_ms | BIGINT | Duration |
| records_processed | BIGINT | Records processed |
| error_message | TEXT | Error details |
| retry_count | INTEGER | Number of retries |

## Analytics APIs

All endpoints return complete metrics captured during execution.

### 1. Global Analytics
**Endpoint:** `GET /api/analytics/global`

**Response:**
```json
{
  "total_executions": 42,
  "successful_executions": 38,
  "failed_executions": 4,
  "avg_duration_ms": 2450.5,
  "success_rate": 90.48
}
```

### 2. System Overview
**Endpoint:** `GET /api/analytics/system-overview`

**Response:**
```json
{
  "total_workflows": 15,
  "total_executions": 42,
  "completed_executions": 42,
  "total_node_executions": 324,
  "total_logs": 1250,
  "avg_execution_duration_ms": 2450.5
}
```

### 3. Execution Health
**Endpoint:** `GET /api/analytics/executions/{executionId}/health`

**Response:**
```json
{
  "execution_id": "exec_12345678",
  "status": "success",
  "health_score": 95.5,
  "total_nodes": 12,
  "successful_nodes": 11,
  "failed_nodes": 1
}
```

### 4. Execution Performance
**Endpoint:** `GET /api/analytics/executions/{executionId}/performance`

**Response:**
```json
{
  "execution_id": "exec_12345678",
  "total_duration_ms": 5234,
  "total_records_processed": 45000,
  "throughput_records_per_sec": 8597.3,
  "total_nodes": 12
}
```

### 5. Node Type Statistics
**Endpoint:** `GET /api/analytics/node-types`

**Response:**
```json
{
  "node_types": [
    {
      "node_type": "DBSource",
      "count": 42,
      "successful": 40,
      "failed": 2,
      "avg_duration": 1250.5
    },
    {
      "node_type": "Compute",
      "count": 38,
      "successful": 38,
      "failed": 0,
      "avg_duration": 800.2
    }
  ],
  "total_node_types": 2
}
```

## Verification Checklist

After executing a workflow, verify data is captured:

### 1. Workflow Execution Record
```sql
SELECT * FROM workflow_executions WHERE execution_id = 'exec_xxxxx';
```

Expected:
- ✓ Row exists with your execution_id
- ✓ workflow_id is populated
- ✓ status changes from "running" to "success" or "failed"
- ✓ end_time is populated after completion
- ✓ total_nodes matches your workflow node count

### 2. Node Execution Records
```sql
SELECT * FROM node_executions WHERE execution_id = 'exec_xxxxx';
```

Expected:
- ✓ Multiple rows for each node in workflow
- ✓ Each row has node_id, node_type, execution_time_ms
- ✓ status shows "success", "failed", or "stopped"
- ✓ records_processed populated for applicable nodes

### 3. Aggregated Metrics
```sql
SELECT
  total_nodes, successful_nodes, failed_nodes,
  total_records, total_execution_time_ms
FROM workflow_executions
WHERE execution_id = 'exec_xxxxx';
```

Expected:
- ✓ All columns have values (>0 for completed executions)
- ✓ total_nodes = count of rows in node_executions
- ✓ total_records = sum of records_processed from node_executions

## Important Notes

1. **Asynchronous Execution**: Jobs execute asynchronously. Response returns immediately with status "running"
   - Use execution_id to poll for completion
   - Check endpoint: `GET /api/execution/{executionId}`
   - Wait for status to change from "running" to "success" or "failed"

2. **Metrics Available After Completion**: Until job completes:
   - total_nodes, records, and durations will be 0
   - status will be "running"
   - After completion, all metrics are populated

3. **Node Data Persisted in Real-Time**: Node execution records are created/updated:
   - Node record created (beforeStep) when node starts executing
   - Node record updated (afterStep) when node completes

4. **Error Handling**: All analytics endpoints have graceful error handling
   - Missing tables return 0 instead of error
   - Each metric query wrapped in try-catch
   - Partial failures return available data

## Architecture

### Data Flow
```
Client Request (POST /api/execute)
  ↓
ExecutionApiService.executeWorkflow()
  ├─ Create workflow_executions record (status='running')
  ├─ Build execution plan from workflow definition
  └─ Launch async job
      ↓
Spring Batch Job Execution
  ├─ PersistenceJobListener.beforeJob()
  └─ For each step:
      ├─ PersistenceStepListener.beforeStep()
      │  └─ INSERT node_executions (status='running')
      ├─ Execute node (reader→processor→writer)
      └─ PersistenceStepListener.afterStep()
         └─ UPDATE node_executions (status, times, records)
      ↓
After all steps complete:
  └─ PersistenceJobListener.afterJob()
     └─ UPDATE workflow_executions with aggregated metrics
```

### Listener Components

**PersistenceStepListener**
- Tracks individual node execution lifecycle
- Records node_id, node_type, timing data
- Captures records processed and errors

**PersistenceJobListener**
- Tracks overall workflow completion
- Aggregates all node metrics
- Updates workflow status and totals

## Configuration

Enable/disable data persistence via `StepFactory.setApiListenerContext()`:
- Set with jdbcTemplate and executionId → persistence enabled
- Not set → no persistence (useful for development)

Current configuration in ExecutionApiService:
```java
// Always enabled for API endpoint
stepFactory.setApiListenerContext(jdbcTemplate, executionId);
```

## Testing Data Capture

1. **Execute workflow:**
   ```bash
   curl -X POST "http://localhost:8999/api/execute?execution_mode=sequential" \
     -H "Content-Type: application/json" \
     -d '{...workflow definition...}'
   ```

2. **Get execution ID from response**, example: `exec_12345678`

3. **Wait for completion** (check status endpoint):
   ```bash
   curl "http://localhost:8999/api/execution/exec_12345678"
   ```

4. **Verify database records:**
   ```sql
   SELECT * FROM workflow_executions WHERE execution_id = 'exec_12345678';
   SELECT COUNT(*) FROM node_executions WHERE execution_id = 'exec_12345678';
   ```

5. **Query analytics:**
   ```bash
   curl "http://localhost:8999/api/analytics/executions/exec_12345678/health"
   curl "http://localhost:8999/api/analytics/system-overview"
   ```

## Summary

All workflow data is now captured end-to-end:

- ✓ Workflow execution records with aggregated metrics
- ✓ Individual node execution details and timing
- ✓ Records processed per node
- ✓ Error messages and failure reasons
- ✓ Complete audit trail in execution_logs table
- ✓ Comprehensive analytics APIs for monitoring

The system provides complete observability into workflow execution for monitoring, debugging, and analytics purposes.
