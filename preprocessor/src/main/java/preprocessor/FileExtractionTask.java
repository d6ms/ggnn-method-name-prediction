package preprocessor;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
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
                .map(MethodToGraphConverter::convert)
                .filter(g -> g.getNumVertices() <= cfg.maxVertices)
                .collect(Collectors.toList());
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
