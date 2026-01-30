package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Component
public class FileSourceExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    @Override
    public String getNodeType() {
        return "FileSource";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        String filePath = config.get("filePath").asText();
        String fileType = config.has("fileType") ? config.get("fileType").asText() : "csv";
        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : ",";
        boolean hasHeader = config.has("header") ? config.get("header").asBoolean() : true;
        String encoding = config.has("encoding") ? config.get("encoding").asText() : "UTF-8";

        if (!"csv".equalsIgnoreCase(fileType)) {
            throw new UnsupportedOperationException(
                "File type '" + fileType + "' is not yet supported. Only CSV files are currently supported."
            );
        }

        FlatFileItemReader<Map<String, Object>> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(filePath));
        reader.setEncoding(encoding);
        reader.setLinesToSkip(hasHeader ? 1 : 0);

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setDelimiter(delimiter);

        DefaultLineMapper<Map<String, Object>> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);

        if (hasHeader) {
            reader.setSkippedLinesCallback(line -> {
                String[] headerTokens = line.split(delimiter, -1);
                tokenizer.setNames(headerTokens);
            });
        }

        lineMapper.setFieldSetMapper(fieldSet -> {
            Map<String, Object> map = new HashMap<>();
            String[] fieldNames = fieldSet.getNames();

            if (fieldNames == null || fieldNames.length == 0) {
                int fieldCount = fieldSet.getFieldCount();
                for (int i = 0; i < fieldCount; i++) {
                    map.put("col" + (i + 1), fieldSet.readString(i));
                }
            } else {
                for (String fieldName : fieldNames) {
                    map.put(fieldName, fieldSet.readString(fieldName));
                }
            }

            return map;
        });

        reader.setLineMapper(lineMapper);

        return reader;
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        return items -> {};
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("filePath")) {
            throw new IllegalArgumentException("FileSource node requires 'filePath' in config");
        }

        String filePath = config.get("filePath").asText();

        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("FileSource 'filePath' cannot be empty");
        }

        if (!Files.exists(Paths.get(filePath))) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        if (!Files.isRegularFile(Paths.get(filePath))) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
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
