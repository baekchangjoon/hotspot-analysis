package io.github.baekchangjoon.hotspotanalysis.parser;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.expr.*;

/**
 * Calculates Cognitive Complexity for a given {@link MethodDeclaration} 
 * based on SonarQube specification.
 */
public class CognitiveComplexityCalculator {

    public int calculate(MethodDeclaration method) {
        if (method == null || !method.getBody().isPresent()) {
            return 0;
        }
        return calculateNodeComplexity(method.getBody().get(), 0);
    }

    private int calculateNodeComplexity(Node node, int nestingLevel) {
        int complexity = 0;

        if (node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt ||
            node instanceof WhileStmt || node instanceof DoStmt || node instanceof CatchClause) {
            complexity += 1 + nestingLevel;

            int childNesting = nestingLevel + 1;
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, childNesting);
            }
        } else if (node instanceof SwitchStmt || node instanceof ConditionalExpr) {
            complexity += 1;

            int childNesting = nestingLevel + 1;
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, childNesting);
            }
        } else if (node instanceof BreakStmt b && b.getLabel().isPresent()) {
            complexity += 1;
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, nestingLevel);
            }
        } else if (node instanceof ContinueStmt c && c.getLabel().isPresent()) {
            complexity += 1;
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, nestingLevel);
            }
        } else if (node instanceof BinaryExpr binaryExpr) {
            BinaryExpr.Operator op = binaryExpr.getOperator();
            if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR) {
                complexity += 1;
            }
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, nestingLevel);
            }
        } else {
            for (Node child : node.getChildNodes()) {
                complexity += calculateNodeComplexity(child, nestingLevel);
            }
        }

        return complexity;
    }
}
