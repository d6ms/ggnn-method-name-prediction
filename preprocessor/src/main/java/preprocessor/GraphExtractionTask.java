package preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import preprocessor.Graph.Vertex;

public class GraphExtractionTask implements Runnable {
    private static final DataKey<Integer> vertexId = new DataKey<>() {
    };

    private final JavaParser parser;
    private final Path path;

    public GraphExtractionTask(Path path) {
        this.parser = new JavaParser();
        this.path = path;
    }

    @Override
    public void run() {
        try {
            parser.parse(path).getResult().ifPresent(cu -> {
                List<Graph> graphs = cu.findAll(MethodDeclaration.class).stream()
                        .map(this::convertToGraph)
                        .collect(Collectors.toList());
                if (!graphs.isEmpty()) {
                    // TODO write file
                    System.out.println(graphs.get(0));
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Graph convertToGraph(MethodDeclaration method) {
        String name = NodeLabelUtil.splitToSubtokens(method.getNameAsString());
        method.getName().setParentNode(null); // method name prediction なので、メソッド名はデータから除く

        List<Vertex> vertices = new ArrayList<>();
        List<Pair<Integer, Integer>> edges = new ArrayList<>();

        Stack<Node> st = new Stack<>();
        st.add(method);
        while (!st.isEmpty()) {
            Node node = st.pop();

            node.setData(vertexId, vertices.size());
            String label;
            if (node.getChildNodes().isEmpty()) {
                label = NodeLabelUtil.splitToSubtokens(node.toString());
            } else {
                label = NodeLabelUtil.shortenNodeName(node.getClass().getSimpleName());
            }
            Vertex vertex = new Vertex(label);
            vertices.add(vertex);
            if (node.getData(vertexId) > 0) {
                int src = node.getParentNode().get().getData(vertexId);
                int dst = node.getData(vertexId);
                edges.add(Pair.of(src, dst));
            }

            for (Node nxt : node.getChildNodes()) {
                st.add(nxt);
            }
        }

        return new Graph(name, vertices, edges);
    }
}
