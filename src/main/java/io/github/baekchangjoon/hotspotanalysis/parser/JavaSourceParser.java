package io.github.baekchangjoon.hotspotanalysis.parser;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodInfo;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import io.github.baekchangjoon.hotspotanalysis.parser.model.ParameterInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Extracts method declarations from a Java source file using JavaParser.
 *
 * <p>Phase 1 scope:
 * <ul>
 *   <li>Only {@code MethodDeclaration} nodes are returned. Constructors and
 *       record compact constructors are ignored.</li>
 *   <li>Each method's enclosing-type chain (outer + inner + record/enum)
 *       is composed dot-separated into the FQCN.</li>
 *   <li>Anonymous-class methods are reported under their nearest named
 *       enclosing type with a "$" suffix (e.g. {@code Foo$1}).</li>
 *   <li>Parameter types are recorded as JavaParser renders them; full
 *       symbol resolution is deferred to a later phase.</li>
 * </ul>
 */
@Component
public class JavaSourceParser {

    static {
        StaticJavaParser
                .getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public List<MethodInfo> parse(Path javaFile) {
        try (Reader reader = Files.newBufferedReader(javaFile)) {
            CompilationUnit cu = StaticJavaParser.parse(reader);
            return extractMethods(cu);
        } catch (IOException e) {
            throw new SourceParseException("Failed to read source file: " + javaFile, e);
        } catch (ParseProblemException e) {
            throw new SourceParseException("Failed to parse source file: " + javaFile, e);
        }
    }

    public List<MethodInfo> parse(String source) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(source);
            return extractMethods(cu);
        } catch (ParseProblemException e) {
            throw new SourceParseException("Failed to parse source string", e);
        }
    }

    private List<MethodInfo> extractMethods(CompilationUnit cu) {
        List<MethodInfo> result = new ArrayList<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            md.getRange().ifPresent(range -> result.add(toMethodInfo(cu, md, range)));
        }
        return List.copyOf(result);
    }

    private MethodInfo toMethodInfo(CompilationUnit cu, MethodDeclaration md, Range range) {
        String packageName = cu.getPackageDeclaration()
                .map(p -> p.getNameAsString())
                .orElse("");
        String typeChain = resolveTypeChain(md);
        String fqcn = packageName.isEmpty() ? typeChain : packageName + "." + typeChain;

        List<ParameterInfo> parameters = new ArrayList<>();
        List<String> parameterTypes = new ArrayList<>();
        md.getParameters().forEach(p -> {
            String typeAsString = p.getType().asString();
            parameters.add(new ParameterInfo(p.getNameAsString(), typeAsString));
            parameterTypes.add(typeAsString);
        });

        MethodSignature signature = new MethodSignature(
                fqcn, md.getNameAsString(), parameterTypes);
        return new MethodInfo(signature, range.begin.line, range.end.line, parameters);
    }

    private String resolveTypeChain(MethodDeclaration md) {
        LinkedList<String> names = new LinkedList<>();
        Node current = md.getParentNode().orElse(null);
        int anonymousCounter = 0;
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration c) {
                names.addFirst(c.getNameAsString());
            } else if (current instanceof RecordDeclaration r) {
                names.addFirst(r.getNameAsString());
            } else if (current instanceof EnumDeclaration e) {
                names.addFirst(e.getNameAsString());
            } else if (current instanceof AnnotationDeclaration a) {
                names.addFirst(a.getNameAsString());
            } else if (current instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) {
                // Anonymous inner class — synthesise a positional marker.
                anonymousCounter++;
                names.addFirst("$" + anonymousCounter);
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join(".", names);
    }
}
