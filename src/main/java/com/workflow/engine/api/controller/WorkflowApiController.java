package com.workflow.engine.api.controller;

import com.workflow.engine.api.dto.WorkflowDto;
import com.workflow.engine.api.service.WorkflowApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class WorkflowApiController {
    @Autowired
    private WorkflowApiService workflowApiService;

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowDto>> getAllWorkflows() {
        List<WorkflowDto> workflows = workflowApiService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<WorkflowDto> getWorkflowById(@PathVariable String id) {
        WorkflowDto workflow = workflowApiService.getWorkflowById(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(workflow);
    }

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody WorkflowDto request) {
        String id = workflowApiService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/workflows/{id}")
    public ResponseEntity<Void> updateWorkflow(@PathVariable String id, @RequestBody WorkflowDto request) {
        workflowApiService.updateWorkflow(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/workflows/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable String id) {
        workflowApiService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workflows/stats")
    public ResponseEntity<Map<String, Object>> getWorkflowStats() {
        Map<String, Object> stats = workflowApiService.getWorkflowStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/workflows/recent")
    public ResponseEntity<Map<String, Object>> getRecentWorkflows(@RequestParam int limit) {
        Map<String, Object> recent = workflowApiService.getRecentWorkflows(limit);
        return ResponseEntity.ok(recent);
    }

    @GetMapping("/workflows/{workflowId}/analytics")
    public ResponseEntity<Map<String, Object>> getWorkflowAnalytics(@PathVariable String workflowId) {
        Map<String, Object> analytics = workflowApiService.getWorkflowAnalytics(workflowId);
        return ResponseEntity.ok(analytics);
    }
}
