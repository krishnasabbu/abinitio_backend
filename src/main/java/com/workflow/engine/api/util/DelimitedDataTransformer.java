package com.workflow.engine.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DelimitedDataTransformer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private DelimitedDataTransformer() {}

    public static List<Map<String, Object>> transform(String rawData, String separator, String headers) {
        if (rawData == null || rawData.isEmpty()) {
            return List.of();
        }

        String[] headerArray = headers.split(Pattern.quote(separator));
        String[] lines = rawData.split("\\R");
        List<Map<String, Object>> result = new ArrayList<>(lines.length);

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] values = line.split(Pattern.quote(separator), -1);
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < headerArray.length; i++) {
                String value = i < values.length ? values[i].trim() : null;
                record.put(headerArray[i].trim(), value);
            }
            result.add(record);
        }

        return result;
    }

    public static String toJson(List<Map<String, Object>> records) throws JsonProcessingException {
        return objectMapper.writeValueAsString(records);
    }

    public static String recordToJson(Map<String, Object> record) throws JsonProcessingException {
        return objectMapper.writeValueAsString(record);
    }

    public static Map<String, Object> buildOutputSummary(String nodeId, String nodeType,
                                                          List<Map<String, Object>> records) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("nodeId", nodeId);
        summary.put("nodeType", nodeType);
        summary.put("totalRecords", records.size());

        if (!records.isEmpty()) {
            summary.put("columns", new ArrayList<>(records.get(0).keySet()));
            int sampleSize = Math.min(records.size(), 5);
            summary.put("sampleData", records.subList(0, sampleSize));
        }

        return summary;
    }
}
