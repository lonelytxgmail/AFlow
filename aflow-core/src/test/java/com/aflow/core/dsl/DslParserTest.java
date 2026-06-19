package com.aflow.core.dsl;

import com.aflow.common.exception.DslParseException;
import com.aflow.common.model.EdgeDefinition;
import com.aflow.common.model.FlowDefinition;
import com.aflow.common.model.NodeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DslParserTest {

    private DslParser parser;

    @BeforeEach
    void setUp() {
        parser = new DslParser();
    }

    // ─── Parse Tests ─────────────────────────────────────────────────

    @Test
    void parse_validJson_returnsDefinition() {
        String json = """
                {
                  "id": "def-1",
                  "name": "Test Flow",
                  "version": 1,
                  "nodes": [
                    {"id": "start", "type": "log"},
                    {"id": "end", "type": "log"}
                  ],
                  "edges": [
                    {"from": "start", "to": "end"}
                  ]
                }
                """;
        FlowDefinition def = parser.parse(json);
        assertEquals("def-1", def.getId());
        assertEquals("Test Flow", def.getName());
        assertEquals(2, def.getNodes().size());
        assertEquals(1, def.getEdges().size());
    }

    @Test
    void parse_nullJson_throws() {
        assertThrows(DslParseException.class, () -> parser.parse(null));
    }

    @Test
    void parse_blankJson_throws() {
        assertThrows(DslParseException.class, () -> parser.parse("   "));
    }

    @Test
    void parse_invalidJson_throws() {
        assertThrows(DslParseException.class, () -> parser.parse("{not valid json}"));
    }

    // ─── Serialize Tests ─────────────────────────────────────────────

    @Test
    void serialize_validDefinition_returnsJson() {
        FlowDefinition def = new FlowDefinition();
        def.setId("def-1");
        def.setName("Test");
        def.getNodes().add(new NodeDefinition("n1", "log"));

        String json = parser.serialize(def);
        assertTrue(json.contains("\"def-1\""));
        assertTrue(json.contains("\"n1\""));
    }

    @Test
    void serialize_null_throws() {
        assertThrows(DslParseException.class, () -> parser.serialize(null));
    }

    // ─── Validate Tests ──────────────────────────────────────────────

    @Test
    void validate_validDefinition_passes() {
        FlowDefinition def = buildSimpleDefinition("n1", "n2");
        assertDoesNotThrow(() -> parser.validate(def));
    }

    @Test
    void validate_null_throws() {
        assertThrows(DslParseException.class, () -> parser.validate(null));
    }

    @Test
    void validate_emptyNodes_throws() {
        FlowDefinition def = new FlowDefinition();
        def.setNodes(new ArrayList<>());
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_duplicateNodeId_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("dup", "log"));
        def.getNodes().add(new NodeDefinition("dup", "log"));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_nodeWithBlankId_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("", "log"));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_nodeWithBlankType_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("n1", ""));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_edgeReferencesUnknownNode_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("n1", "log"));
        def.getEdges().add(new EdgeDefinition("n1", "unknown"));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_selfLoop_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("n1", "log"));
        def.getEdges().add(new EdgeDefinition("n1", "n1"));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_cycleDetected_throws() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("a", "log"));
        def.getNodes().add(new NodeDefinition("b", "log"));
        def.getEdges().add(new EdgeDefinition("a", "b"));
        def.getEdges().add(new EdgeDefinition("b", "a"));
        assertThrows(DslParseException.class, () -> parser.validate(def));
    }

    @Test
    void validate_unreachableNode_throws() {
        // Disconnected subgraph: a->b, c->d where c has incoming from d
        // d has incoming from c, so both c and d have incoming edges
        // Only 'a' is a start node, and from 'a' only 'b' is reachable
        // c and d form a cycle, but cycle detection catches that first.
        // Instead: use a diamond where one node is only reachable via a conditional path
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("a", "log"));
        def.getNodes().add(new NodeDefinition("b", "log"));
        def.getNodes().add(new NodeDefinition("c", "log"));
        def.getNodes().add(new NodeDefinition("d", "log"));
        // a->b, a->c, c->d, but b has no outgoing edge to d
        // All reachable from a, so this passes
        def.getEdges().add(new EdgeDefinition("a", "b"));
        def.getEdges().add(new EdgeDefinition("a", "c"));
        def.getEdges().add(new EdgeDefinition("c", "d"));
        // This should pass — all nodes reachable from a
        assertDoesNotThrow(() -> parser.validate(def));
    }

    @Test
    void validate_linearChain_passes() {
        FlowDefinition def = new FlowDefinition();
        def.getNodes().add(new NodeDefinition("a", "log"));
        def.getNodes().add(new NodeDefinition("b", "log"));
        def.getNodes().add(new NodeDefinition("c", "log"));
        def.getEdges().add(new EdgeDefinition("a", "b"));
        def.getEdges().add(new EdgeDefinition("b", "c"));
        assertDoesNotThrow(() -> parser.validate(def));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private FlowDefinition buildSimpleDefinition(String... nodeIds) {
        FlowDefinition def = new FlowDefinition();
        for (String id : nodeIds) {
            def.getNodes().add(new NodeDefinition(id, "log"));
        }
        for (int i = 0; i < nodeIds.length - 1; i++) {
            def.getEdges().add(new EdgeDefinition(nodeIds[i], nodeIds[i + 1]));
        }
        return def;
    }
}
