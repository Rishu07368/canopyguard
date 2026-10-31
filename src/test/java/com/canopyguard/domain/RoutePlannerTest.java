package com.canopyguard.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerTest {
    private RoutePlanner planner(boolean bridgeClosed) {
        List<RoutePlanner.Node> nodes = List.of(new RoutePlanner.Node("A", "A"), new RoutePlanner.Node("B", "B"),
                new RoutePlanner.Node("C", "C"), new RoutePlanner.Node("D", "D"));
        List<RoutePlanner.Edge> edges = List.of(
                new RoutePlanner.Edge("AB", "A", "B", 2, 20, 100, .1, .1, bridgeClosed, "road", true),
                new RoutePlanner.Edge("BD", "B", "D", 2, 20, 100, .1, .1, false, "road", true),
                new RoutePlanner.Edge("AC", "A", "C", 4, 20, 100, .2, .1, false, "road", true),
                new RoutePlanner.Edge("CD", "C", "D", 4, 20, 100, .1, .1, false, "road", true));
        return new RoutePlanner(nodes, edges);
    }

    @Test void closedBridgeIsExcludedAndAlternativeIsFound() {
        var routes = planner(true).alternatives("A", "D", 40, false, 3);
        assertFalse(routes.isEmpty());
        assertTrue(routes.get(0).edges().stream().noneMatch(edge -> edge.id().equals("AB") || edge.id().equals("BD")));
    }

    @Test void unsafeAndOverCapacityEdgesAreExcluded() {
        RoutePlanner graph = new RoutePlanner(List.of(new RoutePlanner.Node("A", "A"), new RoutePlanner.Node("B", "B")),
                List.of(new RoutePlanner.Edge("blocked", "A", "B", 1, 20, 10, .95, .1, false, "road", true)));
        assertTrue(graph.safestRoute("A", "B", 20, false).isEmpty());
    }

    @Test void extremeSmokeExposureIsExcluded() {
        RoutePlanner graph = new RoutePlanner(List.of(new RoutePlanner.Node("A", "A"), new RoutePlanner.Node("B", "B")),
                List.of(new RoutePlanner.Edge("smoke", "A", "B", 1, 20, 100, .1, .95, false, "road", true)));
        assertTrue(graph.safestRoute("A", "B", 2, false).isEmpty());
    }

    @Test void inaccessibleRouteIsNotAssignedToAccessibilityRequiredCommunity() {
        RoutePlanner graph = new RoutePlanner(List.of(new RoutePlanner.Node("A", "A"), new RoutePlanner.Node("B", "B")),
                List.of(new RoutePlanner.Edge("foot", "A", "B", 1, 4, 40, .1, .1, false, "footpath", false)));
        assertTrue(graph.safestRoute("A", "B", 2, true).isEmpty());
    }

    @Test void alternativesAreReturnedWhenTwoPathsExist() {
        assertTrue(planner(false).alternatives("A", "D", 5, false, 2).size() >= 2);
    }
}
