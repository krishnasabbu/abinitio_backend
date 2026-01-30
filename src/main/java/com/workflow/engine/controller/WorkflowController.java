package com.workflow.engine.controller;

import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowExecutionService workflowExecutionService;

    public WorkflowController(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<String> executeWorkflow(@RequestBody WorkflowDefinition workflow) {
        try {
            workflowExecutionService.executeWorkflow(workflow);
            return ResponseEntity.ok("Workflow execution started");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Workflow execution failed: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Workflow engine is running");
    }
}
