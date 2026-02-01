package com.workflow.engine.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.api.dto.WorkflowExecutionDto;
import com.workflow.engine.api.dto.NodeExecutionDto;
import com.workflow.engine.api.persistence.ExecutionLogWriter;
import com.workflow.engine.api.persistence.PersistenceJobListener;
import com.workflow.engine.api.persistence.PersistenceStepListener;
import com.workflow.engine.api.util.PayloadNormalizer;
import com.workflow.engine.execution.job.DynamicJobBuilder;
import com.workflow.engine.execution.job.StepFactory;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.PartialRestartManager;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

@Service
public class ExecutionApiService {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionApiService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutionGraphBuilder executionGraphBuilder;

    @Autowired
    private DynamicJobBuilder dynamicJobBuilder;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StepFactory stepFactory;

    @Autowired
    private PartialRestartManager partialRestartManager;

    private ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> executeWorkflow(String executionMode, Map<String, Object> request) {
        String executionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        try {
            // Normalize payload to handle both frontend and backend formats
            request = PayloadNormalizer.normalize(request);

            // Parse workflow definition from request
            @SuppressWarnings("unchecked")
            Map<String, Object> workflowData = (Map<String, Object>) request.get("workflow");
            if (workflowData == null) {
                return buildErrorResponse("Workflow not found in request");
            }

            String workflowName = (String) workflowData.get("name");
            if (workflowName == null || workflowName.isEmpty()) {
                workflowName = "Unnamed Workflow";
            }

            // Store workflow payload for potential rerun
            String workflowPayload = objectMapper.writeValueAsString(workflowData);

            // Convert to WorkflowDefinition
            WorkflowDefinition workflow = objectMapper.convertValue(workflowData, WorkflowDefinition.class);

            // Get workflow ID if available
            String workflowId = workflow.getId() != null ? workflow.getId() : "workflow_" + UUID.randomUUID().toString().substring(0, 8);

            // Build execution plan FIRST to get total_nodes count
            ExecutionPlan plan = executionGraphBuilder.build(workflow);
            int totalNodes = plan.steps().size();

            // Create execution record in database with total_nodes
            String recordId = UUID.randomUUID().toString();
            String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_id, workflow_name, status, start_time, execution_mode, parameters, total_nodes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            int rowsInserted = jdbcTemplate.update(insertSql, recordId, executionId, workflowId, workflowName, "running", startTime, executionMode, workflowPayload, totalNodes);

            if (rowsInserted == 0) {
                logger.error("Failed to insert workflow_executions record for executionId: {}", executionId);
                return buildErrorResponse("Failed to create execution record in database");
            }

            logger.debug("Created workflow_executions record: id={}, execution_id={}, workflow_id={}, total_nodes={}", recordId, executionId, workflowId, totalNodes);

            // Log execution trace summary
            logExecutionTraceSummary(executionId, workflowName, plan, executionMode);

            // Build and launch job
            launchWorkflowJob(plan, executionId, executionMode, workflowId);

            // Return immediate response with "running" status
            return Map.of(
                    "execution_id", executionId,
                    "status", "running",
                    "message", "Workflow execution started",
                    "total_nodes", plan.steps().size()
            );

        } catch (Exception e) {
            logger.error("Error executing workflow {}", executionId, e);
            // Update execution with error status
            try {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                String updateSql = "UPDATE workflow_executions SET status = ?, error_message = ?, end_time = ? WHERE execution_id = ?";
                jdbcTemplate.update(updateSql, "failed", errorMsg, System.currentTimeMillis(), executionId);
            } catch (Exception ex) {
                logger.error("Error updating execution status", ex);
            }
            return buildErrorResponse("Failed to execute workflow: " + e.getMessage());
        }
    }

    private void launchWorkflowJob(ExecutionPlan plan, String executionId, String executionMode, String workflowId) throws Exception {
        stepFactory.setApiListenerContext(jdbcTemplate, executionId);

        Job job = dynamicJobBuilder.buildJob(plan, workflowId);

        // Add job execution listener (works for FlowJob and SimpleJob)
        if (job instanceof org.springframework.batch.core.job.AbstractJob abstractJob) {
            abstractJob.registerJobExecutionListener(new PersistenceJobListener(jdbcTemplate, executionId));
            logger.debug("Registered PersistenceJobListener for executionId: {}", executionId);
        } else {
            logger.warn("Cannot register job listener - job type {} not supported", job.getClass().getName());
        }

        // Create job parameters
        JobParameters params = new JobParametersBuilder()
                .addString("executionId", executionId)
                .addString("executionMode", executionMode)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // Launch job asynchronously
        jobLauncher.run(job, params);
    }

    private void logExecutionTraceSummary(String executionId, String workflowName, ExecutionPlan plan, String executionMode) {
        int nodeCount = plan.steps().size();
        List<String> entrySteps = plan.entryStepIds();

        logger.info("===== EXECUTION TRACE SUMMARY =====");
        logger.info("ExecutionId: {}", executionId);
        logger.info("WorkflowName: {}", workflowName);
        logger.info("NodeCount: {}", nodeCount);
        logger.info("EntrySteps: {}", entrySteps);
        logger.info("ExecutionMode: {}", executionMode);

        for (String stepId : plan.steps().keySet()) {
            logger.debug("Step: id={}, type={}", stepId, plan.steps().get(stepId).nodeType());
        }

        logger.info("===== END TRACE SUMMARY =====");
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }

    public List<WorkflowExecutionDto> getExecutionHistory(String workflowId) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions";
        if (workflowId != null && !workflowId.isEmpty()) {
            sql += " WHERE workflow_id = ?";
            return jdbcTemplate.query(sql, new Object[]{workflowId}, this::mapExecutionDto);
        }
        return jdbcTemplate.query(sql, this::mapExecutionDto);
    }

    public WorkflowExecutionDto getExecutionById(String executionId) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions WHERE execution_id = ?";
        List<WorkflowExecutionDto> result = jdbcTemplate.query(sql, new Object[]{executionId}, this::mapExecutionDto);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<NodeExecutionDto> getNodeExecutions(String executionId) {
        try {
            String sql = "SELECT id, execution_id, node_id, node_label, node_type, status, start_time, end_time, execution_time_ms, records_processed, retry_count, error_message FROM node_executions WHERE execution_id = ? ORDER BY start_time ASC";
            List<NodeExecutionDto> result = jdbcTemplate.query(sql, new Object[]{executionId}, (rs, rowNum) -> {
                NodeExecutionDto dto = new NodeExecutionDto();
                dto.setId(rs.getString("id"));
                dto.setExecutionId(rs.getString("execution_id"));
                dto.setNodeId(rs.getString("node_id"));
                dto.setNodeLabel(rs.getString("node_label"));
                dto.setNodeType(rs.getString("node_type"));
                dto.setStatus(rs.getString("status"));
                dto.setStartTime(rs.getLong("start_time"));
                dto.setEndTime(rs.getLong("end_time"));
                dto.setExecutionTimeMs(rs.getLong("execution_time_ms"));
                dto.setRecordsProcessed(rs.getLong("records_processed"));
                dto.setRetryCount(rs.getInt("retry_count"));
                dto.setErrorMessage(rs.getString("error_message"));
                return dto;
            });

            logger.debug("Retrieved {} node executions for executionId: {}", result.size(), executionId);
            return result;
        } catch (Exception e) {
            logger.error("Error retrieving node executions for executionId: {}", executionId, e);
            return new ArrayList<>();
        }
    }

    public Map<String, Object> getExecutionTimeline(String executionId) {
        try {
            List<NodeExecutionDto> nodes = getNodeExecutions(executionId);
            List<Map<String, Object>> timeline = new ArrayList<>();

            if (nodes == null || nodes.isEmpty()) {
                logger.debug("No node executions found for executionId: {}", executionId);
                return Map.of(
                        "timeline", timeline,
                        "execution_id", executionId,
                        "status", "running",
                        "message", "Execution is still running or no nodes have started yet"
                );
            }

            for (NodeExecutionDto node : nodes) {
                timeline.add(Map.of(
                        "node_id", node.getNodeId(),
                        "node_label", node.getNodeLabel() != null ? node.getNodeLabel() : "",
                        "start_time", node.getStartTime() != null ? node.getStartTime() : 0,
                        "end_time", node.getEndTime() != null ? node.getEndTime() : 0,
                        "duration_ms", node.getExecutionTimeMs() != null ? node.getExecutionTimeMs() : 0
                ));
            }
            return Map.of("timeline", timeline);
        } catch (Exception e) {
            logger.error("Error retrieving execution timeline for executionId: {}", executionId, e);
            return Map.of(
                    "timeline", new ArrayList<>(),
                    "execution_id", executionId,
                    "error", e.getMessage()
            );
        }
    }

    public Map<String, Object> getExecutionMetrics(String executionId) {
        WorkflowExecutionDto execution = getExecutionById(executionId);
        if (execution == null) {
            return new HashMap<>();
        }
        return Map.of(
                "execution_id", executionId,
                "total_duration_ms", execution.getTotalExecutionTimeMs() != null ? execution.getTotalExecutionTimeMs() : 0,
                "total_records", execution.getTotalRecords() != null ? execution.getTotalRecords() : 0,
                "total_nodes", execution.getTotalNodes() != null ? execution.getTotalNodes() : 0,
                "successful_nodes", execution.getSuccessfulNodes() != null ? execution.getSuccessfulNodes() : 0,
                "failed_nodes", execution.getFailedNodes() != null ? execution.getFailedNodes() : 0
        );
    }

    public Map<String, Object> getExecutionBottlenecks(String executionId, int topN) {
        List<NodeExecutionDto> nodes = getNodeExecutions(executionId);
        WorkflowExecutionDto execution = getExecutionById(executionId);
        long totalTime = execution != null && execution.getTotalExecutionTimeMs() != null ? execution.getTotalExecutionTimeMs() : 1;

        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        nodes.stream()
                .filter(n -> n.getExecutionTimeMs() != null && n.getExecutionTimeMs() > 0)
                .sorted((a, b) -> Long.compare(b.getExecutionTimeMs(), a.getExecutionTimeMs()))
                .limit(topN)
                .forEach(node -> {
                    double percentage = (node.getExecutionTimeMs() * 100.0) / totalTime;
                    bottlenecks.add(Map.of(
                            "node_id", node.getNodeId(),
                            "node_label", node.getNodeLabel() != null ? node.getNodeLabel() : "",
                            "execution_time_ms", node.getExecutionTimeMs(),
                            "percentage_of_total", percentage
                    ));
                });

        return Map.of("bottlenecks", bottlenecks);
    }

    public Map<String, Object> rerunExecution(String executionId, String fromNodeId) {
        String newExecutionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowExecutionDto original = getExecutionById(executionId);

        if (original == null) {
            return buildErrorResponse("Original execution not found");
        }

        try {
            String payloadSql = "SELECT parameters FROM workflow_executions WHERE execution_id = ?";
            String workflowPayload = jdbcTemplate.queryForObject(payloadSql, String.class, executionId);

            if (workflowPayload == null || workflowPayload.isEmpty()) {
                return buildErrorResponse("Workflow payload not found");
            }

            String executionMode = "parallel";
            String workflowName = original.getWorkflowName();

            @SuppressWarnings("unchecked")
            Map<String, Object> workflowData = objectMapper.readValue(workflowPayload, Map.class);
            WorkflowDefinition workflow = objectMapper.convertValue(workflowData, WorkflowDefinition.class);

            String workflowId = workflow.getId() != null ? workflow.getId() : "workflow_" + UUID.randomUUID().toString().substring(0, 8);

            ExecutionPlan fullPlan = executionGraphBuilder.build(workflow);
            ExecutionPlan executionPlan;
            String message;

            if (fromNodeId != null && !fromNodeId.isEmpty()) {
                if (!fullPlan.steps().containsKey(fromNodeId)) {
                    return buildErrorResponse("Node '" + fromNodeId + "' not found in workflow");
                }

                executionPlan = partialRestartManager.createPartialPlan(fullPlan, fromNodeId);
                workflowName = workflowName + " (restart from " + fromNodeId + ")";
                message = "Partial restart from node '" + fromNodeId + "' started";
                logger.info("Starting partial rerun from node '{}' with {} steps", fromNodeId, executionPlan.steps().size());
            } else {
                executionPlan = fullPlan;
                message = "Full rerun started";
            }

            String recordId = UUID.randomUUID().toString();
            int planSize = executionPlan.steps().size();
            String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_id, workflow_name, status, start_time, execution_mode, parameters, total_nodes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, recordId, newExecutionId, workflowId, workflowName, "running",
                    System.currentTimeMillis(), executionMode, workflowPayload, planSize);

            launchWorkflowJob(executionPlan, newExecutionId, executionMode, workflowId);

            return Map.of(
                    "new_execution_id", newExecutionId,
                    "status", "running",
                    "message", message,
                    "total_nodes", planSize
            );

        } catch (Exception e) {
            logger.error("Error rerunning execution {}", executionId, e);
            return buildErrorResponse("Failed to rerun execution: " + e.getMessage());
        }
    }

    public Map<String, Object> rerunFromFailed(String executionId) {
        WorkflowExecutionDto original = getExecutionById(executionId);

        if (original == null) {
            return buildErrorResponse("Original execution not found");
        }

        try {
            String payloadSql = "SELECT parameters FROM workflow_executions WHERE execution_id = ?";
            String workflowPayload = jdbcTemplate.queryForObject(payloadSql, String.class, executionId);

            if (workflowPayload == null || workflowPayload.isEmpty()) {
                return buildErrorResponse("Workflow payload not found");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> workflowData = objectMapper.readValue(workflowPayload, Map.class);
            WorkflowDefinition workflow = objectMapper.convertValue(workflowData, WorkflowDefinition.class);

            String workflowId = workflow.getId() != null ? workflow.getId() : "workflow_" + UUID.randomUUID().toString().substring(0, 8);

            ExecutionPlan fullPlan = executionGraphBuilder.build(workflow);
            ExecutionPlan restartPlan = partialRestartManager.createPartialPlanFromFailedNodes(
                fullPlan, jdbcTemplate, executionId);

            String newExecutionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);
            String workflowName = original.getWorkflowName() + " (restart from failed)";
            String executionMode = "parallel";

            String recordId = UUID.randomUUID().toString();
            int planSize = restartPlan.steps().size();
            String insertSql = "INSERT INTO workflow_executions (id, execution_id, workflow_id, workflow_name, status, start_time, execution_mode, parameters, total_nodes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, recordId, newExecutionId, workflowId, workflowName, "running",
                    System.currentTimeMillis(), executionMode, workflowPayload, planSize);

            launchWorkflowJob(restartPlan, newExecutionId, executionMode, workflowId);

            logger.info("Started restart from failed nodes for execution '{}', new execution '{}'",
                executionId, newExecutionId);

            return Map.of(
                    "new_execution_id", newExecutionId,
                    "original_execution_id", executionId,
                    "status", "running",
                    "message", "Restart from failed nodes started",
                    "total_nodes", planSize
            );

        } catch (Exception e) {
            logger.error("Error rerunning from failed for execution {}", executionId, e);
            return buildErrorResponse("Failed to restart from failed: " + e.getMessage());
        }
    }

    public Map<String, Object> cancelExecution(String executionId) {
        try {
            String sql = "UPDATE workflow_executions SET status = ? WHERE execution_id = ? AND status = 'running'";
            int updated = jdbcTemplate.update(sql, "cancel_requested", executionId);

            if (updated > 0) {
                // Log the cancellation request
                String insertLogSql = "INSERT INTO execution_logs (timestamp, datetime, level, execution_id, message) " +
                        "VALUES (?, ?, ?, ?, ?)";
                long timestamp = System.currentTimeMillis();
                String datetime = java.time.Instant.ofEpochMilli(timestamp).toString();
                jdbcTemplate.update(insertLogSql, timestamp, datetime, "INFO", executionId, "Execution cancellation requested");

                return Map.of("status", "cancel_requested", "message", "Execution cancellation requested");
            } else {
                return buildErrorResponse("Execution not found or not in running state");
            }
        } catch (Exception e) {
            logger.error("Error cancelling execution {}", executionId, e);
            return buildErrorResponse("Failed to cancel execution: " + e.getMessage());
        }
    }

    public Map<String, Object> getRecentExecutions(int limit) {
        String sql = "SELECT id, execution_id, workflow_name, status, start_time, end_time, total_nodes, completed_nodes, successful_nodes, failed_nodes, total_records, total_execution_time_ms, error_message FROM workflow_executions ORDER BY start_time DESC LIMIT ?";
        List<WorkflowExecutionDto> executions = jdbcTemplate.query(sql, new Object[]{limit}, this::mapExecutionDto);
        return Map.of("executions", executions);
    }

    private WorkflowExecutionDto mapExecutionDto(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        WorkflowExecutionDto dto = new WorkflowExecutionDto();
        dto.setId(rs.getString("id"));
        dto.setExecutionId(rs.getString("execution_id"));
        dto.setWorkflowName(rs.getString("workflow_name"));
        dto.setStatus(rs.getString("status"));
        dto.setStartTime(rs.getLong("start_time"));
        dto.setEndTime(rs.getLong("end_time"));
        dto.setTotalNodes(rs.getInt("total_nodes"));
        dto.setCompletedNodes(rs.getInt("completed_nodes"));
        dto.setSuccessfulNodes(rs.getInt("successful_nodes"));
        dto.setFailedNodes(rs.getInt("failed_nodes"));
        dto.setTotalRecords(rs.getLong("total_records"));
        dto.setTotalExecutionTimeMs(rs.getLong("total_execution_time_ms"));
        dto.setErrorMessage(rs.getString("error_message"));
        return dto;
    }
}
