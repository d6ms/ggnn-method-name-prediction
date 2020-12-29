package preprocessor;

import com.github.javaparser.Range;

import java.util.*;

public class Graph {

    private final String packageName;
    private final String methodName;
    private final List<Vertex> vertices;
    private final List<Edge> edges;

    public Graph(String packageName, String methodName, List<Vertex> vertices, List<Edge> edges) {
        this.packageName = packageName;
        this.methodName = methodName;
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

    public String getMethodName() {
        return methodName;
    }

    public String getPackageName() {
        return packageName;
    }

    public static class Vertex {
        private final int id;
        private final String label;
        private final Range range;
        private final VertexType type;
        private int index;

        public Vertex(int id, String label, Range range, VertexType type) {
            this.id = id;
            this.label = label;
            this.range = range;
            this.type = type;
        }

        public int getId() {
            return id;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Vertex vertex = (Vertex) o;
            return id == vertex.id &&
                    index == vertex.index &&
                    Objects.equals(label, vertex.label) &&
                    Objects.equals(range, vertex.range) &&
                    type == vertex.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, label, range, type, index);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return Objects.equals(src, edge.src) &&
                    Objects.equals(dst, edge.dst) &&
                    type == edge.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(src, dst, type);
        }
    }

    public enum EdgeType {
        CHILD(1),
        NEXT_TOKEN(2),
        LAST_USE(3),
        LAST_WRITE(4),
        COMPUTED_FROM(5);

        int value;

        EdgeType(int value) {
            this.value = value;
        }

    }
}
