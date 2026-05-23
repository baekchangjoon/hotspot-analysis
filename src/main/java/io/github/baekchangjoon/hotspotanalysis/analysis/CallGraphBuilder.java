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

    public record CallGraphResult(
            Map<MethodSignature, List<MethodSignature>> callGraphs
    ) {}

    public CallGraphResult buildCallGraphs(Path repoRoot, List<Path> javaFiles, List<String> classpathDirectories) {
        setupSymbolSolver(repoRoot, classpathDirectories);

        List<CompilationUnit> cus = new ArrayList<>();
        Map<String, MethodSignature> resolvedToSignature = new HashMap<>();
        Map<String, MethodDeclaration> resolvedKeyToNode = new HashMap<>();
        Map<String, List<String>> interfaceCallToImplKeys = new HashMap<>();
        List<MethodDeclaration> controllerMethods = new ArrayList<>();

        for (Path file : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                cus.add(cu);

                for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    boolean isController = decl.isAnnotationPresent("RestController") ||
                                           decl.isAnnotationPresent("Controller");

                    if (!decl.isInterface()) {
                        try {
                            ResolvedReferenceTypeDeclaration resolvedClass = decl.resolve();
                            List<ResolvedReferenceType> ancestors = resolvedClass.getAllAncestors();

                            for (MethodDeclaration md : decl.findAll(MethodDeclaration.class)) {
                                try {
                                    ResolvedMethodDeclaration resolvedM = md.resolve();
                                    String resolvedKey = toResolvedCanonicalString(resolvedM);

                                    MethodSignature signature = buildMethodSignature(cu, md);
                                    resolvedToSignature.put(resolvedKey, signature);
                                    resolvedKeyToNode.put(resolvedKey, md);

                                    if (isController && hasApiMapping(md)) {
                                        controllerMethods.add(md);
                                    }

                                    String params = getParamTypeString(resolvedM);
                                    for (ResolvedReferenceType ancestor : ancestors) {
                                        if (ancestor.getTypeDeclaration().isPresent()) {
                                            String ancestorFqcn = ancestor.getTypeDeclaration().get().getQualifiedName();
                                            String ancestorMethodKey = ancestorFqcn + "#" + resolvedM.getName() + "(" + params + ")";
                                            interfaceCallToImplKeys.computeIfAbsent(ancestorMethodKey, k -> new ArrayList<>()).add(resolvedKey);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Skip unresolved method
                                }
                            }
                        } catch (Exception e) {
                            // Skip unresolved class
                        }
                    } else {
                        for (MethodDeclaration md : decl.findAll(MethodDeclaration.class)) {
                            try {
                                ResolvedMethodDeclaration resolvedM = md.resolve();
                                String resolvedKey = toResolvedCanonicalString(resolvedM);
                                MethodSignature signature = buildMethodSignature(cu, md);
                                resolvedToSignature.put(resolvedKey, signature);
                                resolvedKeyToNode.put(resolvedKey, md);
                            } catch (Exception e) {
                                // Skip
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Skip unparseable files
            }
        }

        Map<MethodSignature, List<MethodSignature>> callGraphs = new HashMap<>();

        for (MethodDeclaration controllerMethod : controllerMethods) {
            try {
                ResolvedMethodDeclaration resolvedM = controllerMethod.resolve();
                String entryKey = toResolvedCanonicalString(resolvedM);
                MethodSignature entrySignature = resolvedToSignature.get(entryKey);
                if (entrySignature == null) {
                    continue;
                }

                Set<String> callGraphKeys = new LinkedHashSet<>();
                Set<String> visited = new HashSet<>();

                traverse(entryKey, resolvedKeyToNode, interfaceCallToImplKeys, callGraphKeys, visited);

                List<MethodSignature> calledSignatures = new ArrayList<>();
                for (String key : callGraphKeys) {
                    MethodSignature sig = resolvedToSignature.get(key);
                    if (sig != null && !sig.equals(entrySignature)) {
                        calledSignatures.add(sig);
                    }
                }
                callGraphs.put(entrySignature, calledSignatures);
            } catch (Exception e) {
                // Skip
            }
        }

        return new CallGraphResult(callGraphs);
    }

    private void traverse(String methodKey,
                          Map<String, MethodDeclaration> resolvedKeyToNode,
                          Map<String, List<String>> interfaceCallToImplKeys,
                          Set<String> callGraphKeys,
                          Set<String> visited) {
        if (visited.contains(methodKey)) {
            return;
        }
        visited.add(methodKey);

        MethodDeclaration node = resolvedKeyToNode.get(methodKey);
        if (node == null) {
            return;
        }

        for (MethodCallExpr mc : node.findAll(MethodCallExpr.class)) {
            try {
                ResolvedMethodDeclaration resolvedCall = mc.resolve();
                String resolvedCallKey = toResolvedCanonicalString(resolvedCall);

                boolean inScope = resolvedKeyToNode.containsKey(resolvedCallKey) || interfaceCallToImplKeys.containsKey(resolvedCallKey);

                if (inScope) {
                    if (interfaceCallToImplKeys.containsKey(resolvedCallKey)) {
                        for (String implKey : interfaceCallToImplKeys.get(resolvedCallKey)) {
                            if (!callGraphKeys.contains(implKey)) {
                                callGraphKeys.add(implKey);
                                traverse(implKey, resolvedKeyToNode, interfaceCallToImplKeys, callGraphKeys, visited);
                            }
                        }
                    } else {
                        if (!callGraphKeys.contains(resolvedCallKey)) {
                            callGraphKeys.add(resolvedCallKey);
                            traverse(resolvedCallKey, resolvedKeyToNode, interfaceCallToImplKeys, callGraphKeys, visited);
                        }
                    }
                }
            } catch (Exception e) {
                // Skip unsolved calls
            }
        }
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
