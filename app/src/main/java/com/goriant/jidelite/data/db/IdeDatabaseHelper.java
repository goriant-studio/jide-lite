package com.goriant.jidelite.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class IdeDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "jide-state.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_WORKSPACE = "workspace";
    public static final String TABLE_PROJECT = "project";
    public static final String TABLE_OPEN_EDITOR = "open_editor";
    public static final String TABLE_BUILD_STATE = "build_state";
    public static final String TABLE_ITEM = "ItemTable";

    public IdeDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_WORKSPACE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "name TEXT NOT NULL, "
                        + "root_path TEXT NOT NULL UNIQUE, "
                        + "created_at INTEGER NOT NULL, "
                        + "last_opened_at INTEGER NOT NULL, "
                        + "pinned INTEGER NOT NULL DEFAULT 0"
                        + ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_PROJECT + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "workspace_id INTEGER NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "root_path TEXT NOT NULL UNIQUE, "
                        + "type TEXT NOT NULL, "
                        + "build_file_path TEXT, "
                        + "output_dir_path TEXT, "
                        + "created_at INTEGER NOT NULL, "
                        + "last_scanned_at INTEGER NOT NULL, "
                        + "FOREIGN KEY(workspace_id) REFERENCES " + TABLE_WORKSPACE + "(id) ON DELETE CASCADE"
                        + ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_project_workspace_id "
                        + "ON " + TABLE_PROJECT + "(workspace_id)"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_OPEN_EDITOR + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "project_id INTEGER NOT NULL, "
                        + "file_path TEXT NOT NULL, "
                        + "cursor_start INTEGER NOT NULL DEFAULT 0, "
                        + "cursor_end INTEGER NOT NULL DEFAULT 0, "
                        + "scroll_x INTEGER NOT NULL DEFAULT 0, "
                        + "scroll_y INTEGER NOT NULL DEFAULT 0, "
                        + "opened_order INTEGER NOT NULL DEFAULT 0, "
                        + "pinned INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL, "
                        + "UNIQUE(project_id, file_path), "
                        + "FOREIGN KEY(project_id) REFERENCES " + TABLE_PROJECT + "(id) ON DELETE CASCADE"
                        + ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_open_editor_project_updated "
                        + "ON " + TABLE_OPEN_EDITOR + "(project_id, updated_at DESC)"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_BUILD_STATE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "project_id INTEGER NOT NULL UNIQUE, "
                        + "status TEXT NOT NULL, "
                        + "last_output TEXT, "
                        + "last_artifact_path TEXT, "
                        + "last_build_at INTEGER NOT NULL, "
                        + "FOREIGN KEY(project_id) REFERENCES " + TABLE_PROJECT + "(id) ON DELETE CASCADE"
                        + ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_ITEM + " ("
                        + "scope TEXT NOT NULL, "
                        + "storage_key TEXT NOT NULL, "
                        + "value TEXT, "
                        + "updated_at INTEGER NOT NULL, "
                        + "PRIMARY KEY(scope, storage_key)"
                        + ")"
        );
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_item_scope_updated "
                        + "ON " + TABLE_ITEM + "(scope, updated_at DESC)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BUILD_STATE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_OPEN_EDITOR);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROJECT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKSPACE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEM);
        onCreate(db);
    }
}
