# Wave C — Path-Safety (Directory Traversal) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the directory-traversal vulnerability class in MCME-Architect: user-supplied names (`/chead`, `/banner`, `/armor`, `/vv` stencils) and archive entry names (`ZipUtil`) must never resolve to a file outside their intended base directory, and the recursive head-delete walk must never ascend above its base or NPE.

**Architecture:** Introduce one tested utility — `com.mcmiddleearth.util.PathSafety` — that resolves a child name inside a base directory using **canonical-path containment** (resolve the absolute path, verify it is the base or a descendant). Every sink that today does `new File(baseDir + "/" + userInput)` is replaced with `PathSafety.resolveInside(baseDir, userInput, ext)`, wrapped so a rejected name aborts the operation cleanly (log + user error / return false / return null) instead of touching the filesystem. The head-delete walk (`CustomHeadManagerData.removeFileAndDirectory`) additionally gets a defense-in-depth bound so it stops at its base directory and null-checks `listFiles()`.

**Tech Stack:** Java 25, Maven, JUnit Jupiter 6.1.0 (pure unit tests with `@TempDir` — **no MockBukkit needed** for the helper), MockBukkit v26.1.2 4.114.0 (only for the one wiring test that loads the plugin). `com.mcmiddleearth.architect.Log` is the logging facade.

**Branch:** `architect-rework-2026` (already checked out). Do NOT work on `main`.

**Deferred out of this slice (tracked, not dropped):** the `/inv` category-name sinks (`SpecialSavedInventoryData:109,146,161`, `CustomInventoryEditor:112,228`) are folded into the `/inv` authorization item of a later Wave C slice; region-file sinks driven by non-user names (`RpManager:143,508`, `ItemBlockManager:137,153`, `CopyPasteManager` UUID names, `WorldConfig` world names) are not arbitrary user input and are out of scope here. Task 7 records this explicitly so nothing is silently skipped.

---

## File Structure

| File | Responsibility | Change |
| --- | --- | --- |
| `src/main/java/com/mcmiddleearth/util/PathSafety.java` | The containment helper — the entire security boundary lives here | **Create** |
| `src/test/java/com/mcmiddleearth/util/PathSafetyTest.java` | Exhaustive pure tests of the boundary | **Create** |
| `src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerData.java` | `/chead` delete/reject/rename/get — the delete-rights sinks + the recursive walk | **Modify** |
| `src/test/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerTraversalTest.java` | Proves the sinks are wired to the helper (reject → false, no throw) | **Create** |
| `src/main/java/com/mcmiddleearth/architect/bannerEditor/BannerEditorConfig.java` | `/banner` save/load/delete | **Modify** |
| `src/main/java/com/mcmiddleearth/architect/armorStand/ArmorStandEditorConfig.java` | `/armor` save/load/delete/rename | **Modify** |
| `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/VvCommand.java` | `/vv` view stencil/list by name | **Modify** |
| `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/StencilList.java` | stencil list load by name | **Modify** |
| `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/SlCommand.java` | stencil search by name | **Modify** |
| `src/main/java/com/mcmiddleearth/util/ZipUtil.java` | archive extraction (Zip-Slip) | **Modify** |

---

## The Uniform Transformation (applies in Tasks 2–6)

Every sink follows the identical shape. **BEFORE:**

```java
File file = new File(baseDir + "/" + userInput + "." + ext);   // or new File(baseDir, userInput + "." + ext)
// ... code that saves / loads / deletes `file`
```

**AFTER:**

```java
File file;
try {
    file = PathSafety.resolveInside(baseDir, userInput, ext);   // ext-less overload when there is no "." + ext
} catch (SecurityException ex) {
    Log.warn("Rejected unsafe name '" + userInput + "' for <operation>: " + ex.getMessage());
    <ABORT>;   // see failure-action rule below
}
// ... unchanged code that saves / loads / deletes `file`
```

**Failure-action rule (`<ABORT>`), by method kind:**
- **Command handler** (`onCommand`, has a `sender`): `PluginData.getMessageUtil().sendErrorMessage(sender, "Invalid name."); return true;`
- **Method returning `boolean`** (save/delete success): `return false;`
- **Method returning an object** (a getter/loader): `return null;`
- **`void` method:** `return;`

**Imports** to add to each modified `.java`: `import com.mcmiddleearth.util.PathSafety;` and (if not already present) `import com.mcmiddleearth.architect.Log;`. Remove any now-unused `import java.util.logging.Logger;` only if the file no longer references `Logger`.

---

## Task 1: PathSafety helper + exhaustive tests

**Files:**
- Create: `src/main/java/com/mcmiddleearth/util/PathSafety.java`
- Test: `src/test/java/com/mcmiddleearth/util/PathSafetyTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.mcmiddleearth.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class PathSafetyTest {

    @Test void resolvesLegitNameWithExtension(@TempDir File base) {
        File out = PathSafety.resolveInside(base, "myhead", "yml");
        assertEquals(new File(base, "myhead.yml").getPath(), out.getPath());
    }

    @Test void resolvesLegitNameWithoutExtension(@TempDir File base) {
        File out = PathSafety.resolveInside(base, "myhead");
        assertEquals(new File(base, "myhead").getPath(), out.getPath());
    }

    @Test void allowsNestedSubdirectory(@TempDir File base) {
        // A path INSIDE the base (with a separator) is not an escape.
        File out = PathSafety.resolveInside(base, "sub/head", "yml");
        assertTrue(PathSafety.isInside(base, out));
    }

    @Test void rejectsParentTraversal(@TempDir File base) {
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, "../evil", "yml"));
    }

    @Test void rejectsDeepParentTraversal(@TempDir File base) {
        assertThrows(SecurityException.class,
                () -> PathSafety.resolveInside(base, "../../../secret", "yml"));
    }

    @Test void rejectsPlatformSeparatorTraversal(@TempDir File base) {
        assertThrows(SecurityException.class,
                () -> PathSafety.resolveInside(base, ".." + File.separator + "evil", "yml"));
    }

    @Test void rejectsNullAndBlankNames(@TempDir File base) {
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, null));
        assertThrows(SecurityException.class, () -> PathSafety.resolveInside(base, "   "));
    }

    @Test void isInsideAllowsBaseItselfAndDescendants(@TempDir File base) {
        assertTrue(PathSafety.isInside(base, base));
        assertTrue(PathSafety.isInside(base, new File(base, "child.yml")));
    }

    @Test void isInsideRejectsParentAndSiblingPrefix(@TempDir File base) {
        assertFalse(PathSafety.isInside(base, base.getParentFile()));
        // Sibling with a shared string prefix must NOT count as inside (e.g. base "foo" vs "foobar").
        File sibling = new File(base.getParentFile(), base.getName() + "bar");
        assertFalse(PathSafety.isInside(base, sibling));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=PathSafetyTest test`
Expected: FAIL — compilation error, `PathSafety` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (C) 2026 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.mcmiddleearth.util;

import java.io.File;
import java.io.IOException;

/**
 * Guards against directory traversal. Every place that turns a user- or archive-supplied name into a
 * {@link File} inside a fixed base directory must go through {@link #resolveInside} so that inputs
 * such as {@code ../../server.properties} cannot escape the base.
 * <p>
 * The check is canonical-path containment, not name blocklisting: the intended file's absolute,
 * symlink-resolved path must equal the base directory or sit beneath it. This is immune to the
 * {@code ..} / encoding / symlink tricks that defeat character filtering.
 */
public final class PathSafety {

    private PathSafety() {}

    /** Resolve {@code childName + "." + extension} inside {@code baseDir}, verifying containment. */
    public static File resolveInside(File baseDir, String childName, String extension) {
        String ext = (extension == null) ? "" : "." + extension;
        return resolveInside(baseDir, childName + ext);
    }

    /**
     * Resolve {@code childName} inside {@code baseDir}, verifying containment.
     *
     * @throws SecurityException if {@code childName} is null/blank or resolves outside {@code baseDir}.
     */
    public static File resolveInside(File baseDir, String childName) {
        if (childName == null || childName.isBlank()) {
            throw new SecurityException("Empty file name is not allowed in " + baseDir);
        }
        File candidate = new File(baseDir, childName);
        if (!isInside(baseDir, candidate)) {
            throw new SecurityException("File name '" + childName
                    + "' resolves outside the allowed directory " + baseDir);
        }
        return candidate;
    }

    /** @return true iff {@code child}'s canonical path is {@code baseDir} itself or a descendant of it. */
    public static boolean isInside(File baseDir, File child) {
        try {
            String base = baseDir.getCanonicalPath();
            String target = child.getCanonicalPath();
            // Guard the sibling-prefix case ("/a/foo" must not contain "/a/foobar") with the separator.
            return target.equals(base) || target.startsWith(base + File.separator);
        } catch (IOException e) {
            // If the path cannot be canonicalised, treat it as unsafe.
            return false;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=PathSafetyTest test`
Expected: PASS — 9 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcmiddleearth/util/PathSafety.java \
        src/test/java/com/mcmiddleearth/util/PathSafetyTest.java
git commit -m "feat(security): add PathSafety canonical-containment helper (Wave C)"
```

---

## Task 2: CustomHeadManagerData — apply helper at all sinks + harden the delete walk

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerData.java`
- Test: `src/test/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerTraversalTest.java`

**Sinks to convert** (base dir shown; all use `fileExtension` = `"yml"` unless the literal `".yml"` is already inline):
`112` (name → submittedHeadDir), `125` (name → acceptedHeadDir), `150` (newName → acceptedHeadDir), `159` (name → submittedHeadDir), `171` `rejectHead` (name → submittedHeadDir), `198`/`203` `addHead` (name / name+index → `dir` param), `210` `deleteHead` (name → acceptedHeadDir), `226`/`227` `renameHead` (oldName/newName → acceptedHeadDir), `259` `getSubmittedHeadData` (name → submittedHeadDir), `267` `getHeadData` (name → acceptedHeadDir).

- [ ] **Step 1: Write the failing wiring test**

```java
package com.mcmiddleearth.architect.customHeadManager;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

// One mock/load per class (Architect caches getDataFolder()-derived paths in static fields at
// class-load); surefire reuseForks=false gives a fresh JVM per test class. Mirrors LogFileTest.
class CustomHeadManagerTraversalTest {

    @BeforeAll static void setUp() { MockBukkit.mock(); MockBukkit.load(ArchitectPlugin.class); }
    @AfterAll  static void tearDown() { if (MockBukkit.isMocked()) MockBukkit.unmock(); }

    @Test void deleteRejectsTraversalNameWithoutThrowing() {
        assertFalse(CustomHeadManagerData.deleteHead("../../evil"),
                "a traversal name must be rejected, not treated as a real head");
    }

    @Test void rejectRejectsTraversalNameWithoutThrowing() {
        assertFalse(CustomHeadManagerData.rejectHead("../../evil"));
    }

    @Test void getHeadDataReturnsNullForTraversalName() {
        assertNull(CustomHeadManagerData.getHeadData("../../evil"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=CustomHeadManagerTraversalTest test`
Expected: FAIL — before the fix, `deleteHead("../../evil")` builds an escaping `File`; if that path happens not to exist it returns `false` (test may pass accidentally) but `getHeadData` will throw / return non-null. The reliable signal is after Step 3. If it passes accidentally at this step, proceed — Step 4 is the gate.

- [ ] **Step 3: Convert every sink and harden `removeFileAndDirectory`**

Add imports `com.mcmiddleearth.util.PathSafety` and `com.mcmiddleearth.architect.Log`.

Apply the Uniform Transformation at each sink line above. Worked examples (use these verbatim; the other sinks are identical in shape):

`deleteHead` (currently line 209–223):
```java
public static boolean deleteHead(String name) {
    File file;
    try {
        file = PathSafety.resolveInside(acceptedHeadDir, name, fileExtension);
    } catch (SecurityException ex) {
        Log.warn("Rejected unsafe custom-head name '" + name + "' for delete: " + ex.getMessage());
        return false;
    }
    if(file.exists()) {
        if(gallery!=null) {
            gallery.remove();
        }
        collection.removeHead(name);
        if(gallery!=null) {
            gallery.place();
        }
        removeFileAndDirectory(file);
        return true;
    }
    return false;
}
```

`rejectHead` (currently 170–177):
```java
public static boolean rejectHead(String name) {
    File file;
    try {
        file = PathSafety.resolveInside(submittedHeadDir, name, fileExtension);
    } catch (SecurityException ex) {
        Log.warn("Rejected unsafe custom-head name '" + name + "' for reject: " + ex.getMessage());
        return false;
    }
    if(file.exists()) {
        removeFileAndDirectory(file);
        return true;
    }
    return false;
}
```

`getHeadData` (currently 266–267, returns `CustomHeadData`) and `getSubmittedHeadData` (259) — same wrap, `<ABORT>` = `return null;`. `renameHead` (226/227) — wrap BOTH `oldFile` and `newFile`, `<ABORT>` = `return false;`. `addHead(name, headData, dir)` (198/203) — wrap using the `dir` parameter as base, `<ABORT>` = `return false;`; keep the `while(file.exists())` index loop, re-resolving `name+index` through the helper. Lines 112/125/150/159 — apply the same wrap with the base dir shown above and the enclosing method's return convention.

Harden the recursive walk (currently 246–256) — bound it to the base dirs and null-check `listFiles()`:
```java
private static void removeFileAndDirectory(File file) {
    if(!PathSafety.isInside(acceptedHeadDir, file) && !PathSafety.isInside(submittedHeadDir, file)) {
        Log.warn("Refusing to delete '" + file + "': it is outside the custom-head directories.");
        return;
    }
    if(!file.isDirectory()) {
        file.delete();
        file = file.getParentFile();
    }
    File[] children;
    while(file != null
            && !file.equals(acceptedHeadDir) && !file.equals(submittedHeadDir)
            && (PathSafety.isInside(acceptedHeadDir, file) || PathSafety.isInside(submittedHeadDir, file))
            && (children = file.listFiles()) != null && children.length == 0) {
        file.delete();
        file = file.getParentFile();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=CustomHeadManagerTraversalTest test`
Expected: PASS — 3 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerData.java \
        src/test/java/com/mcmiddleearth/architect/customHeadManager/CustomHeadManagerTraversalTest.java
git commit -m "fix(security): guard custom-head file ops against path traversal (Wave C)"
```

---

## Task 3: BannerEditorConfig — `/banner` save/load/delete

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/bannerEditor/BannerEditorConfig.java`

Base dir: `dataDir` (`<dataFolder>/banners`, declared line 24). Sinks: `53` (save, `fileName`+`fileExtension`), `64`, `79`, `95`, `106` (`filename`+`.yml` — pass `"yml"` as ext), and the ext-less `99`, `114` (`new File(dataDir+"/"+filename)` → `PathSafety.resolveInside(dataDir, filename)`).

- [ ] **Step 1: Read each method** (`save`, `load`, `delete`, and any list/exists helper) to note its return type, then apply the Uniform Transformation at lines 53, 64, 79, 95, 99, 106, 114. Add the two imports.

- [ ] **Step 2: Compile**

Run: `mvn -q -o compile` (offline; falls back without `-o` if deps missing)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/bannerEditor/BannerEditorConfig.java
git commit -m "fix(security): guard banner file ops against path traversal (Wave C)"
```

---

## Task 4: ArmorStandEditorConfig — `/armor` save/load/delete/rename

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/armorStand/ArmorStandEditorConfig.java`

Base dir: `dataDir` (getDataFolder-based). Sinks (all `new File(dataDir, name + ext)`): `92`, `112` (save), `123`, `163`, `174`, `196` (`filename`), `197` (`newName` — rename). Use ext `"yml"` where the literal is `+"."+fileExtension` or `+".yml"`.

- [ ] **Step 1: Read each method, apply the Uniform Transformation at lines 92, 112, 123, 163, 174, 196, 197.** For `rename` wrap BOTH `file` and `newFile`. Add the two imports.

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/armorStand/ArmorStandEditorConfig.java
git commit -m "fix(security): guard armor-stand file ops against path traversal (Wave C)"
```

---

## Task 5: Voxel stencils — `/vv`, StencilList, SlCommand

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/VvCommand.java`
- Modify: `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/StencilList.java`
- Modify: `src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/SlCommand.java`

Base dirs: `VoxelConstants.STENCILS_DIR`, `VoxelConstants.STENCIL_LISTS_DIR`. Sinks: `VvCommand:92` (`STENCILS_DIR`+`args[1]`), `VvCommand:94` (`STENCIL_LISTS_DIR`+`args[1]`), `StencilList:74` (name), `SlCommand:150` (`STENCILS_DIR`+`stencilName`).

- [ ] **Step 1: Read each method, apply the Uniform Transformation** (ext-less overload — these names already include or omit extension inline; preserve whatever suffix the original concatenated). `VvCommand`/`SlCommand` are command handlers → `<ABORT>` sends the user `"Invalid name."` and `return true`. `StencilList:74` → match its method's return convention. Add the two imports to each file.

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/VvCommand.java \
        src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/StencilList.java \
        src/main/java/com/mcmiddleearth/architect/voxelStencilEditor/SlCommand.java
git commit -m "fix(security): guard voxel-stencil file ops against path traversal (Wave C)"
```

---

## Task 6: ZipUtil — Zip-Slip on archive extraction

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/util/ZipUtil.java`

Sink: line 121 `Path targetPath = new File(targetDirectory + File.separator + fileName).toPath();` where `fileName` comes from a zip entry (attacker-controlled) — the classic Zip-Slip.

- [ ] **Step 1: Read the extraction method (around 110–130)** to see the loop variable names and how `targetPath` is used.

- [ ] **Step 2: Replace the sink with a guarded resolve.** `targetDirectory` may be a `String` here — resolve to a `File` base first:

```java
File targetPath;
try {
    targetPath = PathSafety.resolveInside(new File(targetDirectory), fileName);
} catch (SecurityException ex) {
    Log.warn("Skipping unsafe zip entry '" + fileName + "' during extraction: " + ex.getMessage());
    continue; // skip this entry; do not extract outside the target directory
}
```

Adjust the following lines that used the old `Path targetPath` to use `targetPath.toPath()` (or keep a `Path` local: `Path target = targetPath.toPath();`). Add imports `com.mcmiddleearth.util.PathSafety` (same package — no import needed) and `com.mcmiddleearth.architect.Log`.

- [ ] **Step 3: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/mcmiddleearth/util/ZipUtil.java
git commit -m "fix(security): guard zip extraction against Zip-Slip (Wave C)"
```

---

## Task 7: Full verification + deferred-sink record

**Files:** none modified (verification only), then update the audit tracker.

- [ ] **Step 1: Full test run**

Run: `mvn -q test`
Expected: BUILD SUCCESS; existing LogFileTest (6) + PathSafetyTest (9) + CustomHeadManagerTraversalTest (3) all green.

- [ ] **Step 2: Grep that the guarded sinks no longer concatenate user input into `new File`**

Run: `grep -rn 'new File(acceptedHeadDir\|new File(submittedHeadDir\|new File(dataDir+\|new File(dataDir,\|STENCILS_DIR+\|STENCIL_LISTS_DIR+' src/main/java`
Expected: only occurrences INSIDE the `try { PathSafety.resolveInside(...) }` blocks remain; no bare `new File(base + "/" + userInput)` sink outside a guard. Manually confirm each remaining hit is guarded.

- [ ] **Step 3: Confirm deferred sinks are still deferred, not accidentally in scope**

The following remain intentionally unguarded by THIS slice (recorded so they are not silently forgotten): `SpecialSavedInventoryData:109,146,161`, `CustomInventoryEditor:112,228` (→ `/inv` auth slice), `RpManager:143,508`, `ItemBlockManager:137,153` (region names), `CopyPasteManager:67,81,235` (UUID names), `WorldConfig:125` (world names). No action — just verify none were half-edited.

- [ ] **Step 4: Update the audit tracker**

In `AUDIT-REMEDIATION-PLAN.md`, mark the path-traversal / delete-rights item as fixed on branch `architect-rework-2026`, referencing `PathSafety` and the six fix commits. Do NOT reference or reintroduce `docs/security/path-traversal-fix.md` (it stays local and git-ignored per prior instruction).

- [ ] **Step 5: Commit**

```bash
git add AUDIT-REMEDIATION-PLAN.md
git commit -m "docs: mark path-traversal item fixed in Wave C path-safety slice"
```

---

## Self-Review Notes (author)

- **Spec coverage:** audit's named traversal commands — `/chead` (Task 2), `/banner` (3), `/armor` (4), `/vv` (5), `/get head` read (covered by `getHeadData`/`getSubmittedHeadData` in Task 2). Zip-Slip (6) is a bonus find. Deferred sinks explicitly recorded (Task 7).
- **The security boundary is 100% in `PathSafety` and is exhaustively unit-tested** (Task 1); Tasks 2–6 are mechanical delegation, so per-site MockBukkit tests are unnecessary — Task 2 adds one wiring test to prove the delegation pattern is correct, and that pattern is identical everywhere.
- **Type consistency:** `resolveInside(base, name, ext)` and `resolveInside(base, name)` and `isInside(base, child)` are the only three signatures; used identically in every task.
- **No placeholders:** the failure-action rule + worked examples make each `<ABORT>` concrete; the uniform pattern is spelled out once and referenced.
