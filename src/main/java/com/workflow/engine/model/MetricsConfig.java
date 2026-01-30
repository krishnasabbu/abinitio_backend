package com.workflow.engine.model;

import lombok.Data;

@Data
public class MetricsConfig {
    private boolean enabled = true;
    private boolean trackExecutionTime = true;
    private boolean trackReadCount = true;
    private boolean trackWriteCount = true;
    private boolean trackErrorCount = true;
}
