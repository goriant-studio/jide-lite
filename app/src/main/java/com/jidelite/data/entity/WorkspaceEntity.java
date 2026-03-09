package com.jidelite.data.entity;

public class WorkspaceEntity {

    private long id;
    private String name;
    private String rootPath;
    private long createdAt;
    private long lastOpenedAt;
    private boolean pinned;

    public WorkspaceEntity() {
    }

    public WorkspaceEntity(long id, String name, String rootPath, long createdAt, long lastOpenedAt, boolean pinned) {
        this.id = id;
        this.name = name;
        this.rootPath = rootPath;
        this.createdAt = createdAt;
        this.lastOpenedAt = lastOpenedAt;
        this.pinned = pinned;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(long lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
