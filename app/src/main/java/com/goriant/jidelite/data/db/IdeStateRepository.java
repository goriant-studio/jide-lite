package com.goriant.jidelite.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.goriant.jidelite.data.entity.BuildStateEntity;
import com.goriant.jidelite.data.entity.OpenEditorEntity;
import com.goriant.jidelite.data.entity.ProjectEntity;
import com.goriant.jidelite.data.entity.WorkspaceEntity;
import com.goriant.jidelite.data.enums.BuildStatus;
import com.goriant.jidelite.data.enums.ProjectType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IdeStateRepository {

    private static final String ITEM_KEY_COLUMN = "storage_key";

    private final IdeDatabaseHelper databaseHelper;

    public IdeStateRepository(Context context) {
        this.databaseHelper = new IdeDatabaseHelper(context.getApplicationContext());
    }

    public synchronized WorkspaceEntity upsertWorkspace(String rootPath, String preferredName) {
        String normalizedRootPath = normalizePath(rootPath);
        String normalizedName = normalizeName(preferredName, "workspace");
        long now = System.currentTimeMillis();

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        WorkspaceEntity existing = findWorkspaceByRootPath(db, normalizedRootPath);
        if (existing != null) {
            ContentValues values = new ContentValues();
            values.put("name", normalizedName);
            values.put("last_opened_at", now);
            db.update(
                    IdeDatabaseHelper.TABLE_WORKSPACE,
                    values,
                    "id=?",
                    new String[]{String.valueOf(existing.getId())}
            );
            existing.setName(normalizedName);
            existing.setLastOpenedAt(now);
            return existing;
        }

        ContentValues values = new ContentValues();
        values.put("name", normalizedName);
        values.put("root_path", normalizedRootPath);
        values.put("created_at", now);
        values.put("last_opened_at", now);
        values.put("pinned", 0);

        long id = db.insertOrThrow(IdeDatabaseHelper.TABLE_WORKSPACE, null, values);
        WorkspaceEntity created = new WorkspaceEntity();
        created.setId(id);
        created.setName(normalizedName);
        created.setRootPath(normalizedRootPath);
        created.setCreatedAt(now);
        created.setLastOpenedAt(now);
        created.setPinned(false);
        return created;
    }

    public synchronized ProjectEntity upsertProject(
            long workspaceId,
            String rootPath,
            ProjectType projectType,
            String buildFilePath,
            String outputDirPath
    ) {
        String normalizedRootPath = normalizePath(rootPath);
        long now = System.currentTimeMillis();
        String normalizedProjectName = deriveProjectName(normalizedRootPath);
        ProjectType safeProjectType = projectType == null ? ProjectType.PLAIN_JAVA : projectType;

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ProjectEntity existing = findProjectByRootPath(db, normalizedRootPath);
        if (existing != null) {
            ContentValues values = new ContentValues();
            values.put("workspace_id", workspaceId);
            values.put("name", normalizedProjectName);
            values.put("type", safeProjectType.name());
            values.put("build_file_path", buildFilePath);
            values.put("output_dir_path", outputDirPath);
            values.put("last_scanned_at", now);
            db.update(
                    IdeDatabaseHelper.TABLE_PROJECT,
                    values,
                    "id=?",
                    new String[]{String.valueOf(existing.getId())}
            );
            existing.setWorkspaceId(workspaceId);
            existing.setName(normalizedProjectName);
            existing.setType(safeProjectType);
            existing.setBuildFilePath(buildFilePath);
            existing.setOutputDirPath(outputDirPath);
            existing.setLastScannedAt(now);
            return existing;
        }

        ContentValues values = new ContentValues();
        values.put("workspace_id", workspaceId);
        values.put("name", normalizedProjectName);
        values.put("root_path", normalizedRootPath);
        values.put("type", safeProjectType.name());
        values.put("build_file_path", buildFilePath);
        values.put("output_dir_path", outputDirPath);
        values.put("created_at", now);
        values.put("last_scanned_at", now);

        long id = db.insertOrThrow(IdeDatabaseHelper.TABLE_PROJECT, null, values);
        ProjectEntity created = new ProjectEntity();
        created.setId(id);
        created.setWorkspaceId(workspaceId);
        created.setName(normalizedProjectName);
        created.setRootPath(normalizedRootPath);
        created.setType(safeProjectType);
        created.setBuildFilePath(buildFilePath);
        created.setOutputDirPath(outputDirPath);
        created.setCreatedAt(now);
        created.setLastScannedAt(now);
        return created;
    }

    public synchronized void upsertOpenEditor(OpenEditorEntity openEditorEntity) {
        if (openEditorEntity == null || openEditorEntity.getProjectId() <= 0) {
            return;
        }
        String filePath = openEditorEntity.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }

        long now = openEditorEntity.getUpdatedAt() > 0
                ? openEditorEntity.getUpdatedAt()
                : System.currentTimeMillis();

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("project_id", openEditorEntity.getProjectId());
        values.put("file_path", filePath);
        values.put("cursor_start", openEditorEntity.getCursorStart());
        values.put("cursor_end", openEditorEntity.getCursorEnd());
        values.put("scroll_x", openEditorEntity.getScrollX());
        values.put("scroll_y", openEditorEntity.getScrollY());
        values.put("opened_order", openEditorEntity.getOpenedOrder());
        values.put("pinned", openEditorEntity.isPinned() ? 1 : 0);
        values.put("updated_at", now);

        db.insertWithOnConflict(
                IdeDatabaseHelper.TABLE_OPEN_EDITOR,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public synchronized List<OpenEditorEntity> listOpenEditors(long projectId, int limit) {
        if (projectId <= 0 || limit <= 0) {
            return new ArrayList<>();
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        List<OpenEditorEntity> editors = new ArrayList<>();
        try (Cursor cursor = db.query(
                IdeDatabaseHelper.TABLE_OPEN_EDITOR,
                null,
                "project_id=?",
                new String[]{String.valueOf(projectId)},
                null,
                null,
                "updated_at DESC",
                String.valueOf(limit)
        )) {
            while (cursor.moveToNext()) {
                editors.add(readOpenEditor(cursor));
            }
        }
        return editors;
    }

    public synchronized void upsertBuildState(BuildStateEntity buildStateEntity) {
        if (buildStateEntity == null || buildStateEntity.getProjectId() <= 0) {
            return;
        }

        long now = buildStateEntity.getLastBuildAt() > 0
                ? buildStateEntity.getLastBuildAt()
                : System.currentTimeMillis();
        BuildStatus status = buildStateEntity.getStatus() == null
                ? BuildStatus.IDLE
                : buildStateEntity.getStatus();

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("project_id", buildStateEntity.getProjectId());
        values.put("status", status.name());
        values.put("last_output", buildStateEntity.getLastOutput());
        values.put("last_artifact_path", buildStateEntity.getLastArtifactPath());
        values.put("last_build_at", now);

        db.insertWithOnConflict(
                IdeDatabaseHelper.TABLE_BUILD_STATE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public synchronized void putState(String scope, String key, String value) {
        if (scope == null || key == null || scope.trim().isEmpty() || key.trim().isEmpty()) {
            return;
        }
        if (value == null) {
            removeState(scope, key);
            return;
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("scope", scope);
        values.put(ITEM_KEY_COLUMN, key);
        values.put("value", value);
        values.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict(
                IdeDatabaseHelper.TABLE_ITEM,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public synchronized String getState(String scope, String key) {
        if (scope == null || key == null || scope.trim().isEmpty() || key.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                IdeDatabaseHelper.TABLE_ITEM,
                new String[]{"value"},
                "scope=? AND " + ITEM_KEY_COLUMN + "=?",
                new String[]{scope, key},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("value"));
            }
        }
        return null;
    }

    public synchronized void removeState(String scope, String key) {
        if (scope == null || key == null || scope.trim().isEmpty() || key.trim().isEmpty()) {
            return;
        }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(
                IdeDatabaseHelper.TABLE_ITEM,
                "scope=? AND " + ITEM_KEY_COLUMN + "=?",
                new String[]{scope, key}
        );
    }

    public synchronized void close() {
        databaseHelper.close();
    }

    private WorkspaceEntity findWorkspaceByRootPath(SQLiteDatabase db, String rootPath) {
        try (Cursor cursor = db.query(
                IdeDatabaseHelper.TABLE_WORKSPACE,
                null,
                "root_path=?",
                new String[]{rootPath},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return readWorkspace(cursor);
            }
        }
        return null;
    }

    private ProjectEntity findProjectByRootPath(SQLiteDatabase db, String rootPath) {
        try (Cursor cursor = db.query(
                IdeDatabaseHelper.TABLE_PROJECT,
                null,
                "root_path=?",
                new String[]{rootPath},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return readProject(cursor);
            }
        }
        return null;
    }

    private WorkspaceEntity readWorkspace(Cursor cursor) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        entity.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        entity.setRootPath(cursor.getString(cursor.getColumnIndexOrThrow("root_path")));
        entity.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        entity.setLastOpenedAt(cursor.getLong(cursor.getColumnIndexOrThrow("last_opened_at")));
        entity.setPinned(cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1);
        return entity;
    }

    private ProjectEntity readProject(Cursor cursor) {
        ProjectEntity entity = new ProjectEntity();
        entity.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        entity.setWorkspaceId(cursor.getLong(cursor.getColumnIndexOrThrow("workspace_id")));
        entity.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        entity.setRootPath(cursor.getString(cursor.getColumnIndexOrThrow("root_path")));
        entity.setType(parseProjectType(cursor.getString(cursor.getColumnIndexOrThrow("type"))));
        entity.setBuildFilePath(cursor.getString(cursor.getColumnIndexOrThrow("build_file_path")));
        entity.setOutputDirPath(cursor.getString(cursor.getColumnIndexOrThrow("output_dir_path")));
        entity.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
        entity.setLastScannedAt(cursor.getLong(cursor.getColumnIndexOrThrow("last_scanned_at")));
        return entity;
    }

    private OpenEditorEntity readOpenEditor(Cursor cursor) {
        OpenEditorEntity entity = new OpenEditorEntity();
        entity.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        entity.setProjectId(cursor.getLong(cursor.getColumnIndexOrThrow("project_id")));
        entity.setFilePath(cursor.getString(cursor.getColumnIndexOrThrow("file_path")));
        entity.setCursorStart(cursor.getInt(cursor.getColumnIndexOrThrow("cursor_start")));
        entity.setCursorEnd(cursor.getInt(cursor.getColumnIndexOrThrow("cursor_end")));
        entity.setScrollX(cursor.getInt(cursor.getColumnIndexOrThrow("scroll_x")));
        entity.setScrollY(cursor.getInt(cursor.getColumnIndexOrThrow("scroll_y")));
        entity.setOpenedOrder(cursor.getInt(cursor.getColumnIndexOrThrow("opened_order")));
        entity.setPinned(cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1);
        entity.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")));
        return entity;
    }

    private ProjectType parseProjectType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ProjectType.PLAIN_JAVA;
        }
        try {
            return ProjectType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ProjectType.PLAIN_JAVA;
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return new File(path).getAbsolutePath();
    }

    private String normalizeName(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String deriveProjectName(String rootPath) {
        String fromPath = new File(rootPath).getName();
        return normalizeName(fromPath, "workspace");
    }
}
