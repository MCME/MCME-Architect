# Wave C — Load-Guards (Failure-Blind Loaders) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCME-Architect's YAML/zip loaders fail-fast-and-skip instead of fail-blind: one corrupt file must never (a) NPE the plugin's `onEnable`, nor (b) get silently overwritten with empty data (the "empty-then-writeback" data-loss pattern) — Risk D from the audit.

**Architecture:** No shared helper — each loader gets a targeted guard because the correct failure action differs per site. Two shapes: **Mode 1 (NPE-on-enable)** loaders return `null`/skip the bad file instead of dereferencing an empty config; the enable-time loop skips the null. **Mode 2 (empty-then-writeback)** loaders detect a failed load explicitly (not Bukkit's error-swallowing `YamlConfiguration.loadConfiguration`) and refuse to save a config that failed to load — preserving the on-disk file for recovery. First-run (file absent) stays distinct from parse-error (file present but corrupt): the former may create a fresh file, the latter must not overwrite.

**Tech Stack:** Java 25, Maven, MockBukkit v26.1.2 4.114.0 + JUnit Jupiter 6.1.0 (`ServerMock` scheduler `performTicks` for the delayed-save test), `com.mcmiddleearth.architect.Log` facade.

**Branch:** `architect-rework-2026` (already checked out). Build note: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"` before `mvn`; never `mvn clean` (OneDrive locks `target/`).

---

## File Structure

| File | Bug mode | Fix |
| --- | --- | --- |
| `customHeadManager/CustomHeadData.java` | Mode 1 — `UUID.fromString(null)` NPE after swallowed load | `fromFile` returns null on load-fail or missing `headId`/`owner` |
| `customHeadManager/CustomHeadManagerData.java` | Mode 1 — null head added to collection | `load()` loop skips a null `fromFile` result |
| `specialBlockHandling/data/SpecialSavedInventoryData.java` | Mode 1 — `categoryConfig.get()` NPE | `loadFromFile` returns on load-fail + null-guards the `category` section |
| `WorldConfig.java` | Mode 2 — empty-on-parse-error then `saveWorldConfig` overwrite | explicit load + `worldConfigReadOnly` flag; `saveWorldConfig` refuses when read-only |
| `specialBlockHandling/data/SpecialBlockInventoryData.java` | Mode 2 — swallowed load then `config.save(file)` | `loadFromFile` returns on load-fail (no parse, no save-back) |
| `specialBlockHandling/data/GetData.java` | Mode 2 — swallowed load then scheduled `save()` wipes kits | distinguish first-run from corrupt; return on corrupt (no scheduled save); null-guard rows |
| `util/ZipUtil.java` | Mode 2 — clears target dir on empty result | only clear+replace when extraction produced ≥1 file |
| `src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java` | — | regression tests for the 3 flagship bugs (head enable-NPE, GetData wipe, WorldConfig wipe) |

**Test-coverage split (honest, per "no silent caps"):** direct MockBukkit regression tests cover the three highest-impact bugs (Tasks 1, 3, 5). Tasks 2 (SpecialSaved), 4 (SpecialBlockInventory), 6 (ZipUtil) are the same two fix patterns applied to less-reachable private loaders — verified by compile + the final adversarial review, not a dedicated test.

---

## Task 1: CustomHeadData.fromFile + CustomHeadManagerData.load (Mode 1 — the enable-killer)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadData.java` (`fromFile`, currently `:50-61`)
- Modify: `src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerData.java` (`load()` loop, currently `:68-74`)
- Test: `src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java` (create)

- [ ] **Step 1: Write the failing tests** (create `LoadGuardTest.java`)

```java
package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.customHeadManager.CustomHeadData;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.specialBlockHandling.data.GetData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class (Architect caches getDataFolder()-derived paths in static fields at
// class-load); surefire reuseForks=false gives a fresh JVM per class. Mirrors LogFileTest.
class LoadGuardTest {
    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll static void setUp() { server = MockBukkit.mock(); plugin = MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    // ---- Task 1: Mode 1 (corrupt head must not NPE) ----
    @Test void fromFileReturnsNullOnCorruptOrIncompleteHead(@TempDir File tmp) throws Exception {
        File corrupt = new File(tmp, "bad.yml");
        Files.writeString(corrupt.toPath(), ":\n  not: [valid");
        assertNull(CustomHeadData.fromFile(corrupt), "corrupt head file must yield null, not NPE");

        File incomplete = new File(tmp, "incomplete.yml");
        Files.writeString(incomplete.toPath(), "texture: abc\n"); // no headId/owner
        assertNull(CustomHeadData.fromFile(incomplete), "head missing headId/owner must yield null, not NPE");
    }

    @Test void customHeadLoadSkipsCorruptFileWithoutThrowing() throws Exception {
        File accepted = new File(plugin.getDataFolder(), "customHeads/accepted");
        assertTrue(accepted.exists() || accepted.mkdirs());
        Files.writeString(new File(accepted, "broken.yml").toPath(), ":\n  not: [valid");
        assertDoesNotThrow(CustomHeadManagerData::load,
                "one corrupt head file must not break the enable-time load");
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"; mvn test -Dtest=LoadGuardTest`
Expected: FAIL — `fromFile` throws `NullPointerException` (from `UUID.fromString(null)`) instead of returning null; `load()` throws too.

- [ ] **Step 3: Fix `CustomHeadData.fromFile`** — replace `:50-61`:

```java
    public static CustomHeadData fromFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Log.error("Failed to load custom head data file " + file.getAbsolutePath() + "; skipping it.", ex);
            return null;
        }
        String headId = config.getString("headId");
        String owner = config.getString("owner");
        if (headId == null || owner == null) {
            Log.warn("Custom head data file " + file.getAbsolutePath()
                    + " is missing required 'headId'/'owner'; skipping it.");
            return null;
        }
        return new CustomHeadData(UUID.fromString(headId), UUID.fromString(owner), config.getString("texture"));
    }
```

- [ ] **Step 4: Fix the enable loop in `CustomHeadManagerData.load()`** — replace `:68-74`:

```java
        List<File> headFiles = getFiles(acceptedHeadDir);
        for(File headFile : headFiles) {
            CustomHeadData data = CustomHeadData.fromFile(headFile);
            if(data == null) {
                continue; // fromFile already logged why; skip the bad file, keep loading the rest
            }
            String headName = headFile.toString().substring(0,headFile.toString().lastIndexOf("."));
            headName = headName.replace('\\', '/');
            headName = headName.substring(acceptedHeadDir.toString().length()+1);
            collection.addHead(headName, data);
        }
```

- [ ] **Step 5: Run to verify pass**

Run: `mvn test -Dtest=LoadGuardTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadData.java \
        src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerData.java \
        src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java
git commit -m "fix(reliability): custom-head loader skips corrupt files instead of NPE-ing enable (Wave C)"
```

---

## Task 2: SpecialSavedInventoryData.loadFromFile (Mode 1)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/SpecialSavedInventoryData.java` (`loadFromFile`, currently `:82-94`)

- [ ] **Step 1: Apply the fix** — replace `:84-93`:

```java
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Log.error("Failed to load saved inventory file " + file + " for RP " + rpName + "; skipping it.", ex);
            return;
        }
        String categoryName = file.getName().substring(0,file.getName().length()-4);
        ConfigurationSection categoryConfig = config.getConfigurationSection("category");
        if(categoryConfig == null) {
            Log.warn("Saved inventory file " + file + " for RP " + rpName + " has no 'category' section; skipping it.");
            return;
        }
        ItemStack item = (ItemStack) categoryConfig.get("item");
        String ownerString = categoryConfig.getString("owner");
        if(ownerString == null) {
            Log.warn("Saved inventory file " + file + " for RP " + rpName + " has no owner; skipping it.");
            return;
        }
        UUID owner = UUID.fromString(ownerString);
```

(This replaces the original lines from `YamlConfiguration config = new YamlConfiguration();` through `UUID owner = UUID.fromString(categoryConfig.getString("owner"));`. The `boolean isPrivate = categoryConfig.getBoolean("isPrivate");` line and everything after it is unchanged.)

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/SpecialSavedInventoryData.java
git commit -m "fix(reliability): saved-inventory loader skips corrupt files instead of NPE (Wave C)"
```

---

## Task 3: WorldConfig — no empty-then-writeback (Mode 2, flagship)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/WorldConfig.java` (add field near `:63`; load branch `:111-113`; `saveWorldConfig` `:156-162`)
- Test: `src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java` (add a test)

- [ ] **Step 1: Add the failing test** (append inside `LoadGuardTest`)

```java
    // ---- Task 3: Mode 2 (corrupt world config must not be overwritten) ----
    @Test void worldConfigDoesNotOverwriteCorruptFile() throws Exception {
        File worldDir = new File(plugin.getDataFolder(), "WorldConfig");
        assertTrue(worldDir.exists() || worldDir.mkdirs());
        File wf = new File(worldDir, "traptown.yml");
        String original = "moduleX: [unclosed";      // invalid YAML
        Files.writeString(wf.toPath(), original);

        WorldConfig wc = new WorldConfig("traptown", new YamlConfiguration());
        wc.setModuleEnabled(Modules.values()[0], false); // would trigger saveWorldConfig

        assertEquals(original, Files.readString(wf.toPath()),
                "a world config that failed to load must not be overwritten by a toggle");
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -Dtest=LoadGuardTest`
Expected: FAIL — `worldConfigDoesNotOverwriteCorruptFile`: the file gets replaced with the empty-config serialization, so it no longer equals `original`.

- [ ] **Step 3: Add the read-only flag field** — after the `worldConfig` field (`:63` `private final YamlConfiguration worldConfig;`), add:

```java
    private boolean worldConfigReadOnly = false;
```

- [ ] **Step 4: Detect the failed load** — replace the load branch (`:111-113`):

```java
        } else {
            YamlConfiguration loaded = new YamlConfiguration();
            try {
                loaded.load(configFile);
            } catch (IOException | InvalidConfigurationException ex) {
                Log.error("Failed to load world config for world " + worldName + " from "
                        + configFile.getAbsolutePath()
                        + "; using defaults and NOT overwriting the file (fix the YAML and reload).", ex);
                worldConfigReadOnly = true;
            }
            worldConfig = loaded;
        }
```

(Requires `import org.bukkit.configuration.InvalidConfigurationException;` — add it if not already imported.)

- [ ] **Step 5: Guard the save** — replace `saveWorldConfig` (`:156-162`):

```java
    private void saveWorldConfig() {
        if (worldConfigReadOnly) {
            Log.warn("Refusing to save world config for world " + worldName
                    + " because it failed to load (would overwrite the on-disk file with incomplete data).");
            return;
        }
        try {
            worldConfig.save(getConfigFile());
        } catch (IOException ex) {
            Log.error("Failed to save world config file for world " + worldName, ex);
        }
    }
```

- [ ] **Step 6: Run to verify pass**

Run: `mvn test -Dtest=LoadGuardTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/WorldConfig.java \
        src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java
git commit -m "fix(reliability): never overwrite a world config that failed to load (Wave C)"
```

---

## Task 4: SpecialBlockInventoryData.loadFromFile (Mode 2)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/SpecialBlockInventoryData.java` (`loadFromFile`, load `:155-160`, save `:186-190`)

- [ ] **Step 1: Return on load failure** — replace the load `try/catch` (`:155-160`):

```java
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            Log.error("Failed to load block inventory file " + file + " for RP " + rpName
                    + "; skipping it (not overwriting).", ex);
            return;
        }
```

This makes the `return` skip the unconditional `config.save(file)` at the end of the method (currently `:186-190`), so a file that failed to load is never rewritten empty. Leave the rest of the method unchanged.

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/SpecialBlockInventoryData.java
git commit -m "fix(reliability): never overwrite a block-inventory file that failed to load (Wave C)"
```

---

## Task 5: GetData.load — no scheduled wipe of corrupt kits (Mode 2)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/GetData.java` (`load()`, currently `:152-176`)
- Test: `src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java` (add a test)

- [ ] **Step 1: Add the failing test** (append inside `LoadGuardTest`)

```java
    // ---- Task 5: Mode 2 (corrupt itemSets.yml must not be wiped by the delayed save) ----
    @Test void getDataDoesNotWipeCorruptFile() throws Exception {
        server.getScheduler().performTicks(2100L); // flush any save scheduled during enable
        File dataFile = new File(plugin.getDataFolder(), "itemSets.yml");
        String original = "set1:\n  owner: not-a-uuid\n  : broken";
        Files.writeString(dataFile.toPath(), original);

        GetData.load();
        server.getScheduler().performTicks(2100L); // a scheduled save (if any) fires here

        assertEquals(original, Files.readString(dataFile.toPath()),
                "a corrupt itemSets.yml must be preserved, not overwritten with empty");
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -Dtest=LoadGuardTest`
Expected: FAIL — `getDataDoesNotWipeCorruptFile`: `load()` swallows the parse error, schedules `save()`, and after `performTicks` the file is overwritten with the serialization of the (empty) `sets`.

- [ ] **Step 3: Apply the fix** — replace `load()` (`:152-176`):

```java
    public static void load() {
        YamlConfiguration config = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                config.load(dataFile);
            } catch (IOException | InvalidConfigurationException ex) {
                Log.error("Failed to load item sets from " + dataFile
                        + "; keeping the file untouched for recovery (not overwriting).", ex);
                return; // do NOT schedule the save that would wipe the corrupt file
            }
        }
        for(String name: config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(name);
            if(section == null) {
                continue;
            }
            String ownerString = section.getString("owner");
            if(ownerString == null) {
                Log.warn("Item set '" + name + "' in " + dataFile + " has no owner; skipping it.");
                continue;
            }
            ItemSet itemSet = new ItemSet(null,null,false,null);
            itemSet.owner = UUID.fromString(ownerString);
            itemSet.description = section.getString("description","no description");
            itemSet.isPrivate = section.getBoolean("isPrivate",false);
            itemSet.items = section.getList("items", new ArrayList<ItemStack>())
                                   .toArray(new ItemStack[0]);
            sets.put(name, itemSet);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                save();
            }
        }.runTaskLater(ArchitectPlugin.getPluginInstance(),2000);
    }
```

(First run — `dataFile` absent — still proceeds with an empty config and schedules the save that creates the file, preserving current behaviour. Only a *present-but-corrupt* file short-circuits.)

- [ ] **Step 4: Run to verify pass**

Run: `mvn test -Dtest=LoadGuardTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/GetData.java \
        src/test/java/com/mcmiddleearth/architect/LoadGuardTest.java
git commit -m "fix(reliability): never wipe a corrupt itemSets.yml via the delayed save (Wave C)"
```

---

## Task 6: ZipUtil.extract — no destructive empty result (Mode 2)

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/util/ZipUtil.java` (the post-extract replace block, currently `:94-108`)

- [ ] **Step 1: Guard the clear-and-replace** — replace the block (`:94-107`, from `File[] files = temp.listFiles();` through the closing brace before `temp.delete();`):

```java
            File[] files = temp.listFiles();
            if(files != null && files.length > 0) {
                downloadedFiles = files.length;
                File[] existing = outPath.listFiles();
                if(existing != null) {
                    for(File file: existing) {
                        if(!file.equals(temp)) {
                            file.delete();
                        }
                    }
                }
                for(File file: files) {
                    Files.move(file.toPath(), new File(outPath,file.getName()).toPath(),
                               StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Log.warn("Zip extraction for " + sourceURL + " matched no entries; keeping the existing files in "
                        + outPath.getAbsolutePath() + " instead of clearing them.");
            }
```

Then `temp.delete();` immediately follows (unchanged).

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/util/ZipUtil.java
git commit -m "fix(reliability): zip extract keeps existing files when download matched nothing (Wave C)"
```

---

## Task 7: Verification + tracker

- [ ] **Step 1: Full test run**

Run: `mvn test`
Expected: BUILD SUCCESS; `LoadGuardTest` (4) + all prior Wave C / logging tests green (24 total).

- [ ] **Step 2: Grep for any remaining swallow-then-save** — sanity check no `catch (...) { log }` is immediately followed by a `config.save` on the same config in the touched files:

Run: `grep -rn "loadConfiguration(" src/main/java` — expected: no *remaining* use that feeds a later `save()` of the same object (the WorldConfig one is now explicit-load). Manually confirm each hit is read-only.

- [ ] **Step 3: Update the tracker** — in `AUDIT-REMEDIATION-PLAN.md`, mark Risk D and Pattern 3 (empty-then-writeback) fixed on `architect-rework-2026`, referencing the six loaders. Do not reference the local-only `docs/security/path-traversal-fix.md`.

- [ ] **Step 4: Commit**

```bash
git add AUDIT-REMEDIATION-PLAN.md
git commit -m "docs: mark Risk D (failure-blind loaders) fixed in Wave C load-guards slice"
```

---

## Self-Review Notes (author)

- **Spec coverage vs audit Risk D:** `CustomHeadData.fromFile` NPE (Task 1) ✓; `SpecialSavedInventoryData:86` NPE (Task 2) ✓; `WorldConfig` empty-writeback (Task 3) ✓; `SpecialBlockInventoryData` empty-writeback (Task 4) ✓; `GetData` scheduled wipe (Task 5) ✓; `ZipUtil` destructive empty-result (Task 6) ✓. All six audit sites covered.
- **First-run vs corrupt** is handled explicitly only where a missing file is legitimate (GetData — `dataFile.exists()` gate). The others load existing per-file entries, where absence just means the loop doesn't run.
- **`worldConfig` is `final`** → the fix uses a separate `worldConfigReadOnly` flag rather than reassigning; verified the flag is set before any `saveWorldConfig` call (set in the constructor's load branch, read in `saveWorldConfig`).
- **Test reachability:** Tasks 1/3/5 have direct regression tests; 2/4/6 apply the identical proven pattern to private loaders and are covered by compile + the final adversarial review (stated in the File Structure section, not hidden).
