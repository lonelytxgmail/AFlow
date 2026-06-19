package com.aflow.core.dsl;

import com.aflow.common.model.EdgeDefinition;
import com.aflow.common.model.FlowDefinition;
import com.aflow.common.model.NodeDefinition;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates a {@link FlowDefinition} and returns a structured list of all validation errors
 * (as opposed to throwing on the first error like {@link DslParser#validate(FlowDefinition)}).
 * <p>
 * Checks performed:
 * <ul>
 *   <li>Node ID uniqueness</li>
 *   <li>Edge referential integrity (from/to reference existing nodes)</li>
 *   <li>No orphan/isolated nodes (all nodes reachable)</li>
 *   <li>Required fields (node id, type non-blank)</li>
 *   <li>No self-loops</li>
 *   <li>Valid edge types</li>
 *   <li>Cycle detection</li>
 * </ul>
 */
@Component
public class FlowDefinitionValidator {

    /**
     * Validate the given flow definition and return all errors found.
     *
     * @param definition the flow definition to validate
     * @return list of validation errors (empty if valid)
     */
    public List<ValidationError> validate(FlowDefinition definition) {
        List<ValidationError> errors = new ArrayList<>();

        if (definition == null) {
            errors.add(ValidationError.of("definition", "DEFINITION_NULL", "Flow definition must not be null"));
            return errors;
        }

        List<NodeDefinition> nodes = definition.getNodes();
        List<EdgeDefinition> edges = definition.getEdges();

        // Check nodes list exists and is non-empty
        if (nodes == null || nodes.isEmpty()) {
            errors.add(ValidationError.of("nodes", "NODES_EMPTY", "Flow definition must contain at least one node"));
            return errors;
        }

        // 1. Required field checks + unique IDs
        Set<String> nodeIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();

        for (int i = 0; i < nodes.size(); i++) {
            NodeDefinition node = nodes.get(i);
            String nodeRef = "nodes[" + i + "]";

            if (node.getId() == null || node.getId().isBlank()) {
                errors.add(ValidationError.of(nodeRef, "NODE_ID_BLANK",
                        "Node at index " + i + " must have a non-blank ID"));
                continue;
            }

            if (node.getType() == null || node.getType().isBlank()) {
                errors.add(ValidationError.ofNode(node.getId(), "NODE_TYPE_BLANK",
                        "Node '" + node.getId() + "' must have a non-blank type"));
            }

            if (!nodeIds.add(node.getId())) {
                duplicateIds.add(node.getId());
            }
        }

        for (String dupId : duplicateIds) {
            errors.add(ValidationError.ofNode(dupId, "DUPLICATE_NODE_ID",
                    "Duplicate node ID: '" + dupId + "'"));
        }

        // 2. Edge referential integrity + valid types
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                EdgeDefinition edge = edges.get(i);
                String edgeRef = "edges[" + i + "]";

                if (edge.getFrom() == null || edge.getFrom().isBlank()) {
                    errors.add(ValidationError.of(edgeRef, "EDGE_FROM_BLANK",
                            "Edge at index " + i + " has blank 'from' field"));
                    continue;
                }
                if (edge.getTo() == null || edge.getTo().isBlank()) {
                    errors.add(ValidationError.of(edgeRef, "EDGE_TO_BLANK",
                            "Edge at index " + i + " has blank 'to' field"));
                    continue;
                }

                if (!nodeIds.contains(edge.getFrom())) {
                    errors.add(ValidationError.of(edgeRef, "EDGE_UNKNOWN_SOURCE",
                            "Edge references unknown source node: '" + edge.getFrom() + "'"));
                }
                if (!nodeIds.contains(edge.getTo())) {
                    errors.add(ValidationError.of(edgeRef, "EDGE_UNKNOWN_TARGET",
                            "Edge references unknown target node: '" + edge.getTo() + "'"));
                }
                if (edge.getFrom().equals(edge.getTo())) {
                    errors.add(ValidationError.ofNode(edge.getFrom(), "SELF_LOOP",
                            "Self-loop detected on node: '" + edge.getFrom() + "'"));
                }

                // Validate edge type
                String edgeType = edge.getEffectiveType();
                if (!EdgeDefinition.TYPE_NORMAL.equals(edgeType) && !EdgeDefinition.TYPE_ERROR.equals(edgeType)) {
                    errors.add(ValidationError.of(edgeRef, "INVALID_EDGE_TYPE",
                            "Invalid edge type '" + edgeType + "' on edge: " + edge.getFrom() + " -> " + edge.getTo()));
                }
            }
        }

        // If we have referential errors, skip graph-based checks
        if (errors.stream().anyMatch(e -> e.code().startsWith("EDGE_UNKNOWN"))) {
            return errors;
        }

        // 3. Orphan/isolated node detection (nodes not reachable from start nodes)
        Set<String> nodesWithIncoming = edges != null
                ? edges.stream().map(EdgeDefinition::getTo).collect(Collectors.toSet())
                : Set.of();
        Set<String> startNodes = nodeIds.stream()
                .filter(id -> !nodesWithIncoming.contains(id))
                .collect(Collectors.toSet());

        if (startNodes.isEmpty() && !nodeIds.isEmpty()) {
            errors.add(ValidationError.of("graph", "NO_START_NODE",
                    "No start node found — every node has incoming edges (possible cycle)"));
        } else if (!startNodes.isEmpty()) {
            // BFS reachability
            Map<String, Set<String>> adjacency = buildAdjacencyList(nodes, edges);
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

            Set<String> unreachable = new HashSet<>(nodeIds);
            unreachable.removeAll(visited);
            for (String orphan : unreachable) {
                errors.add(ValidationError.ofNode(orphan, "ORPHAN_NODE",
                        "Node '" + orphan + "' is not reachable from any start node"));
            }
        }

        // 4. Cycle detection via DFS
        if (errors.isEmpty()) {
            Map<String, Set<String>> adjacency = buildAdjacencyList(nodes, edges);
            detectCycles(nodeIds, adjacency, errors);
        }

        return errors;
    }

    private Map<String, Set<String>> buildAdjacencyList(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (NodeDefinition node : nodes) {
            adjacency.put(node.getId(), new HashSet<>());
        }
        if (edges != null) {
            for (EdgeDefinition edge : edges) {
                if (adjacency.containsKey(edge.getFrom())) {
                    adjacency.get(edge.getFrom()).add(edge.getTo());
                }
            }
        }
        return adjacency;
    }

    private void detectCycles(Set<String> nodeIds, Map<String, Set<String>> adjacency, List<ValidationError> errors) {
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
                            errors.add(ValidationError.ofNode(neighbor, "CYCLE_DETECTED",
                                    "Cycle detected in flow graph involving node: '" + neighbor + "'"));
                            return; // one cycle error is enough
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

    private enum Color { WHITE, GRAY, BLACK }

    private record StackFrame(String nodeId, Iterator<String> neighbors) {}

    /**
     * Structured validation error.
     *
     * @param field   the field or location of the error (e.g., "nodes[0]", "edges[2]", node ID)
     * @param code    machine-readable error code
     * @param message human-readable description
     * @param nodeId  optional related node ID (for front-end jump-to-node)
     */
    public record ValidationError(String field, String code, String message, String nodeId) {

        public static ValidationError of(String field, String code, String message) {
            return new ValidationError(field, code, message, null);
        }

        public static ValidationError ofNode(String nodeId, String code, String message) {
            return new ValidationError("node:" + nodeId, code, message, nodeId);
        }
    }
}
