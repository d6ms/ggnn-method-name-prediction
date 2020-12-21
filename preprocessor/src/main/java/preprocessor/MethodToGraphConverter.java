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
            JavaToken token = null;
            if (node.getChildNodes().isEmpty()) {
                label = NodeLabelUtil.splitToSubtokens(node.toString());
                TokenRange range = node.getTokenRange().get();
                assert range.getBegin().equals(range.getEnd());
                token = range.getBegin();
            } else {
                label = NodeLabelUtil.shortenNodeName(node.getClass().getSimpleName());
            }
            Range range = token != null ? token.getRange().orElse(null) : null;
            Vertex vertex = new Vertex(label, range);
            vertices.add(vertex);
            
            // AST の親子関係に辺を張る
            // TODO MethodCall の . みたいなトークンにも Child の辺を張る
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
                if (token.getText().strip().equals("")) {
                    token.deleteToken();
                } else if (token.getRange().isPresent()) {
                    rawTokens.add(token);
                }
            }
        });

        // AST に含まれているトークンを示す Vertex オブジェクトを抽出
        Map<Range, Vertex> astTokens = vertices.stream()
                .filter(v -> v.getRange() != null)
                .collect(Collectors.toMap(Vertex::getRange, v -> v));

        // 全トークンの Vertex オブジェクトを作り、ソースコード中の出現順に並べる
        List<Vertex> tokenSequence = new ArrayList<>();
        for (JavaToken rawToken : rawTokens) {
            Range range = rawToken.getRange().get();
            Vertex vertex;
            if (astTokens.get(range) == null) {
                vertex = new Vertex(rawToken.getText(), rawToken.getRange().orElse(null));
                vertices.add(vertex);
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
    }
}
