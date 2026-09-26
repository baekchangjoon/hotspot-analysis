package io.github.baekchangjoon.hotspotanalysis.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.github.baekchangjoon.hotspotanalysis.parser.model.MethodSignature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class CallGraphBuilder {

    /**
     * @param callGraphs           entry (controller endpoint) → reachable methods
     * @param unresolvedEndpoints  controller methods that carry a mapping annotation
     *                             but could not be resolved by the symbol solver
     *                             (typically a parameter or return type from a
     *                             dependency that is not on
     *                             {@code apiAnalysis.classpathDirectories}); they
     *                             are absent from {@code callGraphs}, so callers
     *                             must surface them instead of reporting a
     *                             silently shorter endpoint list
     * @param incompleteCallGraphs endpoints that were ranked, but whose call
     *                             graph stopped early because a classpath jar
     *                             referenced a missing class ({@link LinkageError})
     */
    public record CallGraphResult(
            Map<MethodSignature, List<MethodSignature>> callGraphs,
            List<MethodSignature> unresolvedEndpoints,
            List<MethodSignature> incompleteCallGraphs
    ) {
        public CallGraphResult {
            unresolvedEndpoints = (unresolvedEndpoints == null)
                    ? List.of() : List.copyOf(unresolvedEndpoints);
            incompleteCallGraphs = (incompleteCallGraphs == null)
                    ? List.of() : List.copyOf(incompleteCallGraphs);
        }

        public CallGraphResult(Map<MethodSignature, List<MethodSignature>> callGraphs,
                                List<MethodSignature> unresolvedEndpoints) {
            this(callGraphs, unresolvedEndpoints, List.of());
        }

        public CallGraphResult(Map<MethodSignature, List<MethodSignature>> callGraphs) {
            this(callGraphs, List.of(), List.of());
        }
    }

    public CallGraphResult buildCallGraphs(Path repoRoot, List<Path> javaFiles, List<String> classpathDirectories) {
        setupSymbolSolver(repoRoot, classpathDirectories);

        Index index = new Index();
        for (Path file : javaFiles) {
            indexFile(file, index);
        }
        for (MethodDeclaration controllerMethod : index.controllerMethods) {
            buildEntryGraph(controllerMethod, index);
        }
        return new CallGraphResult(
                index.callGraphs, distinct(index.unresolvedEndpoints), distinct(index.incompleteCallGraphs));
    }

    /** Working state shared by the indexing pass and the per-endpoint walk. */
    private static final class Index {
        final Map<String, MethodSignature> resolvedToSignature = new HashMap<>();
        final Map<String, MethodDeclaration> resolvedKeyToNode = new HashMap<>();
        final Map<String, List<String>> interfaceCallToImplKeys = new HashMap<>();
        final List<MethodDeclaration> controllerMethods = new ArrayList<>();
        final List<MethodSignature> unresolvedEndpoints = new ArrayList<>();
        final List<MethodSignature> incompleteCallGraphs = new ArrayList<>();
        final Map<MethodSignature, List<MethodSignature>> callGraphs = new HashMap<>();
    }

    private void indexFile(Path file, Index index) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface()) {
                    indexInterface(cu, decl, index);
                } else {
                    indexClass(cu, decl, index);
                }
            }
        } catch (Exception | LinkageError e) {
            // Skip unparseable files
        }
    }

    private void indexInterface(CompilationUnit cu, ClassOrInterfaceDeclaration decl, Index index) {
        for (MethodDeclaration md : decl.findAll(MethodDeclaration.class)) {
            try {
                registerMethod(cu, md, md.resolve(), index);
            } catch (Exception | LinkageError e) {
                // Skip
            }
        }
    }

    private void indexClass(CompilationUnit cu, ClassOrInterfaceDeclaration decl, Index index) {
        boolean isController = decl.isAnnotationPresent("RestController")
                || decl.isAnnotationPresent("Controller");
        List<ResolvedReferenceType> ancestors;
        try {
            ancestors = decl.resolve().getAllAncestors();
        } catch (Exception | LinkageError e) {
            // Unresolvable class: every mapped method in it is an endpoint the
            // report will lack.
            if (isController) {
                collectMappedSignatures(cu, decl, index.unresolvedEndpoints);
            }
            return;
        }
        for (MethodDeclaration md : decl.findAll(MethodDeclaration.class)) {
            indexClassMethod(cu, md, ancestors, isController, index);
        }
    }

    private void indexClassMethod(CompilationUnit cu, MethodDeclaration md,
                                  List<ResolvedReferenceType> ancestors,
                                  boolean isController, Index index) {
        boolean endpoint = isController && hasApiMapping(md);
        try {
            ResolvedMethodDeclaration resolvedM = md.resolve();
            String resolvedKey = registerMethod(cu, md, resolvedM, index);
            if (endpoint) {
                index.controllerMethods.add(md);
            }
            linkAncestors(resolvedM, resolvedKey, ancestors, index);
        } catch (Exception | LinkageError e) {
            // Unresolvable method (unknown parameter/return type). A
            // LinkageError (NoClassDefFoundError) surfaces when a classpath
            // jar references a class that is not on the classpath; it must not
            // abort the whole analysis.
            if (endpoint) {
                index.unresolvedEndpoints.add(buildMethodSignature(cu, md));
            }
        }
    }

    private String registerMethod(CompilationUnit cu, MethodDeclaration md,
                                  ResolvedMethodDeclaration resolvedM, Index index) {
        String resolvedKey = toResolvedCanonicalString(resolvedM);
        index.resolvedToSignature.put(resolvedKey, buildMethodSignature(cu, md));
        index.resolvedKeyToNode.put(resolvedKey, md);
        return resolvedKey;
    }

    /** Maps every ancestor's declaration of this method to the concrete implementation. */
    private void linkAncestors(ResolvedMethodDeclaration resolvedM, String resolvedKey,
                               List<ResolvedReferenceType> ancestors, Index index) {
        String params = getParamTypeString(resolvedM);
        for (ResolvedReferenceType ancestor : ancestors) {
            ancestor.getTypeDeclaration().ifPresent(type -> {
                String ancestorMethodKey = type.getQualifiedName() + "#" + resolvedM.getName() + "(" + params + ")";
                index.interfaceCallToImplKeys
                        .computeIfAbsent(ancestorMethodKey, k -> new ArrayList<>())
                        .add(resolvedKey);
            });
        }
    }

    private void collectMappedSignatures(CompilationUnit cu, ClassOrInterfaceDeclaration decl,
                                         List<MethodSignature> out) {
        for (MethodDeclaration md : decl.findAll(MethodDeclaration.class)) {
            if (hasApiMapping(md)) {
                out.add(buildMethodSignature(cu, md));
            }
        }
    }

    private void buildEntryGraph(MethodDeclaration controllerMethod, Index index) {
        try {
            String entryKey = toResolvedCanonicalString(controllerMethod.resolve());
            MethodSignature entrySignature = index.resolvedToSignature.get(entryKey);
            if (entrySignature == null) {
                return;
            }
            Set<String> callGraphKeys = new LinkedHashSet<>();
            boolean incomplete = traverse(entryKey, index, callGraphKeys, new HashSet<>());

            List<MethodSignature> calledSignatures = new ArrayList<>();
            for (String key : callGraphKeys) {
                MethodSignature sig = index.resolvedToSignature.get(key);
                if (sig != null && !sig.equals(entrySignature)) {
                    calledSignatures.add(sig);
                }
            }
            index.callGraphs.put(entrySignature, calledSignatures);
            if (incomplete) {
                index.incompleteCallGraphs.add(entrySignature);
            }
        } catch (Exception | LinkageError e) {
            index.unresolvedEndpoints.add(buildMethodSignature(
                    controllerMethod.findCompilationUnit().orElseThrow(), controllerMethod));
        }
    }

    private static List<MethodSignature> distinct(List<MethodSignature> items) {
        Map<String, MethodSignature> unique = new LinkedHashMap<>();
        for (MethodSignature item : items) {
            unique.putIfAbsent(item.toCanonicalString(), item);
        }
        return new ArrayList<>(unique.values());
    }

    /** @return true when a classpath jar referenced a class that is not loadable */
    private boolean traverse(String methodKey, Index index, Set<String> callGraphKeys, Set<String> visited) {
        if (!visited.add(methodKey)) {
            return false;
        }
        MethodDeclaration node = index.resolvedKeyToNode.get(methodKey);
        if (node == null) {
            return false;
        }

        boolean linkageFailure = false;
        for (MethodCallExpr mc : node.findAll(MethodCallExpr.class)) {
            try {
                String calleeKey = toResolvedCanonicalString(mc.resolve());
                for (String next : keysToFollow(calleeKey, index)) {
                    if (callGraphKeys.add(next)) {
                        linkageFailure |= traverse(next, index, callGraphKeys, visited);
                    }
                }
            } catch (LinkageError e) {
                // A missing class referenced by a classpath jar. The endpoint
                // stays ranked; the caller warns that this edge is absent.
                linkageFailure = true;
            } catch (Exception e) {
                // Skip unsolved calls (types outside the analysed sources).
            }
        }
        return linkageFailure;
    }

    /**
     * Where a resolved callee leads: the concrete implementations when the
     * callee is an interface/ancestor declaration, the callee itself when it
     * is a project method, nothing when it is outside the analysed sources.
     */
    private static List<String> keysToFollow(String calleeKey, Index index) {
        List<String> implementations = index.interfaceCallToImplKeys.get(calleeKey);
        if (implementations != null) {
            return implementations;
        }
        return index.resolvedKeyToNode.containsKey(calleeKey) ? List.of(calleeKey) : List.of();
    }

    private void setupSymbolSolver(Path repoRoot, List<String> classpathDirectories) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        if (classpathDirectories != null && !classpathDirectories.isEmpty()) {
            List<URL> urls = new ArrayList<>();
            for (String dir : classpathDirectories) {
                Path p = repoRoot.resolve(dir).toAbsolutePath().normalize();
                if (Files.exists(p)) {
                    try {
                        urls.add(p.toUri().toURL());
                        if (Files.isDirectory(p)) {
                            try (var stream = Files.walk(p)) {
                                stream.filter(Files::isRegularFile)
                                      .filter(path -> path.toString().endsWith(".jar"))
                                      .forEach(path -> {
                                          try {
                                              urls.add(path.toUri().toURL());
                                          } catch (MalformedURLException e) {
                                              // ignore
                                          }
                                      });
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
            if (!urls.isEmpty()) {
                URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
                typeSolver.add(new ClassLoaderTypeSolver(classLoader));
            }
        }

        for (Path srcRoot : findSourceRoots(repoRoot)) {
            try {
                typeSolver.add(new JavaParserTypeSolver(srcRoot.toFile()));
            } catch (Exception e) {
                // ignore
            }
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    private List<Path> findSourceRoots(Path repoRoot) {
        List<Path> sourceRoots = new ArrayList<>();
        try (var walk = Files.walk(repoRoot)) {
            walk.filter(Files::isDirectory)
                .filter(p -> p.endsWith(Path.of("src/main/java")))
                .forEach(sourceRoots::add);
        } catch (IOException e) {
            // ignore
        }
        if (sourceRoots.isEmpty()) {
            sourceRoots.add(repoRoot);
        }
        return sourceRoots;
    }

    private String toResolvedCanonicalString(ResolvedMethodDeclaration resolved) {
        String fqcn = resolved.declaringType().getQualifiedName();
        String name = resolved.getName();
        String params = getParamTypeString(resolved);
        return fqcn + "#" + name + "(" + params + ")";
    }

    private String getParamTypeString(ResolvedMethodDeclaration resolved) {
        List<String> params = new ArrayList<>();
        for (int i = 0; i < resolved.getNumberOfParams(); i++) {
            params.add(resolved.getParam(i).getType().describe());
        }
        return String.join(", ", params);
    }

    private MethodSignature buildMethodSignature(CompilationUnit cu, MethodDeclaration md) {
        String packageName = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String typeChain = resolveTypeChain(md);
        String fqcn = packageName.isEmpty() ? typeChain : packageName + "." + typeChain;
        List<String> parameterTypes = new ArrayList<>();
        md.getParameters().forEach(p -> parameterTypes.add(p.getType().asString()));
        return new MethodSignature(fqcn, md.getNameAsString(), parameterTypes);
    }

    private boolean hasApiMapping(MethodDeclaration md) {
        return md.isAnnotationPresent("GetMapping") ||
               md.isAnnotationPresent("PostMapping") ||
               md.isAnnotationPresent("PutMapping") ||
               md.isAnnotationPresent("DeleteMapping") ||
               md.isAnnotationPresent("PatchMapping") ||
               md.isAnnotationPresent("RequestMapping");
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
                anonymousCounter++;
                names.addFirst("$" + anonymousCounter);
            }
            current = current.getParentNode().orElse(null);
        }
        return String.join(".", names);
    }
}
