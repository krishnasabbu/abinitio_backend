package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.model.WorkflowDefinition;
import com.workflow.engine.service.WorkflowExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

@SpringBootTest
public class DynamicJobBuilderTest {

    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testBasicWorkflow() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEST 1: Basic Workflow Execution");
        System.out.println("=".repeat(80) + "\n");

        File jsonFile = new File("./test1_simple.json");
        WorkflowDefinition workflow = objectMapper.readValue(jsonFile, WorkflowDefinition.class);

        workflowExecutionService.executeWorkflow(workflow);

        System.out.println("\nTest completed successfully!");
    }
}
