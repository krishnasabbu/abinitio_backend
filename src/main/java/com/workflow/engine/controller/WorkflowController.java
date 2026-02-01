package com.workflow.engine.controller;

import com.workflow.engine.api.dto.WorkflowExecutionResponseDto;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * REST controller for workflow execution endpoints.
 *
 * Exposes HTTP API for submitting workflows for execution and checking
 * the health status of the workflow engine.
 *
 * Endpoints:
 * - POST /api/workflows/execute: Submit a workflow for execution
 * - GET /api/workflows/health: Check engine health status
 *
 * Thread safety: Thread-safe. Stateless REST controller.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see WorkflowExecutionService
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowExecutionService workflowExecutionService;

    /**
     * Constructs a WorkflowController with the workflow execution service.
     *
     * @param workflowExecutionService the service for executing workflows
     */
    public WorkflowController(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    /**
     * Submits a workflow definition for execution.
     *
     * Accepts a workflow definition in the request body and submits it to the
     * execution service. Returns execution details including execution ID and node statuses.
     *
     * @param workflow the workflow definition to execute
     * @return ResponseEntity with workflow execution response including ID and node statuses
     */
    @PostMapping("/execute")
    public ResponseEntity<WorkflowExecutionResponseDto> executeWorkflow(@RequestBody WorkflowDefinition workflow) {
        logger.info("Received workflow execution request: {}", workflow.getName());
        try {
            workflow.setId(UUID.randomUUID().toString());
            WorkflowExecutionResponseDto response = workflowExecutionService.executeWorkflowWithResponse(workflow);
            logger.info("Workflow execution completed: {}", workflow.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Workflow execution failed: {}", workflow.getName(), e);
            WorkflowExecutionResponseDto errorResponse = new WorkflowExecutionResponseDto();
            errorResponse.setWorkflowName(workflow.getName());
            errorResponse.setStatus("FAILED");
            errorResponse.setErrorMessage("Workflow execution failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Checks the health status of the workflow engine.
     *
     * @return ResponseEntity with health status message
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.debug("Health check requested");
        return ResponseEntity.ok("Workflow engine is running");
    }
}
