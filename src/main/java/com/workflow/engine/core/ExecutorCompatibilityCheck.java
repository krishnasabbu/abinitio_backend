package com.workflow.engine.core;

import com.workflow.engine.execution.NodeExecutorRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs startup compatibility check between frontend node types and registered backend executors.
 *
 * This component verifies that all expected workflow node types defined in the frontend specification
 * have corresponding executor implementations registered in the backend. Runs during application
 * initialization to catch missing executor implementations early.
 *
 * If any expected node type lacks a registered executor, the application startup fails with
 * a detailed error message indicating which executors are missing.
 *
 * Thread safety: Not thread-safe. Intended for single execution during application startup.
 *
 * @author Workflow Engine
 * @version 1.0
 * @see NodeExecutorRegistry
 */
@Configuration
public class ExecutorCompatibilityCheck implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorCompatibilityCheck.class);

    private static final Set<String> EXPECTED_NODE_TYPES = new HashSet<>(java.util.Arrays.asList(
        "Start", "End", "FailJob", "Wait", "JobCondition", "SchemaValidator",
        "FileSource", "FileSink", "DBSource", "DBSink", "KafkaSource", "KafkaSink",
        "Reformat", "Compute", "Map", "Normalize", "Denormalize", "Filter",
        "Decision", "Switch", "Split", "Gather", "Reject", "Join", "Lookup",
        "Merge", "Deduplicate", "Intersect", "Minus", "Sort", "Aggregate",
        "Rollup", "Window", "Scan", "Partition", "HashPartition", "RangePartition",
        "Replicate", "Broadcast", "Collect", "Validate", "Assert", "Sample",
        "Count", "Limit", "Checkpoint", "PythonNode", "ScriptNode", "ShellNode",
        "CustomNode", "RestAPISource", "RestAPISink", "Subgraph", "WebServiceCall",
        "XMLSplit", "XMLCombine", "DBExecute", "Encrypt", "Decrypt", "ErrorSink",
        "XMLParse", "XMLValidate", "JSONFlatten", "JSONExplode", "Resume", "Alert",
        "Audit", "SLA", "Throttle"
    ));

    private final NodeExecutorRegistry registry;

    /**
     * Constructs an ExecutorCompatibilityCheck with the provided executor registry.
     *
     * @param registry the registry containing all registered node executors
     */
    public ExecutorCompatibilityCheck(NodeExecutorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Runs the compatibility check during application startup.
     *
     * Verifies that all expected frontend node types have corresponding executor implementations
     * in the registry. Logs detailed status information and throws RuntimeException if any
     * expected executors are missing.
     *
     * @param args command line arguments (unused)
     * @throws RuntimeException if any expected executor implementations are missing
     */
    @Override
    public void run(String... args) throws Exception {
        logger.info("=== FRONTEND ↔ BACKEND COMPATIBILITY CHECK ===");

        Set<String> registeredTypes = new HashSet<>();
        List<String> missingTypes = new ArrayList<>();
        List<String> extraTypes = new ArrayList<>();

        for (String nodeType : EXPECTED_NODE_TYPES) {
            if (registry.hasExecutor(nodeType)) {
                registeredTypes.add(nodeType);
            } else {
                missingTypes.add(nodeType);
            }
        }

        logger.info("✓ Registered Executors: {}", registeredTypes.size());
        if (!missingTypes.isEmpty()) {
            logger.warn("✗ MISSING Executors ({}): {}", missingTypes.size(), missingTypes);
        } else {
            logger.info("✓ ALL expected node types have executors registered");
        }

        logger.info("=== COMPATIBILITY STATUS ===");
        if (missingTypes.isEmpty()) {
            logger.info("✓ FULL COMPATIBILITY: Frontend can execute ANY workflow");
            logger.info("  - All {} node types are supported", EXPECTED_NODE_TYPES.size());
            logger.info("  - All executors are registered and ready");
        } else {
            logger.error("✗ INCOMPLETE: {} node types missing executors", missingTypes.size());
            logger.error("  Missing: {}", missingTypes);
            throw new RuntimeException("Executor compatibility check failed. Missing executors: " + missingTypes);
        }
    }
}
