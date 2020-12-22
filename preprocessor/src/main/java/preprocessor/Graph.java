package preprocessor;

import com.github.javaparser.Range;

import java.util.*;

public class Graph {

    private final String name;
    private final List<Vertex> vertices;
    private final List<Edge> edges;

    public Graph(String name, List<Vertex> vertices, List<Edge> edges) {
        this.name = name;
        this.vertices = vertices;
        this.edges = edges;
        for (int i = 0; i < vertices.size(); i++) {
            vertices.get(i).index = i;
        }
    }

    public int getNumVertices() {
        return vertices.size();
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public String getName() {
        return name;
    }

    public static class Vertex {
        private final String label;
        private final Range range;
        private final VertexType type;
        private int index;

        public Vertex(String label, Range range, VertexType type) {
            this.label = label;
            this.range = range;
            this.type = type;
        }

        public String getLabel() {
            return label;
        }

        public Range getRange() {
            return range;
        }

        public VertexType getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    public enum VertexType {
        SYNTAX_NODE,
        SYNTAX_TOKEN;
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

        public Vertex getSrc() {
            return src;
        }

        public Vertex getDst() {
            return dst;
        }

        public EdgeType getType() {
            return type;
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
