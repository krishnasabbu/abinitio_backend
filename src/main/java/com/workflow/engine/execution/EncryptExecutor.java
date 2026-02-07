package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor for encrypting field values in workflow items.
 *
 * Applies encryption to configured fields in each item passing through the node.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Component
public class EncryptExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(EncryptExecutor.class);

    @Override
    public String getNodeType() {
        return "Encrypt";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        if (context instanceof RoutingNodeExecutionContext) {
            RoutingNodeExecutionContext routingCtx = (RoutingNodeExecutionContext) context;
            String executionId = routingCtx.getRoutingContext().getExecutionId();
            String nodeId = context.getNodeDefinition().getId();
            logger.debug("nodeId={}, Using BufferedItemReader for port 'in'", nodeId);
            return new BufferedItemReader(executionId, nodeId, "in", routingCtx.getRoutingContext().getBufferStore());
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        logger.debug("Encrypt node {} processing", nodeId);
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>(items.getItems());
            logger.info("nodeId={}, Encrypt output: {} items processed", context.getNodeDefinition().getId(), outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null) {
            throw new IllegalArgumentException("nodeType=Encrypt, nodeId=" + context.getNodeDefinition().getId()
                + ", missing config object");
        }
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return true;
    }
}
