package preprocessor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.common.collect.Sets;
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
        for (var p : n.getParameters()) {
            p.accept(this, arg);
        }
        if (!n.isStatic()) {
            recordVariableDeclaration(n, "this", n, false);
        }
        n.getBody().ifPresent(b -> b.accept(this, arg));
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

    // =====================================================

    @Override
    public void visit(BlockStmt n, Object arg) {
        handleParallelBlocks(new Runnable[]{() -> {
            for (var statement : n.getStatements()) {
                statement.accept(this, arg);
            }
        }});
    }

    @Override
    public void visit(IfStmt n, Object arg) {
        n.getCondition().accept(this, arg);
        handleParallelBlocks(new Runnable[]{
                () -> n.getThenStmt().accept(this, arg),
                () -> n.getElseStmt().ifPresent(e -> e.accept(this, arg))
        });
    }

    @Override
    public void visit(SwitchStmt n, Object arg) {
        n.getSelector().accept(this, arg);
        var runnables = new ArrayList<Runnable>();
        for (var e : n.getEntries()) {
            runnables.add(() -> {
                for (var s : e.getStatements()) {
                    s.accept(this, arg);
                }
            });
        }
        handleParallelBlocks(runnables.toArray(new Runnable[0]));
    }

    @Override
    public void visit(ForStmt n, Object arg) {
        for (var i : n.getInitialization()) {
            i.accept(this, arg);
        }
        n.getCompare().ifPresent(c -> c.accept(this, arg));
        handleParallelBlocks(new Runnable[]{() -> {
            n.getBody().accept(this, arg);
            for (var u : n.getUpdate()) {
                u.accept(this, arg);
            }
            n.getCompare().ifPresent(c -> c.accept(this, arg));
            n.getBody().accept(this, arg);
            for (var u : n.getUpdate()) {
                u.accept(this, arg);
            }
        }}, true);
    }

    @Override
    public void visit(ForEachStmt n, Object arg) {
        n.getIterable().accept(this, arg);
        handleParallelBlocks(new Runnable[]{() -> {
            recordVariableDeclaration(n, n.getVariableDeclarator().getName().getIdentifier(), n.getIterable());
            n.getBody().accept(this, arg);
            n.getIterable().accept(this, arg);
            n.getBody().accept(this, arg);
        }});
    }

    @Override
    public void visit(WhileStmt n, Object arg) {
        n.getCondition().accept(this, arg);
        handleParallelBlocks(new Runnable[]{() -> {
            n.getBody().accept(this, arg);
            n.getCondition().accept(this, arg);
            n.getBody().accept(this, arg);
        }}, true);
    }

    @Override
    public void visit(DoStmt n, Object arg) {
        n.getBody().accept(this, arg);
        n.getCondition().accept(this, arg);
        handleParallelBlocks(new Runnable[]{() -> {
            n.getBody().accept(this, arg);
            n.getCondition().accept(this, arg);
            n.getBody().accept(this, arg);
        }});
    }

    @Override
    public void visit(TryStmt n, Object arg) {
        for (var res : n.getResources()) {
            res.accept(this, arg);
        }

        var runnables = new ArrayList<Runnable>();
        runnables.add(() -> n.getTryBlock().accept(this, arg));
        for (var ctch : n.getCatchClauses()) {
            runnables.add(() -> {
                var param = ctch.getParameter();
                recordVariableDeclaration(param, param.getName().getIdentifier(), param, false);
                ctch.getBody().accept(this, arg);
            });
        }
        handleParallelBlocks(runnables.toArray(new Runnable[0]));
        n.getFinallyBlock().ifPresent(f -> f.accept(this, arg));
    }

    @Override
    public void visit(Parameter n, Object arg) {
        var name = n.getName().getIdentifier();
        varToLastWrite.peek().put(name, Sets.newHashSet(n));
        varToLastUse.peek().put(name, Sets.newHashSet(n));
    }

    @Override
    public void visit(VariableDeclarator n, Object arg) {
        // TODO int x = 3, y = 2; のケースに対応できていない
        String varName = n.getName().getIdentifier();
        Expression initializer = n.getInitializer().orElse(null);
        recordVariableDeclaration(n, varName, initializer);
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
    public void visit(UnaryExpr n, Object arg) {
        if (n.isPostfix()) {
            super.visit(n, arg);
        }
        if (n.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT
                || n.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                || n.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT
                || n.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT) {
            var vars = getAllKnownVariablesInSyntaxTree(n.getExpression());
            if (!vars.isEmpty()) {
                var writtenVar = vars.get(0);
                recordFlow(varToLastWrite.peek(), writtenVar.getKey(), writtenVar.getValue(), EdgeType.LAST_WRITE);
            }
        }
        if (n.isPrefix()) {
            super.visit(n, arg);
        }
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

    @Override
    public void visit(FieldAccessExpr n, Object arg) {
        if (n.getScope().getClass().isAssignableFrom(NameExpr.class)
                && ((NameExpr) n.getScope()).getNameAsString().equals("this")
                || n.getScope().getClass().isAssignableFrom(ThisExpr.class)) {
            n.getName().accept(this, arg);
        } else {
            n.getScope().accept(this, arg);
        }
    }

}

