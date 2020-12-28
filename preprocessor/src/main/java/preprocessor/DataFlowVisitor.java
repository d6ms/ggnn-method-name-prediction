package preprocessor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import javassist.expr.MethodCall;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static preprocessor.Graph.Vertex;
import static preprocessor.Graph.Edge;
import static preprocessor.Graph.EdgeType;

public class DataFlowVisitor extends VoidVisitorAdapter<Object> {

    private final List<Vertex> vertices;

    private Set<Edge> edges;
    private Stack<Map<String, Set<Node>>> varToLastUse;
    private Stack<Map<String, Set<Node>>> varToLastWrite;
    private Map<Integer, Vertex> node2vertex;

    public DataFlowVisitor(List<Vertex> vertices) {
        this.vertices = vertices;
        init();
    }

    private void init() {
        this.edges = new HashSet<>();
        this.varToLastUse = new Stack<>();
        this.varToLastWrite = new Stack<>();
        this.node2vertex = new HashMap<>();
        for (Vertex vertex : vertices) {
            node2vertex.put(vertex.getId(), vertex);
        }
    }

    private Vertex toV(Node node) {
        int vertexId = node.getData(MethodToGraphConverter.VERTEX_ID);
        if (node2vertex.containsKey(vertexId)) {
            return node2vertex.get(vertexId);
        } else {
            throw new IllegalArgumentException("unknown vertex id: " + vertexId);
        }
    }

    public Set<Edge> computeDataFlowEdges(MethodDeclaration n) {
        init();
        visit(n, null);
        return Collections.unmodifiableSet(edges);
    }

    @Override
    public void visit(MethodDeclaration n, Object arg) {
        varToLastUse.push(new HashMap<>());
        varToLastWrite.push(new HashMap<>());
        super.visit(n, arg);
        for (Edge edge : edges) {
//            System.out.println(edge.getSrc().getLabel()+ " at " + edge.getSrc().getRange()+ " to " + edge.getDst().getLabel() + " at " + edge.getDst().getRange()+" as " + edge.getType());
        }
//        System.out.println("end");
//        System.exit(0);
    }

    private void recordVariableDeclaration(Node declarationNode, String varName) {
        recordVariableDeclaration(declarationNode, varName, null);
    }
    private void recordVariableDeclaration(Node declarationNode, String varName, Node initializer) {
        recordVariableDeclaration(declarationNode, varName, initializer, true);
    }
    private void recordVariableDeclaration(Node declarationNode, String varName, Node initializer, boolean isComputed) {
        if (varName == null) {
            return;
        }
        varToLastUse.peek().put(varName, Sets.newHashSet(declarationNode));
        if (initializer == null) {
            varToLastWrite.peek().put(varName, new HashSet<>());
        } else {
            if (isComputed) {
                recordVariableComputation(declarationNode, initializer);
            }
            varToLastWrite.peek().put(varName, Sets.newHashSet(declarationNode));
        }
    }

    private void recordVariableComputation(Node assignedVariable, Node assignedExpression) {
        recordVariableComputation(assignedVariable, assignedExpression, Collections.emptyList());
    }
    private void recordVariableComputation(Node assignedVariable, Node assignedExpression, List<Pair<Node, String>> indexers) {
        var usedVars = getAllKnownVariablesInSyntaxTree(assignedExpression);
        usedVars.addAll(indexers);
        for (var usedVar : usedVars) {
            edges.add(new Edge(toV(assignedVariable), toV(usedVar.getKey()), EdgeType.COMPUTED_FROM));
        }
        edges.add(new Edge(toV(assignedVariable), toV(assignedExpression), EdgeType.COMPUTED_FROM));
    }

    private void recordFlow(Map<String, Set<Node>> context, Node useNode, String varName, EdgeType edgeType) {
        recordFlow(context, useNode, varName, edgeType, true);
    }
    private void recordFlow(Map<String, Set<Node>> context, Node useNode, String varName, EdgeType edgeType, boolean replaceContextInfo) {
        for (var lastUse : context.getOrDefault(varName, Collections.emptySet())) {
            edges.add(new Edge(toV(useNode), toV(lastUse), edgeType));
        }
        if (replaceContextInfo) {
            context.put(varName, Sets.newHashSet(useNode));
        }
    }

    private List<Pair<Node, String>> getAllKnownVariablesInSyntaxTree(Node node) {
        List<Pair<Node, String>> vars = new ArrayList<>();
        Stack<Node> st = new Stack<>();
        st.push(node);
        while (!st.isEmpty()) {
            Node descendant = st.pop();

            if (!descendant.getChildNodes().isEmpty()) {
                for (Node nxt : descendant.getChildNodes()) {
                    st.push(nxt);
                }
                continue;
            }

            // TODO descendant が 変数名 を示すもの以外であれば continue;
//            var descendantId = descendant as IdentifierNameSyntax;
//            if (descendantId == null) continue;
            // TODO これだとメソッド名とかも入らない？
            if (descendant.getClass().isAssignableFrom(SimpleName.class)) {
                var idName = ((SimpleName) descendant).getIdentifier();
                if (isVariableLike((SimpleName) descendant) && varToLastUse.peek().containsKey(idName)) {
                    vars.add(Pair.of(descendant, idName));
                }
            }
        }
        return vars;
    }

    private boolean isVariableLike(SimpleName name) {
        if (name.getParentNode().isEmpty()) {
            return false;
        }
        var parent = name.getParentNode().get();
        // TODO これだけ？
        return parent.getClass().isAssignableFrom(NameExpr.class);
    }

    private <K, V> Map<K, Set<V>> deepCloneTopContext(Stack<Map<K, Set<V>>> contextStack) {
        var topContext = contextStack.peek();
        var clonedContext = new HashMap<K, Set<V>>();
        for (var kv : topContext.entrySet()) {
            clonedContext.put(kv.getKey(), Sets.newHashSet(kv.getValue()));
        }
        contextStack.push(clonedContext);
        return clonedContext;
    }

    private void handleParallelBlocks(Runnable[] parallelBlockExecutors) {
        handleParallelBlocks(parallelBlockExecutors, false);
    }
    private void handleParallelBlocks(Runnable[] parallelBlockExecutors, boolean maySkip) {
        var newLastUses = new ArrayList<Map<String, Set<Node>>>();
        var newLastWrites = new ArrayList<Map<String, Set<Node>>>();
        for (var blockExecutor : parallelBlockExecutors) {
            newLastUses.add(deepCloneTopContext(varToLastUse));
            newLastWrites.add(deepCloneTopContext(varToLastWrite));

            blockExecutor.run();

            varToLastUse.pop();
            varToLastWrite.pop();
        }

        var varToLastUse2 = varToLastUse.peek();
        var varsInScope = varToLastUse2.keySet();
        var varToLastWrite2 = varToLastWrite.peek();

        if (maySkip) {
            newLastUses.add(varToLastUse2);
            newLastWrites.add(varToLastWrite2);
        }

        for (var variable : varsInScope) {
            varToLastUse2.put(variable, newLastUses.stream()
                    .flatMap(e -> e.values().stream())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
            varToLastWrite2.put(variable, newLastWrites.stream()
                    .flatMap(e -> e.values().stream())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
        }
    }

    @Override
    public void visit(VariableDeclarator n, Object arg) {
        // TODO int x = 3, y = 2; のケース？
        String varName = n.getName().getIdentifier();
        Expression initializer = n.getInitializer().orElse(null);
        recordVariableDeclaration(n, varName, initializer);  // TODO edge の始点は VariableDeclarator ではなくない？
    }

    @Override
    public void visit(AssignExpr n, Object arg) {
        n.getValue().accept(this, arg);
        n.getTarget().accept(this, arg);

        var vars = getAllKnownVariablesInSyntaxTree(n.getTarget());
        if (vars.isEmpty()) {
            return;
        }

        var writtenVar = vars.get(0);
        var indexers = vars.subList(1, vars.size());

        recordVariableComputation(writtenVar.getKey(), n.getValue(), indexers);

        recordFlow(varToLastWrite.peek(), writtenVar.getKey(), writtenVar.getValue(), EdgeType.LAST_WRITE);
        varToLastUse.peek().put(writtenVar.getValue(), Sets.newHashSet(writtenVar.getKey()));
    }

    @Override
    public void visit(WhileStmt n, Object arg) {
        n.getCondition().accept(this, arg);
        handleParallelBlocks(new Runnable[] { () -> {
            n.getBody().accept(this, arg);
            n.getCondition().accept(this, arg);
            n.getBody().accept(this, arg);
        }}, true);
    }

    @Override
    public void visit(SimpleName node, Object arg) {
        if (isVariableLike(node)) {
            var usedId = node.getIdentifier();
            if (varToLastUse.peek().containsKey(usedId)) {
                recordFlow(varToLastUse.peek(), node, usedId, EdgeType.LAST_USE);
                recordFlow(varToLastWrite.peek(), node, usedId, EdgeType.LAST_WRITE, false);
            }
        }
    }
}

