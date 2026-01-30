package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DBSourceExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private final DataSourceProvider dataSourceProvider;

    public DBSourceExecutor(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public String getNodeType() {
        return "DBSource";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        String connectionId = config.get("connectionId").asText();
        int fetchSize = config.has("fetchSize") ? config.get("fetchSize").asInt() : 1000;

        String query = determineQuery(config);

        DataSource dataSource = dataSourceProvider.getDataSource(connectionId);

        JdbcCursorItemReader<Map<String, Object>> reader = new JdbcCursorItemReader<>();
        reader.setDataSource(dataSource);
        reader.setSql(query);
        reader.setFetchSize(fetchSize);
        reader.setRowMapper(new ColumnMapRowMapper());

        return reader;
    }

    private String determineQuery(JsonNode config) {
        if (config.has("query")) {
            String query = config.get("query").asText();
            if (query != null && !query.trim().isEmpty()) {
                return query.trim();
            }
        }

        if (config.has("tableName")) {
            String tableName = config.get("tableName").asText();
            if (tableName != null && !tableName.trim().isEmpty()) {
                return "SELECT * FROM " + tableName;
            }
        }

        throw new IllegalArgumentException("DBSource requires either 'query' or 'tableName' in config");
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> new LinkedHashMap<>(item);
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null) {
            throw new IllegalArgumentException("DBSource node requires config");
        }

        if (!config.has("connectionId")) {
            throw new IllegalArgumentException("DBSource requires 'connectionId' in config");
        }

        String connectionId = config.get("connectionId").asText();
        if (connectionId == null || connectionId.trim().isEmpty()) {
            throw new IllegalArgumentException("DBSource 'connectionId' cannot be empty");
        }

        if (!dataSourceProvider.hasDataSource(connectionId)) {
            throw new IllegalArgumentException("DataSource not found for connectionId: " + connectionId);
        }

        boolean hasQuery = config.has("query") && config.get("query").asText() != null && !config.get("query").asText().trim().isEmpty();
        boolean hasTableName = config.has("tableName") && config.get("tableName").asText() != null && !config.get("tableName").asText().trim().isEmpty();

        if (!hasQuery && !hasTableName) {
            throw new IllegalArgumentException("DBSource requires either 'query' or 'tableName' in config");
        }

        if (config.has("fetchSize")) {
            int fetchSize = config.get("fetchSize").asInt();
            if (fetchSize <= 0) {
                throw new IllegalArgumentException("DBSource 'fetchSize' must be greater than 0");
            }
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
