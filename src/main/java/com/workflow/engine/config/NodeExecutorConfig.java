package com.workflow.engine.config;

import com.workflow.engine.execution.AggregateExecutor;
import com.workflow.engine.execution.AlertExecutor;
import com.workflow.engine.execution.AssertExecutor;
import com.workflow.engine.execution.AuditExecutor;
import com.workflow.engine.execution.BroadcastExecutor;
import com.workflow.engine.execution.CheckpointExecutor;
import com.workflow.engine.execution.CollectExecutor;
import com.workflow.engine.execution.ComputeExecutor;
import com.workflow.engine.execution.CountExecutor;
import com.workflow.engine.execution.DBSinkExecutor;
import com.workflow.engine.execution.DBSourceExecutor;
import com.workflow.engine.execution.DecisionExecutor;
import com.workflow.engine.execution.DeduplicateExecutor;
import com.workflow.engine.execution.DenormalizeExecutor;
import com.workflow.engine.execution.EndExecutor;
import com.workflow.engine.execution.ErrorSinkExecutor;
import com.workflow.engine.execution.FailJobExecutor;
import com.workflow.engine.execution.FileSinkExecutor;
import com.workflow.engine.execution.FileSourceExecutor;
import com.workflow.engine.execution.FilterExecutor;
import com.workflow.engine.execution.HashPartitionExecutor;
import com.workflow.engine.execution.IntersectExecutor;
import com.workflow.engine.execution.JoinExecutor;
import com.workflow.engine.execution.LimitExecutor;
import com.workflow.engine.execution.LookupExecutor;
import com.workflow.engine.execution.MapExecutor;
import com.workflow.engine.execution.MergeExecutor;
import com.workflow.engine.execution.MinusExecutor;
import com.workflow.engine.execution.NodeExecutorRegistry;
import com.workflow.engine.execution.NormalizeExecutor;
import com.workflow.engine.execution.PartitionExecutor;
import com.workflow.engine.execution.RangePartitionExecutor;
import com.workflow.engine.execution.ReformatExecutor;
import com.workflow.engine.execution.RejectExecutor;
import com.workflow.engine.execution.ReplicateExecutor;
import com.workflow.engine.execution.ResumeExecutor;
import com.workflow.engine.execution.SampleExecutor;
import com.workflow.engine.execution.SchemaValidatorExecutor;
import com.workflow.engine.execution.SLAExecutor;
import com.workflow.engine.execution.SortExecutor;
import com.workflow.engine.execution.StartExecutor;
import com.workflow.engine.execution.SwitchExecutor;
import com.workflow.engine.execution.ThrottleExecutor;
import com.workflow.engine.execution.ValidateExecutor;
import com.workflow.engine.execution.WaitExecutor;
import com.workflow.engine.execution.JobConditionExecutor;
import com.workflow.engine.execution.SplitExecutor;
import com.workflow.engine.execution.GatherExecutor;
import com.workflow.engine.execution.KafkaSourceExecutor;
import com.workflow.engine.execution.KafkaSinkExecutor;
import com.workflow.engine.execution.RestAPISourceExecutor;
import com.workflow.engine.execution.RestAPISinkExecutor;
import com.workflow.engine.execution.DBExecuteExecutor;
import com.workflow.engine.execution.XMLParseExecutor;
import com.workflow.engine.execution.XMLValidateExecutor;
import com.workflow.engine.execution.JSONFlattenExecutor;
import com.workflow.engine.execution.JSONExplodeExecutor;
import com.workflow.engine.execution.RollupExecutor;
import com.workflow.engine.execution.WindowExecutor;
import com.workflow.engine.execution.ScanExecutor;
import com.workflow.engine.execution.EncryptExecutor;
import com.workflow.engine.execution.DecryptExecutor;
import com.workflow.engine.execution.PythonNodeExecutor;
import com.workflow.engine.execution.ScriptNodeExecutor;
import com.workflow.engine.execution.ShellNodeExecutor;
import com.workflow.engine.execution.CustomNodeExecutor;
import com.workflow.engine.execution.SubgraphExecutor;
import com.workflow.engine.execution.WebServiceCallExecutor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for node executor auto-registration.
 *
 * DEPRECATED: With auto-registration via NodeExecutorRegistry constructor injection,
 * manual registration is no longer needed. Kept for backward compatibility only.
 * All @Component NodeExecutor beans are automatically discovered and registered
 * at application startup.
 */
@Configuration
public class NodeExecutorConfig {

    /**
     * Legacy registration bean (no-op).
     *
     * Replaced by automatic constructor-injection registration in NodeExecutorRegistry.
     * This method is kept to avoid breaking existing configuration references.
     *
     * @return a no-op CommandLineRunner
     */
    @Bean
    public CommandLineRunner registerExecutors(NodeExecutorRegistry registry) {
        return args -> {
        };
    }
}
