# MCME-Architect

The builder's toolkit of [MCME](https://www.mcmiddleearth.com) (Minecraft Middle-earth). Architect changes how blocks
behave on the build server and gives builders the tools to build Middle-earth:
- resource-pack blocks with their own placing rules;
- build inventories;
- control over physics;
- editors for armor stands, banners and signs;
- its own copy and paste;
- resource-pack switching;
- live biome tuning.

## Status

**3.2.1** (October 2026) is the current release, for Paper 26.2, and it runs on MCME's servers. `master` holds the
releases; the next version is built on `development`.

## Features

- **Blocks and inventories:**
  - **Special blocks** are block states that a resource pack retextures, each with its own placing rule: doors,
    branches, orientable and variant blocks, and more.
  - **Build inventories:** builders take blocks from the build inventory of their resource pack (`/inv`, or the F key).
  - **Kits:** builders save hotbar kits (`/get`).
  - **Item blocks** are models shown by invisible armor stands, with a limit per chunk.
- **Physics and placing:**
  - a per-world no-physics list, with exception areas for redstone and water;
  - a server-wide redstone freeze;
  - special placement, such as half doors and half beds;
  - block-state editing with a stick;
  - a flint block picker that fills in WorldEdit patterns.
- **Editors:** armor stands, banners, signs and paintings.
- **Copy and paste:**
  - Architect's own clipboard (`/copy`, `/cut`, `/paste`, `/undo`, `/redo`, `/flip`, `/rot`);
  - a block-state randomiser (`/random`);
  - VoxelSniper stencil tools and a schematic browser.
- **Custom heads:** heads are submitted, reviewed and accepted into categories, and shown in a head gallery in the world
  (`/chead`).
- **Resource packs:**
  - `/rp` switches packs, with per-player settings (variant, client, resolution) kept in a database that all servers
    share;
  - regions switch a player's pack automatically;
  - `/rp release` builds and publishes a pack, and reports how it went in chat.
- **Live biome tuning:** `/biometune` and an in-game editor change a biome's colours and atmosphere while players watch,
  without a restart. Saving writes the biome into its datapack.
- **Map layers:** resource-pack regions, item-block limits and budgets, and no-physics exception areas, drawn on the
  dynmap web map.
- **Comfort:**
  - full brightness (`/fbt`);
  - a client view distance (`/viewdistance`);
  - an F3+F4 game-mode switcher for builders who are not op.
- **Per-world switches:** 64 modules, each of which can be turned off for one world.

## Requirements

| What | Version and use |
|---|---|
| Server | Paper 26.2 |
| Java | 25 |
| Required plugins | [PluginUtils](https://github.com/MCME/PluginUtils) 2.0.4 or later, and WorldEdit 7.4 (needed even though `plugin.yml` does not declare it) |
| Optional plugins | ProtocolLib 5.5.0 or later (game-mode switcher, view distance), dynmap (map layers), ViaVersion (a pack per client version), [MCME-Connect](https://github.com/MCME/MCME-Connect) (packs on network join), [TheGaffer](https://github.com/MCME/TheGaffer) (build-permission checks) |
| Database | MySQL, for the players' resource pack settings |

## Installation

1. Download `MCME-Architect-<version>.jar` from [Releases](https://github.com/MCME/MCME-Architect/releases). It is also
   in MCME's Maven repository as `com.mcmiddleearth:MCME-Architect`.
2. Put it in `plugins/`, next to PluginUtils and WorldEdit and any optional plugins you want.
3. Start the server. Architect writes `config.yml`, and a `WorldConfig/` folder with a file per world.
4. Fill in `config.yml`: the resource packs, the pack settings database, and the other sections you use.

> [!WARNING]
> Every per-world switch starts **on**. On a fresh install:
> - dropped items are deleted (`dropBlocking`);
> - enchantments are stripped (`blockOpItems`);
> - the no-physics list becomes an allow list (`noPhysicsListInverted`).
>
> Review `WorldConfig/defaultWorldConfig.yml` before builders log in.

[Running Architect](docs/USER_MANUAL.md#running-architect) explains every setting.

## Quick start for builders

- Press **F**, or run `/inv`, to open the build inventory of your resource pack, and take blocks from its tabs.
- `/rp <pack>` switches your resource pack. `/rp help` lists the shortcuts.
- `/switchstick` turns on block-state editing with the stick: left-click picks a property, right-click changes it.
- `/copy` and `/paste` work on your WorldEdit selection, with Architect's own clipboard.
- `/biometune` opens the biome editor for the biome you stand in.
- `/architect help` lists the commands you may use.

## Commands at a glance

| Command | What it does |
|---|---|
| `/inv` | Opens the build inventory (the F key does too) |
| `/get` | Hotbar kits, dyed leather armor and heads |
| `/itemblock` | Item blocks and their limits |
| `/nophy` | The no-physics list and its exception areas |
| `/switchstick`, `/weselect` | Turns stick editing on; sets the preset for the flint block picker |
| `/armor`, `/banner`, `/sign` | Armor stand, banner and sign editors |
| `/copy`, `/cut`, `/paste`, `/undo`, `/redo`, `/flip`, `/rot` | Architect's copy and paste |
| `/random` | Randomises block states around you |
| `/vv`, `/sl`, `/schlist` | VoxelSniper stencils and WorldEdit schematics |
| `/chead` | Custom heads and the head gallery |
| `/rp` | Resource packs: switching, settings, regions, releases |
| `/biometune` | Live biome tuning |
| `/fbt`, `/viewdistance` | Full brightness and the client view distance |
| `/architect` | Version, reload, map layers, help |

The [command reference](docs/USER_MANUAL.md#command-reference) lists every command, with its syntax and permission.

## Documentation

- [User Manual](docs/USER_MANUAL.md): for builders, staff and server admins. It covers every feature, command,
  permission and setting.
- [Developer Manual](docs/DEV_MANUAL.md): architecture, building, testing and releasing.

## Building

You need JDK 25 and Maven 3.9. The dependencies come from Maven Central, the repositories of PaperMC, EngineHub,
ProtocolLib, ViaVersion and dynmap, and MCME's repository at repo.mcmiddleearth.com.

```bash
mvn -B -ntp clean verify
```

This builds the plugin and runs the tests. The jar lands in `target/` as `MCME-Architect-<version>-DEV-<commit>.jar`;
add `-Dbuild.type=RELEASE` for a release build.

## Contributing

- Work branches from `development`, and pull requests go into `development`.
- A release is a pull request from `development` to `master` plus a `v<version>` tag. CI then publishes the jar to MCME's
  Maven repository. [Releasing](docs/DEV_MANUAL.md#releasing) describes the steps.

## License

GNU General Public License v3.0; see [LICENSE](LICENSE).

Architect was started by Eriol_Eandur for MCME and is maintained by the MCME team.
