package com.workflow.engine.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.DagExecutionEngineApplication;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;

public class WorkflowRunner {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java WorkflowRunner <workflow-json-file>");
            System.exit(1);
        }

        String workflowFile = args[0];

        System.out.println("\n" + "=".repeat(100));
        System.out.println("DYNAMIC JOB BUILDER - WORKFLOW EXECUTION TEST");
        System.out.println("=".repeat(100));
        System.out.println("Workflow File: " + workflowFile);
        System.out.println("=".repeat(100) + "\n");

        try {
            File jsonFile = new File(workflowFile);
            if (!jsonFile.exists()) {
                System.err.println("ERROR: File not found: " + workflowFile);
                System.exit(1);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            WorkflowDefinition workflow = objectMapper.readValue(jsonFile, WorkflowDefinition.class);

            System.out.println("Workflow loaded:");
            System.out.println("  ID: " + workflow.getId());
            System.out.println("  Name: " + workflow.getName());
            System.out.println("  Nodes: " + workflow.getNodes().size());
            System.out.println("  Edges: " + workflow.getEdges().size());
            System.out.println();

            ConfigurableApplicationContext context = SpringApplication.run(
                DagExecutionEngineApplication.class,
                "--spring.main.web-application-type=none"
            );

            WorkflowExecutionService service = context.getBean(WorkflowExecutionService.class);

            System.out.println("Starting workflow execution...\n");
            service.executeWorkflow(workflow);

            System.out.println("\n" + "=".repeat(100));
            System.out.println("✓ WORKFLOW EXECUTION COMPLETED SUCCESSFULLY");
            System.out.println("=".repeat(100) + "\n");

            context.close();
            System.exit(0);

        } catch (Exception e) {
            System.err.println("\n" + "=".repeat(100));
            System.err.println("✗ WORKFLOW EXECUTION FAILED");
            System.err.println("=".repeat(100));
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
