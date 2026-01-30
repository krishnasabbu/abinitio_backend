package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.WorkflowExecutionDto;
import com.workflow.engine.api.dto.NodeExecutionDto;
import com.workflow.engine.api.service.ExecutionApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExecutionApiController {
    @Autowired
    private ExecutionApiService executionApiService;

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeWorkflow(
            @RequestParam String execution_mode,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = executionApiService.executeWorkflow(execution_mode, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/executions")
    public ResponseEntity<List<WorkflowExecutionDto>> getExecutionHistory(
            @RequestParam(required = false) String workflow_id) {
        List<WorkflowExecutionDto> executions = executionApiService.getExecutionHistory(workflow_id);
        return ResponseEntity.ok(executions);
    }

    @GetMapping("/execution/{executionId}")
    public ResponseEntity<WorkflowExecutionDto> getExecutionById(@PathVariable String executionId) {
        WorkflowExecutionDto execution = executionApiService.getExecutionById(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution);
    }

    @GetMapping("/executions/{executionId}/nodes")
    public ResponseEntity<List<NodeExecutionDto>> getNodeExecutions(@PathVariable String executionId) {
        List<NodeExecutionDto> nodes = executionApiService.getNodeExecutions(executionId);
        return ResponseEntity.ok(nodes);
    }

    @GetMapping("/executions/{executionId}/timeline")
    public ResponseEntity<Map<String, Object>> getExecutionTimeline(@PathVariable String executionId) {
        Map<String, Object> timeline = executionApiService.getExecutionTimeline(executionId);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/executions/{executionId}/metrics")
    public ResponseEntity<Map<String, Object>> getExecutionMetrics(@PathVariable String executionId) {
        Map<String, Object> metrics = executionApiService.getExecutionMetrics(executionId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/executions/{executionId}/bottlenecks")
    public ResponseEntity<Map<String, Object>> getExecutionBottlenecks(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "5") int top_n) {
        Map<String, Object> bottlenecks = executionApiService.getExecutionBottlenecks(executionId, top_n);
        return ResponseEntity.ok(bottlenecks);
    }

    @PostMapping("/executions/{executionId}/rerun")
    public ResponseEntity<Map<String, Object>> rerunExecution(
            @PathVariable String executionId,
            @RequestParam(required = false) String from_node_id) {
        Map<String, Object> result = executionApiService.rerunExecution(executionId, from_node_id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelExecution(@PathVariable String executionId) {
        Map<String, Object> result = executionApiService.cancelExecution(executionId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/executions/recent")
    public ResponseEntity<Map<String, Object>> getRecentExecutions(@RequestParam int limit) {
        Map<String, Object> recent = executionApiService.getRecentExecutions(limit);
        return ResponseEntity.ok(recent);
    }
}
