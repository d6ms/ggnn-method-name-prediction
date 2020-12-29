package preprocessor;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class GraphPrinter {

    public static String print(Graph graph) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(graph.getMethodName() + " " + graph.getVertices().size() + " " + graph.getEdges().size());

        String labels = graph.getVertices().stream()
                .map(Graph.Vertex::getLabel)
                .collect(Collectors.joining(" "));
        joiner.add(labels);

        for (Graph.Edge edge : graph.getEdges()) {
            joiner.add(edge.getSrc().getIndex() + " " + edge.getDst().getIndex() + " " + edge.getType().value);
            // joiner.add(edge.getSrc().getLabel() + " to " + edge.getDst().getLabel() + " as " + edge.getType());
        }

        return joiner.toString();
    }

    public static String printAdjMat(Graph graph) {
        Map<Integer, Set<Pair<Integer, Integer>>> adj = new HashMap<>();  // {src: (dst, type)}
        for (Graph.Edge edge : graph.getEdges()) {
            int src = edge.getSrc().getIndex();
            int dst = edge.getDst().getIndex();
            if (!adj.containsKey(src)) {
                adj.put(src, new HashSet<>());
            }
            adj.get(src).add(Pair.of(dst, edge.getType().value));
        }

        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(graph.getMethodName() + " " + graph.getVertices().size() + " " + graph.getEdges().size());

        String labels = graph.getVertices().stream()
                .map(Graph.Vertex::getLabel)
                .collect(Collectors.joining(" "));
        joiner.add(labels);

        List<List<Integer>> mat = new ArrayList<>();
        for (int i = 0; i < graph.getVertices().size(); i++) {
            List<Integer> row = new ArrayList<>(graph.getVertices().size());
            for (int j = 0; j < graph.getVertices().size(); j++) {
                row.add(0);
            }
            if (adj.containsKey(i)) {
                for (Pair<Integer, Integer> lnk : adj.get(i)) {
                    int dst = lnk.getLeft();
                    int type = lnk.getRight();
                    row.set(dst, type);
                }
            }
            mat.add(row);
        }

        for (int i = 0; i < graph.getVertices().size(); i++) {
            String row = mat.get(i).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(" "));
            joiner.add(row);
        }
        return joiner.toString();
    }
}
