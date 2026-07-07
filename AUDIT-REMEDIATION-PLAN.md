# MCME-Architect — Audit Summary & Remediation Plan

**Prepared for:** Head Developer
**Build reviewed:** v2.10.6 (`master` @ `8049b16`), Paper 1.21.4 / Java 21
**Date:** 2026-07-04
**Companion document:** [`audit.md`](audit.md) — full technical findings with file:line references and a per-finding verification table.

---

## 1. Bottom line

The plugin is **stable on our current production server and there is no evidence of a live compromise or data corruption today.** The core building features are solid, and — importantly — there is **no NMS version-pinning to rot**: all native access is delegated to PluginUtils, so 1.21.x upgrades won't silently break internals.

The risks the audit found are **latent and conditional** — they bite when something *else* changes: a specific command is run, a config/plugin is added or removed, a file gets corrupted, or we deploy to a fresh server. They fall into three buckets:

1. **Incident triggers** — a handful of commands can freeze the server or delete other players' data.
2. **Fragility on change** — the plugin fails to enable (or silently half-loads) if an optional dependency is missing, and turns one corrupt YAML file into a plugin-wide outage or data wipe.
3. **Tech debt** — dead code, debug logging, and repeated anti-patterns that slow future work and hide real bugs.

**None of this requires a fire drill.** It does justify a short, focused remediation effort — most of the highest-impact fixes are small and isolated.

### How the review was run

Four parallel review lanes (security/input-handling, performance/thread-safety, version/dependency compatibility, code-quality/dead-code), followed by a **second independent verification pass** that re-derived every high-severity finding from source. Version claims were checked with `javap` against the actual ProtocolLib 5.4.0 / ViaVersion 5.0.0 / Paper 1.21.4 jars. Verification outcome: **0 findings refuted, all confirmed, 2 strengthened.** Roughly 45 findings total.

**Scope caveat:** this was a static code audit. It did not include runtime profiling, a live penetration test, or review of the server-side shell scripts' host environment. Effort sizes below are relative (S/M/L) — they need the team's velocity to become a schedule.

---

## 2. Risk snapshot

| # | Risk | Trigger | Impact | Sev |
|---|------|---------|--------|-----|
| A | `/armor place2 <n> 0` infinite loop | One command, builder-level (wrong perm) | **Server freeze / watchdog crash** | 🔴 High |
| B | `/inv delete` skips ownership check | One command, any builder | Deletes anyone's saved inventories | 🔴 High |
| C | Missing/undeclared dependencies | Fresh deploy, or ProtocolLib/Via/Connect absent | **Plugin won't enable**, or RP system silently dead | 🔴 High |
| D | Failure-blind loaders | One corrupt/edited YAML file | Blocks plugin enable, or overwrites good data with empty | ✅ Fixed |
| E | Path traversal in file commands | `/chead /vv /banner /armor /get head` + `..` | Arbitrary `.yml` create/delete/read | ✅ Fixed |
| F | Async touches main-thread state | `/inv download`, `/chead submit` | Corrupts event dispatch / stalls ticks | ✅ Fixed |
| G | Data-layer correctness | Fresh DB, stale protocol map, item-block edits | RP settings never persist; NPE crashes | ✅ Fixed |
| H | Per-event cost & memory leaks | Busy build sessions over long uptime | Tick lag, slow heap growth | 🟡 Low-Med |
| I | Dead code, logging, hygiene | Always | Log spam, maintenance drag, one broken `/armor rollback` | 🟡 Low |

Full detail and every file:line reference is in [`audit.md`](audit.md).

### The most useful finding: five root-cause patterns

The individual bugs cluster into **five repeating patterns**. This is good news — it means we fix *classes* of bug with shared helpers, not 40 one-off patches:

1. **Missing `return` after a guard** — e.g. the `/inv delete` auth bypass; 3 occurrences in `InvCommand` alone.
2. **Path traversal via `new File(DIR + "/" + arg)`** with no `..` filter — 5 commands, one shared resolver fixes all. ✅ *Fixed in Wave C via `PathSafety` (canonical-containment); the sweep also caught two-arg `new File(dir, arg)` sinks the first pass missed and a Windows-backslash Zip-Slip edge in `ZipUtil`.*
3. **Empty-then-writeback data loss** — a swallowed load error followed by an unconditional save (4 files). ✅ *Fixed in Wave C: loaders return/skip on a failed load and never save a config they couldn't read; the Mode-1 NPE loaders return null / skip the bad file.*
4. **Unchecked command arguments** — no arg-count/precondition checks before dereference (4 commands).
5. **NPE from unchecked lookup results** — `getSpecialBlock`/`getArmorStand`/`getConfigurationSection` dereferenced without null checks.

---

## 3. Plan of approach

Four phases, ordered by risk-reduction per unit of effort. Each phase is independently shippable and independently testable.

### Phase 0 — Hotfixes (target: this week) · effort **S**

Small, isolated, high-impact. Each is a few lines and can go out as one PR.

- **Kill the `/armor place2` freeze** (Risk A) — remove the debug subcommand outright (it's a leftover), or guard `step > 0` + arg-count + correct permission. *S*
- **Add the missing `return` in `/inv delete`** (Risk B), and sweep the other two `InvCommand` fall-throughs. *S*
- **Declare `ProtocolLib`, `ViaVersion`, `MCME-Connect` as softdepends and null-guard the two unconditional calls** (Risk C). Prevents "plugin won't enable" on any server without them. *S*
- **Add arg-count guards to crashing commands** — `/weselect`, `/sign` (needs an `isEditor` precheck), `/armor place2`. *S*

**Validation:** manual command tests on a dev server + a fresh-server enable test with ProtocolLib/Via/Connect removed one at a time.

### Phase 1 — Systemic pattern fixes (target: next sprint) · effort **M**

Fix the five patterns at the source with shared helpers, then grep-sweep every call site.

- ✅ **Path-safety helper** *(done on `architect-rework-2026`, Wave C — commits `7d82b52`…`c4d15e7`)* — `com.mcmiddleearth.util.PathSafety` (canonical-containment, 9 unit tests) applied to `/chead` (recursive delete-walk also bounded to the head dirs + `listFiles()` NPE-guarded), `/banner`, `/armor`, `/vv` stencils, and `ZipUtil` extraction (Zip-Slip). A MockBukkit regression test proves a `/chead` traversal delete can no longer reach a sentinel file outside the head dirs. `/get head` read is covered via the guarded `getHeadData`. Deferred (tracked): `/inv` category-name sinks fold into the Risk B `/inv` work; region/UUID/world-name sinks are not arbitrary user input. Independently security-reviewed — the review caught (and this slice then fixed, with a regression test) a stencil-list **write**-path escape (`/sl create` + `/sl save`) that the first pass left open while guarding only the read methods. (Risk E). *M*
- ✅ **"Never save a config that failed to load" guard** *(done on `architect-rework-2026`, Wave C — commits `c5018af`…`d819a22`)* — Mode 1 (NPE-on-enable): `CustomHeadData.fromFile`/`CustomHeadManagerData.load` and `SpecialSavedInventoryData.loadFromFile` skip a corrupt/incomplete file instead of NPE-ing enable. Mode 2 (empty-then-writeback): `WorldConfig` (read-only flag), `SpecialBlockInventoryData`, `GetData` (first-run vs corrupt) and `ZipUtil.extract` (no destructive empty result) never overwrite a file they failed to read. MockBukkit regression tests reproduce the enable-NPE, the world-config wipe, and the `/get` scheduled wipe (all red→green). Independently reviewed — the review caught, and this slice then fixed with tests, a default-config `saveDefaultConfig` wipe (a second writer of the same file the read-only flag hadn't covered), a `SpecialSavedInventoryData` missing-`items` NPE, and an unguarded sibling loader `SpecialItemInventoryData` (which the audit itself missed). (Risk D). *M*
- ✅ **Thread-safety corrections (concurrency core)** *(done on `architect-rework-2026`, Wave C — commits `a5dd1a7`…`ca812ac`)* — `/inv download|upload` and RP-release callbacks now run on the main thread (were reloading inventories / messaging off-thread) and their leaked `newSingleThreadExecutor`s are shut down; `/chead submit` now does the whole Mojang HTTP round-trip off-thread and hops only the Bukkit result to main (dropping the main-thread response-read stall and the non-volatile `received`/`connection` race); `playerRpData` (written from `AsyncPlayerPreLogin`) + `sodiumClients` are now `ConcurrentHashMap` and cleared on quit. Independently reviewed — the review caught, and this slice then fixed, a `ConcurrentHashMap`-vs-null regression the map change introduced in an untouched caller (`RpDatabaseConnector` used `put(uuid, null)` as a load-failed marker, which would have NPE'd on the async DB thread on every DB hiccup). **Deferred follow-ups** (tracked): the pure per-player memory-leak maps (`CopyPasteManager`, `BlockCycleListener` — Risk H hygiene); `RpManager.refreshSHA` (InputStream leak + off-thread `saveConfig`) and `EntityLogger` async CME (more-surgical Risk F); and a **newly-found Risk G item — `RpDatabaseConnector` starts an eager async DB keep-alive on enable (`runTaskTimerAsynchronously` delay 0) that connects to `localhost` even with no DB configured** (blocks the async thread on connect, flakes MockBukkit; make it lazy/guarded). (Risk F). *M*
- **Per-player state hygiene** — make the shared maps concurrent and clear them on `PlayerQuitEvent` (Risk H, memory half). *S–M*

**Validation:** unit tests for the path helper and the load-guard (MockBukkit is already in the toolchain); a soak test for the map cleanup.

### Phase 2 — Correctness & data layer (target: following sprint) · effort **M**

- ✅ **Fix the RP database schema** *(done on `architect-rework-2026`, Wave C — commits `922e49b`…`5a26073`)* — added the missing `client` column to `CREATE TABLE` (+ an idempotent `ALTER ADD COLUMN` to patch pre-existing tables); closed the leaked `ResultSet` (try-with-resources) and the executors (in the thread-safety slice); the connector now degrades to a logged no-op when no DB is configured instead of NPE-spamming the async thread. Independently reviewed — the review caught, and this slice fixed, a no-DB join-stall regression the guard introduced (it now seeds a default entry so joins don't poll ~11s for a load that will never come), plus the same-root-cause empty-array durability accessors and the save-path `ResultSet`. (The `refreshSHA` `InputStream` leak remains a deferred Risk-F follow-up.) *S*
- ✅ **Refresh `protocolVersions.yml`** and null-guard `getLatestVersion` — done: `protocolVersions.yml` was refreshed in the 26.2 migration (26_2/26_1 added, 1.21.2 fixed) and `getLatestVersion` is already null-guarded (`RpManager.java:486` `if(boxed==null) continue;`). (Risk G). *S*
- ✅ **Item-block robustness** *(done, Wave C — commit `5a26073`)* — null-guarded the `getSpecialBlock`/`getArmorStand`/`getHelmet` dereferences and the empty `contentDamage` array (was an `IllegalArgumentException` from `getRandom(0,-1)`). (Invisible-item-frame de-duplication remains a smaller follow-up.) *M*
- **Per-event cost** (optional, measure first) — cache the TheGaffer reflective `Method`; reduce the per-`BlockBreakEvent` config-tree walk and O(n) special-block scans (Risk H). *M*

**Validation:** fresh-DB integration test; connect a 1.21.4 + a 1.21.2 client and confirm correct pack selection.

### Phase 3 — Hygiene & maintainability (ongoing, lower priority) · effort **M–L**

- Remove dead code (`armorStand/guard/` — also fixes the broken `/armor rollback`; `watcher/`; unregistered classes; ~35 large commented blocks).
- Logging cleanup — unregister `TestPluginMessageListener`, delete leftover debug lines, standardise on the plugin logger.
- Permission-tree fixes (the `archtiect` typo, dead/undeclared nodes), align `config.yml` keys with what the code reads, POM hygiene, drop committed binaries (`heads.zip`/`cheads.zip`, `1.18_fix/`).

---

## 4. Preventing regressions

Two low-cost investments stop these patterns from creeping back:

1. **Extend the existing contract-test approach.** We already pin the TheGaffer protection API with `ProtectionApiContractTest` and have MockBukkit wired up — the same style covers the load-guard, the path helper, and command arg-validation cheaply.
2. **A CI lint pass for the five patterns** — e.g. flag `new File(dir + "/" + <arg>)` and empty catch blocks followed by a `save()`. Even a grep-based check in CI catches the next occurrence at review time.

---

## 5. Decisions I need from you

1. **Sequencing** — is the Phase 0 → 3 order right, or do specific items need to jump the queue for an upcoming event/release?
2. **Fail-open protection policy** — `TheGafferUtil` intentionally allows building if the protection bridge breaks. That's the current design and I preserved it. Confirm we keep fail-*open* (favour builders) vs switching to fail-*closed* (favour safety) if the bridge errors.
3. **RP MySQL feature** — the player-RP-settings database is broken on fresh deploys and only works because our live table was hand-patched. Do we fix it properly (Phase 2), or is it effectively deprecated and better removed?
4. **Dead-code appetite** — green-light to delete the unregistered/stub classes and commented blocks in Phase 3? (Reduces the surface meaningfully but touches a lot of files.)
5. **Target version confirmation** — compile is against Paper 1.21.4; please confirm that's the runtime target so I finalise the ProtocolLib/Via version bumps.

---

## 6. Suggested first step

Phase 0 is the obvious opener: it's a single small PR, removes the two worst incident triggers (server freeze + data-deletion) and the "won't enable" risk, and carries near-zero regression risk. I can have it up for review as soon as you approve the approach.
