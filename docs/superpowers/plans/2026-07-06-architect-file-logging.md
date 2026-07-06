# Architect Dedicated File Logging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route all of Architect's output into a rotating file under `plugins/MCME-Architect/logs/` (out of the server log), via a `Log` facade + a `FileHandler` on the plugin logger, and migrate the ~400 scattered logging calls onto the facade — improving each message as it moves.

**Architecture:** A tiny static `Log` facade backs onto the plugin's own `java.util.logging.Logger`; `LogFileManager` attaches a `FileHandler` (per-restart daily-dated file, size rotation, retention) and turns off parent-handler propagation so nothing reaches the server console. `DevUtil` routes its debug stream through `Log.debug`. Then every legacy `getGlobal()`/`printStackTrace()` call is migrated onto `Log`, with a message-clarity pass. Subsystem tasks (1-2) are TDD with MockBukkit; the migration tasks (3-5) are compile-and-diff-review sweeps.

**Tech Stack:** Java 25, Paper 26.1.2, `java.util.logging`, MockBukkit `v26.1.2:4.114.0`, JUnit 5. Branch `architect-rework-2026`. All Maven: prefix `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"`, and **do not use `mvn clean`** (OneDrive locks `target/`) — plain `mvn compile`/`mvn test`.

**Spec:** [2026-07-06-architect-file-logging-design.md](../specs/2026-07-06-architect-file-logging-design.md).

---

## File structure

| File | Responsibility | Action |
|---|---|---|
| `src/main/java/com/mcmiddleearth/architect/Log.java` | Static facade: `info/warn/error/debug` → plugin logger | Create |
| `src/main/java/com/mcmiddleearth/architect/LogFileManager.java` | Install/uninstall the FileHandler; formatter; rotation; retention; fail-safe | Create |
| `src/main/java/com/mcmiddleearth/architect/ArchitectPlugin.java` | Call `install` in `onEnable` (after `pluginInstance` set), `uninstall` in `onDisable` | Modify |
| `src/main/java/com/mcmiddleearth/util/DevUtil.java` | Route debug stream through `Log.debug` after the level gate | Modify |
| `src/main/resources/config.yml` | Add the `logging.file.*` section | Modify |
| `src/test/java/com/mcmiddleearth/architect/LogFileTest.java` | MockBukkit tests for the subsystem | Create |
| ~68 source files across the packages | Migrate `getGlobal()`/`getLogger(X)`/`printStackTrace` → `Log.*` with clarity | Modify (Tasks 3-5) |

---

## Task 1: `Log` facade + `LogFileManager` (core file logging)

**Files:**
- Create: `src/main/java/com/mcmiddleearth/architect/Log.java`
- Create: `src/main/java/com/mcmiddleearth/architect/LogFileManager.java`
- Modify: `src/main/java/com/mcmiddleearth/architect/ArchitectPlugin.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/com/mcmiddleearth/architect/LogFileTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/mcmiddleearth/architect/LogFileTest.java`:
```java
package com.mcmiddleearth.architect;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class LogFileTest {
    private ArchitectPlugin plugin;

    @BeforeEach void setUp() { MockBukkit.mock(); plugin = MockBukkit.load(ArchitectPlugin.class); }
    @AfterEach  void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void logFileCreatedAndOutOfServerLog() {
        File[] logs = logs();
        assertNotNull(logs, "logs/ dir should exist");
        assertTrue(logs.length >= 1, "a log file should be created on enable");
        assertFalse(plugin.getLogger().getUseParentHandlers(),
                "Architect logging must not propagate to the server console");
    }

    @Test void infoIsWrittenToFile() throws Exception {
        Log.info("marker-12345");
        LogFileManager.flush();
        assertTrue(readLatestLog().contains("marker-12345"));
    }

    @Test void errorWritesMessageAndStacktrace() throws Exception {
        Log.error("boom-context", new IllegalStateException("kaboom"));
        LogFileManager.flush();
        String c = readLatestLog();
        assertTrue(c.contains("boom-context"), "error message present");
        assertTrue(c.contains("kaboom"), "stacktrace present");
    }

    private File[] logs() {
        return new File(plugin.getDataFolder(), "logs")
                .listFiles((d, n) -> n.startsWith("architect_") && n.endsWith(".log"));
    }
    private String readLatestLog() throws Exception {
        File[] logs = logs();
        File latest = logs[0];
        for (File f : logs) if (f.lastModified() >= latest.lastModified()) latest = f;
        return new String(Files.readAllBytes(latest.toPath()));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn test -Dtest=LogFileTest`
Expected: FAIL/compile-error — `Log` and `LogFileManager` don't exist yet.

- [ ] **Step 3: Create `Log.java`**
```java
package com.mcmiddleearth.architect;

import java.util.logging.Level;
import java.util.logging.Logger;

/** The single channel all Architect logging flows through (see {@link LogFileManager}). */
public final class Log {
    private Log() {}
    private static Logger log() { return ArchitectPlugin.getPluginInstance().getLogger(); }
    public static void info(String msg)                { log().info(msg); }
    public static void warn(String msg)                { log().warning(msg); }
    public static void error(String msg)               { log().severe(msg); }
    public static void error(String msg, Throwable t)  { log().log(Level.SEVERE, msg, t); }
    public static void debug(String msg)               { log().fine(msg); }
}
```

- [ ] **Step 4: Create `LogFileManager.java`**
```java
package com.mcmiddleearth.architect;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Attaches a rotating file handler to Architect's plugin logger and keeps it off the server log. */
public final class LogFileManager {
    private LogFileManager() {}
    private static FileHandler handler;

    public static void install(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        if (!plugin.getConfig().getBoolean("logging.file.enabled", true)) return;
        boolean alsoServerLog = plugin.getConfig().getBoolean("logging.file.alsoServerLog", false);
        int maxSizeMB     = plugin.getConfig().getInt("logging.file.maxSizeMB", 10);
        int maxFiles      = Math.max(1, plugin.getConfig().getInt("logging.file.maxFiles", 5));
        int retentionDays = plugin.getConfig().getInt("logging.file.retentionDays", 14);
        try {
            File logDir = new File(plugin.getDataFolder(), "logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                logger.warning("Could not create log directory " + logDir + "; Architect file logging disabled.");
                return;
            }
            pruneOldLogs(logDir, retentionDays);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String pattern = new File(logDir, "architect_" + ts + "_%g.log").getAbsolutePath();
            handler = new FileHandler(pattern, maxSizeMB * 1024 * 1024, maxFiles, true);
            handler.setFormatter(new SingleLineFormatter());
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setUseParentHandlers(alsoServerLog);
            logger.info("Architect file logging started -> " + pattern);
        } catch (IOException | SecurityException ex) {
            logger.setUseParentHandlers(true);
            logger.log(Level.WARNING, "Could not start Architect file logging; using the server log instead.", ex);
        }
    }

    public static void uninstall(JavaPlugin plugin) {
        if (handler != null) {
            handler.flush();
            handler.close();
            plugin.getLogger().removeHandler(handler);
            handler = null;
        }
    }

    /** Flush pending records to disk (used by tests and before reads). */
    public static void flush() { if (handler != null) handler.flush(); }

    private static void pruneOldLogs(File dir, int retentionDays) {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000;
        File[] files = dir.listFiles((d, n) -> n.startsWith("architect_") && n.endsWith(".log"));
        if (files != null) for (File f : files) if (f.lastModified() < cutoff) f.delete();
    }

    private static final class SingleLineFormatter extends Formatter {
        private final SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        @Override public String format(LogRecord r) {
            StringBuilder sb = new StringBuilder(128)
                    .append('[').append(time.format(new Date(r.getMillis()))).append("] [")
                    .append(r.getLevel()).append("] ").append(formatMessage(r)).append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter sw = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }
            return sb.toString();
        }
    }
}
```

- [ ] **Step 5: Wire into `ArchitectPlugin`**

In `onEnable()`, immediately after `pluginInstance = this;` (so `Log`/`getPluginInstance()` are usable), add:
```java
        LogFileManager.install(this);
```
In `onDisable()`, as the **last** statement (so shutdown logging is still captured), add:
```java
        LogFileManager.uninstall(this);
```

- [ ] **Step 6: Add the config section**

Append to `src/main/resources/config.yml`:
```yaml
logging:
    file:
        enabled: true
        alsoServerLog: false
        maxSizeMB: 10
        maxFiles: 5
        retentionDays: 14
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn test -Dtest=LogFileTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/mcmiddleearth/architect/Log.java \
        src/main/java/com/mcmiddleearth/architect/LogFileManager.java \
        src/main/java/com/mcmiddleearth/architect/ArchitectPlugin.java \
        src/main/resources/config.yml \
        src/test/java/com/mcmiddleearth/architect/LogFileTest.java
git commit -m "feat: dedicated rotating file logging on Architect's plugin logger"
```

---

## Task 2: Route the `DevUtil` debug stream to the file

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/util/DevUtil.java`
- Test: `src/test/java/com/mcmiddleearth/architect/LogFileTest.java`

- [ ] **Step 1: Add the failing tests**

Add to `LogFileTest`:
```java
    @Test void devUtilDebugWritesToFileWhenLevelPasses() throws Exception {
        com.mcmiddleearth.util.DevUtil.setLevel(5);
        com.mcmiddleearth.util.DevUtil.log(1, "dbg-777");
        LogFileManager.flush();
        assertTrue(readLatestLog().contains("dbg-777"));
    }

    @Test void devUtilDebugSuppressedBelowLevel() throws Exception {
        com.mcmiddleearth.util.DevUtil.setLevel(1);
        com.mcmiddleearth.util.DevUtil.log(9, "dbg-should-not-appear");
        LogFileManager.flush();
        assertFalse(readLatestLog().contains("dbg-should-not-appear"));
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn test -Dtest=LogFileTest`
Expected: the two new tests FAIL (debug not reaching the file yet).

- [ ] **Step 3: Route DevUtil through `Log.debug`**

In `DevUtil.log(int msglevel, String message)`, right after the gate `if(level<msglevel) { return; }`, add:
```java
        com.mcmiddleearth.architect.Log.debug(message);
```
(Leave the existing in-game developer loop and the `consoleOutput` branch as-is.)

- [ ] **Step 4: Run to verify all pass**

Run: `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn test -Dtest=LogFileTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/mcmiddleearth/util/DevUtil.java src/test/java/com/mcmiddleearth/architect/LogFileTest.java
git commit -m "feat: capture the /architect dev debug stream in the log file"
```

---

## Tasks 3-5: Migrate the ~400 legacy logging calls (per-package sweeps)

These are **not** TDD (there's no behavior to assert on a log string) and **not** blind find-replace. Each call moves onto `Log` **and** the message is brought up to the spec's §5 quality bar. After each package batch: `mvn compile` green, read the diff for message clarity, commit.

**Pattern mapping (apply everywhere):**
| From | To |
|---|---|
| `Logger.getGlobal().info(x)` | `Log.info(x)` |
| `Logger.getGlobal().warning(x)` | `Log.warn(x)` |
| `Logger.getLogger(X.class.getName()).log(Level.SEVERE, msg, ex)` | `Log.error(msg, ex)` |
| `Logger.getLogger(X.class.getName()).log(Level.SEVERE, null, ex)` | `Log.error("<what failed + ids>", ex)` |
| `Logger.getLogger(...).log(Level.INFO, x)` | `Log.info(x)` |
| `e.printStackTrace()` | `Log.error("<what failed + ids>", e)` |
| commented `System.out.println(...)` | delete |

Add `import com.mcmiddleearth.architect.Log;`, remove now-unused `java.util.logging.Logger`/`Level` imports.

**Message-quality bar (spec §5) — apply to each:**
- Errors: state the operation that failed + the identifiers on hand (player, world, file, RP name, region, location, args) + pass the throwable. Never `Log.error("error", e)` or a bare stacktrace.
- Readable full phrases; no cryptic tags / byte-dumps (e.g. delete `"Key search"`, `Arrays.toString(bytes)` spam).
- Correct severity: `info` normal, `warn` handled-but-unexpected, `error` failure.
- Pure noise/spam → delete, or `Log.debug("<clear label>")` only if useful under `/architect dev`.

**Concrete examples:**
- `RpManager.java` region load — `} catch (IOException | InvalidConfigurationException ex) { Logger.getLogger(RpManager.class.getName()).log(Level.SEVERE, null, ex); }` → `Log.error("Failed to load RP region file " + file.getName(), ex);`
- `RpReleaseUtil.java` — `updateSection` `Logger.getGlobal().info("Key search: "+path)` and the `getKeys(true).forEach(... info(key))` dump → **delete** (left-in debug).
- `TestPluginMessageListener.java` — the per-join `Arrays.toString(data)` dump → `Log.debug("Plugin message on " + channel + " from " + player.getName())` (label, debug-level, no raw bytes).

### Task 3: `serverResoucePack` package (heaviest logging)
**Files:** all `.java` under `src/main/java/com/mcmiddleearth/architect/serverResoucePack/` (and its `RegionEditConversation/` subpackage).
- [ ] **Step 1: Enumerate the calls**
Run: `grep -rnE "getGlobal\(\)|getLogger\(|printStackTrace\(\)" src/main/java/com/mcmiddleearth/architect/serverResoucePack/`
- [ ] **Step 2: Migrate each call** using the mapping + quality bar above (real context on every error; delete the `RpReleaseUtil`/`RpManager` debug dumps).
- [ ] **Step 3: Compile** — `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn compile` → `BUILD SUCCESS`.
- [ ] **Step 4: Review** — `git diff` this package; confirm every error names what failed + has identifiers, no cryptic lines remain.
- [ ] **Step 5: Commit** — `git commit -am "refactor(logging): route serverResoucePack logging through Log with clear messages"`

### Task 4: `specialBlockHandling` package
**Files:** all `.java` under `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/` (command/, data/, listener/, specialBlocks/, itemBlock/, customInventories/).
- [ ] **Step 1: Enumerate** — `grep -rnE "getGlobal\(\)|getLogger\(|printStackTrace\(\)" src/main/java/com/mcmiddleearth/architect/specialBlockHandling/`
- [ ] **Step 2: Migrate** each per the mapping + quality bar (e.g. `SpecialBlockBranch2` "Handle block break!" spam → `Log.debug(...)` or delete; loader errors → `Log.error("Failed to load <thing> from " + file, ex)`).
- [ ] **Step 3: Compile** → `BUILD SUCCESS`.
- [ ] **Step 4: Review** the diff for clarity.
- [ ] **Step 5: Commit** — `git commit -am "refactor(logging): route specialBlockHandling logging through Log with clear messages"`

### Task 5: All remaining packages
**Files:** every remaining `.java` with a match under `src/main/java/com/mcmiddleearth/architect/` and `com/mcmiddleearth/util/` (customHeadManager, copyPaste, armorStand, bannerEditor, noPhysicsEditor, viewDistance, chunkUpdate, blockData, voxelStencilEditor, entityLogging, additionalCommands, additionalListeners, paintingEditor, randomiser, signEditor, weSchematicsViewer, WorldGeneration, watcher, util).
- [ ] **Step 1: Enumerate** — `grep -rnE "getGlobal\(\)|getLogger\(|printStackTrace\(\)" src/main/java/com/mcmiddleearth/ | grep -vE "serverResoucePack|specialBlockHandling"`
- [ ] **Step 2: Migrate** each per the mapping + quality bar. (Note: `DevUtil.java`'s own `Logger.getLogger(ArchitectPlugin.class)` console line already flows to the file via `Log.debug`; leave DevUtil's structure from Task 2 intact.)
- [ ] **Step 3: Compile** → `BUILD SUCCESS`.
- [ ] **Step 4: Review** the diff for clarity.
- [ ] **Step 5: Commit** — `git commit -am "refactor(logging): route remaining packages through Log with clear messages"`

---

## Task 6: Verification

**Files:** none (verification).
- [ ] **Step 1: No stray direct logging remains**
Run: `grep -rnE "getGlobal\(\)|printStackTrace\(\)" src/main/java/ | grep -vE "^\s*//|/\*"`
Expected: only intentional/commented cases (ideally none live). Any live hit → migrate it.
- [ ] **Step 2: Full build + tests**
Run: `JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot" mvn test`
Expected: `BUILD SUCCESS`, `LogFileTest` and `PluginEnableTest` green.
- [ ] **Step 3: Dev-server sanity note (manual, Wave B)**
On the 26.2 dev server: enable Architect, confirm `plugins/MCME-Architect/logs/architect_<timestamp>.log` fills and the server console shows no Architect lines; `/architect dev 5` then confirm debug detail appears in the file. (Runtime check — not automatable here.)

---

## Self-review

**Spec coverage:** §3 subsystem → Task 1 (`Log`, `LogFileManager`, handler, `useParentHandlers(false)`, formatter, rotation, retention, fail-safe, teardown, wiring). §4 DevUtil → Task 2. §5 migration + quality bar → Tasks 3-5 (mapping + bar + per-package diff review). §6 config → Task 1 Step 6. §7 tests → Task 1/2 tests. §8 success criteria → Task 6 (grep-clean = #2, tests = #5, message quality = per-package review #6; #1/#3/#4 = the dev-server note + tests). Reload-safety (§3) is satisfied because install/uninstall are only called from enable/disable, and `loadData()` is untouched — noted here so it isn't lost.

**Placeholder scan:** the only `<...>` tokens are in the migration mapping/examples (`<what failed + ids>`), which are *intentionally* per-call-site judgment — the quality bar + concrete examples define exactly how to fill them. This is migration guidance, not a lazy placeholder; the code tasks (1-2) contain complete code.

**Type consistency:** `Log.info/warn/error/error(msg,t)/debug` and `LogFileManager.install/uninstall/flush` are used identically across tasks and tests.

---

## Execution handoff

Two options:
1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks. (Tasks 1-2 TDD; Tasks 3-5 each end in a message-clarity diff review.)
2. **Inline Execution** — run in this session with checkpoints.
