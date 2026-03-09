package com.goriant.jidelite.runner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

final class MavenPomParser {

    PomModel parse(File pomFile) throws IOException {
        Document document = parseDocument(pomFile);
        Element project = document.getDocumentElement();
        if (project == null) {
            throw new IOException("Invalid pom.xml: missing <project> root.");
        }

        Element parent = child(project, "parent");
        String parentGroupId = text(parent, "groupId");
        String parentVersion = text(parent, "version");

        String groupId = firstNonBlank(text(project, "groupId"), parentGroupId);
        String artifactId = text(project, "artifactId");
        String version = firstNonBlank(text(project, "version"), parentVersion);
        String packaging = firstNonBlank(text(project, "packaging"), "jar");

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("project.groupId", nullToEmpty(groupId));
        properties.put("project.artifactId", nullToEmpty(artifactId));
        properties.put("project.version", nullToEmpty(version));
        properties.put("pom.groupId", nullToEmpty(groupId));
        properties.put("pom.artifactId", nullToEmpty(artifactId));
        properties.put("pom.version", nullToEmpty(version));

        Element propertiesNode = child(project, "properties");
        if (propertiesNode != null) {
            NodeList children = propertiesNode.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    properties.put(child.getNodeName(), child.getTextContent().trim());
                }
            }
        }

        groupId = resolvePlaceholders(groupId, properties);
        artifactId = resolvePlaceholders(artifactId, properties);
        version = resolvePlaceholders(version, properties);
        packaging = resolvePlaceholders(packaging, properties);

        properties.put("project.groupId", nullToEmpty(groupId));
        properties.put("project.artifactId", nullToEmpty(artifactId));
        properties.put("project.version", nullToEmpty(version));
        properties.put("pom.groupId", nullToEmpty(groupId));
        properties.put("pom.artifactId", nullToEmpty(artifactId));
        properties.put("pom.version", nullToEmpty(version));

        List<String> repositories = parseRepositories(project, properties);
        List<PomDependency> dependencies = parseDependencies(project, properties);

        if (isBlank(groupId) || isBlank(artifactId) || isBlank(version)) {
            throw new IOException("Unsupported pom.xml: missing groupId, artifactId, or version.");
        }

        return new PomModel(groupId, artifactId, version, packaging, repositories, dependencies);
    }

    String resolvePlaceholders(String value, Map<String, String> properties) throws IOException {
        if (value == null) {
            return null;
        }

        String resolved = value;
        for (int depth = 0; depth < 10; depth++) {
            PlaceholderResolution resolution = replacePlaceholdersOnce(resolved, properties);
            resolved = resolution.value;
            if (!resolution.changed) {
                return resolved.trim();
            }
        }

        throw new IOException("Could not fully resolve property expression: " + value);
    }

    private PlaceholderResolution replacePlaceholdersOnce(String value, Map<String, String> properties) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean changed = false;
        int index = 0;
        while (index < value.length()) {
            int start = value.indexOf("${", index);
            if (start < 0) {
                builder.append(value, index, value.length());
                break;
            }

            builder.append(value, index, start);
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                builder.append(value, start, value.length());
                break;
            }

            String propertyName = value.substring(start + 2, end);
            String replacement = properties.get(propertyName);
            if (replacement == null) {
                builder.append(value, start, end + 1);
            } else {
                builder.append(replacement);
                changed = true;
            }
            index = end + 1;
        }
        return new PlaceholderResolution(builder.toString(), changed);
    }

    private List<String> parseRepositories(Element project, Map<String, String> properties) throws IOException {
        List<String> repositories = new ArrayList<>();
        Element repositoriesNode = child(project, "repositories");
        if (repositoriesNode == null) {
            return repositories;
        }

        NodeList children = repositoriesNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"repository".equals(child.getNodeName())) {
                continue;
            }
            String url = resolvePlaceholders(text((Element) child, "url"), properties);
            if (!isBlank(url)) {
                repositories.add(url);
            }
        }
        return repositories;
    }

    private List<PomDependency> parseDependencies(Element project, Map<String, String> properties) throws IOException {
        List<PomDependency> dependencies = new ArrayList<>();
        Element dependenciesNode = child(project, "dependencies");
        if (dependenciesNode == null) {
            return dependencies;
        }

        NodeList children = dependenciesNode.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"dependency".equals(child.getNodeName())) {
                continue;
            }

            Element dependencyNode = (Element) child;
            String groupId = resolvePlaceholders(text(dependencyNode, "groupId"), properties);
            String artifactId = resolvePlaceholders(text(dependencyNode, "artifactId"), properties);
            String version = resolvePlaceholders(text(dependencyNode, "version"), properties);
            String type = resolvePlaceholders(firstNonBlank(text(dependencyNode, "type"), "jar"), properties);
            String scope = resolvePlaceholders(text(dependencyNode, "scope"), properties);
            boolean optional = Boolean.parseBoolean(resolvePlaceholders(text(dependencyNode, "optional"), properties));

            if (shouldSkipDependency(scope, optional)) {
                continue;
            }

            if (isBlank(groupId) || isBlank(artifactId) || isBlank(version)) {
                throw new IOException("Unsupported pom.xml dependency: missing groupId, artifactId, or version.");
            }

            dependencies.add(
                    new PomDependency(
                            new MavenCoordinate(groupId, artifactId, version, type),
                            scope,
                            optional
                    )
            );
        }
        return dependencies;
    }

    private boolean shouldSkipDependency(String scope, boolean optional) {
        if (optional) {
            return true;
        }

        if (isBlank(scope)) {
            return false;
        }

        return "test".equals(scope)
                || "provided".equals(scope)
                || "system".equals(scope)
                || "import".equals(scope);
    }

    private Document parseDocument(File pomFile) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(pomFile);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IOException("Could not parse pom.xml: " + pomFile.getAbsolutePath(), exception);
        }
    }

    private Element child(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }

        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private String text(Element parent, String tagName) {
        Element child = child(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class PlaceholderResolution {
        private final String value;
        private final boolean changed;

        private PlaceholderResolution(String value, boolean changed) {
            this.value = value;
            this.changed = changed;
        }
    }
}
