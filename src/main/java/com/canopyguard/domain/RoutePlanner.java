package com.canopyguard.domain;

import java.util.*;

public final class RoutePlanner {
    public record Node(String id, String label) {
        public Node { Objects.requireNonNull(id); Objects.requireNonNull(label); }
    }
    public record Edge(String id, String from, String to, double distanceKm, double speedKph,
                       int capacity, double fireRisk, double smokeRisk, boolean closed,
                       String mode, boolean accessible) {
        public Edge {
            if (!Double.isFinite(distanceKm) || !Double.isFinite(speedKph) || distanceKm <= 0 || speedKph <= 0 || capacity < 0)
                throw new IllegalArgumentException("Invalid route edge dimensions");
            if (!Double.isFinite(fireRisk) || !Double.isFinite(smokeRisk) || fireRisk < 0 || fireRisk > 1 || smokeRisk < 0 || smokeRisk > 1)
                throw new IllegalArgumentException("Risk must be between 0 and 1");
        }
        double cost(int evacuees) {
            double minutes = distanceKm / speedKph * 60.0;
            double congestion = Math.max(0, (double) evacuees / Math.max(1, capacity) - 0.5);
            return minutes * (1 + fireRisk * 7 + smokeRisk * 2 + congestion);
        }
    }
    public record Route(List<String> nodeIds, List<Edge> edges, double estimatedMinutes, double maxRisk) {
        public Route { nodeIds = List.copyOf(nodeIds); edges = List.copyOf(edges); }
    }

    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    public RoutePlanner(Collection<Node> nodes, Collection<Edge> edges) {
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (byId.put(node.id(), node) != null) throw new IllegalArgumentException("Duplicate route node: " + node.id());
        }
        for (Edge edge : edges) {
            if (!byId.containsKey(edge.from()) || !byId.containsKey(edge.to())) throw new IllegalArgumentException("Route edge references missing node: " + edge.id());
        }
        this.nodes = Map.copyOf(byId);
        this.edges = List.copyOf(edges);
    }

    public Optional<Route> safestRoute(String origin, String destination, int evacuees, boolean requireAccessible) {
        if (evacuees < 0) throw new IllegalArgumentException("Evacuees cannot be negative");
        return shortest(origin, destination, evacuees, requireAccessible, Set.of());
    }

    public List<Route> alternatives(String origin, String destination, int evacuees, boolean requireAccessible, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Limit must be positive");
        List<Route> result = new ArrayList<>();
        Set<String> banned = new HashSet<>();
        Optional<Route> first = shortest(origin, destination, evacuees, requireAccessible, banned);
        if (first.isEmpty()) return List.of();
        result.add(first.get());
        for (Edge edge : first.get().edges()) {
            if (result.size() >= limit) break;
            banned.add(edge.id());
            shortest(origin, destination, evacuees, requireAccessible, banned).ifPresent(candidate -> {
                if (result.stream().noneMatch(r -> r.edges().stream().map(Edge::id).toList().equals(candidate.edges().stream().map(Edge::id).toList()))) result.add(candidate);
            });
        }
        result.sort(Comparator.comparingDouble(Route::estimatedMinutes));
        return List.copyOf(result.subList(0, Math.min(limit, result.size())));
    }

    private Optional<Route> shortest(String origin, String destination, int people, boolean accessible, Set<String> banned) {
        if (!nodes.containsKey(origin) || !nodes.containsKey(destination)) return Optional.empty();
        Map<String, Double> distance = new HashMap<>();
        Map<String, Edge> previous = new HashMap<>();
        PriorityQueue<String> queue = new PriorityQueue<>(Comparator.comparingDouble(id -> distance.getOrDefault(id, Double.POSITIVE_INFINITY)));
        distance.put(origin, 0.0);
        queue.add(origin);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(destination)) break;
            double currentDistance = distance.get(current);
            for (Edge edge : edges) {
                if (!edge.from().equals(current) && !edge.to().equals(current)) continue;
                if (edge.closed() || edge.capacity() < people || edge.fireRisk() >= 0.90 || edge.smokeRisk() >= 0.90
                        || (accessible && !edge.accessible()) || banned.contains(edge.id())) continue;
                String next = edge.from().equals(current) ? edge.to() : edge.from();
                double candidate = currentDistance + edge.cost(people);
                if (candidate < distance.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    distance.put(next, candidate);
                    previous.put(next, edge);
                    queue.remove(next);
                    queue.add(next);
                }
            }
        }
        if (!distance.containsKey(destination)) return Optional.empty();
        List<String> path = new ArrayList<>();
        List<Edge> pathEdges = new ArrayList<>();
        String current = destination;
        path.add(current);
        double maxRisk = 0;
        while (!current.equals(origin)) {
            Edge edge = previous.get(current);
            if (edge == null) return Optional.empty();
            pathEdges.add(edge);
            maxRisk = Math.max(maxRisk, Math.max(edge.fireRisk(), edge.smokeRisk()));
            current = edge.from().equals(current) ? edge.to() : edge.from();
            path.add(current);
        }
        Collections.reverse(path);
        Collections.reverse(pathEdges);
        return Optional.of(new Route(path, pathEdges, distance.get(destination), maxRisk));
    }
}
