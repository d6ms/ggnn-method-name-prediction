package preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import org.apache.commons.lang3.tuple.Pair;
import preprocessor.Graph.Vertex;

public class FileExtractionTask implements Callable<List<Graph>> {
    private static final DataKey<Integer> vertexId = new DataKey<>() {
    };

    private final Path path;
    private final GraphExtractionConfig cfg;

    public FileExtractionTask(Path path, GraphExtractionConfig cfg) {
        this.path = path;
        this.cfg = cfg;
    }

    @Override
    public List<Graph> call() throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(path);
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> !(cfg.excludeBoilerplates && isBoilerplate(m)))
                .map(this::convertToGraph)
                .filter(g -> g.getNumVertices() <= cfg.maxVertices)
                .collect(Collectors.toList());
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

    private boolean isBoilerplate(MethodDeclaration m) {
        // TODO constructor?
        if (m.getNameAsString().equals("toString")
                || m.getNameAsString().equals("hashCode")
                || m.getNameAsString().equals("equals")) {
            return true;
        }
        if (m.getNameAsString().startsWith("get") || m.getNameAsString().startsWith("is")) {
            for (Node node : m.getChildNodes()) {
                if (node instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) node;
                    if (block.getStatements().size() == 1
                            && block.getStatements().get(0) instanceof ReturnStmt) {
                        Expression expr = ((ReturnStmt) block.getStatements().get(0)).getExpression().orElse(null);
                        if (expr instanceof FieldAccessExpr || expr instanceof NameExpr) {
                            return true;
                        }
                    }
                }
            }
        }
        if (m.getNameAsString().startsWith("set")) {
            for (Node node : m.getChildNodes()) {
                if (node instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) node;
                    if (block.getStatements().size() == 1
                            && block.getStatements().get(0) instanceof ExpressionStmt
                            && ((ExpressionStmt) block.getStatements().get(0)).getExpression() instanceof AssignExpr) {
                        AssignExpr assign = (AssignExpr) ((ExpressionStmt) block.getStatements().get(0)).getExpression();
                        if (assign.getTarget() instanceof FieldAccessExpr) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
