package com.workflow.engine.controller;

import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * execution service. Returns immediately with a status indicating whether
     * the submission was successful.
     *
     * @param workflow the workflow definition to execute
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/execute")
    public ResponseEntity<String> executeWorkflow(@RequestBody WorkflowDefinition workflow) {
        logger.info("Received workflow execution request: {}", workflow.getName());
        try {
            workflowExecutionService.executeWorkflow(workflow);
            logger.info("Workflow execution started: {}", workflow.getName());
            return ResponseEntity.ok("Workflow execution started");
        } catch (Exception e) {
            logger.error("Workflow execution failed: {}", workflow.getName(), e);
            return ResponseEntity.badRequest().body("Workflow execution failed: " + e.getMessage());
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
