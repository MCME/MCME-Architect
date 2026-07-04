# MCME-Architect — Code Audit

- **Target build:** v2.10.6 (`master` @ `8049b16`)
- **Runtime target:** Paper 1.21.4 (`api-version: 1.19`), Java 21
- **Date:** 2026-07-04
- **Method:** Four parallel review lanes — security/input-handling, performance/thread-safety, version/dependency compatibility (cross-checked with `javap` against the actual ProtocolLib 5.4.0 / ViaVersion 5.0.0 / paper-api 1.21.4 jars), and code-quality/error-handling/dead-code. Every finding was confirmed by reading the code.

> Verification status: this document was produced by an initial fan-out audit and then a second independent verification pass. Per-finding verification notes are in the **Re-audit verification** section at the bottom.

---

## Overall assessment

The plugin is **functionally mature but structurally fragile**. Core building features are solid and there is no NMS version-pinning to rot (all native access is delegated to PluginUtils, which is Mojang-mapped and 1.21-safe). Three classes of latent failure dominate:

1. **Undeclared dependencies** that make the plugin refuse to enable or silently half-register.
2. **Failure-blind file/DB loaders** that turn one corrupt file into a plugin-wide outage or a data wipe.
3. **Async code touching main-thread server state.**

None of these surface in normal operation — they appear exactly when something else already went wrong.

The unifying anti-pattern is **failure-blind continuation** (`catch → log → continue with empty/partial state`). Because data loaders run inside `onEnable()`, one unreadable YAML file can take down the whole plugin and all its protection listeners. Fail-*fast-and-skip* (drop the bad file, keep the rest) is the right posture — the mirror image of the deliberately fail-*open* choice in the TheGaffer protection bridge.

---

## Critical / High

### Dependency declarations that break enable or silently degrade (`plugin.yml:6-7`)

- **ProtocolLib hard-used in `onEnable` with no guard, but only `softdepend`** — `ArchitectPlugin.java:120`. If ProtocolLib is absent, `onEnable` throws `NoClassDefFoundError` and the whole plugin fails. The used 5.x API surface is otherwise binary-compatible (verified via `javap`), so this is a missing null-check + wrong pin, not a runtime API break.
- **ViaVersion called unconditionally in the RP hot path but declared nowhere** — `RpManager.java:220`. The `Via.getAPI()` call sits *outside* the protocol-version guard, so every `/rp` switch and the 1 s auto-switch task hit it → `NoClassDefFoundError` per switch when Via is absent.
- **MCME-Connect undeclared; its absence unregisters *all* of `RpListener`** — `RpListener.java:68`. A handler signature references a Connect event type; Bukkit's registration reflection `NoClassDefFoundError`s and silently drops every handler in the class (RP status, pre-login DB load, quit cleanup).

### Failure-blind loaders — one bad file breaks enable or wipes data

- **`CustomHeadData.fromFile` continues with an empty config after a read error** → `UUID.fromString(null)` NPE, in a loop that runs during `onEnable` — `CustomHeadData.java:51`. One corrupt head YAML prevents the plugin (and its protection listeners) from enabling. Same shape in `SpecialSavedInventoryData.java:86`.
- **`WorldConfig` silently loads an empty config on YAML parse error, then writes it back** — `WorldConfig.java:115`. A single typo in a world file plus any later toggle **permanently wipes that world's no-physics list, module overrides, and view-distance settings**.
- **`ZipUtil.extract` deletes everything in the target dir even when the download yielded no matching entries** — `ZipUtil.java:94`. A failed/renamed `/inv download` **destroys the RP's working inventory configs**. (Not Zip-Slip vulnerable — entries are flattened to basename — the danger is the destructive empty-result path.)

### Async work touching main-thread / server state

- **`/inv download` runs its callback on an async thread that mutates Bukkit `HandlerList`s and swaps live static inventory state** — `InventoryUtil.java:50` → `InvCommand.java:92`. `loadInventories()` calls `HandlerList.unregisterAll` and re-`registerEvents` off-thread, racing main-thread event dispatch → corrupted handler arrays server-wide. (`RpCommand` does this correctly with `runTask`; `InvCommand` does not.) The same handler has a **missing `return` after "No RP found"** (`InvCommand.java:64`, `:88`) so an empty name reaches the script and throws.
- **`/chead submit` reads the Mojang HTTP response on the main thread** (5 s timeout) with an unsafe-publication race on the `connection` field — `HeadDataBuilderPlayer.java:62`. Only the TCP connect is async; the request/response and JSON parse stall every tick.

### Auto chunk-update flood-fill, default-on, on every block event

- `ChunkUpdateUtil.java:52` — for walls/fences/panes it does a recursive DFS over the whole connected network (up to 16×view-distance steps), one `sendBlockChange` per block, on the main thread, for every place/break/interact by every builder. Modules default to `true` (`WorldConfig.java:134`). (The *manual* `/chunkupdate` command is separately a dead no-op — `ChunkUpdateCommand.java:73`.)

---

## Medium

### Security — chat commands as filesystem primitives

- **`/chead delete` & `/chead reject`: arbitrary file + recursive directory deletion.** Raw `args[1]` → `new File(dir+"/"+name+".yml")` → `removeFileAndDirectory` (`CustomHeadManagerData.java:209`, `:170`, `:246`), which deletes the file then **walks up deleting every empty parent directory**, with a stop condition (`equals(acceptedHeadDir)`) that a `../` path never satisfies. Gated by the head-manager permission (headDesigner).
- **`/vv delete`: path traversal → arbitrary single-file deletion** — `VvCommand.java:91`. Gated by `architect.voxel.delete` (headDesigner).
- **`/chead submit`: user-level file *write* with an attacker-influenced path** — `HeadCommand.java:118`; reachable at adventurer rank, the lowest-privilege traversal vector.

### Data layer

- **`architect_rp` is auto-created without the `client` column** the INSERT/UPDATE/SELECT all reference — `RpDatabaseConnector.java:149` vs `:112`. On a fresh DB every RP-settings write fails forever (each swallowed as SEVERE). Also leaks the `ResultSet` on the no-row path and reports null entries as "loaded."

### Concurrency & memory

- Unbounded per-player static maps never cleared on quit: clipboards/undo/redo loaded on *join* and never freed (`CopyPasteManager.java:43`), `playerRpData` (`RpManager.java:70`), cycle-block/inventory/banner state (`BlockCycleListener.java:56`). Several are also written from async threads into plain `HashMap`s.
- `refreshSHA` mutates the shared config and calls `saveConfig()` from an async thread while main-thread handlers read the same section — CME risk (`RpManager.java:470`); it also **never closes the RP-zip `InputStream`** (`:451`).
- ProtocolLib packet listener reads/writes non-thread-safe config maps (with file I/O on a miss) from netty threads (`ViewDistanceListener.java:18`).

### Hot-path cost per event

- Every `BlockBreakEvent` walks the entire RP-config tree plus two O(n) scans of the full special-block list (`SpecialBlockListener.java:213`); every `BlockPhysicsEvent` eagerly builds 10-15-part debug strings even with logging off (`NoPhysicsListener.java:77`); `/chead accept` rebuilds the whole head gallery block-by-block (`CustomHeadGallery.java:97`); copy/paste is unbounded on the main thread once a player has `architect.copypaste.unlimited` (`Clipboard.java:94`).

### `protocolVersions.yml` stale for 1.21.x

- Missing 768/769 and mis-maps 1.21.2, so 1.21.2+ clients can't match pack variants and an admin-added `1_21_4` key triggers an unboxing NPE in `getLatestVersion` (`RpManager.java:491`). Copied to the data folder once and never refreshed, so updates don't fix deployed servers.

---

## Low / hygiene

- **Debug logging left in production:** `TestPluginMessageListener` dumps a byte array on *every* client join (`TestPluginMessageListener.java:19`); `RpReleaseUtil` dumps the entire RP config key tree per release (`RpReleaseUtil.java:84`); `SpecialBlockBranch2` logs on every branch-block place/break. Logging overwhelmingly uses `Logger.getGlobal()`/JUL instead of the plugin logger.
- **`/armor rollback` is player-reachable but throws `UnsupportedOperationException`** — the whole `armorStand/guard/` package is unimplemented stubs. The `watcher/` package is dead scaffolding. ~35 commented blocks ≥20 lines; several unregistered classes (`ItemTexCommand`, `NewAfkCommand`, `ProtocolLibUtil`).
- **`Essentials` softdepend is obsolete** (only referenced in a commented, unregistered command); **TeamSpeak is fully absent**; **dynmap softdepend is justified** (actively used, null-guarded).
- **Permission tree:** besides the known `archtiect.resourcePackAdmin` typo (line 127), 8 declared nodes are never checked (6 flagged `//DELETE` in the enum), and 3 checked nodes (`architect.armorStandRollback`, `architect.chunkupdate.auto`, `architect.itemTexture.manager`) aren't declared. ~40 entries use `descriptions:` (ignored by Bukkit).
- **`config.yml` ships `inventoryDownload` but not `inventoryUpload`**, so `/inv upload` always fails (`InventoryUtil.java:29`); shipped DB creds are `xxx` placeholders (no secrets committed — good — but they guarantee a SEVERE SQL stack at every boot).
- **Executors leaked** per `/inv upload|download` and `/rp release` — `newSingleThreadExecutor()` never `shutdown()` (`InventoryUtil.java:44`, `RpReleaseUtil.java:41`). Committed binaries `heads.zip`/`cheads.zip` and `1.18_fix/script.py` are vestigial.

---

## What's solid

- **No SQL injection** — `PreparedStatement` throughout, with query timeouts.
- **No NMS version-pinning in-repo** — zero `net.minecraft`/`v1_XX_RX` live usage; all native access delegated to PluginUtils (Mojang-mapped, 1.21-safe). Deprecated Bukkit APIs in use are all still present on 1.21.4.
- **`ZipUtil` is not Zip-Slip vulnerable**; download URLs come from admin config, not user input.
- **No committed secrets**; `DevUtil` debug system is properly gated behind `/architect dev`.
- **The TheGaffer protection bridge is intact and matches its contract** — `TheGafferUtil.java:44` still reflectively calls `hasBuildPermission(Player, Location)` and fails *open* by design. Caveat: it does an uncached `getMethod` lookup on every protection check — worth caching, but **any refactor must preserve that exact reflective signature** (PlotBuild and MCME-Architect both depend on it; pinned by `ProtectionApiContractTest`).

---

## Suggested fix order

1. **Declare `ProtocolLib`, `ViaVersion`, `MCME-Connect` in `plugin.yml` softdepend, and null-guard the ProtocolLib/Via calls** — highest blast radius, smallest change.
2. **Make the YAML loaders skip corrupt files instead of NPE-ing enable** (`CustomHeadData.fromFile`, `SpecialSavedInventoryData`, the `loadData` loops).
3. **Guard `ZipUtil.extract` against wiping the target when nothing was extracted.**
4. **Hop `/inv download`'s reload back to the main thread** (mirror `RpCommand`) and add the missing `return`.
5. **Sanitize filenames in `/chead` and `/vv`** (reject `..`/separators, or resolve-and-verify the canonical path stays under the base dir).
6. **Add the `client` column to the CREATE TABLE; close the `ResultSet`/`InputStream`/executors.**
7. **Fix `protocolVersions.yml`** (add 768/769) and null-check `getLatestVersion`.
8. **Remove the production debug logging** and unregister `TestPluginMessageListener`.
9. **Add `PlayerQuitEvent` cleanup** for the per-player static maps.
10. **Cache the TheGaffer `Method`** (preserving the signature) and reduce per-`BlockBreakEvent` scanning.

---

## Re-audit verification

A second independent pass re-derived each HIGH finding (and several mediums) directly against the source. **Outcome: 0 refuted, all confirmed; 2 strengthened.**

| Finding | Status | Evidence / correction |
|---|---|---|
| ProtocolLib unguarded in `onEnable` | ✅ Confirmed | `ArchitectPlugin.java:120-121` — `getProtocolManager().addPacketListener(...)` unconditional, no null-check/try-catch. |
| MCME-Connect absence unregisters all of `RpListener` | ✅ Confirmed | `RpListener.java:21` imports `connect.events.PlayerConnectEvent`, `:68` handles it; registered unconditionally at `ArchitectPlugin.java:107`. |
| ViaVersion used unconditionally | ✅ Confirmed (nuance) | `RpManager.java:218` *is* guarded by `protocolVersion==0`, but the log line at **`:220-221` calls `Via.getAPI()` unconditionally**; also `RpListener.java:80,91`. Finding stands. |
| `CustomHeadData.fromFile` NPE on bad load | ✅ **Strengthened** | `CustomHeadData.java:58` — `UUID.fromString(getString("headId"))` NPEs not only on a failed load but on **any successfully-loaded file missing `headId`**. |
| `WorldConfig` empty-on-parse-error, then writes back | ✅ Confirmed | `:115` `loadConfiguration` + `setModuleEnabled`→`saveWorldConfig` `:180-182`. Also confirms modules default `true` (`:135`). |
| `ZipUtil.extract` wipes target on empty download | ✅ Confirmed | `:96-106` — empty `temp.listFiles()` is non-null, so `outPath` is cleared and nothing is moved in. |
| `/inv download` async callback + missing `return` | ✅ Confirmed | `InventoryUtil.java:50` callback in async body; `InvCommand.java:64,88` fall through on "No RP found". |
| `/chead submit` Mojang HTTP on main thread | ✅ Confirmed | `HeadDataBuilderPlayer.java:88` async does only `connect()`; the response wait `getResponseCode()`/stream read is on the main-thread timer `:67-68`; `received`/`connection` non-volatile. |
| Chunk-update flood-fill default-on | ✅ Confirmed | Gate `ChunkUpdateUtil.java:54` (`CHUNK_UPDATE_AUTO`, default true); recursive DFS `:120-134`, `maxStep = 16×viewDistance`. |
| `getLatestVersion` unboxing NPE | ✅ Confirmed | `RpManager.java:491` `int = protocolVersions.get(version)` on a possibly-absent key. |
| `refreshSHA` `InputStream` leak + async config write | ✅ Confirmed | `RpManager.java:451` `url.openStream()` never closed; `:470-471` `set`+`saveConfig()` off-thread. |
| `architect_rp` missing `client` column | ✅ Confirmed | `RpDatabaseConnector.java:149-150` CREATE vs `:112-117` INSERT/SELECT. |
| `/chead delete|reject` + `/vv delete` traversal | ✅ Confirmed | `CustomHeadManagerData.java:209,170,246` (upward empty-dir walk); `VvCommand.java:91`. |
| TheGaffer bridge intact, uncached `getMethod` | ✅ Confirmed | `TheGafferUtil.java:44-57`, fail-open by design; per-call reflection lookup. |

### New findings from the breadth pass

A second, fresh sweep over the packages the first audit only touched lightly (interactive editors, command handlers, data-persistence and special-block *placement* internals) surfaced **7 new High, 9 new Medium, 5 new Low**. These are additive to the findings above.

#### New — High

- **`/inv delete` bypasses its own ownership check (auth bypass + data loss)** — `InvCommand.java:207-211`. The `!owner && !INV_OTHER` branch sends an error but **does not `return`**, so `deleteInventory` runs unconditionally. Any player with base `INV_SAVE` can delete anyone's saved inventory. (Independently confirmed against source.)
- **`/armor place2 <n> 0` infinite-loops on the main thread → server freeze** — `ArmorStandEditorCommand.java:124-134`. Leftover debug subcommand: `for(int i=0; i<getInt(args[1]); i += getInt(args[2]))` with step `0` never terminates, spawning armor stands forever (watchdog crash). Also AIOOBE on `/armor place2` with too few args, and gated by the unrelated `RANDOMISER_MATERIALS` permission instead of an armor-stand node.
- **Path traversal in `/banner save|delete|load` (write/delete/read outside the banner folder)** — `BannerEditorConfig.java:53,64,79,95,106,114`. `args[1]` → `new File(dataDir+"/"+name+ext)` with no `..` filter → arbitrary `.yml` create/delete/read. The identical pattern exists in `ArmorStandEditorConfig.java` (`save`/`delete`/`rename`/`load`, lines 92-197). These join the known `/chead` and `/vv` traversals — same root cause.
- **`/sign …` before selecting a sign throws NPE** — `SignCommand.java:74,78` → `SignEditorData.java:96-98,217-219`. No `isEditor(player)` guard; every path does `signEditors.get(player).block()` on a null entry.
- **`/weselect` (no args) and `/weselect shift` throw AIOOBE** — `WeSelectCommand.java:61,79`. Dereferences `args[0]`/`args[1]` with no length check; the bare command (the first thing a user types) crashes.

#### New — Medium

- **`SpecialBlockInventoryData.loadFromFile` overwrites a corrupt category file with empty data** — `SpecialBlockInventoryData.java:156-192`. Failed `config.load` is swallowed, then `config.save(file)` at `:186` writes an empty config back — a data-loss twin of the known `SpecialSavedInventoryData` bug in a different file.
- **`GetData.load()` overwrites `itemSets.yml` with empty data on a corrupt read** — `GetData.java:154-178`. Same empty-then-writeback hazard; destroys all saved `/get` kits ~100 s after a bad load.
- **Saved-inventory category files collide across owners** — `SpecialSavedInventoryData.java:105-142`. Files are keyed `…/<rp>/<category>.yml` with no owner component, so one player's `create`/`save` can overwrite another's.
- **NPE in item-block placement/copy** — `SpecialBlockItemBlock.java:257-270` (`getSpecialBlock(...)` returns null for a stale/renamed id, then dereferenced) and `ItemBlockData.java:55-57` (null armor-stand / null helmet dereferenced). Breaks copy/paste and block-cycle near disturbed item blocks.
- **Empty `contentDamage` array crashes item-block placement** — `SpecialBlockItemBlock.java:123`. `NumericUtil.getRandom(0, length-1)` with a length-0 array → `IllegalArgumentException: bound must be positive`. A single mis-authored config entry makes that block un-placeable.
- **Invisible item frames duplicate on re-placement** — `SpecialBlockItemFrame.java:45-68`. Unlike the armor-stand path, it doesn't remove a pre-existing invisible fixed frame before spawning a new one → entity accumulation.
- **`EntityLogger` async dump can CME and touches dynmap off-thread** — `EntityLogger.java:81-96`. `logData.forEach` on the async timer races main-thread `put` (structural-mod CME, distinct from the known lost-update race), and `ELogDynmapUtil.createMarker` is called from the async thread.
- **`/get <kit>` AIOOBE from a saved kit with trailing empty slots** — `GetCommand.java:275-284`. `giveItems` iterates `items[0..8]`, but a kit saved with empty tail slots deserialises to a shorter array after a round-trip.
- **`/get head <name>` read traversal** — `GetCommand.java:213-236`. Normalises `\` and `//` but not `..`; read-only, lower impact, same missing-filter root cause.
- **`BannerEditorConfig.loadBanner` NPE on a file missing its `Banner` section** — `BannerEditorConfig.java:71-74`. `getConfigurationSection("Banner").getValues(true)` before the null check.

#### New — Low

- **`RandomiserConfig.setProbs` can index negative** — `RandomiserConfig.java:70-84`. `/random prob …` values are each range-checked but their *sum* isn't; `sum>100` on a short array walks `lastNonZero` to `-1` → AIOOBE.
- **`/random radius 150` scans ~27M cells synchronously on the main thread** — `RandomiserCommand.java:180-208`. Perm-gated and radius-bounded, but a multi-second stall / watchdog-trip footgun.
- **`SpecialItemInventoryData` reads `damage` as durability, ignoring CustomModelData** — `SpecialItemInventoryData.java:173-192`. Latent (item inventories are disabled), but wrong for 1.21.4 model-data packs.
- **`SpecialBlockInventoryData.rpName(id)` throws on an id without `/`** — `:523-525`. `substring(0, indexOf("/"))` → `substring(0,-1)` when a hand-crafted item's lore lacks a slash.
- **`CmdPrompt` casts the wrong variable, so leather colour can't be set when *adding* an item** — `CmdPrompt.java:27-38`. Logic defect, no crash.
- _Context note:_ the `customInventories/editor/**` classes null-guard with Java `assert`, which is disabled in production JVMs — those guards are no-ops at runtime (not exploitable given server-controlled inputs, but ineffective).

### Systemic patterns (most actionable)

The re-audit shows several findings are instances of the same root cause — fix the pattern, not just the site:

1. **Missing `return` after a guard** → `/inv delete` auth bypass + `/inv upload|download` "No RP found" fall-through (3 occurrences in `InvCommand` alone).
2. **Path traversal via unsanitised filename args** → `/chead`, `/vv`, `/banner`, `/armor`, `/get head` — all `new File(DIR + "/" + arg)` with no `..` rejection. One shared "resolve-and-verify-under-base-dir" helper closes all five.
3. **Empty-then-writeback data loss** → `WorldConfig`, `SpecialBlockInventoryData`, `GetData` — a swallowed load error followed by an unconditional save. Guard: never save a config that failed to load.
4. **Unchecked command args** → `/weselect`, `/armor place2`, `/sign`, `/get <kit>` — no arg-count / precondition checks before dereference.
5. **NPE from unchecked lookup results** → item-block placement/copy, banner load — `getSpecialBlock`/`getArmorStand`/`getConfigurationSection` results dereferenced without null checks.
