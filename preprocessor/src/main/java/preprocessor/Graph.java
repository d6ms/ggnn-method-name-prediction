package preprocessor;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Graph {

    private final String name;
    private final List<Vertex> vertices;
    private final Map<Integer, Set<Integer>> adj = new HashMap<>();

    public Graph(String name, List<Vertex> vertices, List<Pair<Integer, Integer>> edges) {
        this.name = name;
        this.vertices = vertices;
        for (Pair<Integer, Integer> edge : edges) {
            addEdge(edge.getLeft(), edge.getRight());
        }
    }

    public void addEdge(int src, int dst) {
        if (!adj.containsKey(src)) {
            adj.put(src, new HashSet<>());
        }
        adj.get(src).add(dst);
    }

    public List<List<Integer>> getAdjacencyMatrix() {
        List<List<Integer>> data = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            List<Integer> row = new ArrayList<>(vertices.size());
            for (int j = 0; j < vertices.size(); j++) {
                row.add(0);
            }
            if (adj.containsKey(i)) {
                for (Integer dst : adj.get(i)) {
                    row.set(dst, 1); // edge type 1 で接続
                }
            }
            data.add(row);
        }
        return data;
    }

    public int getNumVertices() {
        return vertices.size();
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(name + " " + vertices.size());

        String labels = vertices.stream()
                .map(Vertex::getLabel)
                .collect(Collectors.joining(" "));
        joiner.add(labels);

        List<List<Integer>> mat = getAdjacencyMatrix();
        for (int i = 0; i < vertices.size(); i++) {
            String row = mat.get(i).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(" "));
            joiner.add(row);
        }

        return joiner.toString();
    }

    public static class Vertex {
        private final String label;

        public Vertex(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
