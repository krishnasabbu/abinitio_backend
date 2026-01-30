package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.engine.execution.AlertExecutor;
import com.workflow.engine.execution.AuditExecutor;
import com.workflow.engine.execution.CheckpointExecutor;
import com.workflow.engine.execution.ResumeExecutor;
import com.workflow.engine.execution.SLAExecutor;
import com.workflow.engine.execution.ThrottleExecutor;
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

public class ControlFlowMonitoringExecutorsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testCheckpointExecutorBasic() throws Exception {
        CheckpointExecutor executor = new CheckpointExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Checkpoint");
        nodeDef.setId("checkpoint_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("checkpointId", "checkpoint1");
        config.put("scope", "step");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("value", "record" + i);
            items.add(item);
        }

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Checkpoint", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testCheckpointExecutorValidation() throws Exception {
        CheckpointExecutor executor = new CheckpointExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Checkpoint");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testCheckpointExecutorEmptyId() throws Exception {
        CheckpointExecutor executor = new CheckpointExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Checkpoint");
        ObjectNode config = mapper.createObjectNode();
        config.put("checkpointId", "");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testResumeExecutorBasic() throws Exception {
        ResumeExecutor executor = new ResumeExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Resume");
        nodeDef.setId("resume_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("checkpointId", "checkpoint1");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Resume", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
    }

    @Test
    public void testResumeExecutorValidation() throws Exception {
        ResumeExecutor executor = new ResumeExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Resume");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testSLAExecutorBasic() throws Exception {
        SLAExecutor executor = new SLAExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SLA");
        nodeDef.setId("sla_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxDurationMs", 5000);
        config.put("action", "FAIL_JOB");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("SLA", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testSLAExecutorValidationMissingDuration() throws Exception {
        SLAExecutor executor = new SLAExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SLA");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testSLAExecutorValidationZeroDuration() throws Exception {
        SLAExecutor executor = new SLAExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SLA");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxDurationMs", 0);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testSLAExecutorValidationInvalidAction() throws Exception {
        SLAExecutor executor = new SLAExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("SLA");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxDurationMs", 5000);
        config.put("action", "INVALID");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testAlertExecutorBasic() throws Exception {
        AlertExecutor executor = new AlertExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Alert");
        nodeDef.setId("alert_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("alertType", "LOG");
        config.put("messageTemplate", "Alert message");
        config.put("trigger", "ALWAYS");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Alert", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testAlertExecutorUnsupportedType() throws Exception {
        AlertExecutor executor = new AlertExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Alert");
        nodeDef.setId("alert_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("alertType", "WEBHOOK");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);

        assertThrows(UnsupportedOperationException.class, () -> {
            executor.createWriter(context).write(items);
        });
    }

    @Test
    public void testAlertExecutorValidationInvalidTrigger() throws Exception {
        AlertExecutor executor = new AlertExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Alert");
        ObjectNode config = mapper.createObjectNode();
        config.put("trigger", "INVALID");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testThrottleExecutorBasic() throws Exception {
        ThrottleExecutor executor = new ThrottleExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Throttle");
        nodeDef.setId("throttle_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxRecordsPerSecond", 100);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            items.add(item);
        }

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Throttle", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testThrottleExecutorValidationMissingRate() throws Exception {
        ThrottleExecutor executor = new ThrottleExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Throttle");
        ObjectNode config = mapper.createObjectNode();
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testThrottleExecutorValidationZeroRate() throws Exception {
        ThrottleExecutor executor = new ThrottleExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Throttle");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxRecordsPerSecond", 0);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testAuditExecutorBasic() throws Exception {
        AuditExecutor executor = new AuditExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Audit");
        nodeDef.setId("audit_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("auditFields", "id,name");
        config.put("target", "LOG");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("name", "test");
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Audit", executor.getNodeType());
        assertTrue(executor.supportsMetrics());
        assertTrue(executor.supportsFailureHandling());
    }

    @Test
    public void testAuditExecutorUnsupportedTarget() throws Exception {
        AuditExecutor executor = new AuditExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Audit");
        nodeDef.setId("audit_1");
        ObjectNode config = mapper.createObjectNode();
        config.put("target", "DB");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);

        assertThrows(UnsupportedOperationException.class, () -> {
            executor.createWriter(context).write(items);
        });
    }

    @Test
    public void testAuditExecutorValidationInvalidTarget() throws Exception {
        AuditExecutor executor = new AuditExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Audit");
        ObjectNode config = mapper.createObjectNode();
        config.put("target", "INVALID");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(context));
    }

    @Test
    public void testAllControlFlowExecutorsTypeNames() {
        CheckpointExecutor checkpoint = new CheckpointExecutor();
        ResumeExecutor resume = new ResumeExecutor();
        SLAExecutor sla = new SLAExecutor();
        AlertExecutor alert = new AlertExecutor();
        ThrottleExecutor throttle = new ThrottleExecutor();
        AuditExecutor audit = new AuditExecutor();

        assertEquals("Checkpoint", checkpoint.getNodeType());
        assertEquals("Resume", resume.getNodeType());
        assertEquals("SLA", sla.getNodeType());
        assertEquals("Alert", alert.getNodeType());
        assertEquals("Throttle", throttle.getNodeType());
        assertEquals("Audit", audit.getNodeType());
    }

    @Test
    public void testAllControlFlowExecutorsSupportsMetrics() {
        CheckpointExecutor checkpoint = new CheckpointExecutor();
        ResumeExecutor resume = new ResumeExecutor();
        SLAExecutor sla = new SLAExecutor();
        AlertExecutor alert = new AlertExecutor();
        ThrottleExecutor throttle = new ThrottleExecutor();
        AuditExecutor audit = new AuditExecutor();

        assertTrue(checkpoint.supportsMetrics());
        assertTrue(resume.supportsMetrics());
        assertTrue(sla.supportsMetrics());
        assertTrue(alert.supportsMetrics());
        assertTrue(throttle.supportsMetrics());
        assertTrue(audit.supportsMetrics());
    }

    @Test
    public void testAllControlFlowExecutorsSupportsFailureHandling() {
        CheckpointExecutor checkpoint = new CheckpointExecutor();
        ResumeExecutor resume = new ResumeExecutor();
        SLAExecutor sla = new SLAExecutor();
        AlertExecutor alert = new AlertExecutor();
        ThrottleExecutor throttle = new ThrottleExecutor();
        AuditExecutor audit = new AuditExecutor();

        assertTrue(checkpoint.supportsFailureHandling());
        assertTrue(resume.supportsFailureHandling());
        assertTrue(sla.supportsFailureHandling());
        assertTrue(alert.supportsFailureHandling());
        assertTrue(throttle.supportsFailureHandling());
        assertTrue(audit.supportsFailureHandling());
    }

    @Test
    public void testCheckpointExecutorPassThrough() throws Exception {
        CheckpointExecutor executor = new CheckpointExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Checkpoint");
        nodeDef.setId("cp1");
        ObjectNode config = mapper.createObjectNode();
        config.put("checkpointId", "cp1");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Checkpoint", executor.getNodeType());
    }

    @Test
    public void testThrottleExecutorHighRate() throws Exception {
        ThrottleExecutor executor = new ThrottleExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Throttle");
        ObjectNode config = mapper.createObjectNode();
        config.put("maxRecordsPerSecond", 10000);
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            items.add(item);
        }

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Throttle", executor.getNodeType());
    }

    @Test
    public void testAlertExecutorOnSuccessTrigger() throws Exception {
        AlertExecutor executor = new AlertExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Alert");
        nodeDef.setId("alert1");
        ObjectNode config = mapper.createObjectNode();
        config.put("alertType", "LOG");
        config.put("messageTemplate", "Success message");
        config.put("trigger", "ON_SUCCESS");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Alert", executor.getNodeType());
    }

    @Test
    public void testAuditExecutorNoFields() throws Exception {
        AuditExecutor executor = new AuditExecutor();

        NodeDefinition nodeDef = new NodeDefinition();
        nodeDef.setType("Audit");
        nodeDef.setId("audit1");
        ObjectNode config = mapper.createObjectNode();
        config.put("target", "LOG");
        nodeDef.setConfig(config);

        NodeExecutionContext context = new NodeExecutionContext();
        context.setNodeDefinition(nodeDef);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        items.add(item);

        context.setVariable("inputItems", items);

        executor.validate(context);
        assertEquals("Audit", executor.getNodeType());
    }
}
