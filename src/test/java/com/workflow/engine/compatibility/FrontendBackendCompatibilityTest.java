package com.workflow.engine.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.GraphValidationException;
import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FrontendBackendCompatibilityTest {

    @Autowired
    private ExecutionGraphBuilder executionGraphBuilder;

    @Autowired
    private NodeExecutorRegistry nodeExecutorRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Set<String> ALL_FRONTEND_NODE_TYPES = new HashSet<>(java.util.Arrays.asList(
        "Start", "End", "FailJob", "Wait", "JobCondition", "SchemaValidator",
        "FileSource", "FileSink", "DBSource", "DBSink", "KafkaSource", "KafkaSink",
        "Reformat", "Compute", "Map", "Normalize", "Denormalize", "Filter",
        "Decision", "Switch", "Split", "Gather", "Reject", "Join", "Lookup",
        "Merge", "Deduplicate", "Intersect", "Minus", "Sort", "Aggregate",
        "Rollup", "Window", "Scan", "Partition", "HashPartition", "RangePartition",
        "Replicate", "Broadcast", "Collect", "Validate", "Assert", "Sample",
        "Count", "Limit", "Checkpoint", "PythonNode", "ScriptNode", "ShellNode",
        "CustomNode", "RestAPISource", "RestAPISink", "Subgraph", "WebServiceCall",
        "XMLSplit", "XMLCombine", "DBExecute", "Encrypt", "Decrypt", "ErrorSink",
        "XMLParse", "XMLValidate", "JSONFlatten", "JSONExplode", "Resume", "Alert",
        "Audit", "SLA", "Throttle"
    ));

    @BeforeEach
    public void setUp() {
    }

    @Test
    public void testAllNodeTypesHaveExecutors() {
        List<String> missingExecutors = new ArrayList<>();

        for (String nodeType : ALL_FRONTEND_NODE_TYPES) {
            if (!nodeExecutorRegistry.hasExecutor(nodeType)) {
                missingExecutors.add(nodeType);
            }
        }

        assertTrue(missingExecutors.isEmpty(),
            "Missing executors for node types: " + missingExecutors);
    }

    @Test
    public void testSimpleSourceTransformSinkWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Simple ETL");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source", "FileSource"));
        nodes.add(createNode("filter", "Filter"));
        nodes.add(createNode("sink", "FileSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source", "control", "control", true));
        edges.add(createEdge("source", "filter", "out", "in", false));
        edges.add(createEdge("filter", "sink", "out", "in", false));
        edges.add(createEdge("sink", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.entryStepIds().contains("source"));
        assertTrue(plan.steps().containsKey("filter"));
        assertTrue(plan.steps().containsKey("sink"));
    }

    @Test
    public void testPartitioningAndCollectWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Partition & Collect");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source", "FileSource"));
        nodes.add(createNode("partition", "HashPartition"));
        nodes.add(createNode("collect", "Collect"));
        nodes.add(createNode("sink", "FileSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source", "control", "control", true));
        edges.add(createEdge("source", "partition", "out", "in", false));
        edges.add(createEdge("partition", "collect", "p1", "in1", false));
        edges.add(createEdge("partition", "collect", "p2", "in2", false));
        edges.add(createEdge("partition", "collect", "p3", "in3", false));
        edges.add(createEdge("collect", "sink", "out", "in", false));
        edges.add(createEdge("sink", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.steps().containsKey("partition"));
        assertTrue(plan.steps().containsKey("collect"));
    }

    @Test
    public void testJoinWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Join Two Sources");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source1", "FileSource"));
        nodes.add(createNode("source2", "FileSource"));
        nodes.add(createNode("join", "Join"));
        nodes.add(createNode("sink", "FileSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source1", "control", "control", true));
        edges.add(createEdge("start", "source2", "control", "control", true));
        edges.add(createEdge("source1", "join", "out", "left", false));
        edges.add(createEdge("source2", "join", "out", "right", false));
        edges.add(createEdge("join", "sink", "out", "in", false));
        edges.add(createEdge("sink", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.steps().containsKey("join"));
    }

    @Test
    public void testSwitchRoutingWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Switch Routing");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source", "FileSource"));
        nodes.add(createNode("switch", "Switch"));
        nodes.add(createNode("sink1", "FileSink"));
        nodes.add(createNode("sink2", "FileSink"));
        nodes.add(createNode("sinkDefault", "FileSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source", "control", "control", true));
        edges.add(createEdge("source", "switch", "out", "in", false));
        edges.add(createEdge("switch", "sink1", "out1", "in", false));
        edges.add(createEdge("switch", "sink2", "out2", "in", false));
        edges.add(createEdge("switch", "sinkDefault", "default", "in", false));
        edges.add(createEdge("sink1", "end", null, "in", false));
        edges.add(createEdge("sink2", "end", null, "in", false));
        edges.add(createEdge("sinkDefault", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.steps().containsKey("switch"));
    }

    @Test
    public void testAggregationWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Aggregation");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source", "FileSource"));
        nodes.add(createNode("aggregate", "Aggregate"));
        nodes.add(createNode("sink", "FileSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source", "control", "control", true));
        edges.add(createEdge("source", "aggregate", "out", "in", false));
        edges.add(createEdge("aggregate", "sink", "out", "in", false));
        edges.add(createEdge("sink", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.steps().containsKey("aggregate"));
    }

    @Test
    public void testValidateRejectWorkflow() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Validate & Reject");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("source", "FileSource"));
        nodes.add(createNode("validate", "Validate"));
        nodes.add(createNode("sink1", "FileSink"));
        nodes.add(createNode("errorSink", "ErrorSink"));
        nodes.add(createNode("end", "End"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "source", "control", "control", true));
        edges.add(createEdge("source", "validate", "out", "in", false));
        edges.add(createEdge("validate", "sink1", "out", "in", false));
        edges.add(createEdge("validate", "errorSink", "invalid", "in", false));
        edges.add(createEdge("sink1", "end", null, "in", false));
        edges.add(createEdge("errorSink", "end", null, "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        ExecutionPlan plan = executionGraphBuilder.build(workflow);

        assertNotNull(plan);
        assertTrue(plan.steps().containsKey("validate"));
        assertTrue(plan.steps().containsKey("errorSink"));
    }

    @Test
    public void testInvalidWorkflowNoStart() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Invalid - No Start");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("source", "FileSource"));

        List<Edge> edges = new ArrayList<>();

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        assertThrows(GraphValidationException.class, () -> {
            executionGraphBuilder.build(workflow);
        });
    }

    @Test
    public void testInvalidWorkflowCycle() {
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setName("Invalid - Cycle");

        List<NodeDefinition> nodes = new ArrayList<>();
        nodes.add(createNode("start", "Start"));
        nodes.add(createNode("node1", "Filter"));
        nodes.add(createNode("node2", "Filter"));

        List<Edge> edges = new ArrayList<>();
        edges.add(createEdge("start", "node1", "control", "control", true));
        edges.add(createEdge("node1", "node2", "out", "in", false));
        edges.add(createEdge("node2", "node1", "out", "in", false));

        workflow.setNodes(nodes);
        workflow.setEdges(edges);

        assertThrows(GraphValidationException.class, () -> {
            executionGraphBuilder.build(workflow);
        });
    }

    private NodeDefinition createNode(String id, String type) {
        NodeDefinition node = new NodeDefinition();
        node.setId(id);
        node.setType(type);
        node.setConfig(objectMapper.createObjectNode());
        return node;
    }

    private Edge createEdge(String source, String target, String sourceHandle, String targetHandle, boolean isControl) {
        Edge edge = new Edge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setSourceHandle(sourceHandle);
        edge.setTargetHandle(targetHandle);
        edge.setControl(isControl);
        return edge;
    }
}
