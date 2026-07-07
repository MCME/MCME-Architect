# Wave C — Thread-Safety (Async → Main-Thread Correctness) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop MCME-Architect touching main-thread server state from async threads (Risk F): the `/inv download|upload` callback, the `/chead submit` Mojang HTTP read, and the async-written per-player `playerRpData` map. Bukkit API and its collections are single-threaded; work that mutates them must run on the main thread, and blocking I/O must not.

**Architecture:** Three targeted fixes. (1) `InventoryUtil` runs its git script async but currently invokes the caller's callback — which reloads inventories (main-thread state) — on the async thread; hop the callback to the main thread and shut down the leaked `ExecutorService`. (2) `HeadDataBuilderPlayer` currently does only `connect()` async and then blocks on the response read from a main-thread timer, with non-volatile `received`/`connection` fields; move the entire HTTP round-trip async and hop only the Bukkit result-handling back to main. (3) `RpManager.playerRpData` is written from `AsyncPlayerPreLogin`; make it (and `sodiumClients`) a `ConcurrentHashMap` and clear it on `PlayerQuitEvent`.

**Tech Stack:** Java 25, Maven, MockBukkit v26.1.2 4.114.0 (the quit-cleanup test), `com.mcmiddleearth.architect.Log`. Bukkit scheduler: `runTask` (main), `runTaskAsynchronously` (off-thread).

**Branch:** `architect-rework-2026`. Build: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"`; never `mvn clean` (OneDrive locks `target/`).

**Explicitly deferred (tracked, NOT in this slice):** the pure memory-leak maps that are *not* async-written — `CopyPasteManager` (clipboards/undo/redo) and `BlockCycleListener` (`playerBlockDataManager`, keyed by `Player`) — belong to a separate memory-hygiene pass (Risk H). Also deferred as more-surgical Risk-F follow-ups: `RpManager.refreshSHA` (InputStream leak + off-thread `saveConfig`) and `EntityLogger` async CME + off-thread dynmap. Task 5 records these so nothing is silently dropped. (`ViewDistanceListener`'s old netty-thread hazard is already gone — Wave A made it a main-thread Bukkit listener.)

---

## File Structure

| File | Change |
| --- | --- |
| `specialBlockHandling/command/InventoryUtil.java` | callback → main thread; `shutdownNow()` the executor in `finally` |
| `customHeadManager/HeadDataBuilderPlayer.java` | full HTTP async + main-thread result hop; drop timer + non-volatile fields |
| `serverResoucePack/RpManager.java` | `playerRpData`/`sodiumClients` → `ConcurrentHashMap`; add `removePlayerData(Player)` |
| `serverResoucePack/RpListener.java` | call `RpManager.removePlayerData` in `playerQuit` |
| `src/test/java/.../RpManagerCleanupTest.java` | **Create** — quit clears `playerRpData` |

---

## Task 1: InventoryUtil — callback on main thread + executor shutdown

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/command/InventoryUtil.java`

- [ ] **Step 1: Replace `executeScript` and add `runOnMain`** (the whole method body, currently lines 23-56):

```java
    private static void executeScript(String action, String rpName, String description, BiConsumer<Boolean, Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(ArchitectPlugin.getPluginInstance(), () -> {
            boolean exit = false;
            int exitCode = -1;
            ExecutorService executorService = null;
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                String script = ArchitectPlugin.getPluginInstance().getConfig().getString(action + ".script");
                String scriptPath = ArchitectPlugin.getPluginInstance().getConfig().getString(action + ".path");
                if (isWindows || script == null || scriptPath == null) {
                    runOnMain(callback, false, -1);
                    return;
                }
                Process process = Runtime.getRuntime()
                        .exec(new String[]{"sh", script,
                                        rpName.substring(0, 1).toUpperCase() + rpName.substring(1).toLowerCase(),
                                        description}, null,
                                new File(scriptPath));
                StreamGobbler streamGobbler =
                        new StreamGobbler(process.getInputStream(), process.getErrorStream(),
                                line -> Log.info("[" + action + " " + rpName + "] " + line));
                executorService = Executors.newSingleThreadExecutor();
                Future<?> future = executorService.submit(streamGobbler);
                exit = process.waitFor(5, TimeUnit.MINUTES);
                future.get(5, TimeUnit.MINUTES);
                process.destroy();
                exitCode = process.waitFor();
                runOnMain(callback, exit, exitCode);
            } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
                Log.error("Failed to run " + action + " script for RP " + rpName, e);
                runOnMain(callback, false, -1);
            } finally {
                if (executorService != null) {
                    executorService.shutdownNow();
                }
            }
        });
    }

    // Deliver the result on the main thread: the /inv download callback reloads inventories (main-thread state).
    private static void runOnMain(BiConsumer<Boolean, Integer> callback, boolean exit, int exitCode) {
        Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> callback.accept(exit, exitCode));
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/command/InventoryUtil.java
git commit -m "fix(concurrency): run /inv download|upload callback on main thread + close executor (Wave C)"
```

---

## Task 1b: RpReleaseUtil — release callback on main thread + executor shutdown

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpReleaseUtil.java`

`releaseResourcePack` (lines 18-52) is the identical shape to Task 1: async body, `callback.accept(...)` invoked off-thread (lines 30/46/49), `Executors.newSingleThreadExecutor()` (line 40) never shut down.

- [ ] **Step 1: Replace `releaseResourcePack` and add a `runOnMain` helper** — apply the identical transform as Task 1: hoist `exit`/`exitCode`/`executorService` out of the `try`, deliver every result through `runOnMain(callback, exit, exitCode)` (a `runTask` hop), and `executorService.shutdownNow()` in a `finally`. **Preserve** the original `callback.accept(true, -1)` value (not `false`) on the isWindows/null-config early return. `runOnMain` is the same 2-line helper as Task 1.

- [ ] **Step 2: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpReleaseUtil.java
git commit -m "fix(concurrency): run RP release callback on main thread + close executor (Wave C)"
```

---

## Task 2: HeadDataBuilderPlayer — full HTTP off-thread, result on main

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/customHeadManager/HeadDataBuilderPlayer.java`

The current class does only `connect()` async, then reads `getResponseCode()`/the stream and parses JSON on a main-thread `runTaskTimer` — blocking the main thread until the response arrives — and shares `received`/`connection` across threads without `volatile`. Replace the whole class body: each lookup becomes a single async round-trip that hops only the Bukkit-touching result back to main. The instance fields `received` and `connection` are removed.

- [ ] **Step 1: Replace the class body** (lines 41-165, from `public class HeadDataBuilderPlayer {` through the final closing brace):

```java
public class HeadDataBuilderPlayer {

    private final String mojangSkinUrl = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    private final String mojangUuidUrl = "https://api.mojang.com/users/profiles/minecraft/%s";

    public HeadDataBuilderPlayer(final Player submitter, final UUID ownerId, final String name) {
        fetchCustomHeadData(submitter, ownerId, name);
    }

    public HeadDataBuilderPlayer(final Player submitter, final String ownerName, final String name) {
        fetchCustomHeadData(submitter, ownerName, name);
    }

    /** Resolve a player name to a UUID (async), then continue on the main thread. */
    private void fetchCustomHeadData(final Player submitter, final String ownerName, final String name) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject jsonObject = readJson(String.format(mojangUuidUrl, ownerName));
                    if (jsonObject == null) {
                        sendOnMain(submitter, "Player name not found.");
                        return;
                    }
                    final UUID ownerId = UUID.fromString(jsonObject.get("id").getAsString()
                            .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                    runOnMain(() -> fetchCustomHeadData(submitter, ownerId, name));
                } catch (IOException | JsonSyntaxException | IllegalStateException | NullPointerException ex) {
                    Log.error("Failed Mojang UUID lookup for player name " + ownerName, ex);
                    sendOnMain(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    /** Fetch a player's skin texture (async), then submit the head on the main thread. */
    private void fetchCustomHeadData(final Player submitter, final UUID ownerId, final String name) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    JsonObject jsonObject = readJson(String.format(mojangSkinUrl, ownerId.toString().replace("-", "")));
                    if (jsonObject == null) {
                        sendOnMain(submitter, "Error. Invalid UUID or too many requests. Wait one minute at least before you try again.");
                        return;
                    }
                    String textures = "";
                    for (JsonElement jProperty : jsonObject.getAsJsonArray("properties")) {
                        if (jProperty.getAsJsonObject().has("name")
                                && jProperty.getAsJsonObject().get("name").getAsString().equals("textures")) {
                            textures = jProperty.getAsJsonObject().get("value").getAsString();
                            break;
                        }
                    }
                    JsonObject skin = JsonParser.parseString(new String(BaseEncoding.base64().decode(textures)))
                            .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                    final String url = BaseEncoding.base64().encode(
                            String.format("{textures:{SKIN:{url:\"%s\"}}}", skin.get("url").getAsString()).getBytes());
                    runOnMain(() -> {
                        CustomHeadData headData = new CustomHeadData(ownerId, url);
                        if (CustomHeadManagerData.addReviewHead(name, headData)) {
                            PluginData.getMessageUtil().sendInfoMessage(submitter, "Head has been submitted.");
                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(submitter, "Error. Your head has not been submitted.");
                        }
                    });
                } catch (IOException | JsonSyntaxException | IllegalStateException | NullPointerException ex) {
                    Log.error("Failed Mojang skin lookup for player " + ownerId, ex);
                    sendOnMain(submitter, "Error. Your head has not been submitted.");
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }

    /** Blocking HTTP GET on the CALLING (async) thread. @return parsed body, or null on a non-200. */
    private static JsonObject readJson(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return (JsonObject) JsonParser.parseReader(reader);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void runOnMain(Runnable task) {
        new BukkitRunnable() {
            @Override public void run() { task.run(); }
        }.runTask(ArchitectPlugin.getPluginInstance());
    }

    private static void sendOnMain(final Player submitter, final String message) {
        runOnMain(() -> PluginData.getMessageUtil().sendErrorMessage(submitter, message));
    }
}
```

- [ ] **Step 2: Compile** (this removes the `received`/`connection` fields and the deprecated `new JsonParser().parse(...)` call).

Run: `mvn -q compile`
Expected: BUILD SUCCESS. If an unused import remains (`java.util.concurrent`-style), remove it.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/customHeadManager/HeadDataBuilderPlayer.java
git commit -m "fix(concurrency): run /chead submit Mojang HTTP fully off-thread, result on main (Wave C)"
```

---

## Task 3: RpManager per-player maps — concurrent + quit cleanup

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpManager.java` (map fields `:62`/`:64`; add `removePlayerData`)
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpListener.java` (`playerQuit`, `:137`)
- Test: `src/test/java/com/mcmiddleearth/architect/serverResoucePack/RpManagerCleanupTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.mcmiddleearth.architect.serverResoucePack;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class RpManagerCleanupTest {
    private static ServerMock server;

    @BeforeAll static void setUp() { server = MockBukkit.mock(); MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void quitRemovesPlayerRpData() {
        PlayerMock player = server.addPlayer(); // fires PlayerJoinEvent
        RpManager.getPlayerData(player);        // ensure an entry exists
        assertTrue(RpManager.hasPlayerDataLoaded(player), "precondition: player data present");

        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(
                player, net.kyori.adventure.text.Component.text("quit")));

        assertFalse(RpManager.hasPlayerDataLoaded(player),
                "playerRpData must be cleared when the player quits");
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -Dtest=RpManagerCleanupTest`
Expected: FAIL — `hasPlayerDataLoaded` still true after quit (no cleanup yet). If `getPlayerData`/`hasPlayerDataLoaded` signatures differ, adjust the test to the real API (they are referenced in `RpListener`).

- [ ] **Step 3: Make the maps concurrent** — in `RpManager.java`, change the fields (currently `:62`/`:64`):

```java
    private static final Map<UUID,RpPlayerData> playerRpData = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Map<UUID, String> sodiumClients = new java.util.concurrent.ConcurrentHashMap<>();
```

(Or add `import java.util.concurrent.ConcurrentHashMap;` and use the short name — match the file's import style.)

- [ ] **Step 4: Add `removePlayerData`** to `RpManager` (near the other player-data methods):

```java
    public static void removePlayerData(Player player) {
        playerRpData.remove(player.getUniqueId());
    }
```

- [ ] **Step 5: Call it on quit** — in `RpListener.playerQuit` (`:137`), add the cleanup alongside the existing sodium removal:

```java
    @EventHandler
    public void playerQuit(PlayerQuitEvent event) {
        RpManager.removeSodiumClient(event.getPlayer());
        RpManager.removePlayerData(event.getPlayer());
    }
```

- [ ] **Step 6: Run to verify pass**

Run: `mvn test -Dtest=RpManagerCleanupTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpManager.java \
        src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpListener.java \
        src/test/java/com/mcmiddleearth/architect/serverResoucePack/RpManagerCleanupTest.java
git commit -m "fix(concurrency): make playerRpData concurrent + clear it on quit (Wave C)"
```

---

## Task 4: Verification + tracker

- [ ] **Step 1: Full test run** — `mvn test`. Expected: BUILD SUCCESS, all prior tests + `RpManagerCleanupTest` green (26).

- [ ] **Step 2: Grep for residual async→main hazards in the touched files** — `grep -n "runTaskAsynchronously\|runTaskTimer\|newSingleThreadExecutor" src/main/java/com/mcmiddleearth/architect/specialBlockHandling/command/InventoryUtil.java src/main/java/com/mcmiddleearth/architect/customHeadManager/HeadDataBuilderPlayer.java` — confirm no main-thread blocking read / unclosed executor remains.

- [ ] **Step 3: Update the tracker** — in `AUDIT-REMEDIATION-PLAN.md`, mark the Risk F items done that this slice covers (`/inv download` hop, `/chead submit` off-thread, `playerRpData` concurrency) and record the deferred follow-ups (CopyPaste/BlockCycle leak cleanup, `refreshSHA` stream+config, `EntityLogger` CME, `RpReleaseUtil` executor). Do not reference the local-only `docs/security/path-traversal-fix.md`.

- [ ] **Step 4: Commit** the tracker.

- [ ] **Step 5: Independent adversarial review** of the slice (concurrency is exactly where an independent pass pays off): verify no Bukkit call remains on an async path, no async write to a non-concurrent collection, no new deadlock/lost-callback, and the HTTP refactor preserves the name→UUID→skin chain.

---

## Self-Review Notes (author)

- **Scope vs audit Risk F:** `/inv download` async callback (T1) ✓; executor leak `InventoryUtil.java:44` (T1) ✓; `/chead submit` main-thread HTTP + non-volatile fields (T2) ✓; `playerRpData` async-write (T3) ✓. Deferred items are listed in the header + Task 4 (not silently dropped).
- **T2 correctness:** the two-step chain is preserved — name→UUID runs async, hops to main, which re-enters the UUID→skin path (again async); only `addReviewHead`/`sendMessage` run on main. Removing `received`/`connection` eliminates the unsafe-publication race. Broadened the catch to include `IllegalStateException`/`NullPointerException` from malformed JSON so a bad response can't kill the async task silently.
- **T1 `exit`/`exitCode`** are passed as method arguments to `runOnMain` (not captured in a lambda), so no effectively-final problem despite reassignment.
- **Testability:** T3 is TDD (MockBukkit quit event). T1/T2 are concurrency/HTTP — compile + the Task 5 adversarial review, consistent with how the prior slices handled non-unit-testable code.
