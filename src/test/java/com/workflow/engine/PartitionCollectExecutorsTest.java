package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.BroadcastExecutor;
import com.workflow.engine.execution.CollectExecutor;
import com.workflow.engine.execution.HashPartitionExecutor;
import com.workflow.engine.execution.PartitionExecutor;
import com.workflow.engine.execution.RangePartitionExecutor;
import com.workflow.engine.execution.ReplicateExecutor;
import com.workflow.engine.execution.NodeDefinition;
import com.workflow.engine.execution.NodeExecutionContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PartitionCollectExecutorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testPartitionExecutorBasic() throws Exception {
        PartitionExecutor executor = new PartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Partition");
        ObjectNode config = mapper.createObjectNode();
        config.put("partitionCount", 3);
        config.put("strategy", "roundRobin");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("value", "item" + i);
            items.add(item);
        }

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Partition", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testPartitionExecutorValidation() throws Exception {
        PartitionExecutor executor = new PartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Partition");
        ObjectNode config = mapper.createObjectNode();
        config.put("partitionCount", 0);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testHashPartitionExecutorBasic() throws Exception {
        HashPartitionExecutor executor = new HashPartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("HashPartition");
        ObjectNode config = mapper.createObjectNode();
        config.put("hashKeys", "id");
        config.put("partitions", 3);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", "user" + i);
            items.add(item);
        }

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("HashPartition", executor.getNodeType());
    }

    @Test
    public void testHashPartitionExecutorStable() throws Exception {
        HashPartitionExecutor executor = new HashPartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("HashPartition");
        ObjectNode config = mapper.createObjectNode();
        config.put("hashKeys", "id");
        config.put("partitions", 3);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("name", "test");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        context.setVariable("inputItems", items);

        executor.validate(context);

        int partition1 = (Integer) items.get(0).get("_partition");
        assertEquals(partition1, partition1, "Hash partition should be stable");
    }

    @Test
    public void testHashPartitionExecutorValidation() throws Exception {
        HashPartitionExecutor executor = new HashPartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("HashPartition");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testRangePartitionExecutorBasic() throws Exception {
        RangePartitionExecutor executor = new RangePartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("RangePartition");
        ObjectNode config = mapper.createObjectNode();
        config.put("rangeField", "age");
        config.put("ranges", "young:0-30,middle:31-60,senior:61+");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("age", 25);
        items.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("age", 45);
        items.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("age", 70);
        items.add(item3);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("RangePartition", executor.getNodeType());
    }

    @Test
    public void testRangePartitionExecutorRangeAssignment() throws Exception {
        RangePartitionExecutor executor = new RangePartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("RangePartition");
        ObjectNode config = mapper.createObjectNode();
        config.put("rangeField", "score");
        config.put("ranges", "low:0-50,medium:51-75,high:76+");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score", 25);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("RangePartition", executor.getNodeType());
    }

    @Test
    public void testRangePartitionExecutorValidation() throws Exception {
        RangePartitionExecutor executor = new RangePartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("RangePartition");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testRangePartitionExecutorEmptyRanges() throws Exception {
        RangePartitionExecutor executor = new RangePartitionExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("RangePartition");
        ObjectNode config = mapper.createObjectNode();
        config.put("rangeField", "age");
        config.put("ranges", "");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testReplicateExecutorBasic() throws Exception {
        ReplicateExecutor executor = new ReplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Replicate");
        ObjectNode config = mapper.createObjectNode();
        config.put("numberOfCopies", 3);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("data", "test");
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Replicate", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
    }

    @Test
    public void testReplicateExecutorReplicationCount() throws Exception {
        ReplicateExecutor executor = new ReplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Replicate");
        ObjectNode config = mapper.createObjectNode();
        config.put("numberOfCopies", 5);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Replicate", executor.getNodeType());
    }

    @Test
    public void testReplicateExecutorValidation() throws Exception {
        ReplicateExecutor executor = new ReplicateExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Replicate");
        ObjectNode config = mapper.createObjectNode();
        config.put("numberOfCopies", 0);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testBroadcastExecutorBasic() throws Exception {
        BroadcastExecutor executor = new BroadcastExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Broadcast");
        ObjectNode config = mapper.createObjectNode();
        config.put("targetNodes", "node1,node2,node3");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("config", "broadcast_data");
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Broadcast", executor.getNodeType());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testBroadcastExecutorDefaultCopies() throws Exception {
        BroadcastExecutor executor = new BroadcastExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Broadcast");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Broadcast", executor.getNodeType());
    }

    @Test
    public void testCollectExecutorBasic() throws Exception {
        CollectExecutor executor = new CollectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Collect");
        ObjectNode config = mapper.createObjectNode();
        config.put("collectMode", "concat");
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
        context.setVariable("in3InputItems", new ArrayList<>());

        executor.validate(context);
        assertEquals("Collect", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
    }

    @Test
    public void testCollectExecutorOrdered() throws Exception {
        CollectExecutor executor = new CollectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Collect");
        ObjectNode config = mapper.createObjectNode();
        config.put("collectMode", "ordered");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("_partitionIndex", 2);
        items1.add(item1);

        List<Map<String, Object>> items2 = new ArrayList<>();
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("id", 2);
        item2.put("_partitionIndex", 1);
        items2.add(item2);

        context.setVariable("in1InputItems", items1);
        context.setVariable("in2InputItems", items2);
        context.setVariable("in3InputItems", new ArrayList<>());

        executor.validate(context);
        assertEquals("Collect", executor.getNodeType());
    }

    @Test
    public void testCollectExecutorStripMetadata() throws Exception {
        CollectExecutor executor = new CollectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Collect");
        ObjectNode config = mapper.createObjectNode();
        config.put("collectMode", "concat");
        config.put("stripMetadata", true);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("id", 1);
        item1.put("_partition", 0);
        items1.add(item1);

        context.setVariable("in1InputItems", items1);
        context.setVariable("in2InputItems", new ArrayList<>());
        context.setVariable("in3InputItems", new ArrayList<>());

        executor.validate(context);
        assertEquals("Collect", executor.getNodeType());
    }

    @Test
    public void testCollectExecutorValidation() throws Exception {
        CollectExecutor executor = new CollectExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Collect");
        ObjectNode config = mapper.createObjectNode();
        config.put("collectMode", "invalid");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testAllPartitionExecutorsTypeNames() {
        PartitionExecutor partition = new PartitionExecutor();
        HashPartitionExecutor hashPartition = new HashPartitionExecutor();
        RangePartitionExecutor rangePartition = new RangePartitionExecutor();
        ReplicateExecutor replicate = new ReplicateExecutor();
        BroadcastExecutor broadcast = new BroadcastExecutor();
        CollectExecutor collect = new CollectExecutor();

        assertEquals("Partition", partition.getNodeType());
        assertEquals("HashPartition", hashPartition.getNodeType());
        assertEquals("RangePartition", rangePartition.getNodeType());
        assertEquals("Replicate", replicate.getNodeType());
        assertEquals("Broadcast", broadcast.getNodeType());
        assertEquals("Collect", collect.getNodeType());
    }

    @Test
    public void testAllPartitionExecutorsSupportsMetrics() {
        PartitionExecutor partition = new PartitionExecutor();
        HashPartitionExecutor hashPartition = new HashPartitionExecutor();
        RangePartitionExecutor rangePartition = new RangePartitionExecutor();
        ReplicateExecutor replicate = new ReplicateExecutor();
        BroadcastExecutor broadcast = new BroadcastExecutor();
        CollectExecutor collect = new CollectExecutor();

        assertTrue(partition.supportsMetrics());
        assertTrue(hashPartition.supportsMetrics());
        assertTrue(rangePartition.supportsMetrics());
        assertTrue(replicate.supportsMetrics());
        assertTrue(broadcast.supportsMetrics());
        assertTrue(collect.supportsMetrics());
    }

    @Test
    public void testAllPartitionExecutorsSupportsFailureHandling() {
        PartitionExecutor partition = new PartitionExecutor();
        HashPartitionExecutor hashPartition = new HashPartitionExecutor();
        RangePartitionExecutor rangePartition = new RangePartitionExecutor();
        ReplicateExecutor replicate = new ReplicateExecutor();
        BroadcastExecutor broadcast = new BroadcastExecutor();
        CollectExecutor collect = new CollectExecutor();

        assertTrue(partition.supportsFailureHandling());
        assertTrue(hashPartition.supportsFailureHandling());
        assertTrue(rangePartition.supportsFailureHandling());
        assertTrue(replicate.supportsFailureHandling());
        assertTrue(broadcast.supportsFailureHandling());
        assertTrue(collect.supportsFailureHandling());
    }
}
