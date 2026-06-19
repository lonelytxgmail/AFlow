package com.aflow.core.dsl;

import com.aflow.common.exception.DslParseException;
import com.aflow.common.model.EdgeDefinition;
import com.aflow.common.model.FlowDefinition;
import com.aflow.common.model.NodeDefinition;
import com.aflow.common.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses, serializes, and validates flow definition JSON (the AFlow DSL).
 * <p>
 * Validation includes:
 * <ul>
 *   <li>Structural checks (non-null nodes/edges, unique node IDs)</li>
 *   <li>Referential integrity (edges reference existing nodes)</li>
 *   <li>Cycle detection via DFS</li>
 *   <li>Reachability check (no unreachable nodes)</li>
 * </ul>
 */
@Service
public class DslParser {

    private static final Logger log = LoggerFactory.getLogger(DslParser.class);

    private final ObjectMapper objectMapper;

    public DslParser() {
        this.objectMapper = JsonUtil.MAPPER;
    }

    public DslParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse a JSON string into a {@link FlowDefinition}.
     *
     * @param json the flow definition JSON
     * @return parsed flow definition
     * @throws DslParseException if the JSON is invalid or cannot be parsed
     */
    public FlowDefinition parse(String json) {
        if (json == null || json.isBlank()) {
            throw new DslParseException("Flow definition JSON must not be null or blank");
        }
        try {
            FlowDefinition definition = objectMapper.readValue(json, FlowDefinition.class);
            log.debug("Parsed flow definition: id='{}', name='{}', nodes={}, edges={}",
                    definition.getId(), definition.getName(),
                    definition.getNodes().size(), definition.getEdges().size());
            return definition;
        } catch (JsonProcessingException e) {
            throw new DslParseException("Failed to parse flow definition JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize a {@link FlowDefinition} to a JSON string.
     *
     * @param definition the flow definition
     * @return JSON representation
     * @throws DslParseException if serialization fails
     */
    public String serialize(FlowDefinition definition) {
        if (definition == null) {
            throw new DslParseException("Flow definition must not be null");
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new DslParseException("Failed to serialize flow definition: " + e.getMessage(), e);
        }
    }

    /**
     * Validate a flow definition for structural correctness.
     *
     * @param definition the flow definition to validate
     * @throws DslParseException if validation fails
     */
    public void validate(FlowDefinition definition) {
        if (definition == null) {
            throw new DslParseException("Flow definition must not be null");
        }

        List<NodeDefinition> nodes = definition.getNodes();
        List<EdgeDefinition> edges = definition.getEdges();

        if (nodes == null || nodes.isEmpty()) {
            throw new DslParseException("Flow definition must contain at least one node");
        }

        // 1. Check unique node IDs
        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                throw new DslParseException("All nodes must have a non-blank ID");
            }
            if (node.getType() == null || node.getType().isBlank()) {
                throw new DslParseException("Node '" + node.getId() + "' must have a non-blank type");
            }
            if (!nodeIds.add(node.getId())) {
                throw new DslParseException("Duplicate node ID: '" + node.getId() + "'");
            }
        }

        // 2. Check edge referential integrity
        if (edges != null) {
            for (EdgeDefinition edge : edges) {
                if (!nodeIds.contains(edge.getFrom())) {
                    throw new DslParseException("Edge references unknown source node: '" + edge.getFrom() + "'");
                }
                if (!nodeIds.contains(edge.getTo())) {
                    throw new DslParseException("Edge references unknown target node: '" + edge.getTo() + "'");
                }
                if (edge.getFrom().equals(edge.getTo())) {
                    throw new DslParseException("Self-loop detected on node: '" + edge.getFrom() + "'");
                }
                // Validate edge type if specified
                String edgeType = edge.getEffectiveType();
                if (!EdgeDefinition.TYPE_NORMAL.equals(edgeType) && !EdgeDefinition.TYPE_ERROR.equals(edgeType)) {
                    throw new DslParseException("Invalid edge type '" + edgeType + "' on edge: " + edge.getFrom() + " -> " + edge.getTo() + ". Must be 'normal' or 'error'.");
                }
            }
        }

        // 3. Build adjacency list and check for cycles
        Map<String, Set<String>> adjacency = buildAdjacencyList(nodes, edges);
        detectCycles(nodeIds, adjacency);

        // 4. Check reachability — find start nodes (no incoming edges) and verify all nodes are reachable
        Set<String> nodesWithIncoming = edges != null
                ? edges.stream().map(EdgeDefinition::getTo).collect(Collectors.toSet())
                : Set.of();
        Set<String> startNodes = nodeIds.stream()
                .filter(id -> !nodesWithIncoming.contains(id))
                .collect(Collectors.toSet());

        if (startNodes.isEmpty()) {
            throw new DslParseException("No start node found — every node has incoming edges (possible cycle)");
        }

        checkReachability(startNodes, adjacency, nodeIds);

        log.debug("Flow definition validation passed: {} nodes, {} edges, start nodes: {}",
                nodes.size(), edges != null ? edges.size() : 0, startNodes);
    }

    // ─── Private helpers ────────────────────────────────────────────

    private Map<String, Set<String>> buildAdjacencyList(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (NodeDefinition node : nodes) {
            adjacency.put(node.getId(), new HashSet<>());
        }
        if (edges != null) {
            for (EdgeDefinition edge : edges) {
                adjacency.get(edge.getFrom()).add(edge.getTo());
            }
        }
        return adjacency;
    }

    /**
     * Detect cycles using iterative DFS with three-colour marking.
     */
    private void detectCycles(Set<String> nodeIds, Map<String, Set<String>> adjacency) {
        // WHITE = unvisited, GRAY = in current path, BLACK = fully processed
        Map<String, Color> colors = new HashMap<>();
        nodeIds.forEach(id -> colors.put(id, Color.WHITE));

        for (String nodeId : nodeIds) {
            if (colors.get(nodeId) == Color.WHITE) {
                Deque<StackFrame> stack = new ArrayDeque<>();
                stack.push(new StackFrame(nodeId, adjacency.getOrDefault(nodeId, Set.of()).iterator()));
                colors.put(nodeId, Color.GRAY);

                while (!stack.isEmpty()) {
                    StackFrame frame = stack.peek();
                    if (frame.neighbors.hasNext()) {
                        String neighbor = frame.neighbors.next();
                        Color neighborColor = colors.get(neighbor);
                        if (neighborColor == Color.GRAY) {
                            throw new DslParseException(
                                    "Cycle detected in flow graph involving node: '" + neighbor + "'");
                        }
                        if (neighborColor == Color.WHITE) {
                            colors.put(neighbor, Color.GRAY);
                            stack.push(new StackFrame(neighbor,
                                    adjacency.getOrDefault(neighbor, Set.of()).iterator()));
                        }
                    } else {
                        colors.put(frame.nodeId, Color.BLACK);
                        stack.pop();
                    }
                }
            }
        }
    }

    /**
     * Verify all nodes are reachable from the set of start nodes using BFS.
     */
    private void checkReachability(Set<String> startNodes, Map<String, Set<String>> adjacency, Set<String> allNodeIds) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(startNodes);
        visited.addAll(startNodes);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        Set<String> unreachable = new HashSet<>(allNodeIds);
        unreachable.removeAll(visited);
        if (!unreachable.isEmpty()) {
            throw new DslParseException("Unreachable nodes detected: " + unreachable);
        }
    }

    private enum Color { WHITE, GRAY, BLACK }

    private record StackFrame(String nodeId, java.util.Iterator<String> neighbors) {}
}
