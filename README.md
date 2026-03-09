# J-IDE Lite

J-IDE Lite is an Android Java editor for quick on-device experiments. It includes a Compose UI (explorer + editor + terminal), built-in formatting/highlighting tools, and a local compile/run pipeline that does not depends remote services.

## Features

- Compose-based multi-pane UI with file explorer, java editor, and terminal output (read-only)
- Local app-private workspace with support for `.java` and `pom.xml`
- Local SQLite state database (`jide-state.db`) for workspace/editor/build metadata
- Quick file creation (`Main.java`, `Main2.java`, `Main3.java`, ...)
- One-tap actions for save, format, run, clear terminal, and dependency resolve
- Java syntax highlighting and editor shortcuts (indent, smart newline, copy/cut/paste/select all)
- Embedded compile/run flow: Janino -> D8 -> in-memory DEX execution
- Maven dependency resolution with local artifact caching

## Requirements

- JDK 17
- Android SDK Platform 34
- Android Build-Tools containing `d8.jar` (prefers `34.0.0`, then falls back to latest installed)
- Android device/emulator running Android 8.0+ (API 26+)
- Internet access on device for Maven dependency downloads

Builds fail early if no `d8.jar` can be found in installed Android Build-Tools.

## Build

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

Install on connected device/emulator:

```bash
./gradlew installDebug
# Windows: .\gradlew.bat installDebug
```

## Play Store Release

- Package name: `com.goriant.jidelite`
- Signing template: `keystore.properties.example`
- Full checklist: [`docs/playstore-release.md`](docs/playstore-release.md)
- Note: release `.aab` must be signed with your upload key before Play Console upload.

## Test

```bash
./gradlew test
# Windows: .\gradlew.bat test
```

Unit tests currently cover formatter/editor helpers, storage behavior, local runner behavior, Maven parser/dependency resolution logic, and output formatting. An instrumentation integration test is included for the main activity.

## Quick Start In App

1. Launch the app. Workspace is created in app-private storage.
2. On first launch, a sample Maven workspace is seeded (`pom.xml` + `src/main/java/demo/Main.java`).
3. Tap `DEPS` to resolve and download dependencies declared in `pom.xml`.
4. Open a `.java` file with `public static void main(String[] args)` and tap `RUN`.
5. Use `NEW`, `SAVE`, `BEAUTIFY`, and terminal `CLEAR` as needed.

## Workspace Rules

- Allowed workspace files are `.java` and `pom.xml`.
- If `pom.xml` exists, sources are compiled from `src/main/java`.
- If `pom.xml` does not exist, sources are compiled from workspace root.
- Running a selected `.java` file requires that file to define `main(String[] args)`.
- Running from a non-Java selection (for example `pom.xml`) scans for runnable classes:
  - If exactly one runnable class exists, it runs automatically.
  - If multiple runnable classes exist, open the target `.java` file first and run from there.

## How It Works

1. Collect source files from active source roots.
2. Resolve project dependencies from `pom.xml` (if present).
3. Compile Java sources to `.class` using Janino.
4. Convert compiled classes (plus runtime dependency jars) to `.dex` with D8.
5. Load DEX in memory and invoke `main(String[] args)`.
6. Capture `stdout` and `stderr` and render in terminal pane.

## Maven Support Scope

- Supports: `dependencies`, `repositories`, placeholder properties, and parent fallback for `groupId`/`version`
- Included scopes: default, `compile`, `runtime`
- Ignored scopes: `test`, `provided`, `system`, `import`
- Optional dependencies are skipped
- Runtime dependency packaging is currently `jar` only
- Downloaded artifacts are cached in app files directory under `m2/repository`
- Workbench-like UI state is persisted in SQLite (`ItemTable`) as key/value entries

## Limitations

- User code is compiled with Java 8 source/target compatibility inside Janino.
- Console stdin input is not supported.
- Maven support is lightweight and not a full Maven lifecycle engine.
- Code executes inside the app process; avoid running untrusted code.
- Termux integration is intentionally disabled; embedded local runner is the supported path.

## Project Layout

```text
.
|-- app/
|   |-- src/main/java/com/goriant/jidelite/
|   |   |-- MainActivity.kt
|   |   |-- data/
|   |   |-- editor/
|   |   |-- model/
|   |   |-- runner/
|   |   |-- storage/
|   |   `-- ui/
|   |-- src/main/res/
|   |-- src/test/
|   `-- src/androidTest/
|-- build.gradle
|-- settings.gradle
|-- gradle.properties
`-- README.md
```

## Key Components

- `MainActivity`: Compose entry point
- `MainViewModel`: workspace/editor actions, run flow, and status updates
- `FileStorageHelper`: workspace initialization, file validation, and file I/O
- `LocalJavaRunner`: compile, dex, and execute pipeline
- `MavenProjectDependencyResolver`: resolves transitive dependencies from `pom.xml`
- `JavaCodeFormatter` and `JavaSyntaxHighlighter`: formatting and syntax highlighting
- `RunOutputFormatter`: normalizes terminal/status messages
