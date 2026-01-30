package com.workflow.engine.compatibility;

import com.workflow.engine.execution.NodeExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class NodeCoverageTest {

    @Autowired
    private NodeExecutorRegistry nodeExecutorRegistry;

    private static final List<String> CRITICAL_NODES = Arrays.asList(
        "Start", "End", "FileSource", "FileSink", "Filter", "Map", "Reformat",
        "Aggregate", "Join", "Switch", "Partition", "Collect", "Validate",
        "DBSource", "DBSink"
    );

    private static final List<String> DATA_NODES = Arrays.asList(
        "FileSource", "FileSink", "DBSource", "DBSink", "KafkaSource", "KafkaSink",
        "RestAPISource", "RestAPISink", "ErrorSink"
    );

    private static final List<String> TRANSFORM_NODES = Arrays.asList(
        "Map", "Compute", "Reformat", "Normalize", "Denormalize", "Filter",
        "XMLParse", "XMLFlatten", "JSONFlatten", "Encrypt", "Decrypt"
    );

    private static final List<String> ROUTING_NODES = Arrays.asList(
        "Switch", "Decision", "Split", "Gather", "Filter"
    );

    private static final List<String> JOIN_NODES = Arrays.asList(
        "Join", "Lookup", "Merge", "Intersect", "Minus", "Deduplicate"
    );

    private static final List<String> AGGREGATION_NODES = Arrays.asList(
        "Aggregate", "Sort", "Rollup", "Window", "Scan", "Sample", "Count", "Limit"
    );

    private static final List<String> PARTITION_NODES = Arrays.asList(
        "Partition", "HashPartition", "RangePartition", "Replicate", "Broadcast", "Collect"
    );

    private static final List<String> CONTROL_NODES = Arrays.asList(
        "Start", "End", "FailJob", "Wait", "JobCondition", "Checkpoint", "Resume"
    );

    private static final List<String> ADVANCED_NODES = Arrays.asList(
        "PythonNode", "ScriptNode", "ShellNode", "CustomNode", "WebServiceCall", "Subgraph"
    );

    @Test
    public void testAllCriticalNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : CRITICAL_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing critical node executors: " + missing);
    }

    @Test
    public void testAllDataSourceSinkNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : DATA_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing data source/sink executors: " + missing);
    }

    @Test
    public void testAllTransformNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : TRANSFORM_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing transform node executors: " + missing);
    }

    @Test
    public void testAllRoutingNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : ROUTING_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing routing node executors: " + missing);
    }

    @Test
    public void testAllJoinNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : JOIN_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing join node executors: " + missing);
    }

    @Test
    public void testAllAggregationNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : AGGREGATION_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing aggregation node executors: " + missing);
    }

    @Test
    public void testAllPartitionNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : PARTITION_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing partition node executors: " + missing);
    }

    @Test
    public void testAllControlNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : CONTROL_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing control node executors: " + missing);
    }

    @Test
    public void testAllAdvancedNodesExist() {
        List<String> missing = new java.util.ArrayList<>();
        for (String nodeType : ADVANCED_NODES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missing.add(nodeType);
            }
        }
        assertTrue(missing.isEmpty(), "Missing advanced node executors: " + missing);
    }

    @Test
    public void testNodesFromFrontendCanBeRetrieved() {
        Set<String> allNodeTypes = new HashSet<>();
        allNodeTypes.addAll(CRITICAL_NODES);
        allNodeTypes.addAll(DATA_NODES);
        allNodeTypes.addAll(TRANSFORM_NODES);
        allNodeTypes.addAll(ROUTING_NODES);
        allNodeTypes.addAll(JOIN_NODES);
        allNodeTypes.addAll(AGGREGATION_NODES);
        allNodeTypes.addAll(PARTITION_NODES);
        allNodeTypes.addAll(CONTROL_NODES);
        allNodeTypes.addAll(ADVANCED_NODES);

        for (String nodeType : allNodeTypes) {
            assertTrue(nodeExecutorRegistry.hasExecutor(nodeType),
                "Executor not found for node type: " + nodeType);
        }
    }
}
