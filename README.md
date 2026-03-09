# J-IDE Lite

J-IDE Lite is a small Android Java editor for quick on-device experiments. It provides a file explorer, a monospace code editor with lightweight syntax highlighting, and a terminal-style output pane for compiling and running Java programs locally.

The app does not depend on Termux or a remote service. Java source is compiled inside the app, converted to DEX, and executed in-process on Android.

## Features

- Compose-based Android UI with editor, explorer, and terminal panes
- Local app-private workspace for `.java` files
- One-tap create, save, clear, and run actions
- Lightweight Java syntax highlighting for keywords, strings, comments, annotations, and numbers
- Multi-file workspace execution by compiling all Java files in the workspace together
- Default starter file generation (`Main.java`, `Main2.java`, `Main3.java`, ...)

## Requirements

- JDK 17
- Android SDK with platform 34
- Android build-tools that include `d8.jar`
- Android device or emulator running Android 8.0 (API 26) or newer

Builds will fail early if `d8.jar` cannot be found in the installed Android SDK build-tools directory.

## Build

```bash
./gradlew assembleDebug
```

To install on a connected device or emulator:

```bash
./gradlew installDebug
```

## Test

```bash
./gradlew test
```

The current test suite covers the storage helper, run result model, disabled Termux stub, and most non-Android helper logic in the local runner.

## How It Works

1. Source files are stored in the app's private `workspace` directory.
2. When you run a file, the app mirrors the full workspace into a cache-backed runner directory.
3. Janino compiles the Java sources to `.class` files.
4. Android's `d8` tool converts those classes into a `classes.dex`.
5. The app loads the DEX in memory and invokes `main(String[] args)`.
6. `stdout` and `stderr` are captured and rendered in the terminal pane.

This keeps the execution path fully local and avoids shelling out to `javac` or `java` on the device.

## Workspace Rules

- Files must match `^[A-Za-z][A-Za-z0-9_]*\\.java$`
- The workspace is flat: files are stored directly in one directory
- `Main.java` is created automatically on first launch if the workspace is empty
- Switching files attempts to save the currently open file first

## Limitations

- User code is compiled with Java 8 source/target settings inside Janino
- Execution expects a `public static void main(String[] args)` entry point
- Console input is not supported
- Projects are limited to simple flat-file Java workspaces, not full Gradle/Android projects
- Termux integration is intentionally disabled; the embedded local runner is the supported path

## Project Layout

```text
.
├── app/
│   ├── src/main/java/com/jidelite/
│   │   ├── MainActivity.kt
│   │   ├── editor/
│   │   ├── runner/
│   │   ├── storage/
│   │   └── model/
│   ├── src/main/res/
│   └── src/test/java/com/jidelite/
├── build.gradle
├── settings.gradle
└── gradlew
```

## Key Components

- `MainActivity`: UI, file selection, save/run actions, and terminal rendering
- `FileStorageHelper`: workspace initialization, safe file naming, and file I/O
- `LocalJavaRunner`: compile, dex, and execute pipeline
- `JavaSyntaxHighlighter`: delayed syntax highlighting for the editor
- `RunResult`: normalized execution result object shared by runner implementations
