package com.workflow.engine.api.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimestampConverter {

    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneOffset.UTC);

    /**
     * Convert Unix timestamp in milliseconds to ISO 8601 format
     * Example: 1674749238000 -> "2026-01-26T12:47:18.240+00:00"
     */
    public static String toISO8601(Long timestampMs) {
        if (timestampMs == null || timestampMs == 0) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(timestampMs);
        OffsetDateTime odt = instant.atOffset(ZoneOffset.UTC);
        String formatted = odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return formatted;
    }

    /**
     * Convert ISO 8601 timestamp to Unix milliseconds
     */
    public static Long fromISO8601(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(isoString);
            return instant.toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert Instant to ISO 8601 string
     */
    public static String fromInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
