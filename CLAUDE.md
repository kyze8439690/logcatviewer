# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LogcatViewer is an Android library module providing an in-app logcat viewer. It displays real-time Android system logs with filtering and export capabilities.

## Build Commands

This is a standard Android library module using Gradle Kotlin DSL:

```bash
# Build debug AAR
./gradlew :logcatviewer:assembleDebug

# Build release AAR
./gradlew :logcatviewer:assembleRelease

# Run lint checks
./gradlew :logcatviewer:lint
```

**Note**: This module has no unit tests.

## Architecture

### Entry Point
- **`LogcatActivity`** (`LogcatActivity.kt`): Simple `AppCompatActivity` that hosts `LogcatFragment`. Entry method: `LogcatActivity.start(context, excludeList)`.

### Core Components

| File | Purpose |
|------|---------|
| `LogcatFragment.kt` | Main UI fragment with toolbar, priority spinner, text filter input, and ListView. Handles lifecycle (start/stop logcat reading). |
| `LogItem.java` | Data model parsing logcat lines using regex `([0-9]{2}-[0-9]{2}...)` pattern. Implements `Parcelable`. |
| `LogcatAdapter.java` | `BaseAdapter` with dual filtering: priority level (V/D/I/W/E/F) + text search. Uses `synchronized` for thread safety. |
| `ExportLogFileUtils.kt` | Kotlin object exporting logs to external cache directory using `Dispatchers.IO`. |
| `LogcatFileProvider.java` | `FileProvider` for sharing exported logs via `ACTION_SEND`. |

### Key Features

1. **Real-time Logcat Reading**: Background thread executes `ProcessBuilder("logcat", "-v", "threadtime")`, parses lines via `Scanner`.

2. **Dual Filtering**:
   - Priority filter via Spinner (V/D/I/W/E/F)
   - Text filter via `EditText` with 300ms debounce using Kotlin Flow

3. **Export**: Logs written to `externalCacheDir/yyyy-MM-dd HH:mm:ss.SSS.log`, shared via `FileProvider`.

4. **Exclude Patterns**: Constructor accepts `List<Pattern>` to filter out matching log lines client-side.

### UI Layouts

- `logcat_viewer_fragment_logcat.xml`: Main fragment with Toolbar, Spinner, filter input, ListView
- `logcat_viewer_item_logcat.xml`: List item showing timestamp, PID/TID, tag, priority badge, content
- `logcat_viewer_item_logcat_dropdown.xml`: Spinner dropdown items

### AndroidManifest Components

```xml
<activity android:name="com.github.logviewer.LogcatActivity" />
<provider android:name="com.github.logviewer.LogcatFileProvider"
          android:authorities="${applicationId}.logcat_fileprovider" />
```

## Integration Notes

This is a **library module**, not a standalone app. Usage in parent project:

```kotlin
// Start viewer
LogcatActivity.start(context)

// With exclude patterns
val excludeRules = listOf(Pattern.compile(".*MotionEvent.*"))
LogcatActivity.start(context, excludeRules)
```

## Build Configuration

- `compileSdk = 36`, `minSdk = 23`
- Java/Kotlin target: 21
- ViewBinding enabled (DataBinding disabled)
- Dependencies: AppCompat, Activity KTX, Material Design, Lifecycle Extensions
