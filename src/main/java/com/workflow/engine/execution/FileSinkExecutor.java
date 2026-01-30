package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileHeaderCallback;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Component
public class FileSinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "FileSink";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        if (items == null) {
            items = new ArrayList<>();
        }
        return new ListItemReader<>(items);
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        String outputPath = config.get("outputPath").asText();
        String fileType = config.has("fileType") ? config.get("fileType").asText().toLowerCase() : "csv";
        String mode = config.has("mode") ? config.get("mode").asText().toLowerCase() : "overwrite";
        String compression = config.has("compression") ? config.get("compression").asText().toLowerCase() : "none";

        if ("json".equals(fileType) || "parquet".equals(fileType) || "excel".equals(fileType)) {
            throw new UnsupportedOperationException(
                "File type '" + fileType + "' is not implemented yet."
            );
        }

        if ("snappy".equals(compression) || "lz4".equals(compression)) {
            throw new UnsupportedOperationException(
                "Compression '" + compression + "' is not implemented yet."
            );
        }

        ensureParentDirectoryExists(outputPath);

        if ("csv".equals(fileType)) {
            return createCsvWriter(config, outputPath, mode, compression);
        } else if ("txt".equals(fileType)) {
            return createTxtWriter(config, outputPath, mode, compression);
        } else {
            throw new UnsupportedOperationException("File type '" + fileType + "' is not supported.");
        }
    }

    private ItemWriter<Map<String, Object>> createCsvWriter(
        JsonNode config, String outputPath, String mode, String compression
    ) {
        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : ",";
        boolean writeHeader = config.has("header") ? config.get("header").asBoolean() : true;
        String columnsStr = config.has("columns") ? config.get("columns").asText() : null;

        DelimitedLineAggregator<Map<String, Object>> aggregator = new DelimitedLineAggregator<Map<String, Object>>() {
            private List<String> columns = null;

            @Override
            public String aggregate(Map<String, Object> item) {
                if (columns == null) {
                    if (columnsStr != null && !columnsStr.trim().isEmpty()) {
                        columns = Arrays.asList(columnsStr.split(","))
                            .stream()
                            .map(String::trim)
                            .collect(Collectors.toList());
                    } else {
                        columns = new ArrayList<>(item.keySet());
                        Collections.sort(columns);
                    }
                }

                List<String> values = new ArrayList<>();
                for (String column : columns) {
                    Object value = item.get(column);
                    values.add(value != null ? value.toString() : "");
                }

                return String.join(delimiter, values);
            }
        };

        if ("gzip".equals(compression)) {
            return createCompressedWriter(aggregator, outputPath, mode, writeHeader, columnsStr, delimiter);
        }

        FlatFileItemWriter<Map<String, Object>> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource(outputPath));
        writer.setAppendAllowed("append".equals(mode));
        writer.setShouldDeleteIfExists("overwrite".equals(mode));
        writer.setLineAggregator(aggregator);

        if (writeHeader && "overwrite".equals(mode)) {
            writer.setHeaderCallback(new FlatFileHeaderCallback() {
                @Override
                public void writeHeader(Writer writer) throws IOException {
                    if (columnsStr != null && !columnsStr.trim().isEmpty()) {
                        writer.write(columnsStr.replace(",", delimiter));
                    }
                }
            });
        }

        return writer;
    }

    private ItemWriter<Map<String, Object>> createTxtWriter(
        JsonNode config, String outputPath, String mode, String compression
    ) {
        String lineFormat = config.has("lineFormat") ? config.get("lineFormat").asText().toLowerCase() : "raw";
        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : "|";
        String columnsStr = config.has("columns") ? config.get("columns").asText() : null;

        org.springframework.batch.item.file.transform.LineAggregator<Map<String, Object>> aggregator;

        if ("raw".equals(lineFormat)) {
            aggregator = item -> {
                if (item.containsKey("line")) {
                    Object lineValue = item.get("line");
                    return lineValue != null ? lineValue.toString() : "";
                } else {
                    try {
                        return objectMapper.writeValueAsString(item);
                    } catch (Exception e) {
                        return item.toString();
                    }
                }
            };
        } else if ("delimited".equals(lineFormat)) {
            aggregator = new DelimitedLineAggregator<Map<String, Object>>() {
                private List<String> columns = null;

                @Override
                public String aggregate(Map<String, Object> item) {
                    if (columns == null) {
                        if (columnsStr != null && !columnsStr.trim().isEmpty()) {
                            columns = Arrays.asList(columnsStr.split(","))
                                .stream()
                                .map(String::trim)
                                .collect(Collectors.toList());
                        } else {
                            columns = new ArrayList<>(item.keySet());
                            Collections.sort(columns);
                        }
                    }

                    List<String> values = new ArrayList<>();
                    for (String column : columns) {
                        Object value = item.get(column);
                        values.add(value != null ? value.toString() : "");
                    }

                    return String.join(delimiter, values);
                }
            };
        } else {
            throw new IllegalArgumentException("Unsupported lineFormat for txt: " + lineFormat);
        }

        if ("gzip".equals(compression)) {
            return createCompressedWriter(aggregator, outputPath, mode, false, null, delimiter);
        }

        FlatFileItemWriter<Map<String, Object>> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource(outputPath));
        writer.setAppendAllowed("append".equals(mode));
        writer.setShouldDeleteIfExists("overwrite".equals(mode));
        writer.setLineAggregator(aggregator);

        return writer;
    }

    private ItemWriter<Map<String, Object>> createCompressedWriter(
        org.springframework.batch.item.file.transform.LineAggregator<Map<String, Object>> aggregator,
        String outputPath,
        String mode,
        boolean writeHeader,
        String columnsStr,
        String delimiter
    ) {
        String finalPath = outputPath + ".gz";
        Path path = Paths.get(finalPath);
        boolean fileExists = Files.exists(path);
        boolean shouldAppend = "append".equals(mode) && fileExists;

        return items -> {
            try (FileOutputStream fos = new FileOutputStream(finalPath, shouldAppend);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter osw = new OutputStreamWriter(gzos);
                 BufferedWriter bw = new BufferedWriter(osw)) {

                if (writeHeader && !shouldAppend && columnsStr != null && !columnsStr.trim().isEmpty()) {
                    bw.write(columnsStr.replace(",", delimiter));
                    bw.newLine();
                }

                for (Map<String, Object> item : items) {
                    String line = aggregator.aggregate(item);
                    bw.write(line);
                    bw.newLine();
                }
            }
        };
    }

    private void ensureParentDirectoryExists(String filePath) {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create parent directory: " + parentDir, e);
            }
        }
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();

        if (config == null || !config.has("outputPath")) {
            throw new IllegalArgumentException("FileSink node requires 'outputPath' in config");
        }

        String outputPath = config.get("outputPath").asText();

        if (outputPath == null || outputPath.trim().isEmpty()) {
            throw new IllegalArgumentException("FileSink 'outputPath' cannot be empty");
        }

        String mode = config.has("mode") ? config.get("mode").asText().toLowerCase() : "overwrite";
        if (!"overwrite".equals(mode) && !"append".equals(mode)) {
            throw new IllegalArgumentException("FileSink 'mode' must be 'overwrite' or 'append'");
        }

        String fileType = config.has("fileType") ? config.get("fileType").asText().toLowerCase() : "csv";
        if ("txt".equals(fileType)) {
            String lineFormat = config.has("lineFormat") ? config.get("lineFormat").asText().toLowerCase() : "raw";
            if (!"raw".equals(lineFormat) && !"delimited".equals(lineFormat)) {
                throw new IllegalArgumentException("FileSink txt lineFormat must be 'raw' or 'delimited'");
            }
        }

        String delimiter = config.has("delimiter") ? config.get("delimiter").asText() : ",";
        if (delimiter.length() != 1) {
            throw new IllegalArgumentException("FileSink delimiter must be a single character");
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
