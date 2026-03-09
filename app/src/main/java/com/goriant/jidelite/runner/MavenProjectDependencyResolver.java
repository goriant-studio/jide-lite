package com.goriant.jidelite.runner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class MavenProjectDependencyResolver implements ProjectDependencyResolver {

    private enum ResolutionMode {
        COMPILE,
        RUNTIME
    }

    private static final class ResolutionNode {
        private final MavenCoordinate coordinate;
        private final List<String> repositories;
        private final ResolutionMode mode;

        private ResolutionNode(MavenCoordinate coordinate, List<String> repositories, ResolutionMode mode) {
            this.coordinate = coordinate;
            this.repositories = repositories;
            this.mode = mode;
        }
    }

    private final MavenPomParser pomParser;
    private final MavenRepositoryClient repositoryClient;

    MavenProjectDependencyResolver(MavenPomParser pomParser, MavenRepositoryClient repositoryClient) {
        this.pomParser = pomParser;
        this.repositoryClient = repositoryClient;
    }

    @Override
    public DependencyResolutionResult resolve(File workspaceDirectory) throws IOException {
        File pomFile = new File(workspaceDirectory, "pom.xml");
        if (!pomFile.exists()) {
            return DependencyResolutionResult.empty();
        }

        PomModel projectPom = pomParser.parse(pomFile);
        List<String> rootRepositories = new ArrayList<>(projectPom.getRepositories());

        LinkedHashMap<String, File> compileArtifacts = new LinkedHashMap<>();
        LinkedHashMap<String, File> runtimeArtifacts = new LinkedHashMap<>();
        Queue<ResolutionNode> pending = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();

        enqueueRootDependencies(projectPom.getDependencies(), rootRepositories, pending);

        while (!pending.isEmpty()) {
            ResolutionNode node = pending.remove();
            String visitKey = node.mode.name() + ":" + node.coordinate.asKey();
            if (!visited.add(visitKey)) {
                continue;
            }

            MavenCoordinate coordinate = node.coordinate;
            File jarFile = repositoryClient.fetchJar(coordinate, node.repositories);
            runtimeArtifacts.put(coordinate.asKey(), jarFile);
            if (node.mode == ResolutionMode.COMPILE) {
                compileArtifacts.put(coordinate.asKey(), jarFile);
            }

            File dependencyPom = repositoryClient.fetchPom(coordinate, node.repositories);
            PomModel dependencyModel = pomParser.parse(dependencyPom);
            List<String> repositories = mergeRepositories(node.repositories, dependencyModel.getRepositories());
            enqueueTransitiveDependencies(dependencyModel.getDependencies(), repositories, node.mode, pending);
        }

        return new DependencyResolutionResult(
                new ArrayList<>(compileArtifacts.values()),
                new ArrayList<>(runtimeArtifacts.values())
        );
    }

    private void enqueueRootDependencies(
            List<PomDependency> dependencies,
            List<String> repositories,
            Queue<ResolutionNode> pending
    ) {
        for (PomDependency dependency : dependencies) {
            if (isCompileDependency(dependency)) {
                pending.add(new ResolutionNode(dependency.getCoordinate(), repositories, ResolutionMode.COMPILE));
            }
            if (isRuntimeDependency(dependency)) {
                pending.add(new ResolutionNode(dependency.getCoordinate(), repositories, ResolutionMode.RUNTIME));
            }
        }
    }

    private void enqueueTransitiveDependencies(
            List<PomDependency> dependencies,
            List<String> repositories,
            ResolutionMode parentMode,
            Queue<ResolutionNode> pending
    ) {
        for (PomDependency dependency : dependencies) {
            if (parentMode == ResolutionMode.COMPILE && isCompileDependency(dependency)) {
                pending.add(new ResolutionNode(dependency.getCoordinate(), repositories, ResolutionMode.COMPILE));
            }
            if (isRuntimeDependency(dependency)) {
                pending.add(new ResolutionNode(dependency.getCoordinate(), repositories, ResolutionMode.RUNTIME));
            }
        }
    }

    private List<String> mergeRepositories(List<String> baseRepositories, List<String> dependencyRepositories) {
        Set<String> merged = new LinkedHashSet<>(baseRepositories);
        merged.addAll(dependencyRepositories);
        return new ArrayList<>(merged);
    }

    private boolean isCompileDependency(PomDependency dependency) {
        if (dependency.isOptional()) {
            return false;
        }

        String scope = dependency.getScope();
        return scope.isEmpty() || "compile".equals(scope);
    }

    private boolean isRuntimeDependency(PomDependency dependency) {
        if (dependency.isOptional()) {
            return false;
        }

        String scope = dependency.getScope();
        return scope.isEmpty() || "compile".equals(scope) || "runtime".equals(scope);
    }
}
