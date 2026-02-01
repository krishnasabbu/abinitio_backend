package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;

@Component
public class DBExecuteExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(DBExecuteExecutor.class);
    private final DataSourceProvider dataSourceProvider;

    public DBExecuteExecutor(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public String getNodeType() {
        return "DBExecute";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String connectionId = config.has("connectionId") ? config.get("connectionId").asText() : "";

        if (connectionId.isEmpty()) {
            throw new IllegalArgumentException("nodeType=DBExecute, nodeId=" + nodeId + ", missing connectionId");
        }

        String sqlQuery = config.has("sqlQuery") ? config.get("sqlQuery").asText() : "";
        if (sqlQuery.isEmpty()) {
            throw new IllegalArgumentException("nodeType=DBExecute, nodeId=" + nodeId + ", missing sqlQuery");
        }

        DataSource dataSource = dataSourceProvider.getOrCreate(connectionId);
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);

        String queryType = config.has("queryType") ? config.get("queryType").asText() : "SELECT";
        String outputMode = config.has("outputMode") ? config.get("outputMode").asText() : "InputOnly";

        return item -> {
            try {
                Map<String, Object> params = new HashMap<>(item);

                if ("SELECT".equals(queryType)) {
                    List<Map<String, Object>> results = template.queryForList(sqlQuery, params);
                    if (results.isEmpty()) {
                        return item;
                    }
                    if ("QueryResult".equals(outputMode)) {
                        item.putAll(results.get(0));
                    } else if ("InputWithRowCount".equals(outputMode)) {
                        item.put("_rowCount", results.size());
                    }
                } else {
                    int rowCount = template.update(sqlQuery, params);
                    item.put("_rowCount", rowCount);
                    if ("InputOnly".equals(outputMode)) {
                        return item;
                    }
                }

                return item;
            } catch (Exception e) {
                logger.error("DB execution failed: {}", e.getMessage());
                boolean stopOnError = config.has("stopOnError") && config.get("stopOnError").asBoolean();
                if (stopOnError) {
                    throw new RuntimeException("DB execution failed", e);
                }
                item.put("_dbError", e.getMessage());
                return item;
            }
        };
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> context.setVariable("outputItems", new ArrayList<>(items.getItems()));
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("connectionId") || !config.has("sqlQuery")) {
            throw new IllegalArgumentException("nodeType=DBExecute, nodeId=" + context.getNodeDefinition().getId()
                + ", missing connectionId or sqlQuery");
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
