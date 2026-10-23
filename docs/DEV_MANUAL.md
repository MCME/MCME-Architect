# MCME-Architect — Developer Manual

For developers of MCME-Architect 3.2.0. For how to use the plugin, see the [User Manual](USER_MANUAL.md).

## Contents

1. [Mental model](#mental-model)
2. [Project layout](#project-layout)
3. [Building and testing](#building-and-testing)
4. [Startup and wiring](#startup-and-wiring)
5. [Configuration and data](#configuration-and-data)
6. [Cross-cutting services](#cross-cutting-services)
7. [Subsystems](#subsystems)
8. [Adding a feature](#adding-a-feature)
9. [Releasing](#releasing)
10. [Pitfalls](#pitfalls)

## Mental model

MCME-Architect is the builder's toolkit of the MCME (Minecraft Middle-earth) build server. It is one Paper 26.2 plugin,
named `MCME-Architect`, with one main class: `com.mcmiddleearth.architect.ArchitectPlugin`. Inside it live about twenty
features that seldom call each other. Among them are special blocks, build inventories, resource packs, live biome
tuning, map layers, copy and paste, the no-physics list, and editors for armor stands, banners, signs and paintings.
Each feature has a package of its own.

### What the features share

| Shared piece | Where | What a feature gets from it |
|---|---|---|
| The main class | `ArchitectPlugin` | Start-up, `loadData()` (which `/architect reload` runs again), shutdown, and the static `getPluginInstance()` |
| Settings | `config.yml`, written through `ConfigFile` | Server-wide settings |
| Per-world switches | `Modules`, `PluginData`, `WorldConfig` | 64 on/off switches, and a few lists, per world |
| Permissions | the `Permission` enum and `plugin.yml` | Node names, checked with `PluginData.hasPermission` |
| Logging | `Log`, `LogFileManager` | Architect's own rotating log files |
| Chat and helpers | PluginUtils, through `PluginData.getMessageUtil()` | The `[Architect]` prefix, colours, standard error texts, paged and clickable lists |
| Build permission | `com.mcmiddleearth.util.TheGafferUtil` | TheGaffer's job-area check |

A typical feature has a manager or data class with static state, which loads its files in
`ArchitectPlugin.loadData()`, one or more Bukkit listeners, and one or more commands whose executors extend
`AbstractArchitectCommand`.

The code comes in two generations. The 2.x code (2016 to 2025) uses static manager classes, legacy `ChatColor` and
large commented-out blocks. The 3.x code (map layers, biome tuning, the game-mode switcher, the `/rp release` feedback)
uses small final classes, records, interfaces as seams for tests, and Javadoc in plain English. New code follows the
3.x style.

### Ideas to hold

1. **Features are switched per world.** Nearly every feature asks `PluginData.isModuleEnabled(world, Modules.X)`
   before it acts. Each switch has a YAML key, such as `modules.environment.noPhysicsListEnabled`, read from
   `WorldConfig/<world>.yml` and then from `WorldConfig/defaultWorldConfig.yml`. A switch missing from both files counts
   as on. On a new server, every feature is therefore on in every world.
2. **Build permission comes from TheGaffer, and the check fails open.** Architect asks TheGaffer, by reflection,
   whether a player may build at a location. Without TheGaffer, or when the call fails, the answer is yes.
3. **Architect logs to its own files.** By default nothing Architect logs reaches the server console or `latest.log`.
   It goes to `plugins/MCME-Architect/logs/architect_*.log`.
4. **Every dependency is provided.** The jar holds Architect's own classes and four resource files; nothing is shaded.
   The server supplies Paper, PluginUtils, WorldEdit, ProtocolLib, dynmap, ViaVersion, MCME-Connect, TheGaffer and a
   JDBC driver. Only PluginUtils is a hard `depend` in `plugin.yml`. WorldEdit and TheGaffer are not declared at all.
5. **Servers share one `config.yml`.** A server that saved the copy it read at start would undo what other servers
   wrote since. No code calls `saveConfig()`: every write goes through `ConfigFile.update`, which reads the file first.
6. **State is static, and paths are fixed when a class loads.** Many classes compute data-folder paths in
   `static final` fields from `ArchitectPlugin.getPluginInstance()`. Loading the plugin twice in one JVM therefore
   breaks, and that is why every test class runs in a JVM of its own.
7. **Architect starts before the worlds.** `plugin.yml` sets `load: STARTUP`, so `Bukkit.getWorlds()` is empty during
   `onEnable`. Worlds are set up on `WorldLoadEvent`, region loaders wait and retry, and map layers look dynmap up
   later.

## Project layout

### Source packages

All main code is under `src/main/java/com/mcmiddleearth/`. Package names below are relative to
`com.mcmiddleearth.architect`, unless they start with `com.`.

| Package | What it holds | Main entry classes |
|---|---|---|
| (root) | The core: the plugin class, world configs, switches, permissions, config writes, logging | `ArchitectPlugin`, `PluginData`, `WorldConfig`, `Modules`, `Permission`, `ConfigFile`, `Log`, `LogFileManager`, `InventoryAccess` |
| `additionalCommands` | The command base class; `/architect`, `/fbt`, `/parrot`, `/weselect` | `AbstractArchitectCommand`, `ArchitectCommand` |
| `additionalListeners` | World environment and protection rules: weather, drops, fire, decay, enchantment stripping, lecterns, container protection | `GameMechanicsListener`, `AdditionalProtectionListener`, `OpItemListener`, `InventoryProtectionListener` |
| `armorStand` (+ `guard`) | The armor stand editor (`/armor`) and armor stand protection | `ArmorStandEditorCommand`, `ArmorStandListener`, `ArmorStandEditorConfig` |
| `bannerEditor`, `signEditor`, `paintingEditor` | Banner, sign and painting editors | `BannerEditorCommand`, `SignCommand`, `SignListener`, `PaintingListener` |
| `biomeTuning` | Live biome tuning (`/biometune`) and its Dialog editor | `BiomeTuning`, `BiomeTuneCommand`, `BiomeTuningService`, `ReflectionNmsBridge`, `RefreshCoordinator`, `BiomeEditor` |
| `blockData` (+ `attributes`) | The block-state properties the stick edits, by reflection on Bukkit `BlockData` interfaces | `BlockDataManager` |
| `chunkUpdate` | Block resends to the clicking player, for clients that show wrong connections | `ChunkUpdateListener`, `ChunkUpdateUtil` |
| `copyPaste` | Architect's own clipboard: `/copy`, `/cut`, `/paste`, `/rot`, `/flip`, `/undo`, `/redo` | `CopyPasteManager`, `Clipboard` |
| `customHeadManager` | `/chead`, the custom head collection and its gallery | `HeadCommand`, `CustomHeadManagerData`, `CustomHeadGallery` |
| `entityLogging` | Entity counts per chunk, with a dynmap layer (`/architect eLog`) | `EntityLogger`, `ELogDynmapUtil` |
| `gamemodeSwitcher` | The F3+F4 game-mode switcher for builders, and the invisible-blocks gate | `GamemodeSwitcher`, `GamemodeSwitcherProtocol`, `InvisibleBlockListener` |
| `mapLayers` | Architect's layers on the dynmap web map | `MapLayers`, `ArchitectLayers`, `DynmapBackend`, `ItemBlockBudget` |
| `noPhysicsEditor` | The no-physics list, exception areas, the redstone freeze (`/nophy`) | `NoPhysicsListener`, `NoPhysicsData`, `NoPhysicsCommand` |
| `randomiser` | `/random` | `RandomiserCommand`, `RandomiserConfig` |
| `serverResoucePack` (sic) (+ `RegionEditConversation`) | Resource packs: `/rp`, regions, player settings, releases | `RpManager`, `RpCommand`, `RpListener`, `RPSwitchTask`, `RpDatabaseConnector`, `RpReleaseUtil` |
| `specialBlockHandling` (+ `command`, `customInventories`, `data`, `itemBlock`, `listener`, `specialBlocks`) | Special blocks, the build inventory (`/inv`), `/get` kits, item blocks, the stick and the flint, doors, furnaces | `SpecialBlockListener`, `SpecialBlockInventoryData`, `SpecialBlock`, `CustomInventory`, `ItemBlockManager`, `InvCommand`, `GetCommand` |
| `viewDistance` | `/viewdistance` | `ViewDistanceManager`, `ViewDistanceProtocol`, `ViewDistanceListener` |
| `voxelStencilEditor`, `weSchematicsViewer` | VoxelSniper stencil files (`/vv`, `/sl`); the WorldEdit schematic list (`/schlist`) | `VvCommand`, `SlCommand`, `SchListCommand` |
| `WorldGeneration` (capital W) | The superflat world generator | `WorldGenerationManager`, `SuperflatChunkGenerator` |
| `util` | The WorldEdit presence check | `WorldEditGuard` |
| `watcher` | A listener health check that nothing triggers | `WatchedListener`, `WatcherEvent` |
| `com.mcmiddleearth.util` | Helpers shared by many packages | `TheGafferUtil`, `PathSafety`, `DevUtil`, `ResourceUtil`, `StreamGobbler`, `HeadUtil`, `DoorUtil` |

`specialBlockHandling` is by far the largest package: more than a third of the 302 main source files.

### Resources

The pom declares one resource directory, `src/main/resources`, with filtering on and a list of includes. Declaring
`<resources>` replaces Maven's default, so **only these four files go into the jar**:

| File | Used for |
|---|---|
| `plugin.yml` | The plugin description. Maven fills in `name: ${project.artifactId}` and `version: ${project.version}` |
| `config.yml` | The default config. `saveDefaultConfig()` writes it to the data folder when none exists, and it is the defaults of the live config (see [config.yml](#configyml)) |
| `protocolVersions.yml` | Version keys mapped to protocol numbers. `ResourceUtil` copies it to the data folder when it is missing |
| `items.json` | Legacy (pre-1.13) item names, read by `ConversionUtil_1_13` |

All four are filtered, so `${...}` or `@...@` written in any of them is replaced at build time.

The other files in `src/main/resources` are **not** in the jar, and the plugin does not read them from the jar:
- seven shell scripts: old copies of resource-pack release scripts (`release*.sh`) and build-inventory sync scripts
  (`downloadInventory.sh`, `updateInventories.sh`, `uploadInventory.sh`). Architect runs the scripts that `config.yml`
  names, from folders on the server's disk;
- `upload.txt`, the default input file of `/chead upload`. By default the command downloads it over HTTP from the raw
  `src/main/resources/` folder of the original author's MCME-Architect repository on GitHub (`CustomHeadUploadPath`,
  written into `config.yml` when missing);
- ten pre-1.13 inventory files (`blocks.yml`, `doors.yml`, `specialBlocks*.yml`, `moriaCustomInventory.yml` and
  others). Live inventories are read from the data folder.

### Tests

`src/test/java/com/mcmiddleearth/` mirrors the main packages, with shared helpers in `architect.testsupport`.
`src/test/resources/rp-release/` holds nine captured outputs of ResourcePackScripts' release scripts (its README says
how they were made), which `RpReleaseReportTest` reads.

### Top-level files

Besides `pom.xml`, `README.md`, `docs/` (the User and Developer Manuals), `.github/workflows/build.yml` (CI) and
`LICENSE` (the GNU GPL v3; source headers say "version 3, or (at your option) any later version"), the repository root
holds some leftovers:

| File | What it is |
|---|---|
| `1.18_fix/script.py` | A one-off 2022 script that shifted the Y values in `.reg` region files for the 1.18 height change |
| `heads.zip`, `cheads.zip` | Custom-head files from 2016. `cheads.zip` has the layout of the data folder's `customHeads/accepted` |
| `nb-configuration.xml` | NetBeans project settings; it sets the GPLv3 licence header that NetBeans puts at the top of new files |

Neither the code nor the build uses them. The repository has no `.gitattributes`, no `.editorconfig` and no Maven
wrapper.

## Building and testing

### Requirements

- **JDK 25.** The compiler runs with `<release>25</release>`, and paper-api 26.x is Java 25 bytecode.
- **Maven 3.9.** There is no Maven wrapper.
- **A git checkout.** The jar name carries the commit id.
- **Network access** to the repositories in the pom: Maven Central; those of PaperMC, Mojang, EngineHub (WorldEdit),
  dmulloy2 (ProtocolLib), ViaVersion and dynmap; and repo.mcmiddleearth.com for the `com.mcmiddleearth` artifacts
  (PluginUtils, MCME-Connect). Reading needs no credentials.

To try the plugin on a server you also need Paper 26.2, PluginUtils, WorldEdit 7.4, and the optional plugins your
feature uses (see [Dependencies](#dependencies)).

### Build commands

From the repository root:

```bash
mvn -B -ntp clean verify                          # build, run every test, make the jar: what CI runs
mvn -B package -DskipTests                        # the jar only
mvn -B package -Dbuild.type=RELEASE               # a jar named as a release build
mvn -B test -Dtest=FbtCommandTest                 # one test class
mvn -B test -Dtest='FbtCommandTest#fbtHasItsAliases'   # one test method
mvn -B test -Dtest='**/mapLayers/*Test'           # one package
```

The jar lands in `target/`.

### The jar name

`<finalName>` is `${project.artifactId}-${project.version}-${build.type}-${git.commit.id.abbrev}`. A local build gives
`target/MCME-Architect-3.2.0-DEV-<commit>.jar`.

- `build.type` is a pom property: `DEV` by default; pass `-Dbuild.type=RELEASE` for a release build (`SNAPSHOT` is
  also allowed).
- `git.commit.id.abbrev` comes from `git-commit-id-maven-plugin` (goal `revision` in phase `initialize`, 7 characters).
  The comment above `<finalName>` gives the reason: three different trees once called themselves 2.10.6.
- **Do not declare `git.commit.id.abbrev` as a pom property.** Maven fills in `finalName` while it builds the model,
  before the plugin runs, and a declared value would win.
- Because the property is not declared, you can set it on the command line. Do so in a linked git worktree, where the
  plugin can read the main checkout's HEAD instead of the worktree's:

  ```bash
  mvn -B -ntp clean verify -Dgit.commit.id.abbrev=$(git rev-parse --short=7 HEAD)
  ```

- Without any `.git`, the build still runs (`failOnNoGitDirectory` is `false`), but the jar name has no real commit id.
  The build type and the commit live only in the file name (see [Versioning](#versioning)).

### Dependencies

Everything the plugin needs at run time is `provided`: the server supplies it. Nothing is shaded; the pom has no shade
or assembly plugin.

| Dependency | Version | Why it is there |
|---|---|---|
| `io.papermc.paper:paper-api` | `26.2.build.126-stable` | The server API. It also brings Adventure, Gson and Guava |
| `com.mojang:datafixerupper` | `10.0.21`, all transitives excluded | `Codec`, `DataResult` and `JsonOps` for biome tuning. The 26.2 server ships this version |
| `com.comphenix.protocol:ProtocolLib` | `5.3.0`, all transitives excluded | Packet listeners for `/viewdistance` and the game-mode switcher. Servers run a 5.5.0+ dev build. The exclusion keeps ProtocolLib's byte-buddy away from the one MockBukkit needs |
| `com.mcmiddleearth:PluginUtils` | `2.0.4` | MCME's helper library: messages, lists, file and number helpers, WorldEdit selections, plot storage. The only hard `depend`. As a direct dependency it wins over the older PluginUtils that MCME-Connect brings in |
| `com.mcmiddleearth:MCME-Connect` | `3.0.1` | One class: `PlayerConnectEvent`, in `RpListener` |
| `com.sk89q.worldedit:worldedit-core`, `worldedit-bukkit` | `7.4.5` | Selections and region classes. `worldedit-bukkit` excludes Bukkit, Spigot and bStats |
| `com.viaversion:viaversion-api` | `5.11.0` | The client's real protocol version, in `RpManager.getClientProtocolVersion` only |
| `us.dynmap:dynmap-api` | `3.5`, Bukkit excluded | Map layers and the entity-log layer. Without the exclusion, the test class path depended on which repository answered (`ClasspathTest` guards this) |
| `org.mockbukkit.mockbukkit:mockbukkit-v26.2` | `4.116.1` (test) | The mock server |
| `org.junit.jupiter:junit-jupiter` | `6.1.0` (test) | JUnit 6: API, parameterised tests and engine |
| `org.junit.platform:junit-platform-launcher` | `6.1.0` (test) | Lets Surefire discover JUnit 6 tests |

**No JDBC driver is declared.** `RpDatabaseConnector` opens a `jdbc:mysql:` URL through `DriverManager`, so the server
must provide a MySQL driver at run time; in tests one reaches the class path through another dependency, and
`FakeMysql` takes it off the driver list. No mocking library is declared either: the test doubles are written by hand.

Only three build plugins are pinned: `git-commit-id-maven-plugin` 9.0.1, `maven-compiler-plugin` 3.14.0 and
`maven-surefire-plugin` 3.5.2. The resources, jar, install and deploy plugins come from the Maven that runs the build.

### The test stack

- JUnit Jupiter 6.1, with `junit-jupiter-params`, and MockBukkit for Paper 26.2. Assertions are JUnit's own.
- 3.2.0 has 86 test classes and 11 helper classes. About two in five test classes use no MockBukkit at all: plain unit
  tests, mostly in `biomeTuning` and `mapLayers`.
- Surefire runs with `<reuseForks>false</reuseForks>`: **every test class runs in a fresh JVM**, one class after
  another. The reason is the static state described in [Mental model](#mental-model): a second load of the plugin in
  the same JVM would use a stale, deleted data-folder path.
- Tests read `plugin.yml` from the class path, after Maven has filtered it. The source copy has
  `name: ${project.artifactId}`, which Bukkit rejects. Run the tests through Maven, or set your IDE up to apply Maven's
  resource filtering.

### How a test class loads the plugin

The rule, repeated in many test headers: one `MockBukkit.mock()` and one `MockBukkit.load(ArchitectPlugin.class)` per
class, in `@BeforeAll`, and `MockBukkit.unmock()` in `@AfterAll`. The tests of a class share that server.

```java
// One mock/load per class, as in LogFileTest.
class MyFeatureTest {
    private static ServerMock server;
    private static ArchitectPlugin plugin;

    @BeforeAll static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll static void tearDown() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }
}
```

- A test that needs its own `config.yml` writes it with `TestConfig.write(server, lines...)` after `mock()` and before
  `load`. Keys the file leaves out still come from the bundled config.
- A test that disables Architect, or needs a first-time setup, gets a class (and so a JVM) of its own, as
  `EntityLogOnDisableTest` does. Where order matters within a class, use `@TestMethodOrder` with `@Order`, as
  `MapLayersWiringTest` does.

### Test helpers and fakes

There is no base test class. The helpers are plain classes:

| Helper | Package | Stands in for |
|---|---|---|
| `TestConfig` | `architect.testsupport` | A server's `config.yml`: writes the test's own file where MockBukkit will put the data folder. `withRpDatabase(server, dbName)` writes a `config.yml` with only an `rpSettingsDatabase` section, for `FakeMysql`, replacing what `TestConfig.write` wrote |
| `FakeMarkerApi` | `architect.testsupport` | dynmap's marker API, in memory, built from JDK proxies |
| `FakeDynmap` | `architect.entityLogging` | The dynmap plugin. Load it after Architect, as on a server: `MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description())`. `failDrawing(true)` makes marker calls fail |
| `FakeGaffer` | `com.mcmiddleearth.util` | TheGaffer 3.0.0's reflective static API (`hasBuildPermission`, `getBuildProtectionMessage`, `recordExternalBuild`). It records what it was asked and told |
| `FakeMysql` | `architect.serverResoucePack` | A MySQL server: a JDBC `Driver` that answers every `jdbc:mysql:` URL and knows exactly the statements `RpDatabaseConnector` sends |
| `FakeScript`, `FakeChild` | `architect.serverResoucePack` | A release script's `Process` and the processes it starts, injected through `RpReleaseUtil.starter` |
| `FakeNmsBridge` | `architect.biomeTuning` | `ReflectionNmsBridge`, and so Minecraft's internals |
| `RecordingPresenter`, `FakeAnswers` | `architect.biomeTuning` | The Paper Dialog presenter, and the values a click on a Dialog sends back |
| `BiomeTuningOnAFakeServer` | `architect.biomeTuning` | Lets tests in other packages start biome tuning on `FakeNmsBridge` |

### Test patterns

- **Commands.** Add a `PlayerMock`, give it permissions with `addAttachment(plugin, "architect.x", true)` (or
  `setOp(true)`), run `player.performCommand("...")` or `server.dispatchCommand(server.getConsoleSender(), "...")`, and
  read the replies with `nextMessage()`.
- **Listeners.** Either fire the real event through every registered listener with
  `server.getPluginManager().callEvent(event)`, as on a server, or call the handler on a fresh listener instance.
- **Module switches.** Turn a switch off for one world with `PluginData.setModuleEnabled(world, Modules.X, false)`.
- **Async work.** Tick with `server.getScheduler().performOneTick()` and short sleeps until a condition holds (a latch,
  a message, a log line, `getActiveWorkers().isEmpty()`), for up to 10 seconds. No test calls `waitAsyncTasksFinished`.
- **Time.** Newer code takes its clock as a parameter (a `LongSupplier` or a `Supplier<Instant>`); tests pass a lambda
  over a value they control (an `AtomicLong` in `BiomeEditorTest` and `ItemBlockBudgetTest`).
- **Logs.** Call `LogFileManager.flush()` and read the newest `logs/architect_*.log`, or add a `Handler` to
  `plugin.getLogger()`.
- **Processes and HTTP.** `RpReleaseUtil` has package-private static seams for tests: `starter`, `onWindows` and
  `releaseSite`. Downloads go to a JDK `HttpServer` on the loopback address.

### MockBukkit lessons

These come from comments and patterns in the test suite:

- MockBukkit makes a plugin's data folder (`<name>-<version>` in its temporary folder) only when it loads the plugin.
  Write files there before `load` with `TestConfig`.
- It does not register the permissions in `plugin.yml`. A test that relies on their defaults or children adds them to
  the plugin manager first, as `InvisibleBlockListenerTest` does. Bukkit `Permission` objects need a server, so even a
  test that only parses `plugin.yml` calls `MockBukkit.mock()` (`PluginYmlPermissionsTest`).
- It has no `/tellraw`, which PluginUtils uses to send clickable lines (`FancyMessage`) from the console. Register a
  fake `tellraw` in `server.getCommandMap()` to capture them, as `NoPhysicsCommandTest` does.
- Where Paper would log an exception, MockBukkit passes it on: a task's exception comes back out of `performTicks`,
  and a failure in `onDisable` out of the disable call. A repeating task that threw is dropped, not run again.
- Its block states have no legacy data values. Asking for one throws an exception that **skips** the test instead of
  failing it, so check the Skipped count of a run.
- It keeps a block's state apart from the block's data, and it cannot make some block data from text.
- It leaves a client's protocol, brand and received packs to the test. `RpJoinCheckTest` subclasses `PlayerMock` for
  this.
- WorldEdit's `BukkitWorld` needs a real WorldEdit, so tests build regions on WorldEdit's `NullWorld`. For each
  `regions/*.reg` or `itemBlockRegions/*.reg` file, `loadData()` schedules a loader 200 ticks later that calls
  `new BukkitWorld(world)`: once a test has saved such a file, it must not tick that far after the next load or reload.
- The real `ReflectionNmsBridge` finds no server internals, so biome tuning reports itself unavailable while the rest
  of Architect runs. Use `FakeNmsBridge`.
- No test builds a Paper Dialog. The editor's windows are plain `EditorView` records shown through `DialogPresenter`;
  tests install a `RecordingPresenter` and run a button's action with `FakeAnswers`.
- `MockBukkit.load` does not check `depend`: Architect loads with PluginUtils only on the class path.

### CI

`.github/workflows/build.yml` is the only workflow.

| Step | What it does |
|---|---|
| Triggers | A push to any branch, a push of a `v*` tag, any pull request, and a manual run (`workflow_dispatch`) |
| Concurrency | One run per ref. A new run cancels the one in progress, except on tags |
| Checkout | `actions/checkout@v4` with `fetch-depth: 0`, so the commit id is real |
| JDK | Temurin 25 through `actions/setup-java@v4`, with the Maven cache. It writes a `settings.xml` server entry with the id `mcme`, which `<distributionManagement>` uses |
| Build and test | `mvn -B -ntp clean verify`, a DEV build |
| Artifact | The DEV jar, found with the glob `target/MCME-Architect-*.jar`, is uploaded as the run's artifact |
| Tag check | On a `v*` tag only: the tag must be exactly `v` plus the pom version, or the run fails |
| Publish | On a `v*` tag or a manual run: `mvn -B -ntp deploy -DskipTests -Dbuild.type=RELEASE`, to repo.mcmiddleearth.com (`/releases`, or `/snapshots` for a SNAPSHOT version) |

- The repository credentials come from the secrets `MCME_MAVEN_NAME` and `MCME_MAVEN_SECRET`, passed on as environment
  variables of the same names. They are GitHub Actions secrets.
- The published jar is a second build of the same commit, with `-DskipTests -Dbuild.type=RELEASE`, not the DEV jar of
  the run's artifact.
- **The manual-run caveat:** a manual run publishes whatever branch it runs on, as a release of the pom's version,
  without the tag check. Run it only on a commit that really is that version.
- The workflow makes no GitHub Release (see [Releasing](#releasing)). Publishing to repo.mcmiddleearth.com lets
  other plugins build against `com.mcmiddleearth:MCME-Architect`.

## Startup and wiring

### onEnable

`ArchitectPlugin.onEnable` runs in this order:

1. Creates PluginUtils' `DevUtil`, the second debug channel (see [Logging](#logging)).
2. Sets `copyDefaults(true)` and calls `saveDefaultConfig()`, which writes the bundled `config.yml` only if the data
   folder has none.
3. Stores the instance that `getPluginInstance()` returns, then calls `LogFileManager.install(this)`. From here on,
   `Log` writes to Architect's log file.
4. Sets PluginUtils' message prefix to `Architect`. This first use of `PluginData` runs its static initialiser, which
   creates the `SwitchStick` folder and reads `switchStickConfig.yml`.
5. Calls `loadData()` (below), then loads the config of the pseudo-world `server` (`WorldConfig/server.yml`).
6. Registers 28 listeners. If a plugin named `ProtocolLib` is present, it also registers the view-distance and
   game-mode-switcher packet listeners; otherwise it logs two warnings.
7. Registers four incoming plugin-message channels: `mcme-modpack-marker:hello` (`RpPluginMessageListener` marks the
   player as a client of the MCME modpack, for the choice of pack), and `minecraft:brand`, `l:fmlhs` and `wdl:init`
   (`TestPluginMessageListener`, debug lines only).
8. Binds the executors of the 28 commands. `/fbt` is bound only if `modules.command.fullBrightness` is on for the
   pseudo-world `server`.
9. `CustomInventoryEditor.init(this)` builds the chat conversations of the build-inventory editor.
10. Starts `RPSwitchTask` (first run after 500 ticks, then every 20) and the item-block glow task.
11. `BiomeTuning.enable(this)`, last, inside a guard of its own; then logs `MCME-Architect Enabled!`.

`loadData()` loads all feature data. It runs at start and again for `/architect reload`:

1. `MapLayers.stop()`: the old layers save and leave the map while the old data is still loaded.
2. `reloadConfig()`.
3. `PluginData.load()`: the `EntityLimit` values, every world config, and the spawn and fire settings of loaded worlds.
4. `NoPhysicsData.loadExceptionAreas()` and `CustomHeadManagerData.load()`.
5. The block, saved, item and head inventories (`SpecialBlockInventoryData`, `SpecialSavedInventoryData`,
   `SpecialItemInventoryData`, `SpecialHeadInventoryData`), then `GetData.load()` for the `/get` kits.
6. `RpManager.init()` (protocol versions, RP regions) and `ItemBlockManager.init()` (item-block regions).
7. `ViewDistanceManager.loadViewDistances()`.
8. `MapLayers.start(...)`, with the layers that `config.yml` switches on, then `GamemodeSwitcher.updateRunning()`.

The steps are not isolated from each other. An exception in one stops the rest, and the sender of `/architect reload`
gets Bukkit's internal-error message.

### Commands

Commands are classic `plugin.yml` commands. There is no Brigadier and no Paper command API. A command needs three
things:

1. An entry under `commands:` in `plugin.yml`.
2. An executor that extends `AbstractArchitectCommand`. That class is a `TabExecutor` with three abstract methods,
   `getHelpPermission()`, `getShortDescription()` and `getUsageDescription()`, and an optional `getHelpCommand()`. Its
   `onTabComplete` returns an empty list; override it to complete arguments.
3. A call such as `setCommandExecutor("copy", new CopyCommand())` in `onEnable`. It calls
   `getCommand(name).setExecutor(executor)` and adds the name to a static list.

`/architect help` walks that list, casts each executor back to `AbstractArchitectCommand`, and lists the commands
whose help permission the player holds. The hover text is `/<name>` followed by `getUsageDescription()`, and the line
text is `getShortDescription()`; by convention the short description starts with `": "`, and the usage description with its arguments, if any, then `": "`. If `getHelpCommand()` returns a command, a click
on the line **runs** it at once. If it returns `null`, a click puts `/<name> ` in the chat box.

A name that `plugin.yml` lacks makes `getCommand` return `null`, and `onEnable` fails with a `NullPointerException`.
No command in `plugin.yml` has a `permission:` key: every check is in the executors.

### Listeners

`onEnable` registers 28 listeners with `registerEvents(new XListener(), this)`. Other parts register their own
listeners when they start:

| Registered by | Listener |
|---|---|
| `BiomeTuning.enable` | `RefreshCoordinator` and `BiomeEditor` |
| `BiomeTuning.setLegacyLoginProbe(true)`, from `/biometune debug legacylogin on` | the debug-only `LegacyLoginProbe` |
| `GamemodeSwitcher` | itself |
| `MapLayers.start` | its `ServerWatch` (dynmap enabled or disabled, worlds loaded), and `ItemBlockBudget` |
| `EntityLogger.start` | `ELogListener`, while entity logging runs |
| `CustomInventory`, `SearchInventory` | each instance, for its own windows; `destroy()` unregisters it |

A few short-lived listeners come and go during an action, such as the sign editor after a sign is placed and the
block-data prompt of the build-inventory editor.

Three listener classes are never registered: `AfkListener` and `TestListener` (commented out in `onEnable`) and
`ArmorStandRollbackListener`.

### Timers

| Timer | Started by | When it runs |
|---|---|---|
| `RPSwitchTask` | `onEnable` | after 500 ticks, then every 20 |
| Item-block glow | `onEnable`, through `ItemBlockManager.startEntityGlowTask` | after 400 ticks, then every 20 |
| Biome tuning: refresh timeouts, the publish queue, editor expiry | `BiomeTuning.enable` | every 20 ticks |
| RP database keep-alive | the `RpDatabaseConnector` constructor; async | every 1200 ticks |
| RP and item-block region loaders | `RpManager.init`, `ItemBlockManager.init` | 200 ticks after a load, then each second until the region's world exists, at most ten times |
| Kit save | `GetData.load` | once, 2000 ticks after a load |
| Map-layer redraws; budget counting and saving | `MapLayers`, `ItemBlockBudget` | redraws on the tick after a change; budget recounts each second and saves each minute |
| Entity-log dumps | `EntityLogger.start` (`/architect eLog true`) | after 500 ticks, then every 2000 |

### The world generator

`getDefaultWorldGenerator(worldName, id)` returns `WorldGenerationManager.getGenerator(worldName, id)`: a void world
with one flat square of grass (`SuperflatChunkGenerator`) that reaches about 512 blocks from the origin in each
direction, or as far as the number after `_` in the id. A world uses it through the generator name `MCME-Architect`
or `MCME-Architect:<name>_<size>`. `load: STARTUP` lets Architect supply the generator before the worlds load.

### onDisable

In this order: `GamemodeSwitcher.stopRunning()` (non-op builders get op level 0 back), `MapLayers.stop()` (the budget
saves its counts), `BiomeTuning.disable()` (logs unsaved live edits), cancel `RPSwitchTask`, disconnect the RP
database, stop the glow task, `EntityLogger.stop()`, and `LogFileManager.uninstall(this)` last. The steps have no
try/catch, so a failing step skips the ones after it; the first two are first for that reason, as their comments say.

### What /architect reload does

`/architect reload` runs `loadData()` and nothing else. These need a server restart:

| Change | Why a reload misses it |
|---|---|
| `logging.*` | `LogFileManager.install` runs only in `onEnable` |
| `biomeTuning.*` | read once, in `BiomeTuning.enable` |
| `rpSettingsDatabase` | the connector is a `static final` field of `RpManager`, built once |
| `CopyPasteUndoLimit`, `CustomHeadUploadPath`, `entityLogger.*` | read in static initialisers of `CopyPasteManager`, `HeadDataBuilderText` and `ELogDynmapUtil` |
| `SwitchStick/switchStickConfig.yml`, `randomiser.yml` | read once: when `PluginData` loads, and when `RandomiserCommand` is built |
| whether `/fbt` exists; every command and plugin channel, and every listener or timer that `onEnable` starts | bound only in `onEnable` |

A reload also does not clear everything in memory. Some loaders add to what they hold instead of replacing it: the
`/get` kits, the no-physics exception areas and the protocol-version table keep entries that were removed from their
files by hand. The parsed no-physics block lists stay cached per world until the next `/nophy add` or `/nophy remove`.
For about ten seconds after a reload, no RP or item-block region exists, because their loaders wait 200 ticks.

## Configuration and data

### config.yml

#### Bundled defaults

- `saveDefaultConfig()` writes the jar's `config.yml` only when the data folder has none. New keys in a later jar are
  never written into an existing file. (The `copyDefaults(true)` that `onEnable` sets is lost when `loadData()` calls
  `reloadConfig()`, which makes a new config object.)
- `reloadConfig()` makes the jar's `config.yml` the **defaults** of the live config. A read without an explicit
  default, such as `getString("a.b")`, falls back to the bundled value when the server's file lacks the key. For a
  section that the server's file lacks but the bundled file has, `getConfigurationSection` returns an empty section. A
  read with an explicit default, such as `getBoolean(path, false)`, does not see the bundled value.
- So **anything in the bundled `config.yml` becomes a setting of every server whose file lacks it.** Keep its values
  safe as defaults. The `mapLayers` block is commented out in the bundled file for this reason, as
  `MapLayerConfigTest` explains, and the `rpSettingsDatabase` placeholders `xxx` mean "no database".

#### Writing: ConfigFile.update

Several servers can share one `config.yml`. `saveConfig()` writes the whole copy a server holds, so it would undo what
another server changed since this one read the file. Every write therefore goes through `ConfigFile.update`:

```java
ConfigFile.update(config -> config.set("myFeature.lastRun", now));
```

`update` runs on the main thread; called from another thread, it schedules itself there and returns `false`. It reads
the file from disk into the live config **in place**, applies the change, and saves. An empty or unreadable file is
left as it is, and the change is dropped and logged. The lambda gets the config just read, so look sections up again
inside it. `ConfigFileTest.nothingButConfigFileSavesTheConfig` fails if any other class calls `saveConfig()`.

#### Reading off the main thread

`update` reloads the config in place, so a read from another thread at that moment can find it empty. Work that runs
off the main thread reads its settings on the main thread before it starts, as `RpReleaseUtil.releaseResourcePack` and
`InventoryUtil` do:

```java
// Read on the main thread: an update of config.yml reads it again there, in place.
String script = plugin.getConfig().getString("myFeature.script");
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> run(script));
```

`ConfigReadThreadTest` checks the existing cases.

#### Who owns which keys

`ServerResourcePacks` and `gitHubRpReleases` belong to `RpManager` and `RpReleaseUtil` (the `/rp server` commands and
`/rp calcsha` write the first); `rpSettingsDatabase` to `RpDatabaseConnector`; `dynmap` and `mapLayers` to
`MapLayerConfig`; `entityLogger` to `ELogDynmapUtil`; `EntityLimit` to `PluginData` (read, but nothing uses the
values); `headGalleryLocation` to `CustomHeadManagerData` (written by `/chead setCollection`); `inventoryDownload` and
`inventoryUpload` to `InventoryUtil`; `logging.file` to `LogFileManager`; `biomeTuning` to `BiomeTuningSettings`. Three
keys are not in the bundled file: `CopyPasteUndoLimit` (`CopyPasteManager`), `CustomHeadUploadPath`
(`HeadDataBuilderText`, written when missing) and `inventoryUpload` (`InventoryUtil`; `/inv upload` fails until it is
set). [What /architect reload does](#what-architect-reload-does) lists the
keys that are read only once.

### WorldConfig

#### Files

| File | Meaning |
|---|---|
| `WorldConfig/defaultWorldConfig.yml` | Defaults for every world. Created when missing, with every module `true` and the built-in lists |
| `WorldConfig/<world>.yml` | Overrides for one world. Created holding only an explanatory `WorldConfiguration` key |
| `WorldConfig/server.yml` | A pseudo-world. Only its `modules.command.fullBrightness` is read, to decide whether `/fbt` is bound at all |

`PluginData` keeps one `WorldConfig` per world name. In memory the default config is named `-default`
(`PluginData.getDefaultKey()`), and commands that take a world name accept `-default` for the default file (`/nophy`,
`/chunkupdate auto`). `PluginData.load()` builds the default config first and shares its YAML with every world config.
Then it loads every `*.yml` in the folder and configures every loaded world.

#### Lookup and overrides

A value comes from `<world>.yml`; if that file lacks the key, from `defaultWorldConfig.yml`. How much a world file
overrides depends on the key:

| Key | Overridden |
|---|---|
| each `modules.*` key | per key |
| `inventoryAccess` | per inventory type |
| `noPhysicsList`, `noInteraction`, `noConnection`, `disabledBlockStates` | as a whole list |
| `allowedEnchantments`, `doubleSlabReplacements`, `armorStand` | as a whole section |
| `viewDistance` | per key |
| `itemBlock.baseLimit` | world file only, with no fallback to the default file |

- Lists and sections are parsed once, in the `WorldConfig` constructor. Module switches and `inventoryAccess` are read
  from the YAML at each call.
- Two settings are read from one world for the whole server: `modules.protection.blockOpItems` and
  `allowedEnchantments` come from the first loaded world.
- A world file that does not parse is logged and put in read-only mode: that world runs on the defaults, and its file
  is never saved.

#### Reading a module switch

```java
if (!PluginData.isModuleEnabled(player.getWorld(), Modules.COPY_PASTE)) {
    PluginData.getMessageUtil().sendErrorMessage(player, "CopyPasting is not enabled for this world.");
    return true;
}
```

`isModuleEnabled` calls `getOrCreateWorldConfig(world.getName())` and then `WorldConfig.isModuleEnabled(module, true)`:
- **Asking about a world writes its file.** `getOrCreateWorldConfig` builds a `WorldConfig` for a world it does not
  know yet, and the constructor saves `<world>.yml` if it is missing. Code that must not create files for unknown
  worlds avoids the call; the map layers do this for unloaded worlds.
- A key missing from both files counts as on, and is written into `defaultWorldConfig.yml` as `true`.
- Each lookup logs `isEnabled? <key>` at Architect's debug level 40.
- Switches change when someone edits the YAML and runs `/architect reload`. Only `/chunkupdate auto` writes one from the
  game, through `PluginData.setModuleEnabled`.

#### Misspelt keys

Two module keys are misspelt: `modules.specialBlocks.pistonExtenstions` and `modules.commmand.itemTexture` (three
m's). Both are stored on every server. Renamed, the new key would be missing everywhere, count as on, and be written as
`true`, which turns the module back on for every world that had it off. Leave them as they are.

The same goes for other odd names: the packages `serverResoucePack` and `WorldGeneration`, and the `Permission`
constants `CUNK_UPDATE` and `CUNK_UPDATE_AUTO`. Renaming them gains nothing and touches many files.

### The data folder

The data folder is `plugins/MCME-Architect/`; the plugin's name is the Maven `artifactId`.

| Path | Owner | Written |
|---|---|---|
| `config.yml` | `ConfigFile` (every write); read in many places | at first start; then only through `ConfigFile.update` |
| `WorldConfig/` | `WorldConfig`, `PluginData` | when a world's config is first asked for; by `/nophy`, `/chunkupdate auto`, `/itemblock limit -base` |
| `logs/architect_<start>_<n>.log` | `LogFileManager` | from every start |
| `SwitchStick/switchStickConfig.yml` | `PluginData` | by `/switchstick` |
| `inventories/block/<pack>/`, `inventories/item/<pack>/`, `inventories/saved/<pack>/` | `SpecialBlockInventoryData`, `SpecialItemInventoryData`, `SpecialSavedInventoryData` | block files are saved back at every load; by the build-inventory editor and `/inv create` |
| `itemSets.yml` | `GetData` | after each change to a kit, and 2000 ticks after a load |
| `itemBlockRegions/<name>.reg` | `ItemBlockManager`, `ItemBlockRegion` | by `/itemblock` |
| `regions/<name>.reg` | `RpManager`, `RpRegion` | by the RP region editor |
| `protocolVersions.yml` | `RpManager` | copied from the jar when missing |
| `rp-release-times.yml` | `RpReleaseTimes` | after a successful `/rp release` |
| `NoPhyExceptionAreas.txt` (`.tmp` while saving; `.bak` after a load that skipped lines) | `NoPhysicsData` | by `/nophy exception` |
| `customHeads/accepted/`, `customHeads/submitted/` | `CustomHeadManagerData` | by `/chead` |
| `armorStands/`, `banners/` | `ArmorStandEditorConfig`, `BannerEditorConfig` | by the editors' save commands |
| `clipboards/<uuid>.cbd` | `CopyPasteManager` | after `/copy` and `/cut`, off the main thread |
| `viewDistances.dat` | `ViewDistanceManager` | by `/viewdistance` |
| `randomiser.yml` | `RandomiserCommand` | by `/random allow` and `/random deny` |
| `entityLog.dat` | `EntityLogger` | while entity logging runs |
| `mapLayers/itemBlockBudget.yml` | `ItemBlockBudget` | each minute when changed, and at stop |
| `biomeTuning/backups/` | `BiomeDocument` | at each biome save |

Outside the data folder, Architect reads and writes `plugins/VoxelSniper/stencils/` and `stencilLists/` (`/vv`,
`/sl`), lists `plugins/WorldEdit/schematics/` (`/schlist`), and writes biome files, spare biomes and
`mcme-biometune.json` in the primary world's `datapacks/` folder (biome tuning). `RpManager` also declares a
`playerRpData.dat` file that nothing reads or writes.

## Cross-cutting services

### Logging

`Log` is the one channel for Architect's log lines: `Log.info`, `Log.warn` and `Log.error` (the last two with an
optional `Throwable`), and `Log.debug` at level `FINE`. It wraps the plugin logger.

`LogFileManager.install`, called in `onEnable`, attaches a rotating `java.util.logging.FileHandler` to that logger:
- Files are `logs/architect_<yyyy-MM-dd_HH-mm-ss>_<n>.log`, a new set from every start; `_0` is the current file.
  `logging.file.maxSizeMB` (10) and `maxFiles` (5) set the rotation; at start, files older than `retentionDays` (14)
  are deleted.
- Each line is `[time] [LEVEL] message`, plus the stack trace. The logger's level is `ALL`, so `Log.debug` reaches the
  file.
- With `logging.file.alsoServerLog: false`, the default, it calls `setUseParentHandlers(false)`: **Architect's lines do
  not reach the server console or `latest.log`**. `LogFileTest` checks the flag.
- With `logging.file.enabled: false`, or when the file cannot be opened, Architect logs to the server log like any
  plugin, and debug lines are dropped.
- `LogFileManager.location(plugin)` names the right place for a message that sends someone to the log. Use it instead
  of writing "see the server log". `LogFileManager.flush()` is there for tests.

Everything that logs through `plugin.getLogger()`, as biome tuning does, ends up in the same file.

There are **two debug channels**, and they are easy to confuse:

| | Architect's `com.mcmiddleearth.util.DevUtil` | PluginUtils' `DevUtil` (`ArchitectPlugin.getDevUtil()`) |
|---|---|---|
| Used by | most features, through the static `DevUtil.log(level, message)` | the RP join diagnostics in `RpListener`, parts of `RpManager`, `RpPluginMessageListener`, `AntiKickListener` |
| Controlled by | `/architect dev [true\|false\|<level>\|-r]` | PluginUtils' developer tools, through the `Debugable` interface that `ArchitectPlugin` implements |
| Output | Architect's log at `FINE` when the level passes; the chat of registered developers; the console when switched on | the chat of registered developers; the console when switched on |
| Default | level 1, no developers, console off | level 1 |

Architect's channel resets at every restart. Messages above level 1 are dropped by default: special-block load
problems, for example, log at level 2, and level 40 logs every module lookup.

### PluginUtils

PluginUtils (`com.mcmiddleearth:PluginUtils`) is the only hard dependency. Architect uses:
- `message.MessageUtil`, for every player message, through one shared instance, `PluginData.getMessageUtil()`: the
  `[Architect]` prefix, the info and error colours, the standard errors (`sendNoPermissionError`,
  `sendPlayerOnlyCommandError`, `sendNotEnoughArgumentsError`, `sendInvalidSubcommandError`), and paged lists
  (`sendFancyListMessage`, `sendFancyFileListMessage`);
- `message.FancyMessage`, for clickable chat lines with hover text, sent as `/tellraw` from the console;
- small helpers: `NumericUtil`, `FileUtil`, `EventUtil.isMainHandEvent`, `WEUtil.getSelection` (a WorldEdit
  selection), `LegacyMaterialUtil`, `ConfigurationUtil` (locations in YAML), and `confirmation` (chat confirmations);
- `plotStoring` (`MCMEPlotFormat`), for the copy-and-paste clipboard and its undo data;
- `developer.DevUtil` and `Debugable`, the second debug channel.

Newer code (biome tuning, `/rp release`) builds Adventure `Component`s itself, with the prefix and colours from
`MessageUtil`. PluginUtils' NMS helpers are still imported in places, but only commented-out code uses them.

### TheGaffer

TheGaffer is MCME's job plugin: it decides where a player may build. Architect does not compile against it and does
not declare it in `plugin.yml`. `com.mcmiddleearth.util.TheGafferUtil` finds a plugin named `TheGaffer` and calls
public static methods on its main class by reflection:

| Architect method | Calls on TheGaffer | Without TheGaffer, or when the reflective call fails |
|---|---|---|
| `hasGafferPermission(player, location)` | `hasBuildPermission(Player, Location)` | returns `true`; a failure is logged as an error |
| `checkGafferPermission(player, location)` | the above, then, for a refusal, `getBuildProtectionMessage(Player, Location)`, sent to the player as an error | returns `true` |
| `recordPlace(player, location)` | `recordExternalBuild(Player, Location, boolean)`, from TheGaffer 3.0.0, so that blocks Architect sets count in job stats | does nothing; a TheGaffer without the method is logged once |

`PluginData.checkBuildPermissions(player, location, permission)` combines an Architect permission with
`checkGafferPermission`. The checks **fail open**: without TheGaffer, or with a TheGaffer whose methods changed, the
build check passes. Keep the method names and signatures in step with TheGaffer when either side changes. `FakeGaffer`
stands in for it in tests.

### WorldEdit

The server needs WorldEdit 7.4, but `plugin.yml` lists it in neither `depend` nor `softdepend`.
- Commands that use a WorldEdit selection call `WorldEditGuard.require(sender)` first, then PluginUtils'
  `WEUtil.getSelection`: `/copy`, `/cut`, `/nophy exception`, `/rp create` and
  `/itemblock create`. The RP region editor's `setborder` checks `WorldEditGuard.isAvailable()` with its own message.
- RP regions and item-block regions are WorldEdit `Region` objects, and `loadData()` loads their files, so start-up
  touches WorldEdit classes. In tests, build regions on `NullWorld` (see [MockBukkit lessons](#mockbukkit-lessons)).
- The comment and the player message in `WorldEditGuard` still say no 26.x WorldEdit exists. That is out of date.

### ProtocolLib

- ProtocolLib serves two features: the client view distance (`ViewDistanceProtocol`, `ViewDistanceListener`) and the
  game-mode switcher (`GamemodeSwitcherProtocol`). Only these classes name ProtocolLib types. A class that names them
  cannot be linked on a server without ProtocolLib, so `onEnable` calls their `register` methods only after it has
  checked that a plugin named `ProtocolLib` is present. This is a convention, written in `ViewDistanceProtocol`'s
  Javadoc; no test enforces it, and `/viewdistance <n>` and `/viewdistance default` break it: they reach
  `ViewDistanceProtocol.sendViewDistance` through `ViewDistanceManager` without that check.
- **Build and server versions differ.** Architect compiles against the stable 5.3.0 API; servers on 26.x need a
  ProtocolLib dev build, 5.5.0 or later. ProtocolLib 5.3.0 has no type for the switcher's packet, so
  `GamemodeSwitcherProtocol` loads `net.minecraft.network.protocol.game.ServerboundChangeGameModePacket` by name from
  the server's class loader and asks ProtocolLib for its `PacketType`. If that fails, the switcher stays off and the
  log says why.
- Packet handlers run on network threads. `GamemodeSwitcher.onSwitchRequest` only decides whether to take a packet,
  and schedules its answer on the main thread. Follow that pattern in new packet code.
- In tests the ProtocolLib API is on the class path but no ProtocolLib plugin is loaded, so these classes are never
  registered. Tests call `GamemodeSwitcher.onSwitchRequest` directly.

### Paper internals

Biome tuning reaches past the Paper API only through `ReflectionNmsBridge`, the one real implementation of the
`NmsBridge` interface. The other places are packet-level, through ProtocolLib: `GamemodeSwitcherProtocol` loads
Minecraft's `ServerboundChangeGameModePacket` by name, and `ViewDistanceListener` writes packet fields by index.
- Paper 26.x runs Minecraft unobfuscated, so the bridge looks up plain Mojang names (`MinecraftServer`,
  `Biome.DIRECT_CODEC`, `RegistryOps`, `SynchronizeRegistriesTask`) and Paper's `PaperCommonConnection`, checked against
  Paper 26.2 (server classes of build 111, API of build 126).
- The constructor resolves every class, method and field once and records failures instead of throwing. If
  `selfCheck()` returns problems, biome tuning logs `Biome tuning is unavailable on <version>: ...` and stays off.
- `BiomeTuning.enable` builds the bridge inside a guard that catches `RuntimeException` and `LinkageError`: an internals
  change the self-check did not foresee switches tuning off, and must never reach `onEnable`, where Paper would disable
  all of Architect.
- The bridge sets private final fields of the live `Biome` by deep reflection. That works on Java 25; the code notes
  that JEP 500 makes Java warn about it from Java 26.

Elsewhere, Architect reflects on Bukkit interfaces (`blockData.attributes`), on TheGaffer and on that packet class.

### dynmap

Two parts of Architect draw on the dynmap web map: the map layers (`mapLayers`) and the entity logger
(`ELogDynmapUtil`).
- dynmap is a `softdepend`, and some servers run without it, where a class that names a dynmap type cannot be linked.
  So only `DynmapBackend` and `ELogDynmapUtil` name `org.dynmap` types, and only `MapLayers` names `DynmapBackend`,
  after `isPluginEnabled("dynmap")` has said yes.
- **`DynmapIsolationTest` enforces this** by scanning the compiled `.class` files. A new class that names a dynmap type
  fails it on purpose: put such code behind `MapBackend`.
- Architect enables before dynmap, so the map is looked up at the first redraw, not in `onEnable`. Ask
  `isPluginEnabled("dynmap")`, not `getPlugin("dynmap") != null`: a dynmap that failed to enable still implements
  `DynmapAPI`. The map layers' marker sets are non-persistent; Architect draws them again at every start.

### MCME-Connect and ViaVersion

- **MCME-Connect** (a `softdepend`) fires `PlayerConnectEvent` when a player joins the network through the proxy or
  moves between servers. Architect uses that one class, in `RpListener.onPlayerConnect`, to send a pack at network
  join. Bukkit inspects all of a listener's methods when it registers the listener, so on a server without
  MCME-Connect, expect **none** of `RpListener`'s handlers to be registered. No test covers that case: Connect is on
  the test class path. Keep Connect types out of every other class.
- **ViaVersion** (a `softdepend`) gives the client's real protocol version, for the choice of pack.
  `RpManager.getClientProtocolVersion` uses it only when the plugin is present, and falls back to Paper's
  `Player.getProtocolVersion()`.

### The RP settings database

Players' resource-pack settings (automatic switching, variant, client, resolution, last pack URL and status) are kept
in a MySQL table that every server shares.
- `RpDatabaseConnector` reads `rpSettingsDatabase`: `user`, `password`, `dbName`, `ip` and `port`. The bundled
  placeholder `xxx`, as the user or the database name, means "no database": players get defaults, and nothing persists.
- The connector is a `static final` field of `RpManager`. It is built once, when `RpManager` first loads during
  `onEnable`, and it **connects on the main thread** there. An unreachable database delays start-up by up to the
  10-second connect timeout. Reads have a 60-second socket timeout, and each statement a 10-second query timeout.
- A keep-alive task checks the connection off the main thread every 1200 ticks, and reconnects. Loads and saves run on
  async tasks, through `synchronized` connector methods (see [Player settings](#player-settings)).
- The table is `architect_rp`, created with `CREATE TABLE IF NOT EXISTS` and extended with `ALTER TABLE ... ADD COLUMN`.
  Its layout must stay the one that 2.10.9 made, because all servers share it; `RpTableSchemaTest` checks it.
- Statuses are stored by the name of their `RpPlayerStatus` constant and read back with `valueOf`, so renaming a
  constant breaks the rows that hold the old name.
- The pom declares no JDBC driver; the server must provide one. Tests use `FakeMysql`.

### Threading rules

- Bukkit objects, `PluginData`, `WorldConfig` and the live config belong to the main thread; none of them is
  thread-safe. Async work gets its settings read on the main thread before it starts.
- Database work, pack downloads and SHA-1 hashing, Mojang lookups, and the release and inventory scripts run on async
  tasks, and hand their results back with `Bukkit.getScheduler().runTask(...)`; chat is always sent from the main
  thread. Two older paths do not follow this, so do not copy them: `/chead upload` (`HttpTextInputHandler`) reads its
  download on the main thread, and `CopyPasteManager.loadClipboard` writes its plain `HashMap` from an async task.
- Biome tuning and map layers are main-thread only, by design, codec decoding and file writes included.
- Packet handlers run on network threads: hand their work to the main thread (see [ProtocolLib](#protocollib)).
  `ViewDistanceListener` is an exception that still reads `PluginData`, `WorldConfig` and `ViewDistanceManager` on the
  network thread; do not copy it.
- State that async code shares is concurrent (`RpManager`'s maps) or guarded (`synchronized` methods in the database
  connector and in `RpReleaseReport`, which a reader thread feeds).

## Subsystems

### Special blocks and item blocks

A **special block** is a block state, or a small group of them, that an MCME resource pack retextures, with a rule for
how it is placed. Builders get its item from the build inventory, from pick-block, or from a `/get` kit.

**Definitions.** Block files live in the data folder under `inventories/block/<pack>/*.yml`, one folder per server
resource pack, named exactly like the pack's key under `ServerResourcePacks`.
`SpecialBlockInventoryData.loadInventories()` reads `categories.yml` (the tabs) first, then every other file. Each
entry's `type` is a `SpecialBlockType` name (case-sensitive; missing means `VANILLA`), and the factory
`SpecialBlock.createSpecialBlock(type, section, fullName)` calls that type's static `loadFromConfig`. The results go
into one registry list and into the pack's `CustomInventory` and `SearchInventory`. Each block file is saved back after
loading, to keep legacy conversions, so the files are rewritten at every load. Skipped entries are logged only at debug
level 2.

**Ids and items.** An entry's id is `<pack>/<key>` (`SpecialBlockInventoryData.fullName`). Its item carries the lore
`MCME Block` and the id, which `getSpecialBlockId(item)` reads back. An item works across reloads while its id exists;
renaming a pack folder breaks all its items.

**Placing.** `SpecialBlockListener.placeSpecialBlock(PlayerInteractEvent)`:
1. acts only where `modules.specialBlocks.place` is on, for a main-hand right-click on a block with an `MCME Block`
   item, and ignores a second click in the same tick within one block of the first;
2. looks the `SpecialBlock` up by the item's id; `VANILLA` and `DOOR_VANILLA` entries are left to vanilla;
3. cancels the event, computes the target with `getBlock(...)`, checks `canPlace` and TheGaffer, and calls
   `placeBlock(...)`, which takes the state from `getBlockState(...)`, sets it without physics and sends it to the
   player (for types whose `isEditOnSneaking()` is true, sneaking edits the clicked block instead);
4. unless the click was a sneak edit, reports every block that `placeBlock` returns to TheGaffer, through
   `TheGafferUtil.recordPlace`.

**Breaking.** `breakSpecialBlock(BlockBreakEvent)` finds the broken block's entry among those of the player's current
pack (`getItem(block, rp)`; the highest `priority` wins), checks TheGaffer, and calls `handleBlockBreak` six ticks
later for clean-up that depends on the type.

**One block set per pack.** The same vanilla state can be different special blocks in different packs, so matching
(pick-block, breaking, the flint, sneak edits) looks at the player's current pack, from
`RpManager.getCurrentRpName`; for breaking and the flint, a held item whose name starts with a pack name wins. Placing uses the id in the item, so an item places its own block anywhere.

**The type classes.** `specialBlocks/` holds a class per type. Orientable types extend `SpecialBlockOrientable`,
variant types `SpecialBlockOrientableVariants`, branches share the `IBranch` interface (except `BRANCH` itself, `SpecialBlockBranch2`), doors extend `SpecialBlockDoor`
and item blocks `SpecialBlockItemBlock`. To add a type, add a constant to `SpecialBlockType`, write a class with a
static `loadFromConfig(ConfigurationSection, String)` that overrides `getBlockState` (and `getBlock`, `placeBlock` or
`matches` where it must), and add a case to `SpecialBlock.createSpecialBlock`. The build-inventory editor offers a
fixed list of types (`BlockTypePrompt`).

**Item blocks.** An item block is a base block (often a barrier) plus an invisible armor stand that wears a model item
on its head, which the resource pack draws as the model. The stand is named after its block and its id, so breaking the
base block finds and removes it. `ItemBlockManager.allowPlace` enforces a limit per chunk: the armor stands, item
frames and paintings in the chunk (`ItemBlockCount`) against the highest limit of the `ItemBlockRegion`s that contain
the block, else the world's `itemBlock.baseLimit` (5 by default); `architect.itemBlock.region.ignore` skips the check.
Item-block regions are WorldEdit regions in `itemBlockRegions/<name>.reg`, loaded by delayed tasks.

**Related listeners** in `specialBlockHandling.listener`: `BlockPickerListener` (pick-block and the flint),
`BlockCycleListener` (the stick, through `blockData.BlockDataManager`), `DoorListener`, `FurnaceListener`,
`LightBlockListener`, and `InventoryListener` (the F key and the `inventoryAccess` rules). `SpecialBlockListener` also
holds the older placements by item name (half doors, half beds, unlit torches, placeable plants, piston extensions,
burning furnaces) and the double-slab replacement.

### Build inventories

The build inventory is a six-row chest window per pack, titled `MCME Blocks - <pack>`, from which builders take special
blocks.

- **Windows.** `CustomInventory` is one window definition (tabs and items), and also its own `Listener`: registered in
  its constructor, unregistered by `destroy()`. `CustomInventoryCategory` is one tab. The state of an open window (the
  tab row, paging, the collection grid) is a `CustomInventoryCategoryState` or a `CustomInventoryCollectionState`, both
  subclasses of `CustomInventoryState`. `SearchInventory` and `SearchInventoryState` serve the search windows.
- **Data classes.** `SpecialBlockInventoryData` (the block windows, and the registry of special blocks),
  `SpecialSavedInventoryData` (saved inventories from `/inv create`), `SpecialHeadInventoryData` (the heads window,
  from the custom-head collection) and `SpecialItemInventoryData` (item windows, loaded but reachable from no command).
  Every reload destroys the windows and builds them again.
- **Opening.** `/inv` (`InvCommand`) and the F key open the window of the player's current pack; `InventoryListener`
  cancels the hand swap wherever `modules.command.getSpecialBlocks` is on.
- **Lore markers.** Tab icons carry the lore `MCME Inventory Category`, blocks `MCME Block`, item-window items
  `MCME Item`.
- **The editor.** Shift+right-click in a block window, with `architect.inventory.edit`, starts a chat conversation
  (Bukkit's Conversations API) built in `CustomInventoryEditor.init`, with prompts in `editor/prompt/add` and
  `editor/prompt/edit`. When it ends, `CustomInventoryEditor.EditExecutor` writes the entry into
  `inventories/block/<pack>/<tab name in lower case>.yml`, which must exist already, and reloads the block windows.
- **GitHub sync.** `/inv download` and `/inv upload` run the scripts that `inventoryDownload` and `inventoryUpload` name
  in `config.yml`, through `InventoryUtil`: off the main thread, with a five-minute limit, output to Architect's log.
- **Tests.** Nothing tests the windows or the editor directly. Block files appear as setup in `SpecialBlockGafferTest`,
  `SpecialBlockBreakTest` and `InvisibleBlockListenerTest`, which are good examples of the YAML.

### Resource packs

#### Pack selection

- Packs are configured under `ServerResourcePacks` in `config.yml` as
  `<Pack>.<client>.<resolution>.<variant>.'<versionKey>'`, each slot with a `url` and a `sha`. The client is `vanilla`,
  `sodium` or `lite`; the resolution is like `16px`; the variant is like `light` or `footprints`; the version keys come
  from `protocolVersions.yml` and must be quoted.
- `RpManager.getRpUrl(pack, player)` and `getSHA(pack, player)` walk that tree in `getConfigSection`: the player's
  client, resolution and variant where the pack has them, else the first one listed. Then `searchInVariantSection`
  picks the slot with the highest protocol not above the player's (from Paper, or ViaVersion when installed), or the
  lowest slot for an older client. Keys that `protocolVersions.yml` does not know are skipped.
- `RpManager.setRp(pack, player, force)` sends the pack with `player.setResourcePack(url, hash)`, where the hash is the
  slot's SHA-1, or `null` when the `sha` is missing or invalid. It records the URL, sets the status to `SENT` and saves
  the player's data. Without `force`, an unchanged URL is not sent again.
- `/rp server` and `/rp calcsha` download pack files and hash them off the main thread, then write each slot's `url`
  and `sha` together through `ConfigFile.update` (`/rp calcsha` only where the slot still has the URL it hashed).
  `/rp server remove` removes a version's slots, also through `ConfigFile.update`.
- `RpManager.getCurrentRpName(player)` maps the URL last sent back to a pack name (`getRpForUrl`). Special blocks, the
  build inventory and the block picker use it as "the player's current pack".
- Other plugins use `RpManager` as an API: MCME-Introduction reads a player's `RpPlayerStatus` (the `SENT` and
  last-status pair exists for it, as the enum's Javadoc says). Keep those methods stable.

#### Player settings

- `RpPlayerData` holds automatic switching, variant, client, resolution, the current URL, the current and last status,
  the current region and the protocol.
- Settings are loaded at `AsyncPlayerPreLoginEvent` (`RpListener.onPlayerPreLogin`, then `RpManager.loadPlayerData`).
  They are saved, off the main thread, at every pack send, at every status reply (`PlayerResourcePackStatusEvent`) and
  at every settings change.
- While a load is pending (`pendingLoads`, one token per load), `getPlayerData` hands out throwaway defaults, and
  `setRp` and `setRpRegion` do nothing. Defaults are never sent or saved over the player's row.
- At network join (`PlayerConnectEvent` with the reason `JOIN_PROXY`), `RpListener` runs a check every 10 ticks, up to
  30 times, until the player's load has ended. Then it resets the status to `NOT_SENT`, sets the protocol and the client
  type (a marker from the MCME modpack means `sodium`), and sends the region's pack, or the pack of the URL stored from
  the last visit.
- At quit, the data and the Sodium mark are dropped, except during a biome refresh (`BiomeTuning.isRefreshing`), which
  looks like a quit and a rejoin.

#### Regions

- An `RpRegion` has a name, a pack, a weight and a WorldEdit `Region` (cuboid, cylinder, ellipsoid or polygonal). It
  is saved as `regions/<name>.reg`.
- `RpManager.init` clears the regions and loads each file with a task that starts 200 ticks later and tries again each
  second, up to ten times, until the region's world exists.
- `RPSwitchTask` calls `RpManager.setRpRegion(player)` for every online player each second. For a player with automatic
  switching on, it finds the region with the highest weight at the player's location; when that differs from the last
  one, it sends the region's pack.
- `/rp create` and `/rp edit` start a chat conversation (`RegionEditConversationFactory`, `RegionEditPrompt`). Every
  change saves the file and marks the RP-region map layer as changed (`RpManager.updateDynmapRegions`).

#### Releases

- `/rp release <pack> <tag> <title>` runs the pack's release script, `sh <script> <pack> <owner> <repo> <tag> <title>`,
  in the folder `gitHubRpReleases.<pack>.path`, on an async task. It has a timeout (`timeoutMinutes`, 30 by default)
  and a 10-second grace before the process and its children are killed. Releases do not run on Windows.
- `RpReleaseUtil.releaseResourcePack` reads the settings on the main thread before the script starts.
- Every output line goes to Architect's log as `[RP release <pack>] <line>`, and to `RpReleaseReport`, which recognises
  step lines, warnings, errors, the launcher's lines, the GitHub release URL and the pipeline wrapper's verdict.
- `RpReleaseReport.verdict(...)` turns the exit code and the report into the closing message (an `RpReleaseVerdict`), `RpReleaseChat` builds the chat
  lines, `RpReleaseFeedback` delivers them and holds the closing message for a releaser who has left, and
  `RpReleaseTimes` keeps the last duration in `rp-release-times.yml`.
- The lines `RpReleaseReport` reads are a **contract with the ResourcePackScripts repository** ("What MCME-Architect
  reads" in its `docs/release-pipeline.md`). Change both together, and capture new fixtures for
  `src/test/resources/rp-release/` when the output changes. Tests use the seams `RpReleaseUtil.starter`, `onWindows`
  and `releaseSite`, with `FakeScript`.

### Biome tuning

Live biome tuning lets builders change a datapack biome's colours, fog, climate, sounds and particles while the server
runs, through `/biometune` and a Paper Dialog editor, and save the result to the biome's JSON file.

`BiomeTuning` is the static wiring: the guarded start, the self-check, the 1-second timer, `disable()` and
`isRefreshing(UUID)`. `BiomeTuneCommand` is the command, `BiomeTuningSettings` reads the `biomeTuning` section,
`BiomeFiles` finds the one folder pack that defines a biome, and `ChatFeedback` builds the chat lines. The editor's
windows (`BiomeEditor`, `EditorView`, `EditorLayout`, `EditorForms`, `ColourPicker`, `ColourMath`) are plain models,
shown through the `DialogPresenter` interface; `PaperDialogPresenter` turns them into Paper Dialogs. The other classes
appear below.

#### Catalogue and codec validation

Each `TuningProperty` has a name for builders, its path in the biome JSON, a kind (`RGB`, `ARGB`, `NUMBER`, `BOOLEAN`,
`CHOICE` or `JSON`) and a copy group (sky, fog, water, climate, audio, particles). `TuningProperties` holds the
catalogue and turns what a builder types into JSON.

Architect does not validate a biome itself. Every candidate JSON is decoded with vanilla's own `Biome.DIRECT_CODEC`,
through the bridge, with registry-aware ops, so that sound and particle references resolve. The codec's error message
goes back to the builder.

#### Live edits through the bridge

`BiomeTuningService` carries out set, unset, copy, edits of several values, undo and revert:
1. `document(biome)` loads the biome's JSON file and its hash. Vanilla `minecraft:` biomes are refused.
2. A candidate JSON changes only catalogue paths; everything else passes through unchanged.
3. The spare lock (`SparePool.lockedBecause`) is checked.
4. `bridge.decode(candidate)`, then `bridge.apply(biome, decoded)`, then `document.accept(candidate)`, which pushes the
   old JSON on the undo stack.

A failure before `accept` leaves the biome as it was. `apply` swaps the climate settings, attributes, special effects
and temperature cache of the running `Biome` object. Generation settings and mob spawns are not touched, so changes to
world generation still need a restart. Working copies and undo history live only in memory: at a restart, the game
reads the files again.

#### Refresh through the configuration phase

Clients see biome data only when they receive the registries. `RefreshCoordinator.refresh(player, inject)` sends a
player back through the configuration phase:
1. It calls `player.getConnection().reenterConfiguration()`. Paper fires `PlayerQuitEvent` inside that call, and the
   coordinator removes the quit message. If no quit fires, Paper refused: it does for players who logged in while a
   plugin listened to the legacy `PlayerLoginEvent`.
2. On `PlayerConnectionReconfigureEvent`, the bridge queues vanilla's full registry sync (`injectRegistrySync`), and
   the coordinator calls `completeReconfiguration()`.
3. On `PlayerJoinEvent`, the refresh ends and the join message is removed. The editor shows its window again once the
   client has loaded the world.
4. The 1-second timer ends refreshes older than `refresh.timeoutSeconds` with a "please rejoin" disconnect, works
   through the publish queue (`refresh.publishPlayersPerSecond` players a second), and closes expired editor windows.

A refresh replaces the `Player` object. The editor and `RpListener` therefore track players by UUID, and other
listeners can ask `BiomeTuning.isRefreshing(uuid)` to skip their own quit and join work.

#### Save and backups

`BiomeTuningService.save` validates the JSON with the codec once more and calls `BiomeDocument.save`. It refuses when
the file's SHA-256 has changed on disk since it was read, copies the old file to
`biomeTuning/backups/<namespace>/<path>/<timestamp>.json` in the data folder (keeping the newest `backupsPerBiome`), and
writes the new JSON, pretty-printed, to `<file>.tmp`, forced to disk and moved over the file. It writes the biome's own
file, in whichever folder pack defines it, which is not necessarily `targetPack`, and logs the values that changed.

#### Spares and labels

- A spare is a datapack biome whose id matches `biomeTuning.sparePattern` (by default `mcme:custom_###`). A free spare
  is locked against edits until a builder claims it, with `/biometune claim` or the editor's New biome window. A claim
  can copy another biome's look, as a live edit that still has to be saved.
- `spares add` writes new, neutral biome files into `<datapacks>/<targetPack>/`. They exist only after the next
  restart.
- Labels and claims are kept in `mcme-biometune.json`, at the root of the target pack. The file is read strictly, read
  again when it changes, and written through a `.tmp` file. A file that cannot be read is never overwritten.

In tests, package-private overloads of `BiomeTuning.enable` (`plugin, bridge, datapacksDir[, knownBiomes]`) inject
`FakeNmsBridge`, `BiomeEditor.presentWith` installs a `RecordingPresenter`, and the clocks are injected.

### Map layers

Architect draws four layers of its own data on the dynmap web map: RP regions (`RpRegionLayer`), item-block limit
regions (`ItemBlockRegionLayer`), no-physics exception areas (`NoPhysicsLayer`), and an item-block budget heat map
(`ItemBlockBudgetLayer`, fed by `ItemBlockBudget`). Everything runs on the main thread.
- `MapLayer` is the interface of a layer: a marker-set id, a label, hidden or not by default, `shapes()`, and optional
  `start`, `stop` and `refresh`. `MapShape` is plain data (areas, circles, styles); `RegionShapes` turns WorldEdit
  regions into shapes.
- `ArchitectLayers.fromConfig(config, dataFolder)` builds the layers that the `mapLayers` section switches on, read
  through `MapLayerConfig`. The two older layers use the legacy `dynmap` section while `mapLayers` has no entry for
  them.
- `MapLayers` keeps one static active instance; `loadData()` stops it first and starts it last. A feature whose data
  changed calls `MapLayers.changed(markerSetId)`; the changes of one tick are drawn together on the next, and only the
  changed layers are rebuilt.
- `MapBackend` is where the layers are drawn: `DynmapBackend`, or `MapBackend.NONE` when dynmap is missing, disabled or
  incompatible. It is looked up at the first redraw, and again after dynmap is enabled again or after
  `/architect maplayers refresh`; when a world loads, every layer is redrawn on the same map.
  `DynmapBackend` updates markers in place: it creates, rewrites only changed shapes, and deletes the ones gone.
- A layer whose data fails to build keeps its old markers. A map that fails switches every layer off until
  `/architect maplayers refresh`, or until dynmap or Architect starts again.
- `ItemBlockBudget` counts armor stands, item frames and paintings per chunk as chunks' entities load and unload,
  recounts marked chunks each second, and saves high counts to `mapLayers/itemBlockBudget.yml`, even without dynmap.

To add a layer, implement `MapLayer`, build it in `ArchitectLayers` with its settings read through `MapLayerConfig`,
and call `MapLayers.changed(id)` wherever its data changes. Do not name dynmap types (see [dynmap](#dynmap)).

The entity logger keeps its own, older dynmap layer, through `ELogDynmapUtil`. `/architect maplayers refresh` does not
touch it.

### Copy and paste

`/copy`, `/cut`, `/paste`, `/rot`, `/flip`, `/undo` and `/redo` are Architect's own clipboard, separate from
WorldEdit's. `CopyPasteManager` holds static maps per player: the clipboards, and the undo and redo lists
(`CopyPasteUndoLimit` steps, 30 by default). `Clipboard` implements PluginUtils' `IStoragePlot`: a copy serialises the
cuboid WorldEdit selection (from `WEUtil.getSelection`, after `WorldEditGuard.require`) with PluginUtils'
`MCMEPlotFormat` into a gzip byte array of block states, block-entity data, paintings, item frames, armor stands and one
biome per column, with the player's position as the reference point. `/rot` and `/flip` change only the clipboard's
transform; a paste loads the data at the player's position plus the stored offset, without physics. Before each paste
or cut, an `UndoData` snapshot of the target box is stored, and an undo pastes it back; undo and redo live in memory
only. After each copy or cut the clipboard is written to `clipboards/<uuid>.cbd` off the main thread, and it is read
again when the player joins (`ClipboardPlayerListener`).

### Smaller tools

| Tool | Package and main classes | How it works |
|---|---|---|
| Armor stands (`/armor`) | `armorStand`: `ArmorStandEditorCommand`, `ArmorStandListener`, `ArmorStandEditorConfig`, `ArmorStandUtil` | The command sets an edit mode per player, and a click with a stick on a stand applies it. Stands are saved to `armorStands/`. The listener also enforces `modules.protection.armorStand`. The `guard` subpackage is an unfinished rollback feature |
| Custom heads (`/chead`) | `customHeadManager`: `HeadCommand`, `CustomHeadManagerData`, `CustomHeadCollection`, `CustomHeadGallery` | Heads are YAML files (`owner`, `headId`, `texture`) under `customHeads/accepted/`, where folders are categories, and `customHeads/submitted/`. `HeadDataBuilderPlayer` asks Mojang's API off the main thread; `HeadDataBuilderText` downloads an upload file. `CustomHeadGallery` builds the gallery as blocks in the world, at `headGalleryLocation`. `com.mcmiddleearth.util.HeadUtil` turns heads into items |
| No-physics (`/nophy`) | `noPhysicsEditor`: `NoPhysicsListener`, `NoPhysicsData`, `NoPhysicsCommand`, `ExceptionArea`, `RedstoneCircuitArea`, `WaterFlowArea` | The listener cancels `BlockPhysicsEvent` and Paper's `BlockDestroyEvent` for blocks that match the world's `noPhysicsList` (or that do not match it, with `noPhysicsListInverted`), outside exception areas. It connects fences, walls, panes, stairs, chests, chorus plants and redstone wire itself, and keeps every `BlockRedstoneEvent` at its old current, in every world. `NoPhysicsData` caches the parsed lists per world and saves exception areas to `NoPhyExceptionAreas.txt` through a `.tmp` file |
| Randomiser (`/random`) | `randomiser`: `RandomiserCommand`, `RandomiserConfig` | Gives matching blocks around the player random values of one numeric block-state attribute, through `blockData.attributes.IntAttribute`. The allowed `material:Attribute` pairs are kept in `randomiser.yml` |
| Stencils (`/vv`, `/sl`) and schematics (`/schlist`) | `voxelStencilEditor`: `VvCommand`, `SlCommand`, `StencilList`, `VoxelConstants`; `weSchematicsViewer`: `SchListCommand` | File browsers over `plugins/VoxelSniper/stencils/`, `plugins/VoxelSniper/stencilLists/` and `plugins/WorldEdit/schematics/`, through PluginUtils' file lists. `StencilList` reads and writes stencil-list files; their names go through `PathSafety` |
| View distance (`/viewdistance`) | `viewDistance`: `ViewDistanceManager`, `ViewDistanceProtocol`, `ViewDistanceListener` | A value per player in `viewDistances.dat`, else the world's `viewDistance`. The ProtocolLib listener writes it into the login and view-distance packets and cancels chunk-unload packets, so clients keep the chunks they received. Its Javadoc asks for the packet field indices it writes (login field 2, view-distance field 0) to be checked on a live 26.2 server. |
| Game-mode switcher | `gamemodeSwitcher`: `GamemodeSwitcher`, `GamemodeSwitcherProtocol`, `InvisibleBlockListener` | The client opens F3+F4 only at op level 2. One tick after a join, a respawn, a world change or a command-tree send, `GamemodeSwitcher` sends a qualifying builder level 2 with Paper's `Player#sendOpLevel`. It takes the switcher's packet only for builders that vanilla would refuse, and answers on the main thread. `InvisibleBlockListener` stops players without `architect.invisibleBlocks` from placing barrier, light and structure void blocks |
| Stick and flint | `blockData`: `BlockDataManager`; `specialBlockHandling.listener`: `BlockCycleListener`, `BlockPickerListener` | `BlockDataManager` holds an ordered list of `Attribute`s that read and set Bukkit `BlockData` properties by reflection on the interfaces' getters and setters. The stick steps through them. The flint shows a block's data and gives pick-block items |

## Adding a feature

### Checklist

1. **Package.** Put the feature in a package of its own under `com.mcmiddleearth.architect`. Write it in the 3.x style:
   final classes, collaborators passed in, and interfaces where a test needs a seam.

2. **`plugin.yml`.** Declare the command, without `permission:` (Architect checks permissions in the executor), and the
   permission, with `description:`. Many older entries say `descriptions:`, which Bukkit ignores; do not copy them.

   ```yaml
   commands:
      myfeature:
         description: Does my feature
         usage: /myfeature help
         aliases: [mf]
   permissions:
       architect.myFeature:
           description: Allows to use /myfeature
           default: op
   ```

3. **The permission node.** Add it to the `Permission` enum. Make it a child of a rank bundle
   (`architect.ranks.adventurer`, `artist`, `designer` or `headDesigner`; each includes the one below) if everyone of
   that rank should have it. `PluginYmlPermissionsTest` fails when a child is not declared. A node that is checked but
   not declared falls back to ops only.

4. **The executor.** Extend `AbstractArchitectCommand`:

   ```java
   public class MyFeatureCommand extends AbstractArchitectCommand {

       @Override
       public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
           if (!(sender instanceof Player player)) {
               PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
               return true;
           }
           if (!PluginData.hasPermission(player, Permission.MY_FEATURE)) {
               PluginData.getMessageUtil().sendNoPermissionError(player);
               return true;
           }
           if (!PluginData.isModuleEnabled(player.getWorld(), Modules.MY_FEATURE)) {
               PluginData.getMessageUtil().sendErrorMessage(player, "My feature is not enabled for this world.");
               return true;
           }
           // the work
           return true;
       }

       @Override public String getHelpPermission()   { return Permission.MY_FEATURE.getPermissionNode(); }
       @Override public String getShortDescription() { return ": Does my feature"; }
       @Override public String getUsageDescription() { return ": What /myfeature does, shown on hover."; }
       @Override public String getHelpCommand()      { return "/myfeature help"; }
   }
   ```

   `/architect help` **runs** `getHelpCommand()` on a click, so return a command that is safe to run as it is, or `null`
   (see [Commands](#commands)). Override `onTabComplete` if the command should complete its arguments.

5. **Registration.** In `ArchitectPlugin.onEnable`, add `setCommandExecutor("myfeature", new MyFeatureCommand());` and
   a `registerEvents` call for each listener. Both run only at start, so a server restart is needed to see them.
6. **A module switch.** Add a constant with its key to `Modules`, in the group that fits (`specialBlocks`,
   `environment`, `command`, `protection`), for example `MY_FEATURE ("modules.command.myFeature")`, and check it as in
   step 4. The key is written to `defaultWorldConfig.yml` as `true` the first time it is asked for, so the feature
   starts **on** in every world. Spell the key with care: once servers have it, it is permanent.
7. **Config keys.** Read with an explicit default wherever the bundled `config.yml` must not decide. Add a key to the
   bundled file only if its value is a safe default for every server; existing servers never get it written into their
   file. Write only through `ConfigFile.update`, and read settings for async work on the main thread first. If
   `/architect reload` should apply a key, read it in `loadData()` or something it calls, never in a static
   initialiser.
8. **Data files.** Keep them in the data folder, with paths built from
   `ArchitectPlugin.getPluginInstance().getDataFolder()`. Turn any name a player types into a file through
   `PathSafety.resolveInside(baseDir, name, extension)`. Write through a `.tmp` file and a move, as `NoPhysicsData` and
   `BiomeFiles` do, and never save over a file that failed to load (`LoadGuardTest` shows the cases already guarded).
9. **A new resource file** in `src/main/resources` needs an `<include>` in the pom's `<resources>` block, or it will not
   be in the jar. It will be filtered too.
10. **Soft dependencies.** Put code that names another plugin's types in a class of its own, and call it only after
    `getPlugin` or `isPluginEnabled` says the plugin is there. dynmap code goes behind `MapBackend`.
11. **Logging, shutdown and reload.** Log with `Log.info`, `warn`, `error` and `debug`, or `DevUtil.log(level, ...)`
    for developer detail, and point players at the log with `LogFileManager.location(plugin)`. Stop timers and save
    state in `onDisable` (and, for data that reloads, at the start of `loadData()`), where an earlier failure cannot
    skip the step.
12. **Tests.** One mock and one load per class, and a class of its own for a test that disables Architect or needs a
    first start. Use the fakes for TheGaffer, dynmap, MySQL, scripts and Minecraft's internals, and keep MockBukkit's
    limits in mind (see [MockBukkit lessons](#mockbukkit-lessons)). Run the whole suite before a pull request:
    `mvn -B -ntp clean verify`. Then add the command, permission, module key and config keys to the User Manual.

### Code conventions

- **Permissions** are `architect.<feature>[.<sub>]` in camelCase, held in `Permission` and checked with
  `PluginData.hasPermission`.
- **Messages** go through `PluginData.getMessageUtil()`, so they carry the prefix and the colours.
- **Javadoc and comments** are plain English and say why. Test names are sentences about behaviour, such as
  `aRegionKeepsItsOwnCopyOfTheSelectionItWasMadeFrom`.
- **Licence headers.** About half the source files carry the GPLv3-or-later header (see `PluginData`); give new files
  that header.
- **Commit messages** follow Conventional Commits: `feat(maplayers): ...`, `fix(rp): ...`, `test(rp): ...`,
  `build: ...`, `chore(release): 3.2.0`.

## Releasing

### Branches

| Branch | What it holds |
|---|---|
| `master` | The releases. Its history is a line of merged pull requests, and every release tag is on one of those merges |
| `development` | The development branch: the next version is built here |
| work branches | Branch from `development`; their pull requests go into `development` |

Both `master` and `development` are protected: changes arrive only by pull request, and a pull request needs one
approving review. Pull requests between the two branches are merged with a merge commit. (Up to 3.2.0, releases came
to `master` straight from feature branches; pull request #110 then brought `development` level with `master`.)

### Making a release

1. Merge everything that belongs in the release into `development`, and check that CI is green there.
2. Bump the version: set `<version>` in `pom.xml` (for example to `3.2.1`) in a commit named
   `chore(release): 3.2.1`, through a pull request into `development`. `plugin.yml` takes the version from the pom.
   In the same commit, update the version named in `README.md` and in the first lines of the two manuals.
3. Run the full suite locally: `mvn -B -ntp clean verify`.
4. Open a pull request from `development` into `master`, get the approving review, and merge it with a merge commit.
5. Tag the merge commit on `master` with an annotated tag named `v` plus the pom version, and push the tag:

   ```bash
   git switch master
   git pull
   git tag -a v3.2.1 -m "MCME-Architect 3.2.1 — <what the release brings, in one line>"
   git push origin v3.2.1
   ```

6. CI builds and tests the tag, checks the tag against the pom version, and publishes
   `com.mcmiddleearth:MCME-Architect:3.2.1` to repo.mcmiddleearth.com as a RELEASE build. Check that the run is green.
7. Make a GitHub Release on the tag. Attach the jar that CI published (the repository stores it under its Maven
   coordinates, as `MCME-Architect-3.2.1.jar`) and a `.sha256` file for it:

   ```bash
   sha256sum MCME-Architect-3.2.1.jar > MCME-Architect-3.2.1.jar.sha256
   ```

   The workflow does not make GitHub Releases itself.

Never publish with a manual workflow run on another branch (see [CI](#ci)).

### Versioning

- The version is `MAJOR.MINOR.PATCH`, in `pom.xml`. 3.x is the Paper 26.x line. A minor release adds features (3.2.0:
  the `/rp release` feedback); a patch release fixes things (3.1.1: resource-pack slots).
- `plugin.yml` gets its version from the pom, so `/architect version` shows the pom version and nothing else. The build
  type and the commit are only in the jar's file name: a `...-DEV-<commit>.jar` is a development build, whatever its
  version says.
- `development` keeps the last released version until the next bump, and the jar names tell its builds apart. A
  version ending in `-SNAPSHOT` would be published to `/snapshots`; the 3.x releases use plain versions.

### Comparing jars

A jar's bytes depend on where it was built:
- The repository has no `.gitattributes`, so a checkout's line endings depend on its git settings, and they reach the
  four filtered resources. A build on Windows can carry CRLF where the CI build on Linux has LF.
- Jar entries carry the times of the build, and the Maven plugins that pack the jar are not pinned.

Two good builds of one commit therefore have different checksums. To check that a jar holds a release's code, compare
it with the published jar entry by entry, not by checksum:

```bash
mkdir a b
(cd a && unzip -q ../MCME-Architect-3.2.0.jar)
(cd b && unzip -q ../MCME-Architect-3.2.0-RELEASE-3c6ab9c.jar)
diff -r --strip-trailing-cr a b
```

Differences in `META-INF/` (the manifest and Maven's metadata) are expected. The class files and the four resources
should match.

## Pitfalls

- **Architect's log is not in the console.** Look in `plugins/MCME-Architect/logs/architect_*.log`, or set
  `logging.file.alsoServerLog: true` and restart.
- **`/architect reload` is partial.** It runs `loadData()` only. Logging, biome tuning, the RP database settings,
  values read in static initialisers, and every command, plugin channel, and listener or timer that `onEnable` starts need a restart (see
  [What /architect reload does](#what-architect-reload-does)).
- **Only four resources reach the jar, and they are filtered.** A new resource needs an `<include>`, and `${...}` in any
  of the four is replaced at build time. Tests need the filtered `plugin.yml`: a run that skips Maven's
  `process-resources` fails on `name: ${project.artifactId}`.
- **One plugin load per JVM.** Never load Architect twice in one test class. A test that disables Architect gets a
  class of its own.
- **Module lookups have side effects.** `PluginData.isModuleEnabled` and `getOrCreateWorldConfig` write
  `WorldConfig/<world>.yml` for any name they are given, and a missing module key is written as `true`. A new module is
  on in every world.
- **Misspelt keys are permanent.** `pistonExtenstions` and `commmand.itemTexture` stay as they are.
- **`config.yml` is shared.** Never call `saveConfig()`; use `ConfigFile.update`. Read settings on the main thread
  before async work.
- **The bundled `config.yml` reaches every server.** It is the defaults of the live config, while existing files never
  get its new keys.
- **Nothing is shaded.** A library you add must be on every server, or be shaded on purpose. The pom has no JDBC
  driver: the server provides MySQL's.
- **Some dependencies are undeclared.** WorldEdit and TheGaffer are used but are not in `plugin.yml`. Guard WorldEdit
  features with `WorldEditGuard`; TheGaffer checks pass when TheGaffer is missing.
- **Soft dependencies stay in their own classes.** dynmap types only in `DynmapBackend` and `ELogDynmapUtil` (checked
  by `DynmapIsolationTest`), ProtocolLib types only in the `*Protocol` classes and `ViewDistanceListener`, MCME-Connect
  types only in `RpListener`.
- **ProtocolLib versions differ.** Architect compiles against 5.3.0; servers need a 5.5.0+ dev build, and packets newer
  than 5.3.0 must be looked up by name.
- **The RP database connects on the main thread at start.** An unreachable database adds up to ten seconds to start-up.
- **A linked worktree can stamp the wrong commit.** Pass `-Dgit.commit.id.abbrev=$(git rev-parse --short=7 HEAD)`.
- **There are two debug channels.** `/architect dev` does not control the PluginUtils `DevUtil` that the RP join
  diagnostics use, and special-block load errors show only from level 2.
- **MockBukkit hides some failures.** A call MockBukkit does not implement skips a test instead of failing it. Once a
  test has saved a region file, ticking 200 ticks past a load runs its loader, which needs a real WorldEdit.
- **The bundled scripts and `upload.txt` are not what runs.** Release and inventory scripts run from the folders that
  `config.yml` names (the release scripts today come from the ResourcePackScripts repository), and `/chead upload`
  downloads `upload.txt` over HTTP from GitHub by default.
- **Some comments are stale.** `build.yml` says paper-api 26.1.2; the pom calls MCME-Connect "3.0.0" above version
  3.0.1; `WorldEditGuard` and `RegionShapes` still speak of a WorldEdit that is not yet available or not yet compiled
  against.
- **Some dead code looks alive.**
  - the `watcher` package: six listeners extend `WatchedListener`, but nothing fires a `WatcherEvent`;
  - `com.mcmiddleearth.util.ProtocolLibUtil`: its body is commented out;
  - the `_invalid_*` classes;
  - the `armorStand.guard` rollback: stubs, and `ArmorStandRollbackListener` is never registered;
  - `TestListener`, `AfkListener`, `NewAfkCommand` and `ItemTexCommand`: commented out of `onEnable`;
  - `StickBlockBreakListener` and `VoxelBiomeBrushListener`: registered, but they do nothing;
  - `Clipboard1`, `SpecialBlockBranch` (the `BRANCH` type uses `SpecialBlockBranch2`), `ItemBlockData`, the
    `downloadConfig` methods and `ZipUtil`.
- **Jar checksums differ between machines.** Compare jars entry by entry (see [Comparing jars](#comparing-jars)).
