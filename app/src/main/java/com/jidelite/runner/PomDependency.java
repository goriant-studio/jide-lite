package com.jidelite.runner;

final class PomDependency {

    private final MavenCoordinate coordinate;
    private final String scope;
    private final boolean optional;

    PomDependency(MavenCoordinate coordinate, String scope, boolean optional) {
        this.coordinate = coordinate;
        this.scope = scope == null ? "" : scope.trim();
        this.optional = optional;
    }

    MavenCoordinate getCoordinate() {
        return coordinate;
    }

    String getScope() {
        return scope;
    }

    boolean isOptional() {
        return optional;
    }
}
