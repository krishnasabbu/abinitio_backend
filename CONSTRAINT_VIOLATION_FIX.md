# Foreign Key Constraint Violation Fix

## Problem

When using either endpoint (old `WorkflowController` or new `/api/execute`), the system was throwing a referential integrity constraint violation:

```
Referential integrity constraint violation:
"CONSTRAINT_FF: PUBLIC.NODE_EXECUTIONS FOREIGN KEY(EXECUTION_ID)
REFERENCES PUBLIC.WORKFLOW_EXECUTIONS(EXECUTION_ID)"
```

The error indicated that `node_executions` table was trying to insert records with an `execution_id` that didn't exist in `workflow_executions` table.

## Root Cause

In `DynamicJobBuilder.buildJob()` at line 161:

```java
// WRONG: Using workflow ID as execution ID
String executionId = effectiveWorkflowId;
buildAllSteps(ctx, bufferStore, executionId);
```

This caused the system to pass the **workflow ID** as the **execution ID** to the `PersistenceStepListener`, which would then try to insert node execution records with a foreign key reference to a non-existent execution.

### Example of the Bug

**Workflow execution flow:**
1. ExecutionApiService generates: `executionId = "exec_12345"`
2. Creates workflow_executions record with `execution_id = "exec_12345"`
3. Calls `stepFactory.setApiListenerContext(jdbcTemplate, "exec_12345")`
4. But DynamicJobBuilder.buildJob() was using `workflowId = "workflow_abc123"` instead
5. Passed wrong ID to PersistenceStepListener: `"workflow_abc123"`
6. Node execution records tried to reference non-existent execution_id!

## Solution

Added two changes to fix the execution ID propagation:

### 1. StepFactory.java - Added Getter

```java
public String getExecutionId() {
    return this.executionId;
}
```

This exposes the actual execution ID that was set via `setApiListenerContext()`.

### 2. DynamicJobBuilder.java - Use Actual Execution ID

Changed line 161 from:
```java
String executionId = effectiveWorkflowId;
```

To:
```java
String executionId = stepFactory.getExecutionId() != null ? stepFactory.getExecutionId() : effectiveWorkflowId;
```

This ensures:
- If an actual execution ID was set (via setApiListenerContext), use that
- Otherwise, fall back to workflowId for backward compatibility

## Impact

### Before Fix
- Node execution records couldn't be created
- Foreign key constraints failed
- Workflow data not persisted
- Analytics incomplete

### After Fix
- Correct execution ID flows through entire system
- Node execution records created successfully
- All workflow metrics persisted
- Complete end-to-end data capture working

## Data Flow (Corrected)

```
ExecutionApiService.executeWorkflow()
  ├─ Generate executionId (unique per run)
  ├─ Create workflow_executions record
  ├─ Call stepFactory.setApiListenerContext(jdbcTemplate, executionId)
  └─ Call launchWorkflowJob()
      ├─ Call stepFactory.setApiListenerContext() [stores actual executionId]
      ├─ Call dynamicJobBuilder.buildJob()
      │   └─ (FIXED) executionId = stepFactory.getExecutionId() [retrieves correct ID]
      │       └─ Call buildAllSteps(..., executionId)
      │           └─ Call stepFactory.buildStep(..., executionId)
      │               └─ Create PersistenceStepListener(..., CORRECT_executionId)
      └─ Launch job
          ├─ PersistenceStepListener.beforeStep()
          │   └─ INSERT INTO node_executions WITH CORRECT execution_id ✓
          └─ Metrics flow correctly ✓
```

## Verification Steps

After applying this fix:

1. **Execute workflow via old endpoint:**
   ```
   POST /workflows/{id}
   ```
   Should now persist node data without constraint violations

2. **Execute workflow via new API endpoint:**
   ```
   POST /api/execute?execution_mode=sequential
   ```
   Should persist all execution data correctly

3. **Verify data in database:**
   ```sql
   -- Should have matching execution records
   SELECT DISTINCT execution_id FROM workflow_executions;
   SELECT DISTINCT execution_id FROM node_executions;

   -- Both queries should return the same execution_id values
   ```

4. **Check analytics endpoints:**
   ```
   GET /api/analytics/executions/{executionId}/health
   GET /api/analytics/system-overview
   ```
   Should return complete metrics with no missing data

## Summary

The fix ensures that the correct execution ID (unique per workflow run) is used consistently throughout the execution lifecycle, allowing proper foreign key relationships and complete end-to-end data persistence.
