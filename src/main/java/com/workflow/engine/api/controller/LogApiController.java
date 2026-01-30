package com.workflow.engine.api.controller;

import com.workflow.engine.api.service.LogApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LogApiController {
    @Autowired
    private LogApiService logApiService;

    @GetMapping("/logs/executions")
    public ResponseEntity<Map<String, Object>> listExecutionLogs() {
        Map<String, Object> logs = logApiService.listExecutionLogs();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/executions/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            @PathVariable String executionId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String node_id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long start_time,
            @RequestParam(required = false) Long end_time,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> logs = logApiService.getExecutionLogs(executionId, level, node_id, search, start_time, end_time, limit, offset);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/nodes/{executionId}/{nodeId}")
    public ResponseEntity<Map<String, Object>> getNodeLogs(
            @PathVariable String executionId,
            @PathVariable String nodeId,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> logs = logApiService.getNodeLogs(executionId, nodeId, level, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/logs/search")
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam String search,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String execution_id,
            @RequestParam(required = false) Long start_time,
            @RequestParam(required = false) Long end_time,
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> results = logApiService.searchLogs(search, level, execution_id, start_time, end_time, limit);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/logs/summary/{executionId}")
    public ResponseEntity<Map<String, Object>> getLogSummary(@PathVariable String executionId) {
        Map<String, Object> summary = logApiService.getLogSummary(executionId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/analytics/execution-logs/{executionId}")
    public ResponseEntity<Map<String, Object>> getAnalyticsExecutionLogs(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "500") int limit) {
        Map<String, Object> logs = logApiService.getAnalyticsExecutionLogs(executionId, limit);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/analytics/logs/{executionId}")
    public ResponseEntity<Map<String, Object>> getLogAnalysis(@PathVariable String executionId) {
        Map<String, Object> analysis = logApiService.getLogAnalysis(executionId);
        return ResponseEntity.ok(analysis);
    }
}
