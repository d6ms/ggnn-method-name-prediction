package preprocessor;

import com.github.javaparser.JavaToken;
import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static preprocessor.Graph.Vertex;
import static preprocessor.Graph.VertexType;
import static preprocessor.Graph.Edge;

public class MethodToGraphConverter {

    public static Graph convert(MethodDeclaration method) {
        String name = NodeLabelUtil.splitToSubtokens(method.getNameAsString());
        method.getName().setParentNode(null); // method name prediction なので、メソッド名はデータから除く

        Pair<List<Vertex>, List<Edge>> elm = extractASTElements(method);
        List<Vertex> vertices = elm.getLeft();
        List<Edge> edges = elm.getRight();

        method.getBody().ifPresent(methodBody -> {
            addRawTokenElements(methodBody, vertices, edges);
        });

        vertices.sort(Comparator.comparing(
                v -> v.getRange() == null ? null : v.getRange().begin,
                Comparator.nullsLast(Comparator.naturalOrder())));  // TODO thenComparing ASTルートからDFS順 今のところ naturalOrder で達成されている
        return new Graph(name, vertices, edges);
    }

    private static Pair<List<Vertex>, List<Edge>> extractASTElements(MethodDeclaration method) {
        List<Vertex> vertices = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // (対象ノード, 親ノードの Vertex)
        Stack<Pair<Node, Vertex>> st = new Stack<>();
        st.add(Pair.of(method, null));
        while (!st.isEmpty()) {
            Pair<Node, Vertex> e = st.pop();
            Node node = e.getKey();
            Vertex parent = e.getValue();

            // 現在の対象ノードを Vertex オブジェクトに変換
            String label;
            Range range = null;
            VertexType type = null;
            if (node.getChildNodes().isEmpty()) {
                label = NodeLabelUtil.splitToSubtokens(node.toString());
                TokenRange tr = node.getTokenRange().get();
                assert tr.getBegin().equals(tr.getEnd());
                range = tr.getBegin().getRange().orElse(null);
                type = VertexType.SYNTAX_TOKEN;
            } else {
                label = NodeLabelUtil.shortenNodeName(node.getClass().getSimpleName());
                range = node.getTokenRange().flatMap(TokenRange::toRange).orElse(null);
                type = VertexType.SYNTAX_NODE;
            }
            Vertex vertex = new Vertex(label, range, type);
            vertices.add(vertex);

            // AST の親子関係に辺を張る
            if (parent != null) {
                edges.add(new Edge(parent, vertex, Graph.EdgeType.CHILD));
            }

            // DFS 次ステップ
            for (Node nxt : node.getChildNodes()) {
                st.add(Pair.of(nxt, vertex));
            }
        }

        return Pair.of(vertices, edges);
    }

    private static void addRawTokenElements(BlockStmt methodBody, List<Vertex> vertices, List<Edge> edges) {
        // AST に含まれないトークンを抽出
        List<JavaToken> rawTokens = new ArrayList<>();
        methodBody.getTokenRange().ifPresent(tr -> {
            for (JavaToken token : tr) {
                if (token.getText().strip().equals("")
                        || token.getText().strip().startsWith("//")
                        || token.getText().strip().startsWith("/*")) {
                    token.deleteToken();
                } else if (token.getRange().isPresent()) {
                    rawTokens.add(token);
                }
            }
        });

        // AST に含まれているトークンを示す Vertex オブジェクトを抽出
        Map<Range, Vertex> astTokens = vertices.stream()
                .filter(v -> v.getType() == VertexType.SYNTAX_TOKEN)
                .collect(Collectors.toMap(Vertex::getRange, v -> v));

        // 全トークンの Vertex オブジェクトを作り、ソースコード中の出現順に並べる
        List<Vertex> tokenSequence = new ArrayList<>();
        List<Vertex> addedVertices = new ArrayList<>();
        for (JavaToken rawToken : rawTokens) {
            Range range = rawToken.getRange().get();
            Vertex vertex;
            if (astTokens.get(range) == null) {
                vertex = new Vertex(rawToken.getText(), rawToken.getRange().orElse(null), VertexType.SYNTAX_TOKEN);
                vertices.add(vertex);
                addedVertices.add(vertex);
            } else {
                vertex = astTokens.get(range);
            }
            tokenSequence.add(vertex);
        }

        // NextToken の辺を張る
        for (int i = 0; i < tokenSequence.size() - 1; i++) {
            Vertex src = tokenSequence.get(i);
            Vertex dst = tokenSequence.get(i + 1);
            edges.add(new Edge(src, dst, Graph.EdgeType.NEXT_TOKEN));
        }

        // Child の辺を張る
        for (Vertex addedVertex : addedVertices) {
            // parent = addedVertex の出現位置を含む最小範囲の syntax_node
            // TODO この実装は O(|V|^2) なので高速化の余地あり
            Vertex parent = null;
            for (Vertex vertex : vertices) {
                if (vertex.getType() == VertexType.SYNTAX_TOKEN || vertex.getRange() == null || vertex.equals(parent)) {
                    continue;
                }
                if (vertex.getRange().contains(addedVertex.getRange())) {
                    if (parent == null || parent.getRange().contains(vertex.getRange())) {
                        parent = vertex;
                    }
                }
            }
            if (parent != null) {
                edges.add(new Edge(parent, addedVertex, Graph.EdgeType.CHILD));
            }
        }
    }
}
