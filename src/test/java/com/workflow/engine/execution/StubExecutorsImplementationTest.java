package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class StubExecutorsImplementationTest {

    private ObjectMapper mapper;
    private NodeExecutionContext context;

    @BeforeEach
    public void setUp() {
        mapper = new ObjectMapper();
        context = createContext("testNode");
    }

    private NodeExecutionContext createContext(String nodeId) {
        return new NodeExecutionContext(
            createNodeDef(nodeId, "TestNode", mapper.createObjectNode()),
            null
        );
    }

    private NodeExecutionContext createContextWithConfig(String nodeId, String nodeType, JsonNode config) {
        return new NodeExecutionContext(
            createNodeDef(nodeId, nodeType, config),
            null
        );
    }

    private com.workflow.engine.model.NodeDefinition createNodeDef(String id, String type, JsonNode config) {
        com.workflow.engine.model.NodeDefinition def = new com.workflow.engine.model.NodeDefinition();
        def.setId(id);
        def.setType(type);
        def.setConfig(config);
        return def;
    }

    // ============ WaitExecutor Tests ============
    @Test
    public void testWaitExecutor_TimeWait() throws Exception {
        WaitExecutor executor = new WaitExecutor();
        ObjectNode config = mapper.createObjectNode();
        config.put("waitType", "TIME");
        config.put("durationSeconds", 0);

        NodeExecutionContext ctx = createContextWithConfig("wait1", "Wait", config);
        ctx.setVariable("inputItems", new ArrayList<>());

        ItemReader<Map<String, Object>> reader = executor.createReader(ctx);
        ItemProcessor<Map<String, Object>, Map<String, Object>> processor = executor.createProcessor(ctx);
        ItemWriter<Map<String, Object>> writer = executor.createWriter(ctx);

        assertNotNull(reader);
        assertNotNull(processor);
        assertNotNull(writer);
    }

    @Test
    public void testWaitExecutor_Validation() {
        WaitExecutor executor = new WaitExecutor();
        ObjectNode config = mapper.createObjectNode();
        NodeExecutionContext ctx = createContextWithConfig("wait1", "Wait", config);

        executor.validate(ctx);
    }

    // ============ JobConditionExecutor Tests ============
    @Test
    public void testJobConditionExecutor_ExpressionEvaluation() {
        JobConditionExecutor executor = new JobConditionExecutor();
        ObjectNode config = mapper.createObjectNode();
        config.put("expression", "1 == 1");

        NodeExecutionContext ctx = createContextWithConfig("cond1", "JobCondition", config);
        ctx.setVariable("inputItems", new ArrayList<>());

        assertDoesNotThrow(() -> {
            ItemProcessor<Map<String, Object>, Map<String, Object>> proc = executor.createProcessor(ctx);
            Map<String, Object> item = new HashMap<>();
            proc.process(item);
        });
    }

    @Test
    public void testJobConditionExecutor_MissingExpression() {
        JobConditionExecutor executor = new JobConditionExecutor();
        ObjectNode config = mapper.createObjectNode();
        NodeExecutionContext ctx = createContextWithConfig("cond1", "JobCondition", config);

        assertThrows(IllegalArgumentException.class, () -> executor.validate(ctx));
    }

    // ============ SplitExecutor Tests ============
    @Test
    public void testSplitExecutor_PassThrough() throws Exception {
        SplitExecutor executor = new SplitExecutor();
        ObjectNode config = mapper.createObjectNode();
        config.put("numberOfOutputs", 3);

        NodeExecutionContext ctx = createContextWithConfig("split1", "Split", config);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("data", "test");
        items.add(item);
        ctx.setVariable("inputItems", items);

        ItemProcessor<Map<String, Object>, Map<String, Object>> processor = executor.createProcessor(ctx);
        Map<String, Object> result = processor.process(item);
        assertEquals("test", result.get("data"));
    }

    // ============ GatherExecutor Tests ============
    @Test
    public void testGatherExecutor_PassThrough() throws Exception {
        GatherExecutor executor = new GatherExecutor();
        ObjectNode config = mapper.createObjectNode();
        config.put("mode", "union");

        NodeExecutionContext ctx = createContextWithConfig("gather1", "Gather", config);
        List<Map<String, Object>> items = Arrays.asList(new HashMap<>(), new HashMap<>());
        ctx.setVariable("inputItems", items);

        ItemProcessor<Map<String, Object>, Map<String, Object>> processor = executor.createProcessor(ctx);
        assertNotNull(processor);
    }

    // ============ KafkaSourceExecutor Tests ============
    @Test
    public void testKafkaSourceExecutor_Validation() {
        KafkaSourceExecutor executor = new KafkaSourceExecutor();
        ObjectNode config = mapper.createObjectNode();
        config.put("topics", "test-topic");

        NodeExecutionContext ctx = createContextWithConfig("kafka1", "KafkaSource", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    @Test
    public void testKafkaSourceExecutor_MissingTopics() {
        KafkaSourceExecutor executor = new KafkaSourceExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("kafka1", "KafkaSource", config);
        assertThrows(IllegalArgumentException.class, () -> executor.validate(ctx));
    }

    // ============ KafkaSinkExecutor Tests ============
    @Test
    public void testKafkaSinkExecutor_Validation() {
        KafkaSinkExecutor executor = new KafkaSinkExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("kafkaOut1", "KafkaSink", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ RestAPISourceExecutor Tests ============
    @Test
    public void testRestAPISourceExecutor_Validation() {
        RestAPISourceExecutor executor = new RestAPISourceExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("rest1", "RestAPISource", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ RestAPISinkExecutor Tests ============
    @Test
    public void testRestAPISinkExecutor_Validation() {
        RestAPISinkExecutor executor = new RestAPISinkExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("restOut1", "RestAPISink", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ DBExecuteExecutor Tests ============
    @Test
    public void testDBExecuteExecutor_Validation() {
        DBExecuteExecutor executor = new DBExecuteExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("db1", "DBExecute", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ XMLParseExecutor Tests ============
    @Test
    public void testXMLParseExecutor_Validation() {
        XMLParseExecutor executor = new XMLParseExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("xml1", "XMLParse", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ EncryptExecutor Tests ============
    @Test
    public void testEncryptExecutor_Validation() {
        EncryptExecutor executor = new EncryptExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("enc1", "Encrypt", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ DecryptExecutor Tests ============
    @Test
    public void testDecryptExecutor_Validation() {
        DecryptExecutor executor = new DecryptExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("dec1", "Decrypt", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ PythonNodeExecutor Tests ============
    @Test
    public void testPythonNodeExecutor_Validation() {
        PythonNodeExecutor executor = new PythonNodeExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("py1", "PythonNode", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ ScriptNodeExecutor Tests ============
    @Test
    public void testScriptNodeExecutor_Validation() {
        ScriptNodeExecutor executor = new ScriptNodeExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("script1", "ScriptNode", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ WebServiceCallExecutor Tests ============
    @Test
    public void testWebServiceCallExecutor_Validation() {
        WebServiceCallExecutor executor = new WebServiceCallExecutor();
        ObjectNode config = mapper.createObjectNode();

        NodeExecutionContext ctx = createContextWithConfig("ws1", "WebServiceCall", config);
        assertDoesNotThrow(() -> executor.validate(ctx));
    }

    // ============ All Executors Support Methods ============
    @Test
    public void testAllExecutorsSupportsMetrics() {
        List<NodeExecutor> executors = Arrays.asList(
            new WaitExecutor(),
            new JobConditionExecutor(),
            new SplitExecutor(),
            new GatherExecutor(),
            new KafkaSourceExecutor(),
            new KafkaSinkExecutor(),
            new RestAPISourceExecutor(),
            new RestAPISinkExecutor(),
            new DBExecuteExecutor(),
            new XMLParseExecutor(),
            new XMLValidateExecutor(),
            new EncryptExecutor(),
            new DecryptExecutor(),
            new PythonNodeExecutor(),
            new ScriptNodeExecutor(),
            new ShellNodeExecutor(),
            new CustomNodeExecutor(),
            new WebServiceCallExecutor()
        );

        for (NodeExecutor executor : executors) {
            assertTrue(executor.supportsMetrics(),
                "Executor " + executor.getNodeType() + " should support metrics");
        }
    }

    @Test
    public void testAllExecutorsReturnNodeType() {
        List<NodeExecutor> executors = Arrays.asList(
            new WaitExecutor(),
            new JobConditionExecutor(),
            new SplitExecutor(),
            new GatherExecutor(),
            new KafkaSourceExecutor()
        );

        for (NodeExecutor executor : executors) {
            assertNotNull(executor.getNodeType());
            assertNotEquals("", executor.getNodeType());
        }
    }

    @Test
    public void testAllExecutorsCreateReaders() {
        NodeExecutionContext ctx = createContext("test");
        ctx.setVariable("inputItems", new ArrayList<>());

        List<NodeExecutor> executors = Arrays.asList(
            new WaitExecutor(),
            new JobConditionExecutor(),
            new SplitExecutor(),
            new GatherExecutor()
        );

        for (NodeExecutor executor : executors) {
            ItemReader<Map<String, Object>> reader = executor.createReader(ctx);
            assertNotNull(reader, "Executor " + executor.getNodeType() + " should create reader");
        }
    }

    @Test
    public void testAllExecutorsCreateProcessors() {
        NodeExecutionContext ctx = createContext("test");
        ctx.setVariable("inputItems", new ArrayList<>());

        List<NodeExecutor> executors = Arrays.asList(
            new WaitExecutor(),
            new JobConditionExecutor(),
            new SplitExecutor(),
            new GatherExecutor()
        );

        for (NodeExecutor executor : executors) {
            ItemProcessor<Map<String, Object>, Map<String, Object>> processor = executor.createProcessor(ctx);
            assertNotNull(processor, "Executor " + executor.getNodeType() + " should create processor");
        }
    }

    @Test
    public void testAllExecutorsCreateWriters() {
        NodeExecutionContext ctx = createContext("test");

        List<NodeExecutor> executors = Arrays.asList(
            new WaitExecutor(),
            new JobConditionExecutor(),
            new SplitExecutor(),
            new GatherExecutor()
        );

        for (NodeExecutor executor : executors) {
            ItemWriter<Map<String, Object>> writer = executor.createWriter(ctx);
            assertNotNull(writer, "Executor " + executor.getNodeType() + " should create writer");
        }
    }
}
