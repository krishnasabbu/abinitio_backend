package com.workflow.engine.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RestAPISinkExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(RestAPISinkExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getNodeType() {
        return "RestAPISink";
    }

    @Override
    public ItemReader<Map<String, Object>> createReader(NodeExecutionContext context) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.getVariable("inputItems");
        return new ListItemReader<>(items != null ? items : new ArrayList<>());
    }

    @Override
    public ItemProcessor<Map<String, Object>, Map<String, Object>> createProcessor(NodeExecutionContext context) {
        return item -> item;
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        String nodeId = context.getNodeDefinition().getId();
        String url = config.has("url") ? config.get("url").asText() : "";

        if (url.isEmpty()) {
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + nodeId + ", missing url");
        }

        return items -> {
            int batchSize = config.has("batchSize") ? config.get("batchSize").asInt() : 1;
            String method = config.has("method") ? config.get("method").asText() : "POST";

            if (batchSize > 1) {
                List<Map<String, Object>> batch = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    batch.add(item);
                    if (batch.size() >= batchSize) {
                        sendBatch(batch, url, config, method);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    sendBatch(batch, url, config, method);
                }
            } else {
                for (Map<String, Object> item : items) {
                    try {
                        sendItem(item, url, config, method);
                    } catch (Exception e) {
                        String onFailure = config.has("onFailure") ? config.get("onFailure").asText() : "Skip";
                        if ("Stop".equals(onFailure)) {
                            throw new RuntimeException("REST API call failed: " + e.getMessage(), e);
                        } else {
                            logger.warn("REST API call failed for item, continuing: {}", e.getMessage());
                        }
                    }
                }
            }

            context.setVariable("outputItems", new ArrayList<>(items));
        };
    }

    private void sendItem(Map<String, Object> item, String url, JsonNode config, String method) throws Exception {
        String body = buildRequestBody(item, config);
        HttpHeaders headers = buildHeaders(config);
        addAuthHeaders(headers, config);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
        logger.debug("Sent item to {} via {}", url, method);
    }

    private void sendBatch(List<Map<String, Object>> batch, String url, JsonNode config, String method) throws Exception {
        String body = mapper.writeValueAsString(batch);
        HttpHeaders headers = buildHeaders(config);
        addAuthHeaders(headers, config);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
        logger.debug("Sent batch of {} items to {}", batch.size(), url);
    }

    private String buildRequestBody(Map<String, Object> item, JsonNode config) throws Exception {
        if (!config.has("bodyTemplate") || config.get("bodyTemplate").asText().isEmpty()) {
            return mapper.writeValueAsString(item);
        }

        String template = config.get("bodyTemplate").asText();
        String result = template;

        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object value = item.get(fieldName);
            String stringValue = value != null ? String.valueOf(value) : "";
            result = result.replace("{{" + fieldName + "}}", stringValue);
        }

        return result;
    }

    private HttpHeaders buildHeaders(JsonNode config) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        if (config.has("headers")) {
            JsonNode headersNode = config.get("headers");
            headersNode.fields().forEachRemaining(entry ->
                headers.add(entry.getKey(), entry.getValue().asText())
            );
        }

        return headers;
    }

    private void addAuthHeaders(HttpHeaders headers, JsonNode config) {
        if (!config.has("authType")) {
            return;
        }

        String authType = config.get("authType").asText();
        switch (authType) {
            case "API Key":
                if (config.has("authKey")) {
                    headers.add("X-API-Key", config.get("authKey").asText());
                }
                break;
            case "Bearer Token":
                if (config.has("authKey")) {
                    headers.add("Authorization", "Bearer " + config.get("authKey").asText());
                }
                break;
            case "Basic Auth":
                if (config.has("authUser") && config.has("authPass")) {
                    String credentials = config.get("authUser").asText() + ":" + config.get("authPass").asText();
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                    headers.add("Authorization", "Basic " + encoded);
                }
                break;
        }
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("url")) {
            throw new IllegalArgumentException("nodeType=RestAPISink, nodeId=" + context.getNodeDefinition().getId()
                + ", missing url");
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
