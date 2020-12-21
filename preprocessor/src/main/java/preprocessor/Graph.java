package preprocessor;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Range;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Graph {

    private final String name;
    private final List<Vertex> vertices;
    private final Map<Integer, Set<Pair<Integer, Integer>>> adj = new HashMap<>();  // {src: (dst, type)}

    public Graph(String name, List<Vertex> vertices, List<Edge> edges) {
        this.name = name;
        this.vertices = vertices;
        for (int i = 0; i < vertices.size(); i++) {
            vertices.get(i).index = i;
        }
        for (Edge edge : edges) {
            addEdge(edge);
        }
    }

    public void addEdge(Edge edge) {
        int src = edge.src.index;
        int dst = edge.dst.index;
        if (!adj.containsKey(src)) {
            adj.put(src, new HashSet<>());
        }
        adj.get(src).add(Pair.of(dst, edge.type.value));
    }

    public List<List<Integer>> getAdjacencyMatrix() {
        List<List<Integer>> data = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            List<Integer> row = new ArrayList<>(vertices.size());
            for (int j = 0; j < vertices.size(); j++) {
                row.add(0);
            }
            if (adj.containsKey(i)) {
                for (Pair<Integer, Integer> lnk : adj.get(i)) {
                    int dst = lnk.getLeft();
                    int type = lnk.getRight();
                    row.set(dst, type);
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
        private final Range range;  // ソースコード中の出現位置, AST 要素の場合は null
        private int index;

        public Vertex(String label, Range range) {
            this.label = label;
            this.range = range;
        }

        public String getLabel() {
            return label;
        }

        public Range getRange() {
            return range;
        }
    }

    public static class Edge {
        private final Vertex src;
        private final Vertex dst;
        private final EdgeType type;
        public Edge(Vertex src, Vertex dst, EdgeType type) {
            this.src = src;
            this.dst = dst;
            this.type = type;
        }
    }

    public enum EdgeType {
        CHILD(1),
        NEXT_TOKEN(2);

        int value;

        EdgeType(int value) {
            this.value = value;
        }

    }
}
