package com.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.engine.execution.job.WorkflowDecisionDecider;
import com.workflow.engine.graph.*;
import com.workflow.engine.model.ExecutionHints;
import com.workflow.engine.service.PartialRestartManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Features Tests")
public class AdvancedFeaturesTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("DECISION Node Tests")
    class DecisionTests {

        @Test
        @DisplayName("WorkflowDecisionDecider parses branches from config")
        void testDecisionDeciderParsesBranches() {
            ObjectNode config = objectMapper.createObjectNode();
            var branches = config.putArray("branches");

            ObjectNode branch1 = branches.addObject();
            branch1.put("name", "high");
            branch1.put("condition", "#readCount > 1000");
            branch1.put("target", "high-volume-processor");

            ObjectNode branch2 = branches.addObject();
            branch2.put("name", "low");
            branch2.put("condition", "#readCount <= 1000");
            branch2.put("target", "low-volume-processor");

            config.put("defaultBranch", "default");

            StepNode decisionNode = new StepNode(
                "decision-1",
                "Decision",
                config,
                List.of("high-volume-processor", "low-volume-processor", "default-processor"),
                null, null, null, null, null, null,
                StepKind.DECISION,
                null
            );

            WorkflowDecisionDecider decider = WorkflowDecisionDecider.fromStepNode(decisionNode);
            assertNotNull(decider);
        }

        @Test
        @DisplayName("DECISION node must be detected correctly")
        void testDecisionNodeDetection() {
            StepNode decisionNode = new StepNode(
                "decision-1",
                "Decision",
                null,
                List.of("branch-a", "branch-b"),
                null, null, null, null, null, null,
                StepKind.DECISION,
                null
            );

            assertTrue(decisionNode.isDecision());
            assertFalse(decisionNode.isFork());
            assertFalse(decisionNode.isJoin());
        }
    }

    @Nested
    @DisplayName("SUBGRAPH Expansion Tests")
    class SubgraphTests {

        private SubgraphExpander expander;

        @BeforeEach
        void setUp() {
            expander = new SubgraphExpander();
        }

        @Test
        @DisplayName("Subgraph expansion creates prefixed node IDs")
        void testSubgraphExpansionPrefixesNodeIds() {
            Map<String, StepNode> subgraphSteps = new LinkedHashMap<>();
            subgraphSteps.put("sub-start", createSimpleNode("sub-start", "Compute", List.of("sub-middle")));
            subgraphSteps.put("sub-middle", createSimpleNode("sub-middle", "Filter", List.of("sub-end")));
            subgraphSteps.put("sub-end", createSimpleNode("sub-end", "Compute", null));

            SubgraphExpander.SubgraphDefinition subgraphDef = new SubgraphExpander.SubgraphDefinition(
                subgraphSteps,
                List.of("sub-start"),
                "sub-end"
            );

            expander.registerSubgraph("my-subgraph", subgraphDef);

            ObjectNode subgraphConfig = objectMapper.createObjectNode();
            subgraphConfig.put("subgraphId", "my-subgraph");

            Map<String, StepNode> mainSteps = new LinkedHashMap<>();
            mainSteps.put("start", createSimpleNode("start", "FileSource", List.of("subgraph-node")));
            mainSteps.put("subgraph-node", new StepNode(
                "subgraph-node",
                "Subgraph",
                subgraphConfig,
                List.of("end"),
                null, null, null, null, null, null,
                StepKind.SUBGRAPH,
                null
            ));
            mainSteps.put("end", createSimpleNode("end", "FileSink", null));

            ExecutionPlan originalPlan = new ExecutionPlan(List.of("start"), mainSteps, "test-workflow");

            ExecutionPlan expandedPlan = expander.expand(originalPlan);

            assertTrue(expandedPlan.steps().containsKey("subgraph-node_sub-start"));
            assertTrue(expandedPlan.steps().containsKey("subgraph-node_sub-middle"));
            assertTrue(expandedPlan.steps().containsKey("subgraph-node_sub-end"));

            assertFalse(expandedPlan.steps().containsKey("subgraph-node"));
        }

        @Test
        @DisplayName("Subgraph expansion maintains flow connections")
        void testSubgraphExpansionMaintainsConnections() {
            Map<String, StepNode> subgraphSteps = new LinkedHashMap<>();
            subgraphSteps.put("inner-a", createSimpleNode("inner-a", "Compute", List.of("inner-b")));
            subgraphSteps.put("inner-b", createSimpleNode("inner-b", "Compute", null));

            SubgraphExpander.SubgraphDefinition subgraphDef = new SubgraphExpander.SubgraphDefinition(
                subgraphSteps,
                List.of("inner-a"),
                "inner-b"
            );

            expander.registerSubgraph("simple-subgraph", subgraphDef);

            ObjectNode subgraphConfig = objectMapper.createObjectNode();
            subgraphConfig.put("subgraphId", "simple-subgraph");

            Map<String, StepNode> mainSteps = new LinkedHashMap<>();
            mainSteps.put("before", createSimpleNode("before", "Start", List.of("sg")));
            mainSteps.put("sg", new StepNode(
                "sg",
                "Subgraph",
                subgraphConfig,
                List.of("after"),
                null, null, null, null, null, null,
                StepKind.SUBGRAPH,
                null
            ));
            mainSteps.put("after", createSimpleNode("after", "End", null));

            ExecutionPlan originalPlan = new ExecutionPlan(List.of("before"), mainSteps, "test");
            ExecutionPlan expandedPlan = expander.expand(originalPlan);

            StepNode beforeNode = expandedPlan.steps().get("before");
            assertTrue(beforeNode.nextSteps().contains("sg_inner-a"));

            StepNode exitNode = expandedPlan.steps().get("sg_inner-b");
            assertTrue(exitNode.nextSteps().contains("after"));
        }

        @Test
        @DisplayName("Circular subgraph references throw exception")
        void testCircularSubgraphReferencesThrow() {
            ObjectNode selfRefConfig = objectMapper.createObjectNode();
            selfRefConfig.put("subgraphId", "circular");

            Map<String, StepNode> circularSteps = new LinkedHashMap<>();
            circularSteps.put("step-a", new StepNode(
                "step-a",
                "Subgraph",
                selfRefConfig,
                null,
                null, null, null, null, null, null,
                StepKind.SUBGRAPH,
                null
            ));

            SubgraphExpander.SubgraphDefinition circularDef = new SubgraphExpander.SubgraphDefinition(
                circularSteps,
                List.of("step-a"),
                "step-a"
            );

            expander.registerSubgraph("circular", circularDef);

            ObjectNode config = objectMapper.createObjectNode();
            config.put("subgraphId", "circular");

            Map<String, StepNode> mainSteps = new LinkedHashMap<>();
            mainSteps.put("entry", new StepNode(
                "entry",
                "Subgraph",
                config,
                null,
                null, null, null, null, null, null,
                StepKind.SUBGRAPH,
                null
            ));

            ExecutionPlan plan = new ExecutionPlan(List.of("entry"), mainSteps, "circular-test");

            assertThrows(GraphValidationException.class, () -> expander.expand(plan));
        }

        private StepNode createSimpleNode(String id, String type, List<String> nextSteps) {
            return new StepNode(
                id, type, null, nextSteps,
                null, null, null, null, null, null,
                StepKind.NORMAL, null
            );
        }
    }

    @Nested
    @DisplayName("Partial Restart Tests")
    class PartialRestartTests {

        private PartialRestartManager restartManager;

        @BeforeEach
        void setUp() {
            restartManager = new PartialRestartManager();
        }

        @Test
        @DisplayName("Partial plan includes only reachable nodes")
        void testPartialPlanIncludesOnlyReachable() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("A", createNode("A", List.of("B", "C")));
            steps.put("B", createNode("B", List.of("D")));
            steps.put("C", createNode("C", List.of("D")));
            steps.put("D", createNode("D", List.of("E")));
            steps.put("E", createNode("E", null));

            ExecutionPlan fullPlan = new ExecutionPlan(List.of("A"), steps, "test");

            ExecutionPlan partialPlan = restartManager.createPartialPlan(fullPlan, "C");

            assertTrue(partialPlan.steps().containsKey("C"));
            assertTrue(partialPlan.steps().containsKey("D"));
            assertTrue(partialPlan.steps().containsKey("E"));
            assertFalse(partialPlan.steps().containsKey("A"));
            assertFalse(partialPlan.steps().containsKey("B"));
        }

        @Test
        @DisplayName("Partial plan sets correct entry point")
        void testPartialPlanSetsCorrectEntryPoint() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("A", createNode("A", List.of("B")));
            steps.put("B", createNode("B", List.of("C")));
            steps.put("C", createNode("C", null));

            ExecutionPlan fullPlan = new ExecutionPlan(List.of("A"), steps, "test");

            ExecutionPlan partialPlan = restartManager.createPartialPlan(fullPlan, "B");

            assertEquals(1, partialPlan.entryStepIds().size());
            assertEquals("B", partialPlan.entryStepIds().get(0));
        }

        @Test
        @DisplayName("Cannot restart from non-existent node")
        void testCannotRestartFromNonExistentNode() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("A", createNode("A", List.of("B")));
            steps.put("B", createNode("B", null));

            ExecutionPlan fullPlan = new ExecutionPlan(List.of("A"), steps, "test");

            assertThrows(GraphValidationException.class,
                () -> restartManager.createPartialPlan(fullPlan, "Z"));
        }

        @Test
        @DisplayName("Partial restart removes upstream dependencies from entry node")
        void testPartialRestartRemovesUpstreamDependencies() {
            ExecutionHints joinHints = new ExecutionHints();

            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("fork", createForkNode("fork", List.of("branch-a", "branch-b"), "join"));
            steps.put("branch-a", createNode("branch-a", List.of("join")));
            steps.put("branch-b", createNode("branch-b", List.of("join")));
            steps.put("join", new StepNode(
                "join", "Join", null, List.of("end"),
                null, null, null, null, null, null,
                StepKind.JOIN, List.of("branch-a", "branch-b")
            ));
            steps.put("end", createNode("end", null));

            ExecutionPlan fullPlan = new ExecutionPlan(List.of("fork"), steps, "test");

            ExecutionPlan partialPlan = restartManager.createPartialPlan(fullPlan, "join");

            StepNode joinNode = partialPlan.steps().get("join");
            assertNull(joinNode.upstreamSteps());
            assertEquals(StepKind.NORMAL, joinNode.kind());
        }

        @Test
        @DisplayName("Cannot restart mid-fork if join is excluded")
        void testCannotRestartMidForkIfJoinExcluded() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("fork", createForkNode("fork", List.of("branch-a", "branch-b"), "join"));
            steps.put("branch-a", createNode("branch-a", List.of("join")));
            steps.put("branch-b", createNode("branch-b", null));
            steps.put("join", createNode("join", List.of("end")));
            steps.put("end", createNode("end", null));

            ExecutionPlan fullPlan = new ExecutionPlan(List.of("fork"), steps, "test");

            assertThrows(GraphValidationException.class,
                () -> restartManager.createPartialPlan(fullPlan, "branch-b"));
        }

        private StepNode createNode(String id, List<String> nextSteps) {
            return new StepNode(
                id, "Compute", null, nextSteps,
                null, null, null, null, null, null,
                StepKind.NORMAL, null
            );
        }

        private StepNode createForkNode(String id, List<String> branches, String joinNodeId) {
            ExecutionHints hints = new ExecutionHints();
            hints.setJoinNodeId(joinNodeId);

            return new StepNode(
                id, "Fork", null, branches,
                null, null, null, hints, null, null,
                StepKind.FORK, null
            );
        }
    }

    @Nested
    @DisplayName("ExecutionPlanValidator Integration Tests")
    class ValidatorIntegrationTests {

        @Test
        @DisplayName("Validator rejects implicit joins in strict mode")
        void testValidatorRejectsImplicitJoins() {
            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("A", createNode("A", List.of("B", "C")));
            steps.put("B", createNode("B", List.of("D")));
            steps.put("C", createNode("C", List.of("D")));
            steps.put("D", createNode("D", null));

            ExecutionPlan plan = new ExecutionPlan(List.of("A"), steps, "test");

            ExecutionPlanValidator.ValidationConfig strictConfig =
                ExecutionPlanValidator.ValidationConfig.strict();

            assertThrows(ExecutionPlanValidator.ExecutionPlanValidationException.class,
                () -> ExecutionPlanValidator.validate(plan, strictConfig));
        }

        @Test
        @DisplayName("Validator accepts explicit JOIN nodes")
        void testValidatorAcceptsExplicitJoins() {
            ExecutionHints forkHints = new ExecutionHints();
            forkHints.setJoinNodeId("join");

            Map<String, StepNode> steps = new LinkedHashMap<>();
            steps.put("fork", new StepNode(
                "fork", "Fork", null, List.of("B", "C"),
                null, null, null, forkHints, null, null,
                StepKind.FORK, null
            ));
            steps.put("B", createNode("B", List.of("join")));
            steps.put("C", createNode("C", List.of("join")));
            steps.put("join", new StepNode(
                "join", "Join", null, List.of("D"),
                null, null, null, null, null, null,
                StepKind.JOIN, List.of("B", "C")
            ));
            steps.put("D", createNode("D", null));

            ExecutionPlan plan = new ExecutionPlan(List.of("fork"), steps, "test");

            ExecutionPlanValidator.ValidationConfig strictConfig =
                ExecutionPlanValidator.ValidationConfig.strict();

            assertDoesNotThrow(() -> ExecutionPlanValidator.validate(plan, strictConfig));
        }

        private StepNode createNode(String id, List<String> nextSteps) {
            return new StepNode(
                id, "Compute", null, nextSteps,
                null, null, null, null, null, null,
                StepKind.NORMAL, null
            );
        }
    }
}
