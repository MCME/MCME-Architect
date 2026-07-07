# Wave C — Argument / Bounds Validation (Risk I / pattern 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the two remaining "unchecked argument / array bounds" crashes in MCME-Architect: `/get <kit>` on a kit that deserialised to a short array, and `RandomiserConfig.setProbs` on an empty or over-100 probability array.

**Architecture:** Two small, self-contained guards. `GetCommand.giveItems` iterates `items[0..8]` but a kit round-tripped through YAML can be shorter than 9 — bound the loop by `items.length`. `RandomiserConfig.setProbs` indexes `newProbs[lastNonZero]` where `lastNonZero` starts at `length-1` (`-1` for an empty array) and, in the `while(sum>100)` loop, walks down past `0` (because `sum` is never decremented, the loop only ends by running off the array) → AIOOBE; guard the empty case and rewrite the reduction loop to track the remaining excess and stop at index 0.

**Tech Stack:** Java 25, Maven, JUnit Jupiter 6.1.0 (a pure unit test for `setProbs` — `RandomiserConfig` constructs with no Bukkit). `GetCommand.giveItems` is verified by compile + review (needs a live inventory).

**Branch:** `architect-rework-2026`. Build: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"`; never `mvn clean` (OneDrive locks `target/`).

**Already fixed (out of scope):** `/armor place2` freeze (Phase 0 — the debug subcommand was removed, `ArmorStandEditorCommand.java:124-128`), `/sign` before selecting a sign (`SignCommand.java:52` `isEditor` guard), `/weselect` no-args / `shift` (`WeSelectCommand.java:61,76` length guards).

---

## File Structure

| File | Change |
| --- | --- |
| `specialBlockHandling/command/GetCommand.java` | bound the `giveItems` loop by `items.length` |
| `randomiser/RandomiserConfig.java` | guard empty `newProbs`; fix the `sum>100` reduction loop to terminate + stay in bounds |
| `src/test/java/com/mcmiddleearth/architect/randomiser/RandomiserConfigTest.java` | **Create** — pure unit tests for `setProbs` |

---

## Task 1: GetCommand.giveItems — bound by array length

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/command/GetCommand.java` (`giveItems`, `:274-284`)

- [ ] **Step 1: Bound the loop** — replace line 275 (`for(int i=0; i<9; i++) {`):

```java
            for(int i=0; i<9 && i<items.length; i++) {
```

(A kit saved with trailing empty/AIR slots deserialises to a shorter `ItemStack[]`; iterating to a fixed 9 then threw `ArrayIndexOutOfBoundsException`. The hotbar is 9 slots, so `i<9` stays as the upper cap.)

- [ ] **Step 2: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/command/GetCommand.java
git commit -m "fix(args): /get bounds the give-items loop by array length (Wave C)"
```

---

## Task 2: RandomiserConfig.setProbs — empty + over-100 bounds

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/randomiser/RandomiserConfig.java` (`setProbs`, `:70-84`)
- Test: `src/test/java/com/mcmiddleearth/architect/randomiser/RandomiserConfigTest.java` (create)

- [ ] **Step 1: Write the failing test**

```java
package com.mcmiddleearth.architect.randomiser;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class RandomiserConfigTest {

    private static int sumOfProps(RandomiserConfig cfg) throws Exception {
        Field f = RandomiserConfig.class.getDeclaredField("props");
        f.setAccessible(true);
        int sum = 0;
        for (int p : (int[]) f.get(cfg)) sum += p;
        return sum;
    }

    @Test void emptyProbsDoesNotThrow() {
        assertDoesNotThrow(() -> new RandomiserConfig().setProbs(new int[0]));
    }

    @Test void overHundredIsNormalisedNotCrashed() throws Exception {
        RandomiserConfig cfg = new RandomiserConfig();
        assertDoesNotThrow(() -> cfg.setProbs(new int[]{50, 60})); // sum 110 > 100
        assertEquals(100, sumOfProps(cfg), "over-100 probs must be normalised to 100, not AIOOBE");
    }

    @Test void underHundredIsToppedUpToHundred() throws Exception {
        RandomiserConfig cfg = new RandomiserConfig();
        cfg.setProbs(new int[]{30, 30}); // sum 60 < 100
        assertEquals(100, sumOfProps(cfg));
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn test -Dtest=RandomiserConfigTest`
Expected: FAIL — `emptyProbsDoesNotThrow` throws `ArrayIndexOutOfBoundsException: -1` (and `overHundredIsNormalisedNotCrashed` AIOOBEs walking `lastNonZero` past 0).

- [ ] **Step 3: Fix `setProbs`** — replace the method (`:70-84`):

```java
    public void setProbs(int[] newProbs) {
        if(newProbs.length == 0) {
            props = newProbs;
            return;
        }
        int sum = 0;
        for(int prob : newProbs) {
            sum+=prob;
        }
        int lastNonZero = newProbs.length-1;
        if(sum<100) {
            newProbs[lastNonZero]=newProbs[lastNonZero]+(100-sum);
        }
        int excess = sum-100;
        while(excess>0 && lastNonZero>=0) {
            int reduce = Math.min(newProbs[lastNonZero], excess);
            newProbs[lastNonZero]=newProbs[lastNonZero]-reduce;
            excess-=reduce;
            lastNonZero--;
        }
        props = newProbs;
    }
```

(Fixes two bugs: the empty-array `newProbs[-1]`, and the original `while(sum>100)` never decremented `sum`, so it only stopped by running off the array — now it tracks `excess`, reduces each slot by at most the remaining excess, and stops at index 0. Net sum is 100 for any input.)

- [ ] **Step 4: Run to verify pass**

Run: `mvn test -Dtest=RandomiserConfigTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/randomiser/RandomiserConfig.java \
        src/test/java/com/mcmiddleearth/architect/randomiser/RandomiserConfigTest.java
git commit -m "fix(args): RandomiserConfig.setProbs guards empty + over-100 arrays (Wave C)"
```

---

## Task 3: Verification + tracker + review

- [ ] **Step 1: Full test run** — `mvn test`. Expected: BUILD SUCCESS; prior 25 + `RandomiserConfigTest` (3) = 28 green.
- [ ] **Step 2: Update the tracker** — in `AUDIT-REMEDIATION-PLAN.md`, mark the "unchecked command args" pattern (pattern 4) done, noting `/armor place2`, `/sign`, `/weselect` were already Phase-0-fixed and `/get`, `setProbs` are fixed here.
- [ ] **Step 3: Commit** the tracker.
- [ ] **Step 4: Independent adversarial review** — verify the `giveItems` cap still fills all present hotbar slots; that `setProbs` normalises to exactly 100 for under/over/empty/all-zero inputs and never indexes out of bounds; and that no other command in the audit's pattern-4 list is still unguarded.

---

## Self-Review Notes (author)

- **Spec coverage vs audit pattern 4:** `/get <kit>` short-array AIOOBE (Task 1) ✓; `setProbs` empty/over-100 AIOOBE (Task 2) ✓; `/armor place2`, `/sign`, `/weselect` verified already-fixed (out of scope, noted). `GetCommand` other paths, banner load = pattern 5 (NPE-from-lookup), handled in the data-layer/load-guard slices.
- **`setProbs` correctness:** for `sum<100` the deficit is added to the last slot (→100); for `sum≥100` the excess (`sum-100`) is removed across slots from the end (→100); empty array is a no-op. `excess ≤ sum` always, so the loop drains `excess` to 0 before `lastNonZero` underflows; the `lastNonZero>=0` guard is the belt-and-suspenders backstop.
- **Testability:** `setProbs` is pure (no Bukkit) → real TDD unit test. `giveItems` needs a live `PlayerInventory` → compile + review.
