package com.jidelite.data.entity;

import com.jidelite.data.enums.NodeKind;

public class FileNodeEntity {

    private long id;
    private long projectId;
    private String path;
    private String parentPath;
    private String name;
    private NodeKind kind;
    private boolean hidden;
    private boolean excluded;
    private boolean generated;
    private long sizeBytes;
    private long modifiedAt;
    private Integer sortOrder;

    public FileNodeEntity() {
    }

    public FileNodeEntity(long id, long projectId, String path, String parentPath, String name, NodeKind kind,
                          boolean hidden, boolean excluded, boolean generated,
                          long sizeBytes, long modifiedAt, Integer sortOrder) {
        this.id = id;
        this.projectId = projectId;
        this.path = path;
        this.parentPath = parentPath;
        this.name = name;
        this.kind = kind;
        this.hidden = hidden;
        this.excluded = excluded;
        this.generated = generated;
        this.sizeBytes = sizeBytes;
        this.modifiedAt = modifiedAt;
        this.sortOrder = sortOrder;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
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

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
