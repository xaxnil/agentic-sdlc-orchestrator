package dev.let.agentic.graph;

import static dev.let.agentic.domain.StageCondition.ALWAYS;
import static dev.let.agentic.domain.StageCondition.IF_AMBIGUOUS;
import static dev.let.agentic.domain.StageCondition.IF_BROWNFIELD;
import static dev.let.agentic.domain.StageId.*;

import dev.let.agentic.domain.StageDefinition;
import dev.let.agentic.domain.StageId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit dependency graph (DAG) of lifecycle stages. Validated on construction. */
public final class StageGraph {

    private final Map<StageId, StageDefinition> definitions = new LinkedHashMap<>();

    public StageGraph(List<StageDefinition> list) {
        for (StageDefinition d : list) {
            if (definitions.put(d.id(), d) != null) {
                throw new IllegalStateException("Duplicate stage: " + d.id());
            }
        }
        for (StageDefinition d : list) {
            for (StageId dep : d.dependsOn()) {
                if (!definitions.containsKey(dep)) {
                    throw new IllegalStateException(d.id() + " depends on unknown stage " + dep);
                }
            }
        }
        topologicalOrder(); // throws if there is a cycle
    }

    public static StageGraph standard() {
        return new StageGraph(List.of(
                StageDefinition.of(REQUIREMENT, ALWAYS, false, 2),
                StageDefinition.of(CLARIFY, IF_AMBIGUOUS, true, 0, REQUIREMENT),
                StageDefinition.of(IMPACT, IF_BROWNFIELD, false, 2, REQUIREMENT),
                StageDefinition.of(PLANNING, ALWAYS, false, 2, CLARIFY, IMPACT),
                StageDefinition.of(ARCHITECTURE, ALWAYS, false, 2, PLANNING),
                StageDefinition.of(SECURITY, ALWAYS, false, 2, PLANNING),
                StageDefinition.of(DATA_API, ALWAYS, false, 2, PLANNING),
                StageDefinition.of(DESIGN_APPROVAL, ALWAYS, true, 0, ARCHITECTURE, SECURITY, DATA_API),
                StageDefinition.of(IMPLEMENTATION, ALWAYS, false, 2, DESIGN_APPROVAL),
                StageDefinition.of(TESTS, ALWAYS, false, 2, IMPLEMENTATION),
                StageDefinition.of(DOCS, ALWAYS, false, 2, IMPLEMENTATION),
                StageDefinition.of(VALIDATION, ALWAYS, false, 1, TESTS, DOCS),
                StageDefinition.of(RELEASE_APPROVAL, ALWAYS, true, 0, VALIDATION),
                StageDefinition.of(RELEASE, ALWAYS, false, 1, RELEASE_APPROVAL)));
    }

    public Collection<StageDefinition> all() { return definitions.values(); }

    public StageDefinition get(StageId id) { return definitions.get(id); }

    /** Kahn's algorithm. Throws if the graph contains a cycle. */
    public List<StageId> topologicalOrder() {
        Map<StageId, Integer> indegree = new LinkedHashMap<>();
        definitions.values().forEach(d -> indegree.put(d.id(), d.dependsOn().size()));
        Deque<StageId> queue = new ArrayDeque<>();
        indegree.forEach((id, n) -> { if (n == 0) queue.add(id); });
        List<StageId> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            StageId current = queue.poll();
            order.add(current);
            for (StageDefinition d : definitions.values()) {
                if (d.dependsOn().contains(current)
                        && indegree.merge(d.id(), -1, Integer::sum) == 0) {
                    queue.add(d.id());
                }
            }
        }
        if (order.size() != definitions.size()) {
            throw new IllegalStateException("Cycle detected in stage graph");
        }
        return order;
    }

    /** All stages that directly or transitively depend on the given one (used for re-planning). */
    public Set<StageId> downstreamOf(StageId start) {
        Set<StageId> result = new LinkedHashSet<>();
        Deque<StageId> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            StageId current = stack.pop();
            for (StageDefinition d : definitions.values()) {
                if (d.dependsOn().contains(current) && result.add(d.id())) {
                    stack.push(d.id());
                }
            }
        }
        return result;
    }
}
