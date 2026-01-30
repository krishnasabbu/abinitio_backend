package com.workflow.engine.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Profile("test")
public class WorkflowTestRunner implements CommandLineRunner {

    private final WorkflowExecutionService workflowExecutionService;
    private final ObjectMapper objectMapper;

    public WorkflowTestRunner(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("WORKFLOW TEST RUNNER");
        System.out.println("=".repeat(80) + "\n");

        if (args.length == 0) {
            System.out.println("Usage: Pass workflow JSON file path as argument");
            System.out.println("Example: java -jar app.jar test1_basic_flow.json");
            return;
        }

        String jsonFilePath = args[0];
        File jsonFile = new File(jsonFilePath);

        if (!jsonFile.exists()) {
            System.out.println("Error: File not found: " + jsonFilePath);
            return;
        }

        System.out.println("Loading workflow from: " + jsonFilePath);
        WorkflowDefinition workflow = objectMapper.readValue(jsonFile, WorkflowDefinition.class);

        System.out.println("Workflow ID: " + workflow.getId());
        System.out.println("Workflow Name: " + workflow.getName());
        System.out.println("Nodes: " + workflow.getNodes().size());
        System.out.println("Edges: " + workflow.getEdges().size());
        System.out.println("\n" + "-".repeat(80));
        System.out.println("EXECUTING WORKFLOW");
        System.out.println("-".repeat(80) + "\n");

        try {
            workflowExecutionService.executeWorkflow(workflow);
            System.out.println("\n" + "-".repeat(80));
            System.out.println("WORKFLOW EXECUTION COMPLETED SUCCESSFULLY");
            System.out.println("-".repeat(80) + "\n");
        } catch (Exception e) {
            System.out.println("\n" + "-".repeat(80));
            System.out.println("WORKFLOW EXECUTION FAILED");
            System.out.println("-".repeat(80));
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
