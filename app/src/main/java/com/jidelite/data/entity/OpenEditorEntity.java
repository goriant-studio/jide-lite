package com.jidelite.data.entity;

public class OpenEditorEntity {

    private long id;
    private long projectId;
    private String filePath;
    private int cursorStart;
    private int cursorEnd;
    private int scrollX;
    private int scrollY;
    private int openedOrder;
    private boolean pinned;
    private long updatedAt;

    public OpenEditorEntity() {
    }

    public OpenEditorEntity(long id, long projectId, String filePath, int cursorStart, int cursorEnd,
                            int scrollX, int scrollY, int openedOrder, boolean pinned, long updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.filePath = filePath;
        this.cursorStart = cursorStart;
        this.cursorEnd = cursorEnd;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.openedOrder = openedOrder;
        this.pinned = pinned;
        this.updatedAt = updatedAt;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getCursorStart() {
        return cursorStart;
    }

    public void setCursorStart(int cursorStart) {
        this.cursorStart = cursorStart;
    }

    public int getCursorEnd() {
        return cursorEnd;
    }

    public void setCursorEnd(int cursorEnd) {
        this.cursorEnd = cursorEnd;
    }

    public int getScrollX() {
        return scrollX;
    }

    public void setScrollX(int scrollX) {
        this.scrollX = scrollX;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void setScrollY(int scrollY) {
        this.scrollY = scrollY;
    }

    public int getOpenedOrder() {
        return openedOrder;
    }

    public void setOpenedOrder(int openedOrder) {
        this.openedOrder = openedOrder;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
