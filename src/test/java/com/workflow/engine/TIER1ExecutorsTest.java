package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.AggregateExecutor;
import com.workflow.engine.execution.AssertExecutor;
import com.workflow.engine.execution.DecisionExecutor;
import com.workflow.engine.execution.DenormalizeExecutor;
import com.workflow.engine.execution.EndExecutor;
import com.workflow.engine.execution.FailJobExecutor;
import com.workflow.engine.execution.FilterExecutor;
import com.workflow.engine.execution.MapExecutor;
import com.workflow.engine.execution.NodeDefinition;
import com.workflow.engine.execution.NodeExecutionContext;
import com.workflow.engine.execution.SchemaValidatorExecutor;
import com.workflow.engine.execution.SortExecutor;
import com.workflow.engine.execution.StartExecutor;
import com.workflow.engine.execution.SwitchExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TIER1ExecutorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testStartExecutor() throws Exception {
        StartExecutor executor = new StartExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Start");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("inputItems", new ArrayList<>());

        executor.validate(context);

        var reader = executor.createReader(context);
        var processor = executor.createProcessor(context);
        var writer = executor.createWriter(context);

        assertNotNull(reader);
        assertNotNull(processor);
        assertNotNull(writer);
        assertEquals("Start", executor.getNodeType());
    }

    @Test
    public void testEndExecutor() throws Exception {
        EndExecutor executor = new EndExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("End");
        ObjectNode config = mapper.createObjectNode();
        config.put("exitStatus", "COMPLETED");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(new HashMap<>());
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("End", executor.getNodeType());
    }

    @Test
    public void testFailJobExecutor() throws Exception {
        FailJobExecutor executor = new FailJobExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("FailJob");
        ObjectNode config = mapper.createObjectNode();
        config.put("message", "Test failure");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("inputItems", new ArrayList<>());

        executor.validate(context);
        assertEquals("FailJob", executor.getNodeType());
    }

    @Test
    public void testDecisionExecutor() throws Exception {
        DecisionExecutor executor = new DecisionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Decision");
        ObjectNode config = mapper.createObjectNode();
        config.put("description", "Route based on condition");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("age", 25);
        items.add(item);
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Decision", executor.getNodeType());
    }

    @Test
    public void testSwitchExecutor() throws Exception {
        SwitchExecutor executor = new SwitchExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Switch");
        ObjectNode config = mapper.createObjectNode();
        config.put("rules", "age > 18:adult\nage <= 18:minor");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("age", 25);
        items.add(item);
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Switch", executor.getNodeType());
    }

    @Test
    public void testSortExecutor() throws Exception {
        SortExecutor executor = new SortExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Sort");
        ObjectNode config = mapper.createObjectNode();
        config.put("sortKeys", "age:asc");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("name", "Bob");
        item1.put("age", 30);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("name", "Alice");
        item2.put("age", 25);

        items.add(item1);
        items.add(item2);
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Sort", executor.getNodeType());
    }

    @Test
    public void testSortExecutorValidation() throws Exception {
        SortExecutor executor = new SortExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Sort");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testAggregateExecutor() throws Exception {
        AggregateExecutor executor = new AggregateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Aggregate");
        ObjectNode config = mapper.createObjectNode();
        config.put("groupByFields", "department");
        config.put("aggregates", "salary:sum\ncount:count");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("department", "Sales");
        item1.put("salary", 50000);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("department", "Sales");
        item2.put("salary", 60000);

        items.add(item1);
        items.add(item2);
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Aggregate", executor.getNodeType());
    }

    @Test
    public void testAggregateExecutorValidation() throws Exception {
        AggregateExecutor executor = new AggregateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Aggregate");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testSchemaValidatorExecutor() throws Exception {
        SchemaValidatorExecutor executor = new SchemaValidatorExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SchemaValidator");
        ObjectNode config = mapper.createObjectNode();
        config.put("schemaFields", "name:string,age:int");
        config.put("onMismatch", "FAIL");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", "John");
        item.put("age", 30);
        items.add(item);
        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("SchemaValidator", executor.getNodeType());
    }

    @Test
    public void testSchemaValidatorExecutorValidation() throws Exception {
        SchemaValidatorExecutor executor = new SchemaValidatorExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SchemaValidator");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testExecutorsSupportsMetrics() {
        StartExecutor startExec = new StartExecutor();
        EndExecutor endExec = new EndExecutor();
        FailJobExecutor failExec = new FailJobExecutor();
        DecisionExecutor decExec = new DecisionExecutor();
        SwitchExecutor switchExec = new SwitchExecutor();
        SortExecutor sortExec = new SortExecutor();
        AggregateExecutor aggExec = new AggregateExecutor();
        SchemaValidatorExecutor schemaExec = new SchemaValidatorExecutor();

        assertTrue(startExec.supportsMetrics());
        assertTrue(endExec.supportsMetrics());
        assertTrue(failExec.supportsMetrics());
        assertTrue(decExec.supportsMetrics());
        assertTrue(switchExec.supportsMetrics());
        assertTrue(sortExec.supportsMetrics());
        assertTrue(aggExec.supportsMetrics());
        assertTrue(schemaExec.supportsMetrics());
    }

    @Test
    public void testExecutorsSupportsFailureHandling() {
        StartExecutor startExec = new StartExecutor();
        EndExecutor endExec = new EndExecutor();
        FailJobExecutor failExec = new FailJobExecutor();
        DecisionExecutor decExec = new DecisionExecutor();
        SwitchExecutor switchExec = new SwitchExecutor();
        SortExecutor sortExec = new SortExecutor();
        AggregateExecutor aggExec = new AggregateExecutor();
        SchemaValidatorExecutor schemaExec = new SchemaValidatorExecutor();

        assertTrue(startExec.supportsFailureHandling());
        assertTrue(endExec.supportsFailureHandling());
        assertTrue(failExec.supportsFailureHandling());
        assertTrue(decExec.supportsFailureHandling());
        assertTrue(switchExec.supportsFailureHandling());
        assertTrue(sortExec.supportsFailureHandling());
        assertTrue(aggExec.supportsFailureHandling());
        assertTrue(schemaExec.supportsFailureHandling());
    }
}
