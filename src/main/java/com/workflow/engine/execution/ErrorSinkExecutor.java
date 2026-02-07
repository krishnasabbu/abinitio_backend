package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.model.NodeDefinition;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for the ErrorSink node type. Routes error records to a configured sink
 * (e.g., file) for error tracking and post-processing analysis.
 */
@Component
public class ErrorSinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ErrorSinkExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final FileSinkExecutor fileSinkExecutor;

    public ErrorSinkExecutor(FileSinkExecutor fileSinkExecutor) {
        this.fileSinkExecutor = fileSinkExecutor;
    }

    @Override
    public String getNodeType() {
        return "ErrorSink";
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
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        boolean includeMetadata = config != null && config.has("includeMetadata")
            ? config.get("includeMetadata").asBoolean()
            : true;

        if (includeMetadata) {
            return item -> item;
        }

        return item -> {
            if (item == null) {
                return null;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : item.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("_")) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode errorSinkConfig = context.getNodeDefinition().getConfig();

        String sinkType = errorSinkConfig.has("sinkType")
            ? errorSinkConfig.get("sinkType").asText()
            : "File";

        if (!"File".equalsIgnoreCase(sinkType)) {
            throw new UnsupportedOperationException(
                "ErrorSink sinkType '" + sinkType + "' not implemented yet. Only 'File' is supported."
            );
        }

        String path = errorSinkConfig.get("path").asText();
        String format = errorSinkConfig.has("format")
            ? errorSinkConfig.get("format").asText()
            : "JSON";

        if ("Parquet".equalsIgnoreCase(format)) {
            throw new UnsupportedOperationException("Parquet format not implemented yet");
        }

        String fileType = mapFormatToFileType(format);

        ObjectNode fileSinkConfig = objectMapper.createObjectNode();
        fileSinkConfig.put("outputPath", path);
        fileSinkConfig.put("fileType", fileType);
        fileSinkConfig.put("mode", "append");

        if ("csv".equals(fileType)) {
            fileSinkConfig.put("header", errorSinkConfig.has("header")
                ? errorSinkConfig.get("header").asBoolean()
                : true);

            if (errorSinkConfig.has("delimiter")) {
                fileSinkConfig.put("delimiter", errorSinkConfig.get("delimiter").asText());
            }

            if (errorSinkConfig.has("columns")) {
                fileSinkConfig.put("columns", errorSinkConfig.get("columns").asText());
            }
        }

        if (path.endsWith(".gz")) {
            fileSinkConfig.put("compression", "gzip");
            String pathWithoutGz = path.substring(0, path.length() - 3);
            fileSinkConfig.put("outputPath", pathWithoutGz);
        }

        NodeDefinition syntheticNodeDef = new NodeDefinition();
        syntheticNodeDef.setId(context.getNodeDefinition().getId() + "_delegated");
        syntheticNodeDef.setType("FileSink");
        syntheticNodeDef.setConfig(fileSinkConfig);

        NodeExecutionContext syntheticContext = new NodeExecutionContext(
            syntheticNodeDef,
            context.getStepExecution()
        );
        syntheticContext.setVariable("inputItems", context.getVariable("inputItems"));

        ItemWriter<Map<String, Object>> delegateWriter = fileSinkExecutor.createWriter(syntheticContext);
        return items -> {
            logger.info("nodeId={}, Writing {} items", context.getNodeDefinition().getId(), items.size());
            delegateWriter.write(items);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null) {
            throw new IllegalArgumentException("ErrorSink node requires config");
        }

        String sinkType = config.has("sinkType") ? config.get("sinkType").asText() : "File";

        if ("File".equalsIgnoreCase(sinkType)) {
            if (!config.has("path")) {
                throw new IllegalArgumentException("ErrorSink with sinkType 'File' requires 'path'");
            }

            String path = config.get("path").asText();
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("ErrorSink 'path' cannot be empty");
            }

            if (config.has("format")) {
                String format = config.get("format").asText();
                if ("Parquet".equalsIgnoreCase(format)) {
                    throw new UnsupportedOperationException("Parquet format not implemented yet");
                }
                if (!"CSV".equalsIgnoreCase(format) && !"JSON".equalsIgnoreCase(format)) {
                    throw new IllegalArgumentException("ErrorSink format must be 'CSV' or 'JSON'");
                }
            }
        } else if ("Database".equalsIgnoreCase(sinkType) || "Queue".equalsIgnoreCase(sinkType)) {
            throw new UnsupportedOperationException("ErrorSink sinkType '" + sinkType + "' not implemented yet");
        } else {
            throw new IllegalArgumentException("ErrorSink sinkType must be 'File', 'Database', or 'Queue'");
        }
    }

    @Override
    public boolean supportsMetrics() {
        return true;
    }

    @Override
    public boolean supportsFailureHandling() {
        return false;
    }

    private String mapFormatToFileType(String format) {
        if ("CSV".equalsIgnoreCase(format)) {
            return "csv";
        } else if ("JSON".equalsIgnoreCase(format)) {
            return "json";
        }
        return "json";
    }
}
