# Wave C — Hygiene: per-player leaks + async CME (Risk H / surgical F) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining lower-severity hygiene items: per-player static/instance maps that leak because they're never cleared on quit (Risk H memory), and two async paths that touch shared Bukkit state without synchronization (`RpManager.refreshSHA`, `EntityLogger` — surgical Risk F).

**Architecture:** Four small, independent fixes. (1) `CopyPasteManager` gains a `removePlayer`, called from `ClipboardPlayerListener` (which already loads clipboards on join) on `PlayerQuitEvent`. (2) `BlockCycleListener` (already a `Listener`, map keyed by `Player`) clears its own entry on quit. (3) `refreshSHA` runs async: close the `InputStream` (try-with-resources) and hop the two Bukkit-touching calls — the config `set`+`saveConfig` and the progress message — to the main thread. (4) `EntityLogger.logData` is iterated on an async timer while the main thread puts into it: make it a `ConcurrentHashMap`, and hop the dynmap `createMarker` loop (currently commented-out-hop) to the main thread.

**Tech Stack:** Java 25, Maven, Bukkit scheduler. **No unit tests** — populating the clipboard/cycle maps and driving the async logger/SHA paths need heavy live setup; verified by compile + inspection + the Task 5 adversarial review (consistent with the concurrency/DB slices).

**Branch:** `architect-rework-2026`. Build: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"`; never `mvn clean` (OneDrive locks `target/`).

**Out of scope (noted, not done):** removing the placeholder DB section from the shipped `config.yml`, the keep-alive reconnect log-spam frequency, invisible-item-frame de-duplication, and general performance work (an explicit cut-candidate from the audit).

---

## File Structure

| File | Change |
| --- | --- |
| `copyPaste/CopyPasteManager.java` | add `removePlayer(Player)` clearing clipboards/undo/redo |
| `copyPaste/ClipboardPlayerListener.java` | `PlayerQuitEvent` → `CopyPasteManager.removePlayer` |
| `specialBlockHandling/listener/BlockCycleListener.java` | `PlayerQuitEvent` → clear `playerBlockDataManager` |
| `serverResoucePack/RpManager.java` | `refreshSHA`: try-with-resources stream + main-thread hop for config/message |
| `entityLogging/EntityLogger.java` | `logData` → `ConcurrentHashMap`; hop dynmap `createMarker` to main |

---

## Task 1: CopyPasteManager — clear per-player state on quit

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/copyPaste/CopyPasteManager.java`
- Modify: `src/main/java/com/mcmiddleearth/architect/copyPaste/ClipboardPlayerListener.java`

- [ ] **Step 1: Add `removePlayer`** to `CopyPasteManager` (near the other static helpers; `clipboards`/`undoData`/`redoData` are the static maps at `:42/44/46`):

```java
    public static void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        clipboards.remove(uuid);
        undoData.remove(uuid);
        redoData.remove(uuid);
    }
```

(`Player` and `UUID` are already imported — `clipboards` is `Map<UUID, Clipboard>`.)

- [ ] **Step 2: Call it on quit** in `ClipboardPlayerListener` — add the import `org.bukkit.event.player.PlayerQuitEvent;` and the handler:

```java
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        CopyPasteManager.removePlayer(event.getPlayer());
    }
```

- [ ] **Step 3: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/copyPaste/CopyPasteManager.java \
        src/main/java/com/mcmiddleearth/architect/copyPaste/ClipboardPlayerListener.java
git commit -m "fix(hygiene): free per-player copy/paste state on quit (Wave C)"
```

---

## Task 2: BlockCycleListener — clear its per-player map on quit

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/listener/BlockCycleListener.java` (map `:56`, `implements Listener`)

- [ ] **Step 1: Add the import** `org.bukkit.event.player.PlayerQuitEvent;` (next to the existing `org.bukkit.event.player.PlayerInteractEvent` import).

- [ ] **Step 2: Add a quit handler** (anywhere in the class body). The map is keyed by `Player`, so it also holds `Player` references — clearing on quit both frees memory and drops the reference:

```java
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerBlockDataManager.remove(event.getPlayer());
    }
```

- [ ] **Step 3: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/listener/BlockCycleListener.java
git commit -m "fix(hygiene): free per-player block-cycle state on quit (Wave C)"
```

---

## Task 3: RpManager.refreshSHA — close the stream, keep Bukkit calls on main

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpManager.java` (`refreshSHA` inner loop, `:445-466`)

`refreshSHA` runs on an async thread (dispatched from `RpReleaseUtil.setServerResourcePack`). It leaks `url.openStream()` and calls `section.set`+`saveConfig()` (mutates the shared config) and `sendInfoMessage(cs, …)` (touches the player) off the main thread.

- [ ] **Step 1: Replace the per-section body** (`:445-466`, from `URL url = …` through `saveConfig();`):

```java
                                URL url = new URL(section.getString("url"));
                                MessageDigest sha1 = MessageDigest.getInstance("SHA1");
                                try (InputStream fis = url.openStream()) {
                                    byte[] data = new byte[1024];
                                    int read;
                                    long time = System.currentTimeMillis();
                                    while ((read = fis.read(data)) != -1) {
                                        sha1.update(data, 0, read);
                                        if (System.currentTimeMillis() - time > 5000) {
                                            time = System.currentTimeMillis();
                                            Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(),
                                                    () -> PluginData.getMessageUtil().sendInfoMessage(cs, "calculating ..."));
                                        }
                                    }
                                }
                                byte[] hashBytes = sha1.digest();
                                StringBuilder sb = new StringBuilder();
                                for (byte b : hashBytes) {
                                    sb.append(String.format("%02x", b));
                                }
                                final String hashString = sb.toString();
                                final ConfigurationSection shaSection = section;
                                Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> {
                                    shaSection.set("sha", hashString);
                                    ArchitectPlugin.getPluginInstance().saveConfig();
                                });
```

(The `InputStream` now closes on every path; the config mutation + save and the progress message run on the main thread. `Bukkit` and `ConfigurationSection` are already imported in `RpManager`.)

- [ ] **Step 2: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpManager.java
git commit -m "fix(hygiene): refreshSHA closes its stream + writes config/messages on main (Wave C)"
```

---

## Task 4: EntityLogger — concurrent logData + dynmap on main

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/entityLogging/EntityLogger.java` (`logData` `:52`, async dump `:80-97`)

The `loggerTask` runs `runTaskTimerAsynchronously` and iterates `logData.forEach` while `ELogListener.onChunkLoad` (main thread) `put`s into the same `HashMap` → structural-mod CME; and `ELogDynmapUtil.createMarker` is called from the async thread.

- [ ] **Step 1: Make `logData` concurrent** — replace `:52`:

```java
    private static final Map<Coordinates,Integer[]> logData = new java.util.concurrent.ConcurrentHashMap<>();
```

- [ ] **Step 2: Hop the dynmap marker creation to the main thread** — replace the commented-hop block (`:91-97`, the `//new BukkitRunnable()…` around the `logData.forEach(... createMarker ...)`):

```java
                        final int max = maxValue;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                logData.forEach((coord,values)->
                                    ELogDynmapUtil.createMarker(coord, entityTypes, values, max, world));
                            }
                        }.runTask(ArchitectPlugin.getPluginInstance());
```

(`ConcurrentHashMap.forEach` is safe under concurrent `put`; the dynmap call now runs on the main thread. `maxValue` is captured into a `final` local because the lambda runs later.)

- [ ] **Step 3: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/entityLogging/EntityLogger.java
git commit -m "fix(hygiene): EntityLogger concurrent logData + dynmap markers on main (Wave C)"
```

---

## Task 5: Verification + tracker + review

- [ ] **Step 1: Full test run** — `mvn test`. Expected: BUILD SUCCESS; existing 28 tests still green (no new tests).
- [ ] **Step 2: Update the tracker** — in `AUDIT-REMEDIATION-PLAN.md`, mark the Risk H per-player leak-map cleanup and the `refreshSHA`/`EntityLogger` items done; note the remaining explicit non-goals (config.yml placeholder, keep-alive log-spam, item-frame dedup, performance).
- [ ] **Step 3: Commit** the tracker.
- [ ] **Step 4: Independent adversarial review** — verify: no map is cleared while still needed (e.g. quit firing before a pending paste); the `refreshSHA` restructure preserves the SHA value and doesn't change the returned success semantics badly; `logData` iteration/put is now race-free and the `maxValue` capture is correct; and no other plain per-player static map remains uncleared on quit.

---

## Self-Review Notes (author)

- **Spec coverage:** CopyPaste leak (Task 1) ✓; BlockCycle leak (Task 2) ✓; `refreshSHA` stream leak + off-thread config/message (Task 3) ✓; `EntityLogger` async CME + off-thread dynmap (Task 4) ✓. `playerRpData` (the async-written map) was already handled in the thread-safety slice.
- **Behavior:** quit-cleanup only drops in-memory per-player state — clipboards are also persisted to disk (`CopyPasteManager.copyToClipboard` saves `.cbd`), so a rejoin reloads them via `loadClipboard`. `refreshSHA` still computes the same hash; only the *timing* of the config write moves to the next tick (acceptable — it's an admin `/rp release` action).
- **Testability:** none added — all paths need live players / async timers; covered by compile + the Task 5 review.
