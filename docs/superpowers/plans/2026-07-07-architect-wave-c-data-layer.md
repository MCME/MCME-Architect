# Wave C — Data-Layer Correctness (Risk G) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCME-Architect's data layer correct and crash-free: RP settings must actually persist on a fresh database, the DB connector must degrade gracefully (no leaks, no NPE spam) when there's no reachable DB, and the item-block edit paths must not NPE/crash on stale or empty data (Risk G).

**Architecture:** Two areas. (1) `RpDatabaseConnector`: the `architect_rp` table is created without the `client` column that every INSERT/UPDATE/SELECT references, so all RP writes fail on a fresh DB — fix the schema (CREATE + an idempotent ALTER for existing tables); close the leaked `ResultSet`; and guard every DB operation so a missing/unreachable DB is a logged no-op instead of a `NullPointerException` on the async DB thread. (2) Item-block placement/copy dereferences lookup results that can legitimately be null (`getSpecialBlock`, `getArmorStand`, `getHelmet`) and indexes an empty `contentDamage` array — add the missing null/length guards.

**Tech Stack:** Java 25, Maven, MySQL (`jdbc:mysql://`) via JDBC, Bukkit. **No unit tests** in this slice — the DB paths need a live MySQL and the item-block paths need heavy Bukkit/entity setup; correctness is by compile + inspection + the Task 4 adversarial review (consistent with the non-unit-testable parts of the prior slices). The audit is the spec: `audit.md` §"Data-layer correctness".

**Branch:** `architect-rework-2026`. Build: `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.2.10-hotspot"`; never `mvn clean` (OneDrive locks `target/`).

**Already fixed / out of scope:** `RpManager.getLatestVersion` unboxing NPE — already null-guarded (`RpManager.java:486` `if(boxed==null) continue;`). The `refreshSHA` stream/config and `EntityLogger` CME remain deferred (Risk F follow-ups). Reducing the keep-alive reconnect **log** frequency and removing the placeholder DB section from the shipped `config.yml` are noted follow-ups, not in this slice.

---

## File Structure

| File | Change |
| --- | --- |
| `serverResoucePack/RpDatabaseConnector.java` | add `client` column (CREATE + ALTER); close leaked `ResultSet`; guard all DB ops against no-DB/null-statements; don't connect/keep-alive when unconfigured |
| `specialBlockHandling/specialBlocks/SpecialBlockItemBlock.java` | guard empty `contentDamage` (line 122) + null `getSpecialBlock` result (line 264) |
| `specialBlockHandling/data/ItemBlockData.java` | guard null `getArmorStand`/`getHelmet` (lines 54-56) |

---

## Task 1: RpDatabaseConnector — add the missing `client` column

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpDatabaseConnector.java` (`checkTablesSync`, `:142-151`)

The INSERT/UPDATE/SELECT (`:108-113`) all reference a `client` column, but `CREATE TABLE` (`:145-146`) omits it → on a fresh DB every RP write throws "Unknown column 'client'" (swallowed as SEVERE), so per-player RP settings never persist.

- [ ] **Step 1: Add `client` to the CREATE and add an idempotent ALTER** — replace `checkTablesSync` (`:142-151`):

```java
    private synchronized void checkTablesSync(){
        try {
            Log.debug("Checking RP database tables exist on " + dbName);
            String statement = "CREATE TABLE IF NOT EXISTS architect_rp (uuid VARCHAR(50), "
                             + "auto BIT, variant VARCHAR(30), resolution INT, client VARCHAR(30), "
                             + "currentURL VARCHAR(100), KEY(uuid))";
            dbConnection.createStatement().execute(statement);
            // Bring pre-existing tables (created before the client column existed) up to date.
            try {
                dbConnection.createStatement().execute("ALTER TABLE architect_rp ADD COLUMN client VARCHAR(30)");
                Log.info("Added missing 'client' column to architect_rp on RP database " + dbName);
            } catch (SQLException alterEx) {
                // Expected when the column already exists (duplicate column) — nothing to do.
                Log.debug("architect_rp already has the 'client' column on " + dbName);
            }
        } catch (SQLException ex) {
            Log.error("Failed to create/verify architect_rp table on RP database " + dbName, ex);
        }
    }
```

- [ ] **Step 2: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpDatabaseConnector.java
git commit -m "fix(data): create/patch the architect_rp client column so RP settings persist (Wave C)"
```

---

## Task 2: RpDatabaseConnector — no leaks, no NPE when the DB is unavailable

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpDatabaseConnector.java` (constructor `:53-69`, `checkConnection` `:83-98`, `disconnect` `:125-140`, `loadRpSettingsSync` `:166-192`, `saveRpSettingsSync` `:204-220`)

Two problems: (a) `loadRpSettingsSync` closes its `ResultSet` only inside `if(result.next())`, leaking it on the no-row path; (b) when `connect()` fails (no/unreachable DB) the prepared statements stay `null`, so the async `load`/`save`/`disconnect` calls NPE. Guard everything and use try-with-resources.

- [ ] **Step 1: Add a `dbConfigured` field + only connect/keep-alive when configured** — the constructor (`:53-69`) currently always `connect()`s and starts the keep-alive even when no DB section is present. Change it so a null config section (no DB configured) skips both. Add the field near the others (`:51`):

```java
    private boolean connected;

    private final boolean dbConfigured;
```

Replace the constructor body (`:53-69`) — keep every `final` field assigned on all paths:

```java
    public RpDatabaseConnector(ConfigurationSection config) {
        dbConfigured = (config != null);
        if(config==null) {
            config = new MemoryConfiguration();
        }
        dbUser = config.getString("user","development");
        dbPassword = config.getString("password","development");
        dbName = config.getString("dbName","development");
        dbIp = config.getString("ip", "localhost");
        port = config.getInt("port",3306);
        if(dbConfigured) {
            connect();
            keepAliveTask = new BukkitRunnable() {
                @Override
                public void run() {
                    checkConnection();
                }
            }.runTaskTimerAsynchronously(ArchitectPlugin.getPluginInstance(),0,1200);
        } else {
            Log.info("No RP database configured; RP settings will not be persisted.");
            keepAliveTask = null;
        }
    }
```

- [ ] **Step 2: Guard `checkConnection`** — first line of `checkConnection` (`:83`), so the keep-alive is a no-op when unconfigured:

```java
    private synchronized void checkConnection() {
        if(!dbConfigured) {
            return;
        }
        try {
```

- [ ] **Step 3: Null-guard `disconnect`** — replace the close block (`:130-139`) so it tolerates never-connected / partial state:

```java
        if(dbConnection!=null) {
            try {
                if(insertPlayerRpSettings!=null) insertPlayerRpSettings.close();
                if(updatePlayerRpSettings!=null) updatePlayerRpSettings.close();
                if(selectPlayerRpSettings!=null) selectPlayerRpSettings.close();
                dbConnection.close();
            } catch (SQLException ex) {
                Log.error("Failed to close RP database connection to " + dbName + " at " + dbIp + ":" + port, ex);
            }
        }
```

- [ ] **Step 4: Guard + fix the leak in `loadRpSettingsSync`** — replace the whole method (`:166-192`):

```java
    private synchronized void loadRpSettingsSync(UUID uuid, Map<UUID, RpPlayerData> dataMap) {
        if(!connected || selectPlayerRpSettings==null) {
            return; // no reachable DB: leave the entry absent (caller falls back to defaults)
        }
        try {
            selectPlayerRpSettings.setString(1, uuid.toString());
            try (ResultSet result = selectPlayerRpSettings.executeQuery()) {
                if(result.next()) {
                    RpPlayerData data = new RpPlayerData();
                    data.setAutoRp(result.getBoolean("auto"));
                    data.setCurrentRpUrl(result.getString("currentURL"));
                    data.setVariant(result.getString("variant"));
                    data.setResolution(result.getInt("resolution"));
                    data.setClient(result.getString("client"));
                    if(data.getClient()==null) data.setClient("vanilla");
                    dataMap.put(uuid,data);
                }
            }
        } catch (SQLException ex) {
            Log.error("Failed to load RP settings for player " + uuid + " from database " + dbName, ex);
            dataMap.put(uuid,new RpPlayerData()); // load-failed marker (default; ConcurrentHashMap forbids null)
            connected = false;
        }
    }
```

(This closes the `ResultSet` on every path via try-with-resources, keeps the no-row behavior unchanged, and preserves the non-null load-failed marker.)

- [ ] **Step 5: Guard `saveRpSettingsSync`** — first lines of the method (`:204`):

```java
    private synchronized void saveRpSettingsSync(Player player, RpPlayerData data) {
        if(!connected || selectPlayerRpSettings==null) {
            return; // no reachable DB: nothing to persist
        }
        try {
```

- [ ] **Step 6: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpDatabaseConnector.java
git commit -m "fix(data): RP connector degrades gracefully with no DB + closes leaked ResultSet (Wave C)"
```

---

## Task 3: Item-block edits — guard null lookups and empty arrays

**Files:**
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/specialBlocks/SpecialBlockItemBlock.java` (`:122`, `:261-264`)
- Modify: `src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/ItemBlockData.java` (`:54-56`)

- [ ] **Step 1: Guard the empty `contentDamage` array** — `SpecialBlockItemBlock.java:122`. `NumericUtil.getRandom(0, contentDamage.length-1)` with a length-0 array throws `IllegalArgumentException: bound must be positive`. Replace line 122:

```java
            int currentDamage = contentDamage.length>0
                    ? contentDamage[NumericUtil.getRandom(0, contentDamage.length-1)] : 0;
            placeArmorStand(blockPlace, blockFace, playerLoc, currentDamage);
```

- [ ] **Step 2: Guard the null `getSpecialBlock` result** — `SpecialBlockItemBlock.java:261-264` (inside `getArmorStand`). `getSpecialBlock(...)` returns null for a stale/renamed id, then `specialBlock.isArmorStandChanged(...)` NPEs. Replace:

```java
                SpecialBlockItemBlock specialBlock = (SpecialBlockItemBlock) SpecialBlockInventoryData
                                                             .getSpecialBlock(SpecialBlockItemBlock
                                                                              .getIdFromArmorStand((ArmorStand)entity));
                if(specialBlock!=null && !specialBlock.isArmorStandChanged((ArmorStand)entity, loc.getBlock())) {
                    return (ArmorStand) entity;
                }
```

- [ ] **Step 3: Guard null armor-stand / helmet** — `ItemBlockData.java:54-56` (inside `createItemBlockData`). `getArmorStand(...)` can return null and `getHelmet()` can return null. Replace lines 54-56:

```java
                ArmorStand armorStand = SpecialBlockItemBlock.getArmorStand(block.getLocation());
                if(armorStand==null) {
                    return null;
                }
                ItemStack contentItem = armorStand.getHelmet();
                if(contentItem==null) {
                    return null;
                }
                ItemMeta meta = contentItem.getItemMeta();
```

- [ ] **Step 4: Compile** — `mvn -q compile` → BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mcmiddleearth/architect/specialBlockHandling/specialBlocks/SpecialBlockItemBlock.java \
        src/main/java/com/mcmiddleearth/architect/specialBlockHandling/data/ItemBlockData.java
git commit -m "fix(data): guard item-block placement/copy against null lookups + empty damage array (Wave C)"
```

---

## Task 4: Verification + tracker + review

- [ ] **Step 1: Full test run** — `mvn test`. Expected: BUILD SUCCESS, existing 25 tests still green (no new tests in this slice).

- [ ] **Step 2: Grep the RP connector for remaining unguarded statement use** — `grep -n "PlayerRpSettings\.\|ResultSet\|executeQuery\|createStatement" src/main/java/com/mcmiddleearth/architect/serverResoucePack/RpDatabaseConnector.java` — confirm every `*PlayerRpSettings.*` use is under a `connected`/null guard and no `ResultSet` is left unclosed.

- [ ] **Step 3: Update the tracker** — in `AUDIT-REMEDIATION-PLAN.md`, mark Risk G (data-layer) fixed for the `client` column + ResultSet leak + item-block NPEs; note the deferred keep-alive-log-frequency / config.yml-placeholder items. Do not reference the local-only `docs/security/path-traversal-fix.md`.

- [ ] **Step 4: Commit** the tracker.

- [ ] **Step 5: Independent adversarial review** of the slice: verify the schema change is idempotent and safe on both fresh and existing tables; that no DB op can still NPE when unconnected; that the `ResultSet` cannot leak on any path; that the item-block guards return sensibly (no behavior regression for the normal, non-null case); and that removing the eager connect when unconfigured didn't break the configured path.

---

## Self-Review Notes (author)

- **Spec coverage vs audit Risk G:** fresh-DB `client` column (Task 1) ✓; ResultSet leak on no-row path (Task 2) ✓; no-DB NPE spam / statement-null (Task 2) ✓; item-block null `getSpecialBlock`/`getArmorStand`/`getHelmet` (Task 3) ✓; empty `contentDamage` crash (Task 3) ✓. `getLatestVersion` NPE already fixed (out of scope, noted). Stale-protocol-map / `Via.getAPI` and keep-alive log spam noted as follow-ups.
- **`final` field discipline (Task 2):** `keepAliveTask` (final) is assigned on both constructor branches (task or null); `dbConfigured` (final) assigned once; the `dbUser`… fields are assigned from defaults even in the unconfigured path so the class stays constructible.
- **Behavior preservation:** `loadRpSettingsSync` keeps its no-row = no-entry behavior; the error path keeps the non-null default marker from the thread-safety review fix. `saveRpSettingsSync`/`loadRpSettingsSync` early-return only when there is genuinely no reachable DB, so the configured happy-path is unchanged.
- **Testability:** no unit tests — DB paths need live MySQL, item-block paths need entity/world setup; verified by compile + Task 4 adversarial review, stated up front.
