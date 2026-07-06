# MCME-Architect — Dedicated File Logging + Debug Mode Design

- **Status:** Approved design (pre-implementation)
- **Date:** 2026-07-06
- **Branch:** `architect-rework-2026`
- **Related:** [`audit.md`](../../../audit.md) — the "logging" hygiene finding (~400 scattered `Logger.getGlobal()`/`printStackTrace` calls) is resolved by this work.

## 1. Goal

Give Architect its own log file under `plugins/MCME-Architect/logs/` and route **all** of Architect's output there — debug, warnings, and errors/stacktraces — **out of the shared server log**, to make debugging Architect easier without wading through the whole server console. Architect already has a debug *mode* (`/architect dev` + `DevUtil`); this keeps that as the live in-game control and adds the file as the complete diagnostic record. Migrating the calls is also a **clarity pass** — every error and message is brought up to the quality bar in §5, so the log is genuinely useful to debug from rather than relocated noise.

**In scope:** a `Log` facade + a file-logging handler on Architect's plugin logger; per-restart, daily-dated, size-rotating log files with retention; config toggles; migrating the ~400 scattered logging calls onto the facade; MockBukkit tests.

**Out of scope:** changing the `/architect dev` command UX; in-game log viewers/tailing; external log shipping; touching other plugins' logging.

## 2. Locked decisions

| Decision | Choice |
|---|---|
| What goes to the file | **All** Architect output — debug + warn + error/stacktraces |
| Server log | Architect output **removed** from it (`setUseParentHandlers(false)`); optional tee via config |
| Consolidation | **Full now** — migrate all ~400 `getGlobal()`/`getLogger(X)`/`printStackTrace` calls onto the facade |
| File naming | **New file per server start**, `architect_YYYY-MM-DD_HH-mm-ss.log` (daily-dated + per-restart) |
| Rotation | Size-based within a session; **retention** prunes old files |
| Debug verbosity | Existing `DevUtil.level` (via `/architect dev <level>`) governs how much debug reaches the file |

## 3. The logging subsystem

**`com.mcmiddleearth.architect.Log`** — thin static facade, the single channel all Architect logging flows through:
```java
public final class Log {
    private Log() {}
    private static Logger log() { return ArchitectPlugin.getPluginInstance().getLogger(); }
    public static void info(String m)              { log().info(m); }
    public static void warn(String m)              { log().warning(m); }
    public static void error(String m)             { log().severe(m); }
    public static void error(String m, Throwable t){ log().log(Level.SEVERE, m, t); }
    public static void debug(String m)             { log().fine(m); }
}
```

**Handler setup** — a small `LogFileManager.install(JavaPlugin)` called **first thing in `onEnable`** (before anything else logs):
- Create `getDataFolder()/logs/` (`mkdirs`).
- Compute the startup timestamp `ts = LocalDateTime.now()` → `"yyyy-MM-dd_HH-mm-ss"`. Pattern: `logs/architect_<ts>_%g.log` (the `%g` generation slot enables size rotation; a fresh `<ts>` each start gives the per-restart + daily-dated file).
- `new FileHandler(pattern, maxBytes, maxFiles, /*append*/ true)` with `maxBytes = maxSizeMB*1MB`, `maxFiles` from config.
- A compact single-line `Formatter`: `[yyyy-MM-dd HH:mm:ss] [LEVEL] message` (+ the throwable's stacktrace on its own lines when present).
- `handler.setLevel(Level.ALL)`; on the plugin logger: `logger.setLevel(Level.ALL)` (so `FINE`/debug isn't filtered before the handler) and `logger.setUseParentHandlers(alsoServerLog)` — `false` by default, so Architect's output no longer reaches the server console.
- **Retention:** at install, list `logs/architect_*.log`, delete any whose last-modified is older than `retentionDays`.
- **Fail-safe:** wrap install in try/catch — on `IOException`, leave `useParentHandlers` true and `Log.warn` that file logging is disabled, so logging never silently dies.

**Teardown** — `LogFileManager.uninstall()` in `onDisable`: `handler.flush(); handler.close(); logger.removeHandler(handler)` — releases the file lock (important on Windows/OneDrive).

**Thread-safety:** none needed — `FileHandler.publish` is `synchronized`, so Architect's async loggers (`RpDatabaseConnector`, RP tasks) write safely.

**Reload:** `/architect reload` must **not** re-run install (would add a duplicate handler / lock a second file). Install/uninstall are tied to enable/disable only; `loadData()` leaves the handler alone.

## 4. Debug-mode integration

`/architect dev` and `DevUtil` are unchanged as the developer-facing control: opt-in developers still get level-gated messages in-game. The single change is in `DevUtil.log(int msglevel, String message)` — **after** its existing `level < msglevel` gate passes, it also calls `Log.debug(message)`. So:
- The file always contains `Log.info/warn/error` (Architect's operational output).
- The file also contains the debug stream that passes the current `DevUtil.level` gate — raise it with `/architect dev <level>` to capture more, exactly as it works for the in-game view today.
- `/architect dev console` (existing) is now redundant with the file but harmless; leave it.

## 5. The call-site migration (~68 files)

Grep-driven, mechanical, batched by package; after each batch `mvn compile` and confirm entries land in the file.

| From | To |
|---|---|
| `Logger.getGlobal().info(x)` | `Log.info(x)` |
| `Logger.getGlobal().warning(x)` | `Log.warn(x)` |
| `Logger.getLogger(X.class.getName()).log(Level.SEVERE, m, ex)` | `Log.error(m, ex)` |
| `Logger.getLogger(X.class.getName()).log(Level.INFO, x)` | `Log.info(x)` |
| `e.printStackTrace()` | `Log.error("<context>", e)` |
| commented `System.out.println(...)` | delete |

Remove the now-unused `java.util.logging.Logger`/`Level` imports as files are cleaned. Commented-out log lines can be left or deleted at the migrator's discretion (don't expand scope chasing them).

**Message-quality bar (applies to every migrated call — this is not a blind find-replace).** As each call site moves onto `Log`, bring the message itself up to standard:
- **Errors carry real context.** Never a bare stacktrace or `Log.error("error", e)`. State *what operation failed* and include the relevant identifiers available at that point — player name/UUID, world, file path, RP name, region, block location, command args — then pass the throwable. Example: `Logger.getLogger(...).log(SEVERE, null, ex)` → `Log.error("Failed to load RP region '" + name + "' from " + file, ex)`.
- **Readable and self-contained.** Full phrases a non-author can understand — what happened, and where useful why and what it means. No cryptic tags or abbreviations (e.g. the audit's `"Key search"` debug line, raw `Arrays.toString(bytes)` dumps).
- **Correct severity.** `info` for normal operational events, `warn` for handled-but-unexpected conditions, `error` for failures. Not everything at `info`.
- **Drop pure noise.** Left-in dev prints / byte-dumps / spammy per-event lines are deleted, or converted to `Log.debug(...)` with a clear label if genuinely useful under `/architect dev`. They must not pollute the file at normal verbosity.
- **Greppable.** Prefer stable identifiers and consistent wording so entries can be filtered later.

Because this needs judgment per message, the plan reviews message clarity **per package** (a read-through of the batch's diff), not just a green compile.

## 6. Config (`config.yml`)

```yaml
logging:
  file:
    enabled: true        # master switch; false -> useParentHandlers(true), back to server log only
    alsoServerLog: false # tee to the console too (default off)
    maxSizeMB: 10        # per-file size cap before rotation
    maxFiles: 5          # rotation generations kept within a session
    retentionDays: 14    # prune architect_*.log older than this at startup
```
Defaults make it work out-of-box (file on, out of server log). Missing keys fall back to these defaults (read via `getConfig().getInt/getBoolean(path, default)`).

## 7. Testing (MockBukkit)

- **Install:** on enable, `logs/` exists, a file matching `architect_*.log` is created, and `plugin.getLogger().getUseParentHandlers() == false`.
- **Info path:** `Log.info("marker-123")` results in a line containing `marker-123` in the current log file.
- **Error path:** `Log.error("boom", new RuntimeException("x"))` writes the message + stacktrace.
- **Debug gate:** with `DevUtil.level` high enough, `DevUtil.log(1, "dbg-123")` writes `dbg-123`; below the gate it does not.
- MockBukkit provides an isolated temp data folder, so file assertions don't touch a real server.
- Teardown test: after `uninstall`, the handler is removed and the file is closed (no lock).

## 8. Success criteria

1. On enable, Architect logs to `plugins/MCME-Architect/logs/architect_<timestamp>.log` and **nothing** from Architect appears in the server console (with defaults).
2. All ~400 legacy logging calls route through `Log`; `grep` for `getGlobal()`/`printStackTrace()` in `src` returns only intentional/commented cases.
3. `/architect dev <level>` controls debug detail in both the in-game view and the file.
4. Config toggles work (disable → back to server log; retention prunes; rotation caps size).
5. `mvn compile` green; MockBukkit tests pass; file handle released on disable.
6. **Message quality (§5):** migrated errors carry the failed operation + relevant identifiers + the throwable; no bare stacktraces or cryptic one-word logs survive; severities (`info`/`warn`/`error`) are used correctly — reviewed per package, not just compiled.
