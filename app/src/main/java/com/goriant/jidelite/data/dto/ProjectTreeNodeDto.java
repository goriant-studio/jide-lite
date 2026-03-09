package com.goriant.jidelite.data.dto;

import com.goriant.jidelite.data.enums.NodeKind;

import java.util.ArrayList;
import java.util.List;

public class ProjectTreeNodeDto {

    private String path;
    private String name;
    private NodeKind kind;
    private boolean expanded;
    private boolean selected;
    private boolean excluded;
    private boolean generated;
    private List<ProjectTreeNodeDto> children = new ArrayList<>();

    public ProjectTreeNodeDto() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NodeKind getKind() {
        return kind;
    }

    public void setKind(NodeKind kind) {
        this.kind = kind;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public List<ProjectTreeNodeDto> getChildren() {
        return children;
    }

    public void setChildren(List<ProjectTreeNodeDto> children) {
        this.children = children;
    }
}
