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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.workflow.engine.execution.routing.BufferedItemReader;
import com.workflow.engine.execution.routing.RoutingNodeExecutionContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executor for web service call nodes that invoke external REST or SOAP services during workflow processing.
 */
@Component
public class WebServiceCallExecutor implements NodeExecutor<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(WebServiceCallExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getNodeType() {
        return "WebServiceCall";
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
        String serviceType = config.has("serviceType") ? config.get("serviceType").asText() : "REST";
        String url = config.has("url") ? config.get("url").asText() : "";

        if (url.isEmpty()) {
            throw new IllegalArgumentException("nodeType=WebServiceCall, nodeId=" + nodeId + ", missing url");
        }

        return item -> {
            try {
                Map<String, Object> response = callService(url, config, item, serviceType);
                applyResponseMapping(item, response, config);
                return item;
            } catch (Exception e) {
                logger.error("WebService call failed: {}", e.getMessage());
                throw new RuntimeException("WebService call failed", e);
            }
        };
    }

    private Map<String, Object> callService(String url, JsonNode config, Map<String, Object> item, String serviceType) throws Exception {
        String method = config.has("method") ? config.get("method").asText() : "POST";
        String body = config.has("bodyTemplate") ? config.get("bodyTemplate").asText() : mapper.writeValueAsString(item);

        HttpHeaders headers = buildHeaders(config);
        addAuthHeaders(headers, config);

        String finalUrl = applyUrlParams(url, item, config);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(finalUrl, HttpMethod.valueOf(method), entity, String.class);

        if ("SOAP".equals(serviceType)) {
            return parseXmlResponse(response.getBody());
        } else {
            return mapper.readValue(response.getBody(), Map.class);
        }
    }

    private String applyUrlParams(String url, Map<String, Object> item, JsonNode config) {
        String[] resultHolder = { url };

        if (config.has("urlParams")) {
            JsonNode params = config.get("urlParams");
            params.fields().forEachRemaining(entry -> {
                String paramName = entry.getKey();
                Object value = item.get(paramName);
                String stringValue = value != null ? String.valueOf(value) : "";
                resultHolder[0] = resultHolder[0].replace(":" + paramName, stringValue);
            });
        }

        return resultHolder[0];
    }

    private void applyResponseMapping(Map<String, Object> item, Map<String, Object> response, JsonNode config) {
        if (!config.has("responseMapping")) {
            return;
        }

        JsonNode mapping = config.get("responseMapping");
        mapping.fields().forEachRemaining(entry -> {
            String responseField = entry.getKey();
            String outputField = entry.getValue().asText();
            Object value = response.get(responseField);
            if (value != null) {
                item.put(outputField, value);
            }
        });
    }

    private Map<String, Object> parseXmlResponse(String xmlBody) {
        Map<String, Object> result = new HashMap<>();
        result.put("xml", xmlBody);
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
        if ("Bearer Token".equals(authType) && config.has("authKey")) {
            headers.add("Authorization", "Bearer " + config.get("authKey").asText());
        } else if ("API Key".equals(authType) && config.has("authKey")) {
            headers.add("X-API-Key", config.get("authKey").asText());
        }
    }

    @Override
    public ItemWriter<Map<String, Object>> createWriter(NodeExecutionContext context) {
        String nodeId = context.getNodeDefinition().getId();
        return items -> {
            List<Map<String, Object>> outputList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item != null) {
                    outputList.add(item);
                }
            }
            logger.info("nodeId={}, WebServiceCall writing {} items to routing context", nodeId, outputList.size());
            context.setVariable("outputItems", outputList);
        };
    }

    @Override
    public void validate(NodeExecutionContext context) {
        JsonNode config = context.getNodeDefinition().getConfig();
        if (config == null || !config.has("url")) {
            throw new IllegalArgumentException("nodeType=WebServiceCall, nodeId=" + context.getNodeDefinition().getId()
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
