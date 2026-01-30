package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.DeduplicateExecutor;
import com.workflow.engine.execution.IntersectExecutor;
import com.workflow.engine.execution.JoinExecutor;
import com.workflow.engine.execution.LookupExecutor;
import com.workflow.engine.execution.MergeExecutor;
import com.workflow.engine.execution.MinusExecutor;
import com.workflow.engine.execution.NodeDefinition;
import com.workflow.engine.execution.NodeExecutionContext;
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

public class JoinLookupExecutorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testJoinExecutorInner() throws Exception {
        JoinExecutor executor = new JoinExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Join");
        ObjectNode config = mapper.createObjectNode();
        config.put("joinType", "inner");
        config.put("leftKeys", "id");
        config.put("rightKeys", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> leftItems = new ArrayList<>();
        Map<String, Object> left1 = new LinkedHashMap<>();
        left1.put("id", 1);
        left1.put("name", "Alice");
        leftItems.add(left1);

        List<Map<String, Object>> rightItems = new ArrayList<>();
        Map<String, Object> right1 = new LinkedHashMap<>();
        right1.put("id", 1);
        right1.put("age", 30);
        rightItems.add(right1);

        context.setVariable("leftInputItems", leftItems);
        context.setVariable("rightInputItems", rightItems);

        executor.validate(context);
        assertEquals("Join", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testJoinExecutorValidation() throws Exception {
        JoinExecutor executor = new JoinExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Join");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testJoinExecutorEmptyInputs() throws Exception {
        JoinExecutor executor = new JoinExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Join");
        ObjectNode config = mapper.createObjectNode();
        config.put("joinType", "inner");
        config.put("leftKeys", "id");
        config.put("rightKeys", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("leftInputItems", new ArrayList<>());
        context.setVariable("rightInputItems", new ArrayList<>());

        executor.validate(context);
        var reader = executor.createReader(context);
        var processor = executor.createProcessor(context);
        var writer = executor.createWriter(context);

        assertNotNull(reader);
        assertNotNull(processor);
        assertNotNull(writer);
    }

    @Test
    public void testLookupExecutor() throws Exception {
        LookupExecutor executor = new LookupExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Lookup");
        ObjectNode config = mapper.createObjectNode();
        config.put("joinKeys", "product_id");
        config.put("cacheSize", 1000);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> mainItems = new ArrayList<>();
        Map<String, Object> main1 = new LinkedHashMap<>();
        main1.put("product_id", 1);
        main1.put("qty", 5);
        mainItems.add(main1);

        List<Map<String, Object>> lookupItems = new ArrayList<>();
        Map<String, Object> lookup1 = new LinkedHashMap<>();
        lookup1.put("product_id", 1);
        lookup1.put("name", "Widget");
        lookup1.put("price", 10.5);
        lookupItems.add(lookup1);

        context.setVariable("mainInputItems", mainItems);
        context.setVariable("lookupInputItems", lookupItems);

        executor.validate(context);
        assertEquals("Lookup", executor.getNodeType());
    }

    @Test
    public void testLookupExecutorValidation() throws Exception {
        LookupExecutor executor = new LookupExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Lookup");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testMergeExecutor() throws Exception {
        MergeExecutor executor = new MergeExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Merge");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        items1.add(item1);

        List<Map<String, Object>> items2 = new ArrayList<>();
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 2);
        items2.add(item2);

        context.setVariable("in1InputItems", items1);
        context.setVariable("in2InputItems", items2);

        executor.validate(context);
        assertEquals("Merge", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testMergeExecutorEmptyInputs() throws Exception {
        MergeExecutor executor = new MergeExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Merge");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("in1InputItems", new ArrayList<>());
        context.setVariable("in2InputItems", new ArrayList<>());

        executor.validate(context);
        var reader = executor.createReader(context);

        assertNotNull(reader);
    }

    @Test
    public void testDeduplicateExecutor() throws Exception {
        DeduplicateExecutor executor = new DeduplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Deduplicate");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        config.put("keep", "first");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "Alice");
        items.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 1);
        item2.put("name", "Alice");
        items.add(item2);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Deduplicate", executor.getNodeType());
    }

    @Test
    public void testDeduplicateExecutorValidation() throws Exception {
        DeduplicateExecutor executor = new DeduplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Deduplicate");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testDeduplicateExecutorLast() throws Exception {
        DeduplicateExecutor executor = new DeduplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Deduplicate");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        config.put("keep", "last");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("value", 10);
        items.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 1);
        item2.put("value", 20);
        items.add(item2);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Deduplicate", executor.getNodeType());
    }

    @Test
    public void testIntersectExecutor() throws Exception {
        IntersectExecutor executor = new IntersectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Intersect");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "Alice");
        items1.add(item1);

        List<Map<String, Object>> items2 = new ArrayList<>();
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 1);
        item2.put("age", 30);
        items2.add(item2);

        context.setVariable("in1InputItems", items1);
        context.setVariable("in2InputItems", items2);

        executor.validate(context);
        assertEquals("Intersect", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
    }

    @Test
    public void testIntersectExecutorValidation() throws Exception {
        IntersectExecutor executor = new IntersectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Intersect");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testIntersectExecutorEmptyInputs() throws Exception {
        IntersectExecutor executor = new IntersectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Intersect");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("in1InputItems", new ArrayList<>());
        context.setVariable("in2InputItems", new ArrayList<>());

        executor.validate(context);
        var reader = executor.createReader(context);

        assertNotNull(reader);
    }

    @Test
    public void testMinusExecutor() throws Exception {
        MinusExecutor executor = new MinusExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Minus");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("name", "Alice");
        items1.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 2);
        item2.put("name", "Bob");
        items1.add(item2);

        List<Map<String, Object>> items2 = new ArrayList<>();
        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("id", 1);
        items2.add(item3);

        context.setVariable("in1InputItems", items1);
        context.setVariable("in2InputItems", items2);

        executor.validate(context);
        assertEquals("Minus", executor.getNodeType());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testMinusExecutorValidation() throws Exception {
        MinusExecutor executor = new MinusExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Minus");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testMinusExecutorEmptyInputs() throws Exception {
        MinusExecutor executor = new MinusExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Minus");
        ObjectNode config = mapper.createObjectNode();
        config.put("keyFields", "id");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);
        context.setVariable("in1InputItems", new ArrayList<>());
        context.setVariable("in2InputItems", new ArrayList<>());

        executor.validate(context);
        var reader = executor.createReader(context);

        assertNotNull(reader);
    }

    @Test
    public void testAllExecutorsTypeNamesMatch() {
        JoinExecutor join = new JoinExecutor();
        LookupExecutor lookup = new LookupExecutor();
        MergeExecutor merge = new MergeExecutor();
        DeduplicateExecutor dedup = new DeduplicateExecutor();
        IntersectExecutor intersect = new IntersectExecutor();
        MinusExecutor minus = new MinusExecutor();

        assertEquals("Join", join.getNodeType());
        assertEquals("Lookup", lookup.getNodeType());
        assertEquals("Merge", merge.getNodeType());
        assertEquals("Deduplicate", dedup.getNodeType());
        assertEquals("Intersect", intersect.getNodeType());
        assertEquals("Minus", minus.getNodeType());
    }

    @Test
    public void testAllExecutorsSupportsMetrics() {
        JoinExecutor join = new JoinExecutor();
        LookupExecutor lookup = new LookupExecutor();
        MergeExecutor merge = new MergeExecutor();
        DeduplicateExecutor dedup = new DeduplicateExecutor();
        IntersectExecutor intersect = new IntersectExecutor();
        MinusExecutor minus = new MinusExecutor();

        assertTrue(join.supportsMetrics());
        assertTrue(lookup.supportsMetrics());
        assertTrue(merge.supportsMetrics());
        assertTrue(dedup.supportsMetrics());
        assertTrue(intersect.supportsMetrics());
        assertTrue(minus.supportsMetrics());
    }

    @Test
    public void testAllExecutorsSupportsFailureHandling() {
        JoinExecutor join = new JoinExecutor();
        LookupExecutor lookup = new LookupExecutor();
        MergeExecutor merge = new MergeExecutor();
        DeduplicateExecutor dedup = new DeduplicateExecutor();
        IntersectExecutor intersect = new IntersectExecutor();
        MinusExecutor minus = new MinusExecutor();

        assertTrue(join.supportsFailureHandling());
        assertTrue(lookup.supportsFailureHandling());
        assertTrue(merge.supportsFailureHandling());
        assertTrue(dedup.supportsFailureHandling());
        assertTrue(intersect.supportsFailureHandling());
        assertTrue(minus.supportsFailureHandling());
    }
}
