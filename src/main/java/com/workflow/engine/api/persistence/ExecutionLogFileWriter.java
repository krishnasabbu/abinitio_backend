package com.workflow.engine.api.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ExecutionLogFileWriter {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionLogFileWriter.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static volatile Path logDirectory = Paths.get("./logs");
    private static final ConcurrentMap<String, Object> DIR_LOCKS = new ConcurrentHashMap<>();

    public static void setLogDirectory(String dir) {
        logDirectory = Paths.get(dir);
    }

    public static Path getLogDirectory() {
        return logDirectory;
    }

    public static Path getLogFilePath(String executionId) {
        return logDirectory.resolve(executionId + ".log");
    }

    public static void appendLog(String executionId, long timestamp, String level, String nodeId, String message, String stackTrace) {
        if (executionId == null || executionId.isEmpty()) {
            return;
        }

        try {
            ensureDirectoryExists();
            Path logFile = getLogFilePath(executionId);

            String formattedTime = FORMATTER.format(Instant.ofEpochMilli(timestamp));
            StringBuilder line = new StringBuilder();
            line.append(formattedTime)
                .append(" [").append(level).append("]");

            if (nodeId != null && !nodeId.isEmpty()) {
                line.append(" [node:").append(nodeId).append("]");
            }

            line.append(" ").append(message).append(System.lineSeparator());

            if (stackTrace != null && !stackTrace.isEmpty()) {
                line.append(stackTrace);
                if (!stackTrace.endsWith(System.lineSeparator())) {
                    line.append(System.lineSeparator());
                }
            }

            Object lock = DIR_LOCKS.computeIfAbsent(executionId, k -> new Object());
            synchronized (lock) {
                try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    writer.write(line.toString());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to write log file for execution {}: {}", executionId, e.getMessage());
        }
    }

    public static void appendLog(String executionId, long timestamp, String level, String message) {
        appendLog(executionId, timestamp, level, null, message, null);
    }

    private static void ensureDirectoryExists() throws IOException {
        Path dir = logDirectory;
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    public static void cleanup(String executionId) {
        DIR_LOCKS.remove(executionId);
    }
}
