package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.routing.EdgeBufferStore;
import com.workflow.engine.execution.routing.OutputPort;
import com.workflow.engine.execution.routing.RoutingContext;
import com.workflow.engine.graph.ExecutionGraphBuilder;
import com.workflow.engine.graph.ExecutionPlan;
import com.workflow.engine.graph.StepNode;
import com.workflow.engine.model.Edge;
import com.workflow.engine.model.NodeDefinition;
import com.workflow.engine.model.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoutingLevelTwoTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();

    @Test
    public void testPortPreservationInStepNode() {
        WorkflowDefinition workflow = new WorkflowDefinition();

        List<NodeDefinition> nodes = new ArrayList<>();
        NodeDefinition start = new NodeDefinition();
        start.setId("start_1");
        start.setType("Start");
        nodes.add(start);

        NodeDefinition source = new NodeDefinition();
        source.setId("source_1");
        source.setType("FileSource");
        nodes.add(source);

        NodeDefinition split = new NodeDefinition();
        split.setId("split_1");
        split.setType("Partition");
        nodes.add(split);

        NodeDefinition sink1 = new NodeDefinition();
        sink1.setId("sink1_1");
        sink1.setType("FileSink");
        nodes.add(sink1);

        NodeDefinition sink2 = new NodeDefinition();
        sink2.setId("sink2_1");
        sink2.setType("FileSink");
        nodes.add(sink2);

        workflow.setNodes(nodes);

        List<Edge> edges = new ArrayList<>();

        Edge startToSource = new Edge();
        startToSource.setSource("start_1");
        startToSource.setTarget("source_1");
        startToSource.setControl(true);
        edges.add(startToSource);

        Edge sourceToSplit = new Edge();
        sourceToSplit.setSource("source_1");
        sourceToSplit.setTarget("split_1");
        sourceToSplit.setSourceHandle("out");
        sourceToSplit.setTargetHandle("in");
        sourceToSplit.setControl(false);
        edges.add(sourceToSplit);

        Edge splitToSink1 = new Edge();
        splitToSink1.setSource("split_1");
        splitToSink1.setTarget("sink1_1");
        splitToSink1.setSourceHandle("out1");
        splitToSink1.setTargetHandle("in");
        splitToSink1.setControl(false);
        edges.add(splitToSink1);

        Edge splitToSink2 = new Edge();
        splitToSink2.setSource("split_1");
        splitToSink2.setTarget("sink2_1");
        splitToSink2.setSourceHandle("out2");
        splitToSink2.setTargetHandle("in");
        splitToSink2.setControl(false);
        edges.add(splitToSink2);

        workflow.setEdges(edges);

        ExecutionPlan plan = graphBuilder.build(workflow);

        StepNode splitNode = plan.steps().get("split_1");
        assertNotNull(splitNode);
        assertNotNull(splitNode.outputPorts());
        assertEquals(2, splitNode.outputPorts().size());

        OutputPort port1 = splitNode.outputPorts().get(0);
        assertEquals("sink1_1", port1.targetNodeId());
        assertEquals("out1", port1.sourcePort());
        assertEquals("in", port1.targetPort());

        OutputPort port2 = splitNode.outputPorts().get(1);
        assertEquals("sink2_1", port2.targetNodeId());
        assertEquals("out2", port2.sourcePort());
    }

    @Test
    public void testRoutingContextRecordRouting() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        List<OutputPort> ports = new ArrayList<>();
        ports.add(new OutputPort("node_a", "out1", "in", false));
        ports.add(new OutputPort("node_b", "out2", "in", false));

        RoutingContext routingContext = new RoutingContext("exec_1", "source_1", ports, bufferStore);

        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("id", 1);
        record1.put("_routePort", "out1");

        routingContext.routeRecord(record1, "out1");

        assertTrue(bufferStore.hasRecords("exec_1", "node_a", "in"));
        assertFalse(bufferStore.hasRecords("exec_1", "node_b", "in"));

        List<Map<String, Object>> recordsA = bufferStore.getRecords("exec_1", "node_a", "in");
        assertEquals(1, recordsA.size());
        assertEquals(1, recordsA.get(0).get("id"));
    }

    @Test
    public void testEdgeBufferStoreMultipleRecords() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("id", 1);

        Map<String, Object> record2 = new LinkedHashMap<>();
        record2.put("id", 2);

        Map<String, Object> record3 = new LinkedHashMap<>();
        record3.put("id", 3);

        bufferStore.addRecord("exec_1", "node_a", "in", record1);
        bufferStore.addRecord("exec_1", "node_a", "in", record2);
        bufferStore.addRecord("exec_1", "node_b", "in", record3);

        List<Map<String, Object>> recordsA = bufferStore.getRecords("exec_1", "node_a", "in");
        assertEquals(2, recordsA.size());

        List<Map<String, Object>> recordsB = bufferStore.getRecords("exec_1", "node_b", "in");
        assertEquals(1, recordsB.size());
        assertEquals(3, recordsB.get(0).get("id"));
    }

    @Test
    public void testRoutingContextDefaultRouting() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        List<OutputPort> ports = new ArrayList<>();
        ports.add(new OutputPort("node_a", "out", "in", false));
        ports.add(new OutputPort("node_b", "out", "in", false));

        RoutingContext routingContext = new RoutingContext("exec_1", "source_1", ports, bufferStore);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", 1);

        routingContext.routeToDefault(record);

        assertTrue(bufferStore.hasRecords("exec_1", "node_a", "in"));
        assertFalse(bufferStore.hasRecords("exec_1", "node_b", "in"));
    }

    @Test
    public void testRoutingContextBroadcast() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        List<OutputPort> ports = new ArrayList<>();
        ports.add(new OutputPort("node_a", "out", "in", false));
        ports.add(new OutputPort("node_b", "out", "in", false));
        ports.add(new OutputPort("node_c", "out", "in", false));

        RoutingContext routingContext = new RoutingContext("exec_1", "source_1", ports, bufferStore);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", 1);

        routingContext.routeToAllPorts(record);

        assertTrue(bufferStore.hasRecords("exec_1", "node_a", "in"));
        assertTrue(bufferStore.hasRecords("exec_1", "node_b", "in"));
        assertTrue(bufferStore.hasRecords("exec_1", "node_c", "in"));
    }

    @Test
    public void testEdgeBufferStoreExecutionCleanup() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", 1);

        bufferStore.addRecord("exec_1", "node_a", "in", record);
        bufferStore.addRecord("exec_1", "node_b", "in", record);

        assertTrue(bufferStore.hasRecords("exec_1", "node_a", "in"));
        assertTrue(bufferStore.hasRecords("exec_1", "node_b", "in"));

        bufferStore.clearExecution("exec_1");

        assertFalse(bufferStore.hasRecords("exec_1", "node_a", "in"));
        assertFalse(bufferStore.hasRecords("exec_1", "node_b", "in"));
    }

    @Test
    public void testMultipleExecutionIsolation() {
        EdgeBufferStore bufferStore = new EdgeBufferStore();

        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("id", 1);

        Map<String, Object> record2 = new LinkedHashMap<>();
        record2.put("id", 2);

        bufferStore.addRecord("exec_1", "node_a", "in", record1);
        bufferStore.addRecord("exec_2", "node_a", "in", record2);

        List<Map<String, Object>> recordsExec1 = bufferStore.getRecords("exec_1", "node_a", "in");
        List<Map<String, Object>> recordsExec2 = bufferStore.getRecords("exec_2", "node_a", "in");

        assertEquals(1, recordsExec1.size());
        assertEquals(1, recordsExec2.size());
        assertEquals(1, recordsExec1.get(0).get("id"));
        assertEquals(2, recordsExec2.get(0).get("id"));
    }

    @Test
    public void testOutputPortEquality() {
        OutputPort port1 = new OutputPort("node_a", "out", "in", false);
        OutputPort port2 = new OutputPort("node_a", "out", "in", false);
        OutputPort port3 = new OutputPort("node_b", "out", "in", false);

        assertEquals(port1, port2);
        assertFalse(port1.equals(port3));
    }
}
