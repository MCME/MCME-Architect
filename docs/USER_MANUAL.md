# MCME-Architect — User Manual

For builders, staff and server admins of MCME-Architect 3.2.0 on Paper 26.2. For the architecture, building, testing
and releasing, see the [Developer Manual](DEV_MANUAL.md).

## Contents

- [Concepts](#concepts)
- [Blocks and inventories](#blocks-and-inventories)
- [Placing and physics](#placing-and-physics)
- [Editing tools](#editing-tools)
- [Copy, paste and shaping](#copy-paste-and-shaping)
- [Custom heads](#custom-heads)
- [Seeing and moving](#seeing-and-moving)
- [Resource packs](#resource-packs)
- [Biome tuning](#biome-tuning)
- [Map layers](#map-layers)
- [Running Architect](#running-architect)
- [Command reference](#command-reference)
- [Permissions](#permissions)
- [Troubleshooting](#troubleshooting)

---

## Concepts

A few ideas run through the whole plugin. Knowing them makes the rest of this manual easier to follow.

### Ranks and permissions

Architect's permissions come in four bundles, named after MCME's build ranks. Each bundle includes the one below it, and
all of them default to op.

| Bundle | Rank | What it adds |
|---|---|---|
| `architect.ranks.adventurer` | Adventurer | the `/inv` command, `/get` kits, the stick and its editors, special placement, `/rp`, `/fbt`, `/viewdistance`, `/switchstick`, viewing the no-physics list |
| `architect.ranks.artist` | Artist | saving armor stand and banner files, private kits, `/weselect`, `/chunkupdate` |
| `architect.ranks.designer` | Designer | copy and paste, `/random`, public kits, saved build inventories, no-physics exception areas, `/schlist`, `/vv`, `/sl` |
| `architect.ranks.headDesigner` | Head Designer | editing the no-physics list, managing custom heads, editing and syncing the build inventory, resource pack admin, item block limits, map layers, `/architect reload` |

`architect.*` grants the Head Designer bundle. Some features are in no bundle and need a node of their own, for
example:
- biome tuning (`architect.biomeTune` and its `.save`, `.publish` and `.admin` nodes);
- the game-mode switcher (`architect.gamemodeSwitcher`);
- placing invisible blocks (`architect.invisibleBlocks`);
- the copy size limit (`architect.copypaste.limit.<n>` or `architect.copypaste.unlimited`).

[Permissions](#permissions) lists every node.

### Worlds and switches

Most features can be switched off for a single world. If Architect answers that something is not enabled "in this
world" or "for this world", the feature's switch is off there. Some features, such as the F key and pick-block, then
simply behave as in vanilla. Server admins set the switches in the `WorldConfig` folder; see
[Per-world settings](#per-world-settings).

### Resource packs and block sets

MCME builds with several resource packs, one per region of Middle-earth. Architect knows them from its configuration,
and each pack brings:
- its own set of special blocks;
- its own build inventory.

The pack you have on decides what the F key opens and which blocks you get. A special block item remembers its pack:
the first line of its lore reads `MCME Block` and the second `<pack>/<block id>`. You switch packs with `/rp`; see
[Resource packs](#resource-packs).

### Tools you hold

Many of Architect's tools are an item in your main hand plus a click.

| Hold | Do | What happens |
|---|---|---|
| Stick | Left-click a block | Selects the block's next property. The stick starts off: run `/switchstick` once to turn it on. |
| Stick | Right-click a block | Changes the selected property. On an item block, steps to its previous texture. |
| Stick | Left-click a sign | Selects the sign for `/sign` |
| Stick | Click a banner | Edits the banner (see `/banner`) |
| Stick | Right-click or punch a painting | Shows the next or previous painting |
| Stick | Click an armor stand | Edits the stand in the mode chosen with `/armor` |
| Stick | Click a player head | Gives you a copy of the head |
| Flint | Left-click a block | Shows the block's data, with clickable lines for `/weselect` |
| Flint | Right-click a block | Gives you the matching MCME block item |
| Anything but flint | Middle-click a block (pick block) | Gives you the matching MCME block item for your pack |
| Anything | F | Opens the build inventory of your pack |
| Anything | Shift+F | Opens the collection of the block in your hand, or the heads |
| A special block | Shift+Q | Swaps the block in your hand for its configured next block |
| Two or more of a special block | Shift+Ctrl+Q | Opens the block's collection |

### Build permission

On MCME, the TheGaffer plugin decides where you may build. Architect asks TheGaffer before many edits, such as placing
special blocks or clicking blocks with the stick. On a server without TheGaffer, Architect does not limit where you
build.

---

## Blocks and inventories

Architect lets builders place block states that the game cannot place by itself, and gives them a build inventory to
take these blocks from. This chapter also covers item sets, item blocks, and how staff maintain the build inventory.

### Special blocks

The MCME resource packs give many vanilla block states a texture of their own: a stair state can show as a chair, a
wall state as part of a branch. A special block is one such state, or a small group of them, with its own rule for how
it is placed. Architect sets the exact state for you, without block updates, so neighbouring blocks do not change it.
Each server resource pack has its own list of special blocks, because each pack retextures different states.

A special block item carries its id in its lore. The first lore line reads `MCME Block`, the second `<pack>/<key>`,
for example `Human/oak_chair`. Architect places the block from this id, so the item places the same block wherever you
use it.

Other block rules are described elsewhere: the older renamed items (such as `Half oak door`), burning furnaces and
dragon eggs under [Special placement](#special-placement); container access and the no-interaction list under
[Protection and access](#protection-and-access); double doors and light blocks under
[Behaviour without a switch](#behaviour-without-a-switch).

#### Placing special blocks

1. Get the item: from the build inventory (press F, or `/inv b`), with pick-block from a block that is already built,
   or from an item set (`/get`).
2. Hold it in your main hand. The off hand does not place special blocks.
3. Right-click a block. The new block goes where its type puts it, usually against the face you clicked. It only
   replaces air, water, lava, fire or short grass; a `MULTI_FACE` block can also join a block of its own material.
   Clicking a flower pot never places anything.
4. To place against a door, trapdoor, button, lever or fence gate, sneak while you click. Without sneaking you use that
   block instead, unless its state is on the world's `noInteraction` list.

Entries of the types `VANILLA` and `DOOR_VANILLA` are placed by the game, like any vanilla block.

**Permission:** none from Architect, so anyone who has the item may place it. Where TheGaffer runs, you need its build
permission at the spot (see [Build permission](#build-permission)).

**Setting:** `modules.specialBlocks.place` switches placing, the tidy-up after breaking and item blocks on or off per
world. Like every world switch, it is on by default (see [Per-world settings](#per-world-settings)).

**Good to know**

- An item whose entry was renamed or removed answers `Special block data not found, item is probably outdated.` Take
  a new one from the build inventory.
- Doors (including three- and four-block doors) and `UPSHIFT` blocks need empty spaces above the spot. Where one is
  taken, they place nothing and say `Not enough room: every space this block fills must be empty.` `DOUBLE_Y_BLOCK`
  places its upper block only where there is room.
- Placed special blocks count for your TheGaffer job statistics; sneak-click edits do not.

#### Placement types

Each entry in the block files has a `type`, which decides what happens when you place it. The types fall into these
families:

| Family | What happens when you place it | Types |
|---|---|---|
| Single blocks | One fixed state against the clicked face. Variants place it one block higher, on the water surface you look at (like a lily pad), or make neighbouring fences, walls and panes connect to it. `VANILLA` is a plain block that the game places. | `BLOCK`, `UPSHIFT`, `BLOCK_ON_WATER`, `BLOCK_CONNECT`, `BLOCK_ON_WATER_CONNECT`, `VANILLA` |
| Turning blocks | The state follows the way you face (4 or 8 directions, or a quarter-block corner), or the face you click (side, top, axis like a log, upper or lower half like a slab). | `FOUR_DIRECTIONS`, `EIGHT_FACES`, `FIVE_FACES`, `SIX_FACES`, `SIX_FACES_XZ`, `THREE_AXIS`, `TWO_AXIS`, `BISECTED`, `WALL_COMBI`, `MATCH_ORIENTATION` |
| Connecting blocks | `MULTI_FACE` adds one face of a fence, pane, vine or similar block, towards the block you click; the other two place like turning blocks. Sneak-click changes the faces or connections of a placed block. | `MULTI_FACE`, `DIAGONAL_CONNECT`, `FOUR_DIRECTIONS_COMPLEX` |
| Branches | Branch and twig pieces that join the branch you click, with slope and direction taken from where you look. | `BRANCH`, `BRANCH_HORIZONTAL`, `BRANCH_DIAGONAL`, `BRANCH_STEEP`, `BRANCH_CONNECT`, `BRANCH_TRUNK_CONNECT`, `BRANCH_TRUNK_CONNECT_COMPLEX`, `BRANCH_TWIGS`, `BRANCH_TWIGS_EIGHT_DIRECTIONS` |
| Doors and stacks | Two or more blocks at once: doors with a fixed powered state or hinge, doors used as thin wall panels, doors three or four blocks high, or two different blocks on top of each other. | `DOOR`, `DOOR_VANILLA`, `THIN_WALL`, `DOOR_THREE_BLOCKS`, `DOOR_FOUR_BLOCKS`, `DOUBLE_Y_BLOCK` |
| Models | A base block with a model on an invisible armor stand (see [Item blocks](#item-blocks)), or an invisible, fixed item frame that holds an item. | `ITEM_BLOCK`, `ITEM_BLOCK_TWO_DIRECTIONS`, `ITEM_BLOCK_FOUR_DIRECTIONS`, `ITEM_FRAME` |
| Signs and furnaces | A standing, wall or hanging sign, chosen by the face you click, after which the sign editor opens; or a lit furnace that faces you and keeps burning. | `SIGN`, `SIGN_POST`, `SIGN_WALL`, `BURNING_FURNACE` |
| Inventory only | Places nothing. | `NONE`, `MOB_SPAWNER_BLOCK` (switched off in 3.2.0) |

#### Sneak-click editing

Some types change a block that is already there when you sneak and right-click a block of their kind, instead of
placing a new one:

| Types | Sneak + right-click on a placed block |
|---|---|
| `MULTI_FACE` | Turns one face of the clicked block on or off: the face on the far side from the one you click. |
| `DIAGONAL_CONNECT`, `FOUR_DIRECTIONS_COMPLEX`, `BRANCH_CONNECT`, `BRANCH_TRUNK_CONNECT_COMPLEX` | Changes the connection on the side that faces you. |
| `BRANCH_TRUNK_CONNECT` | Adds a side branch on the side you click, or removes it. |
| `BRANCH_HORIZONTAL`, `BRANCH_DIAGONAL`, `BRANCH_STEEP`, `BRANCH_TWIGS` | Switches the clicked piece to its next variant: thick or thin, or upper or lower twigs. |
| `BRANCH` with a fixed `width` | Changes a connection of the trunk wall you click. |

For every other type, sneaking only lets you place against doors and the like. A sneak-click edit needs build
permission at the clicked block.

#### Breaking special blocks

Break special blocks as usual. Where TheGaffer runs, it must allow you to build at the block, or the break is refused.
When you break an item block's base block, its armor stand is removed at once. Shortly after a break, Architect also
removes the water a waterlogged special block leaves behind, and lowers the trunk wall that a broken `BRANCH` piece was
joined to. This part only happens for special blocks of your current pack, because the same state can be a different
block in another pack.

#### Pick-block

Pick-block gives you the special block you point at, so you can copy what is already built.

| Action | Result |
|---|---|
| Middle-click a block (pick block), up to 6 blocks away | You get 2 of the matching special block of your pack in your hotbar, or the hotbar slot that already holds it is selected. With a full hotbar, the item in your hand is replaced. |
| Sneak + middle-click a block | Opens that block's collection in the build inventory. While sneaking, pick-block also reaches water and lava, and opens the collection of a special block that matches them. |
| Middle-click a painting or item frame | You get a painting, the framed item, or an empty item frame of the same kind. |
| Right-click with flint, on a block or in the air (up to 1000 blocks) | As middle-click, but your selected slot stays: the item goes into a free hotbar slot. Sneak to open the collection instead. |

Middle-clicking chests, trapped chests and ender chests works as in vanilla, as does any middle-click while you hold
flint. A player head gives that head. Left-clicking with flint shows the block's data instead (see
[The block picker](#the-block-picker-flint-and-weselect)).

Pick-block only looks at the special blocks of your current pack. If several match the block, the one with the highest
`priority` wins; if none matches, you get the vanilla item. If Architect cannot tell your pack, pick-block says
`Your resource pack could not be determined. You might not get correct block picks.` and gives the vanilla item.

**Permission:** none, except that picking a player head needs `architect.customHeadManager.user` (Adventurer bundle)
in a world where `modules.command.customHeadManager` is on.

**Setting:** `modules.specialBlocks.flint` switches pick-block and the flint. When it is off, pick-block works as in
vanilla.

### The build inventory

The build inventory is a chest window that lists the special blocks of a resource pack, sorted into tabs. A block
without a `category` is in no tab, but search finds it. Taking a
block gives you copies, so nothing runs out. The window is titled `MCME Blocks - <pack>`. There are also a head
inventory (`MCME Head Collection`), search windows and saved inventories.

#### Opening the build inventory

| Command or key | What it does |
|---|---|
| F | Opens the build inventory of your pack (see [Inventory keys](#inventory-keys)). |
| `/inv b [rp:<pack>]` | Opens the build inventory; with `rp:`, that of another pack. Any word that starts with `b` works, such as `/inv blocks`. |
| `/inv b [rp:<pack>] s:<text>` | Opens a search window (see [Search windows](#search-windows)). |
| `/inv h [s:<text>]` | Opens the head inventory, or searches it. Heads do not depend on the pack. |
| `/inv c [rp:<pack>]` or `/inv s [rp:<pack>]` | Opens the saved inventories (see [Saved inventories](#saved-inventories)). |
| `/inv create [rp:<pack>] <name>` | Saves your inventory as a saved inventory. |
| `/inv delete [rp:<pack>] <name>` | Deletes a saved inventory. |
| `/inv` or `/inv help [page]` | Shows the help. |

`rp:<pack>` takes the start of a pack name, in any case: `rp:hum` means `Human`. The staff commands `/inv reload`,
`/inv upload` and `/inv download` are described under [Editing the build inventory](#editing-the-build-inventory).

**Permission:** `architect.inventory` (Adventurer bundle) for every player subcommand of `/inv`. F and the other keys
need no permission.

**Setting:** `modules.command.getSpecialBlocks` switches the build inventory, its keys and `/get` on or off per world.
When it is off, `/inv` answers `Custom build inventories are not enabled in this world.`

**Good to know**

- `/inv search` opens the saved inventories, because every word that starts with `s` or `c` does. Search with
  `/inv b s:<text>`. The letters are case-sensitive: `/inv B` is refused.
- `/inv i` answers `Not implemented yet!`: no command opens item inventories in 3.2.0.

#### Using the window

- **Tabs.** The top row holds the tabs; click one to show its blocks. When there are more tabs than fit, the first and
  last slots of the row hold `previous Categories` and `next Categories` icons that scroll the row. A Heads tab, if the
  pack has one, opens the head inventory, whose Blocks tab brings you back.
- **Pages.** The five rows below the tabs hold 45 blocks. A longer tab gets a `page down` icon in the bottom-right
  slot and, from the second page on, a `page up` icon in the top-right slot below the tabs. How these icons look
  depends on the resource pack.
- **Taking blocks.** Left-click a block to put 2 of it on your cursor; left-click it again for one more, up to a full
  stack. Then click a slot of your own inventory to put them there. You cannot drag or shift-click items into the
  window.
- **Collections.** Some blocks have a collection: their variants, laid out around them. Right-click or
  Shift+left-click such a block to open it; in tabs set up for it, a plain left-click opens it too. The block sits in
  the centre with the icon of its tab below it. That icon, or any tab, takes you back. Collections have no pages.
- **Remembered view.** When you open the window again, it shows the tab, page or collection where you left it, until
  the inventories are reloaded.

#### Inventory keys

| Key | Holding | What happens |
|---|---|---|
| F | anything | Opens the build inventory of your pack. |
| Shift+F (sneak and press F) | a special block | Opens its collection in quick-pick mode. Left-click a variant: the block in your hand is removed, you get 2 of the variant, and the window closes. |
| Shift+F | anything else | Opens the head inventory. |
| Shift+Q (sneak and drop one item) | a special block | Your hand becomes 2 of the block's next block (its `nextBlock`), or 2 of the same block if it has none. |
| Shift+Ctrl+Q (sneak and drop the stack) | 2 or more of a special block | Opens the block's collection, if it has one, and refills your hand with 2. |

**Good to know**

- Where the build inventory is on, F never swaps the items in your hands, and you cannot drop other items while
  sneaking.
- After a Shift+F pick, the next F opens the same collection, still in quick-pick mode: a left-click there again
  replaces the block in your hand. Click a tab to get back to the normal view.

#### Which pack you get

Your current pack is the server resource pack Architect last sent you, for example with `/rp` (see
[Resource packs](#resource-packs)).

| Way in | Pack used |
|---|---|
| F | Your current pack. Without one, the pack named by the first word of the name of the item in your main hand, then of the item in your off hand. |
| `/inv b`, `/inv c`, `/inv create`, `/inv delete` | The `rp:` pack if you give one, else your current pack. With neither: `You are not in a valid resource pack region. Please use '/inv <subcommand> <rp:rp-name>'.` |
| Collections (Shift+F, Shift+Ctrl+Q, sneak + pick-block) | The pack in the block's id. |
| Pick-block | Your current pack. |
| Flint (right-click), breaking | The pack named by the first word of your hand item's name, if that word is a pack name; otherwise your current pack. |

#### Search windows

`/inv b [rp:<pack>] s:<text>` opens a window with every block of the pack whose name contains the text, in any case,
including blocks that are in no tab. `/inv h s:<text>` does the same for heads. The title shows the number of hits,
such as `12 blocks found for *door*`.

- Left-click a result to take 1; click it again for one more.
- Clicking a result while you hold a different item on the cursor deletes that item.
- Page with the `page up` icon (top right) and the `page down` icon (bottom right).
- Search windows have no collections. Only names are searched, not ids.

#### Saved inventories

Saved inventories let a project leader share the blocks a build needs: they save their own inventory, and other
builders take from it.

1. Fill your inventory with the blocks. Hold the item you want as the tab icon in your off hand.
2. Run `/inv create [rp:<pack>] <name>`. Your main inventory and hotbar (not your armor or off hand) become a new
   saved inventory of that pack. With an empty off hand the icon is a gold nugget.
3. Run `/inv c [rp:<pack>]` (or `/inv s`) to open `Saved Inventories - <pack>`. Each saved inventory is one tab. Take
   items as in the build inventory.
4. Run `/inv delete [rp:<pack>] <name>` to delete one of yours.

**Permissions:** `architect.inventory.save` (Designer bundle) to create saved inventories and delete your own;
`architect.inventory.other` (Head Designer bundle) to delete those of other players. Opening them needs
`architect.inventory`.

**Good to know**

- In 3.2.0 every saved inventory is public: everyone who can use `/inv` can open it and take from it. The file says
  `isPrivate: true`, which Architect reads the other way round.
- `/inv create` always answers `Inventory saved.`, even when saving failed; check the window.
- After a create or delete, ask staff to run `/inv reload`. Until then, players who opened the window before do not
  see a new tab, and after a delete the window may not open for them.
- If your pack has no saved-inventory folder yet, the window shows the saved inventories of the `Human` pack. A pack's
  first saved inventory shows in a window titled `Custom Inventory` until the next `/inv reload`, and after a pack's
  last saved inventory is deleted, `/inv c` fails with an error for that pack.
- The files are in `plugins/MCME-Architect/inventories/saved/<pack>/`, one per saved inventory.

### Item sets (/get)

An item set is a saved hotbar: up to nine items, often special blocks, that anyone can get back with one command. The
help calls them kits. `/get` also hands out heads from the MCME head collection and dyed leather armor.

| Command | What it does | Extra permission |
|---|---|---|
| `/get` or `/get help [page]` | Shows the help. | none |
| `/get list [page]` | Lists the public sets and your own private ones, with owner and description. Click a line to put `/get <set>` into your chat line. | none |
| `/get <set>` | Adds the set's items to free slots of your inventory. Items that do not fit are lost. | none |
| `/get <set> -o` | Puts each item into the hotbar slot it was saved from, replacing what is there. | none |
| `/get head <head or folder>` | Gives one head, or every head in that folder, from the MCME head collection. | `architect.get.head` |
| `/get armor <colour>` | Gives a full set of leather armor, dyed in a hex RGB colour such as `FF0000` or `0xFF0000`. | `architect.get.armor` |
| `/get pcreate <set> [description]` | Saves your hotbar as a private set. | `architect.get.private` |
| `/get create <set> [description]` | Saves your hotbar as a public set. | `architect.get.public` |
| `/get publish <set>` or `/get unpublish <set>` | Makes a set public or private. | your own set or `architect.get.other`, and `architect.get.public` |
| `/get delete <set>` | Deletes a set. | your own set or `architect.get.other` |

A private set is only hidden: `/get list` shows it to its owner alone, but anyone who knows the name can get it.

**Limits**

- A player can have at most 11 private sets; the twelfth is refused.
- In 3.2.0, private sets can be lost once their owner has been offline for about 14 hours: the next save of the set
  file leaves them out, and after the next restart they are gone. Use a public set
  for anything you want to keep.
- Set names are case-sensitive, but `list`, `armor` and `help` cannot be used as set names in any case (`List` and
  `HELP` neither).

**Permissions**

| Permission | Allows | Bundle |
|---|---|---|
| `architect.getCommand` | `/get` itself: help, list, getting sets, deleting your own | Adventurer |
| `architect.get.head` | `/get head` | Adventurer |
| `architect.get.armor` | `/get armor` | Adventurer |
| `architect.get.private` | `/get pcreate` | Artist |
| `architect.get.public` | `/get create`, `/get publish`, `/get unpublish` | Designer |
| `architect.get.other` | deleting, publishing and unpublishing other players' sets | Head Designer |

**Setting:** `/get` follows `modules.command.getSpecialBlocks`, like the build inventory. When it is off:
`Get command is not enabled in this world.` The sets are stored in `plugins/MCME-Architect/itemSets.yml`.

**Good to know**

- Only `head` and `armor` are subcommands: `/get doors` and the like are read as set names and answer `Item set not
  found.` The permissions `architect.get.doors` and the like do nothing.
- A set keeps copies of the items. A special block in a set stops working when its entry is renamed or removed.
- `/architect reload` reads `itemSets.yml` again, but keeps sets you deleted from the file by hand. Edit the file while
  the server is stopped.

### Item blocks

Item blocks show detailed models that are not blocks, such as furniture. Each one is a base block, often a barrier so
that it is solid but invisible, plus an invisible armor stand at the same spot that wears an item on its head. The
resource pack draws that item as the model.

1. Take an item block from the build inventory and right-click like any special block. The types
   `ITEM_BLOCK_TWO_DIRECTIONS` and `ITEM_BLOCK_FOUR_DIRECTIONS` turn to the way you face. If the entry lists several
   models, one is picked at random.
2. To change the model, right-click the base block with a stick: it steps to the previous model in the entry's list.
   This works even while your stick is switched off with `/switchstick`.
3. To remove the item block, break its base block. The armor stand goes with it.

The armor stand itself cannot be right-clicked, and the armor stand editor treats it as locked.

**Permission:** placing needs none, as for every special block. Changing the model with a stick needs
`architect.cycleBlocks` (Adventurer bundle) and build permission, in a world where `modules.command.cycleBlocks` is
on.

**Good to know**

- An armor stand stays behind when its base block disappears in another way, such as with WorldEdit or `/fill`. A
  head designer can remove it with `/itemblock remove`.
- The stick, pick-block and `/itemblock stats` read the damage value of the model item. Only diamond chestplate models
  use damage; other models use custom model data, so for them the stick probably changes nothing visible, and
  pick-block may give the base block instead.

#### Item block limits

- Item blocks are limited per chunk. You can place one while its chunk holds fewer armor stands, item frames and
  paintings together than the limit. All of them count, not only item blocks, but only item blocks are refused.
- The limit is the highest limit of the limit regions that contain the block. Outside regions it is the world's base
  limit, 5 unless changed.
- A full chunk answers:
  `Too many entities (paintings, item frames, item blocks and armorstands) in this chunk already. (Limit: <n>)`
- Players with `architect.itemBlock.region.ignore` (Head Designer bundle) are not limited.

Head designers manage the regions and limits with `/itemblock` (below). The web map can show the limit regions and how
full each chunk is (see [Map layers](#map-layers)).

**Settings**

| Setting | What it does |
|---|---|
| `itemBlock.baseLimit` | The base limit, default 5. It is read only from the world's own file, `WorldConfig/<world>.yml`; a value in `defaultWorldConfig.yml` is ignored. `/itemblock limit -base` writes it. |
| `plugins/MCME-Architect/itemBlockRegions/` | One `<name>.reg` file per limit region, read at start-up and on `/architect reload`. |

#### The /itemblock command

| Command | What it does | Extra permission |
|---|---|---|
| `/itemblock help [page]` | Shows a short help. | none |
| `/itemblock stats <radius> [height]` | Counts the named armor stands around you by head item and damage (`Type: … Durability: … Number: …`). | none |
| `/itemblock glow <radius>` | Makes every armor stand within `<radius>` blocks of you glow, refreshed every second, until you turn it off or log out. Everyone sees the glow. | none |
| `/itemblock glow off` | Stops the glow. Any word that is not a number works. | none |
| `/itemblock remove <radius> [height]` | Removes every armor stand with a custom name around you, not only those of item blocks. | `architect.itemBlock.remove` |
| `/itemblock list` | Lists the limit regions with their limits. | none |
| `/itemblock create <name>` | Creates a limit region from your WorldEdit selection. | `architect.itemBlock.region.create` |
| `/itemblock delete <name>` | Deletes a limit region. | `architect.itemBlock.region.create` |
| `/itemblock limit <name> <number>` | Sets a region's limit. | `architect.itemBlock.region.limit` |
| `/itemblock limit -base <number>` | Sets the base limit of the world you are in. | `architect.itemBlock.region.limit` |

`<radius>` reaches that many blocks sideways, and `[height]` that many up and down (default: the radius).

**Permissions:** every subcommand needs `architect.itemBlock`, plus the extra permission in the table. All of these
come with the Head Designer bundle.

**Setting:** `modules.specialBlocks.place`. When it is off: `Item blocks are not enabled in this world.`

**Good to know**

- `/itemblock` without a subcommand fails with an internal error; use `/itemblock help`. A radius or limit that is not a
  number answers `You need to specify a number.`; a missing radius shows the help.
- A new region has limit 0: nobody without the ignore permission can place an item block in it until you set a limit.
- `/itemblock remove` also deletes other named armor stands in range. Look first with `/itemblock glow`.

### Editing the build inventory

This part is for staff and for the server admin. The blocks of each pack live in YAML files in
`plugins/MCME-Architect/inventories/block/<pack>/`. You can change them with the in-game editor, by hand, or by syncing
them with a git repository.

> [!WARNING]
> In 3.2.0, two editor paths write values that Bukkit cannot read back. The whole tab file then fails to load, and all
> its blocks disappear from the build inventory, although the editor reports the save and `Inventory reload done!`.
>
> - **Changing the item during an edit.** At `To change the inventory item hold in main hand a new item and type 'ok'`,
>   answer `!skip`, and change `itemMaterial` in the file instead.
> - **Answering `yes` in a collection view.** At
>   `Should the … item be listed directly in the category of its collection?`, answer `no`. When you edit an entry
>   that is already in a tab, `!skip` does the same harm, so edit such entries from their tab, not from a collection
>   view.
>
> Back up the pack folder, or keep it in git, before you edit in game, and check the file after every edit. To repair
> a broken file, look for a value that starts with `!!`: give `itemMaterial` its item name again, and set a broken
> `indirect` to the key of the block whose collection it belongs to, or delete that line. Then run `/inv reload`.

#### The in-game editor

1. Open the build inventory of your current pack. New blocks always go into the pack you are using, even if you opened
   another pack's window with `rp:`.
2. Open the tab the block belongs to, or a block's collection to add a variant to it.
3. Shift+right-click in the item area: on an empty slot to add a block, on a block to edit or delete it. For
   players with `architect.inventory.edit`, Shift+right-click always starts the editor; a plain right-click still opens
   collections. The window closes and the editor asks its questions in chat, marked `[Inventory Editor]`.
4. Answer in chat. Where a list in `[…]` is shown, type one of its words exactly as shown. Type `!cancel` to stop at
   any time; after 300 seconds without an answer the editor stops by itself. Either way you see
   `Custom inventory editor conversation cancelled.` and nothing is saved.

**Permission:** `architect.inventory.edit` (Head Designer bundle).

**Adding a block**

1. `Type in a unique ID for the new item!`: a key that the pack does not have yet. Use letters, digits and
   underscores.
2. `Which type of block do you want to add?`: one of the types in the table below.
3. `Left-click a block with a prismarine shard to use for blockData<Key>.`: asked once for every state the type needs.
   Type a state, such as `minecraft:oak_stairs[facing=north]`, or hold a prismarine shard and left-click a block in the
   world to copy its state.
4. `Hold the item you want to add to custom inventory in main hand and type 'ok'.`: the item's type, custom model data
   and leather colour are taken.
5. `Current custom model data is …`: a whole number (0 or more), or `!skip`.
6. `Enter a display name for the new inventory item:`: the block's name in the window, which search also uses.
7. Only in a collection view: `Should the new inventory item be listed directly in the category of its collection?`:
   answer `no` (see the warning).

The editor then saves and reloads the build inventories, and says so:
`Custom inventory item saved to file <pack>/<file> Reloading inventories...`, then `Inventory reload done!`. The entry
is added under `Items` in `<tab name in lower case>.yml`, with the open tab as its `category`. In a collection view it
gets no tab; instead it is added to the base block's `collection`, at the slot you clicked.

| Type you enter | States asked (saved as `blockData<Key>`) |
|---|---|
| `block`, `block_connect`, `upshift`, `block_on_water` | one, saved as `blockData` |
| `bisected` | `Up`, `Down` |
| `four_directions` | `North`, `West`, `South`, `East` |
| `five_faces` | `North`, `West`, `South`, `East`, `Up` |
| `six_faces` | `North`, `West`, `South`, `East`, `Up`, `Down` |
| `eight_faces` | `North`, `NorthWest`, `West`, `SouthWest`, `South`, `SouthEast`, `East`, `NorthEast` |
| `three_axis` | `X`, `Y`, `Z` |
| `diagonal_connect` | `North`, `West`, `South`, `East`, `Up` |
| `double_y_block` | `Upper`, `Lower` |
| `vanilla` | none: the block is the item's own block |
| `branch_twigs`, `branch_horizontal` | none, so the entry never loads: write these by hand |

**Editing or deleting a block**

1. `What do you want to do with inventory item <id>? [edit, delete]`: `delete` removes the entry at once.
2. `Current item id is <id>. Enter a new id or '!skip'.`: a new key, or `!skip`. Typing the same key again is refused.
3. `Current block type is <TYPE>.`: a type from the list, or `!skip`.
4. One question for each state: type or shard-click a new state, or `!skip`.
5. `To change the inventory item …`: answer `!skip` (see the warning).
6. `Current custom model data is …`: a number, or `!skip`.
7. `Current display name is …`: a new name, or `!skip`.
8. Only in a collection view: the tab question (see the warning), then `Current item slot is <slot>.`: a slot label
   from the list, or `!skip`. A new slot swaps places with the block that is there.

**Good to know**

- An edit writes the entry anew. Keys the editor does not ask about are lost: `priority`, `nextBlock`, `damage`, the
  entry's own `collection`, extra tabs and type-specific keys. Edit entries of types the editor does not list (doors,
  item blocks, signs, most branches) in the file; for them, `!skip` at the type question saves at once.
- The editor only handles tabs whose blocks are in a file named after the tab in lower case (tab `Furniture`:
  `furniture.yml`). The file must exist, or you get
  `Can't save custom inventory. Inventory config file not found: <pack>/<file>`. The editor never creates files or
  tabs.
- Deleting an entry, or giving it a new key, does not update other entries that name it (`collection`, `nextBlock`,
  `Main`). Fix those in the files.
- All editor sessions since the last restart share one set of block states, so a new entry can get extra
  `blockData…` keys from an earlier or parallel session. Check each saved entry for stray `blockData…` keys; a restart
  empties the set.

#### Block files

Each pack has one folder, `plugins/MCME-Architect/inventories/block/<pack>/`. The folder name must be exactly the
pack's key under `ServerResourcePacks` in `config.yml`, with the same upper and lower case. Architect does not create
it. Only `.yml` files directly in the folder are read:

- `categories.yml` (optional) defines the tabs: their order, names and icons. When it exists, a Heads tab comes first.
  Tabs that blocks use but this file does not list are added at the end with a gold nugget icon. Without the file, the
  tab order depends on the order in which the server lists the files, so use `categories.yml` to fix it.
- Every other `.yml` file holds blocks under `Items`, and optionally a `Main` list.

`categories.yml`:

```yaml
Categories:
  Furniture:                 # tab name; tabs appear in this order
    itemMaterial: OAK_STAIRS # icon item
    display: Furniture       # icon name (default: the tab name)
    cmd: 101                 # icon custom model data (also possible: damage, color)
    cmdCurrent: 102          # icon while the tab is open (also possible: damageCurrent)
    useSubcategories: false  # true: a left-click on a block with a collection opens it
```

A block file, `furniture.yml`:

```yaml
Main:                        # optional: these keys come first, in this order, in the
  - oak_chair                # tab whose name starts with the file name ("Furniture")
Items:
  oak_chair:                 # key; the item's id is <pack>/oak_chair
    type: FOUR_DIRECTIONS
    itemMaterial: OAK_STAIRS
    display: Oak chair
    category: Furniture      # or a list: [Furniture, Wood]
    blockDataNorth: minecraft:oak_stairs[facing=north]
    blockDataEast: minecraft:oak_stairs[facing=east]
    blockDataSouth: minecraft:oak_stairs[facing=south]
    blockDataWest: minecraft:oak_stairs[facing=west]
    nextBlock: oak_chair_top # Shift+Q switches to this block
    collection:
      A1: oak_chair_top      # top row, just left of the centre
  oak_chair_top:             # no category: found only by search and in collections
    type: BLOCK
    itemMaterial: OAK_STAIRS
    display: Oak chair, upside down
    blockData: minecraft:oak_stairs[half=top]
    collection:
      indirect: oak_chair    # show the chair's collection for this block too
```

A block listed under `Main` goes only into that tab, and its own `category` is ignored. If that tab does not exist yet,
the block falls back to its own `category`.

| Key | What it does |
|---|---|
| `type` | The placement type, in upper case (see [Placement types](#placement-types)). Missing: `VANILLA`. Unknown: the entry is skipped. |
| `itemMaterial` | The item that stands for the block in the window. An invalid name gives a plain stone item that is not a special block. |
| `display` | The block's name in the window, which search also uses. Default: the key. |
| `cmd`, `damage`, `color` | Custom model data, damage, and colour (a decimal RGB number, for leather, potions and firework stars) of the item. |
| `category` | The tab, or a list of tabs. Without it, the block is only found by search and in collections. |
| `blockData…` | The states to place. The keys depend on the type: `blockData` for single blocks, `blockDataNorth` to `blockDataWest` for four directions, `blockDataX` to `blockDataZ` for axes, and so on. Item blocks also take `contentItem`, `contentCmd` or `contentDamage` (a comma list of model numbers) and `contentHeight`. |
| `priority` | Which entry wins when several match the same placed block (pick-block, breaking). Default 1. |
| `nextBlock` | The key of the block that Shift+Q switches to. |
| `collection` | Slot labels with the keys of the blocks shown around this one (grid below), or `indirect: <key>` to show another block's collection. |

The collection grid, below the tab row. `--` is a background icon and `back` the tab icon that leads back. Leave the
`B0`, `C0` and `D0` slots (and `G0`, `H0`, `I0`) alone: they belong to the block and the two icons.

```
A4  A3  A2  A1  A0     F1  F2  F3  F4
B4  B3  B2  B1  --     G1  G2  G3  G4
C4  C3  C2  C1  block  H1  H2  H3  H4
D4  D3  D2  D1  back   I1  I2  I3  I4
E4  E3  E2  E1  E0     J1  J2  J3  J4
```

**Good to know**

- Architect saves every block file again right after loading it (2-space indentation, old formats converted). The
  first reload after a hand edit therefore changes the file, which shows up in git.
- A file with a YAML error is skipped as a whole, and Architect's log names it. Single skipped entries (unknown type,
  duplicate key, invalid state) are only reported at debug level 2: run `/architect dev 2` and `/architect dev`, then
  `/inv reload`, to see them in chat.
- These types cannot be loaded in 3.2.0, so their entries are skipped: `OPEN_HALF_DOOR`, `BRANCH_TWIGS_UPPER`,
  `BRANCH_TWIGS_LOWER`, `BRANCH_TWIGS_UPPER_EIGHT_DIRECTIONS` and `BRANCH_TWIGS_LOWER_EIGHT_DIRECTIONS`.
  `MATCH_ORIENTATION` behaves like `FOUR_DIRECTIONS`, and `MOB_SPAWNER_BLOCK` places nothing.
- An item's id is `<pack>/<key>`. Renaming a key or a pack folder makes the existing items of it outdated.

#### Reloading the inventories

| Command | What it reloads | Permission |
|---|---|---|
| `/inv reload` | The build inventories, the head window and the saved inventories (new head files need `/architect reload`). `rp:` arguments are ignored. Also works from the console. | `architect.inventory.reload` |
| `/architect reload` | All of Architect, including these inventories, the item sets and the item block regions. | `architect.reload` |

Both permissions come with the Head Designer bundle. The editor reloads the build inventories by itself after each
save. A reload resets everyone's remembered window view. Items that players already hold keep working as long as their
ids still exist.

#### Syncing with git

`/inv upload` and `/inv download` run shell scripts on the server that copy a pack's block files to and from a git
repository, so that files edited on one server can be pulled onto another.

| Command | What it does | Permission |
|---|---|---|
| `/inv upload <pack> <description>` | Copies the pack's block files into a git clone, commits them with the description, and pushes. | `architect.inventory.upload` |
| `/inv download <pack>` | Pulls the git clone and copies its files into the pack folder, then reloads all inventories. | `architect.inventory.download` |

Both permissions come with the Head Designer bundle, and both commands also work from the console. `<pack>` takes the
start of a pack name, in any case. Nothing is shown when the commands start; up to 5 minutes later (10 if the script hangs) you see
`Custom inventory for RP <pack> pushed to GitHub!` or `Custom inventory for RP <pack> updated!`, or an error line with
the exit code. The scripts' output goes to Architect's log.

What the server needs:

1. A Linux server: the scripts run with `sh`. On Windows the commands fail at once.
2. A section per command in `config.yml`, with the script's file name and the folder it runs in:

   ```yaml
   inventoryDownload:
     script: <download script>
     path: <scripts folder>
   inventoryUpload:
     script: <upload script>
     path: <scripts folder>
   ```

   The shipped `config.yml` has only `inventoryDownload`, and its script name, `updateInventory.sh`, matches none of
   the bundled scripts. Without `inventoryUpload`, `/inv upload` always fails.
3. The scripts in `<scripts folder>`, with Unix line endings. The plugin's source repository holds
   `downloadInventory.sh` (the same as `updateInventories.sh`) and `uploadInventory.sh`; they are not in the jar.
4. In `<scripts folder>`, a git clone named `<Pack>-Inventories`, with an `inventories` folder for the pack's files.
   Git must pull and push without asking for a password.
5. The bundled scripts copy to and from one fixed server's plugin folder, written into the scripts. Check that this is
   the server where you run the command, and that its `inventories/block/<Pack>/` folder exists.

Architect passes the pack name with the first letter in upper case and the rest in lower case (`Human`), so clone and
folder names must match that form.

**Good to know**

- Keep the description to one word. The bundled upload script passes it unquoted, so a longer description commits
  nothing, yet the command reports success.
- A failure inside a script can still be reported as success, because only the script's last line sets the exit code.
  Check Architect's log.
- `git commit -a` does not add new files: add a new tab file to the repository by hand once.
- `/inv download` reloads all inventories even when the script failed, and never deletes files that were removed from
  the repository.

---

## Placing and physics

This chapter covers how Architect places blocks that the game cannot, how it stops block updates, the stick and the
flint, and the rules that protect inventories, books, hanging entities and items. Every `modules.…` switch named here
is set per world (see [Per-world settings](#per-world-settings)). On a new install every switch is on.

### Special placement

The game cannot place half a door, one half of a bed, crops without farmland or a piston head on its own. Architect
places such blocks when you use a vanilla item whose name starts with a key word. Today most special blocks come from
the build inventory; these named items are the older way.

#### Named items

Rename the vanilla item in an anvil so that its name starts with the key word, for example `Half oak door`, then use it
as the table shows. Each placement also needs build permission at the spot. All six permissions are in the Adventurer
bundle.

| Item in your main hand | Name starts with | What happens | Permission | Switch |
|---|---|---|---|---|
| Oak, spruce, birch, jungle, acacia, dark oak or iron door | `Half` | Right-click a block: only the lower half of the door is placed, turned the way you look. | `architect.place.halfDoor` | `modules.specialBlocks.halfDoors` |
| Red bed | `Half` | Right-click a block: one half of a bed is placed, turned the way you look. You get the foot, or the head if the name ends in `(head)`. | `architect.place.halfBed` | `modules.specialBlocks.halfBeds` |
| Redstone torch | `Unlit` | Right-click a block: a redstone torch is placed on the face you clicked. In 3.2.0 it comes out lit, despite the key word; set `lit=false` with the stick. | `architect.place.torch` | `modules.specialBlocks.redstoneTorch` |
| Wheat, melon seeds, pumpkin seeds, brown or red mushroom, cactus, rose bush, dead bush, nether wart or lily pad | `Placeable` | Right-click a block: the plant is placed without the soil or water it needs. Right-click a crop of the same kind to lower its growth stage by one instead; below the lowest it starts again at the highest. | `architect.place.plant` | `modules.specialBlocks.plants` |
| Piston | `Table` or `Wheel` | Place it: you get a sticky piston head on its own, facing you. | `architect.place.pistonExtension` | `modules.specialBlocks.pistonExtenstions` (spelt this way) |
| Furnace | `Burning` | Place it: you get a lit furnace that faces you and stays lit. | `architect.place.burningFurnace` | `modules.specialBlocks.burningFurnace` |

#### Burning furnaces

Where `modules.specialBlocks.burningFurnace` is on, any furnace, smoker or blast furnace can be set to burn for ever:

1. Open it. With the default inventory access, a furnace, smoker or blast furnace needs `architect.inventory.open` and
   build permission (see [Inventory access](#inventory-access)).
2. Click its top slot, the input. If the slot is empty, the furnace lights: its input becomes the cod, salmon, tropical
   fish, chicken, rabbit, porkchop or mutton on your cursor (cod if you hold none of these), and its fuel coal.
3. To put it out, click the input slot again while it holds something. The furnace goes out and is emptied.

Lighting or putting out a furnace needs build permission there.

In such a world every furnace that finishes smelting gets its input and coal back and loses its result, so it keeps
burning. Furnaces there cannot be used for real smelting.

#### Double slabs

Where `modules.specialBlocks.place` is on, stacking a second slab on a slab can give a full block instead of a double
slab. The world's `doubleSlabReplacements` list decides, per resource pack. Architect looks under your current pack or,
if it does not know yours, under the pack of the region the block is in. An entry whose `from` is exactly the slab that
was there before is replaced by its `to` block. No permission is needed.

```yaml
doubleSlabReplacements:
  human:                   # pack name, in lower case
    oakPlanks:             # any name for the entry
      from: minecraft:oak_slab[type=bottom,waterlogged=false]
      to: minecraft:oak_planks
```

A new `defaultWorldConfig.yml` holds one example entry: acacia slabs to acacia planks, under the pack name `gondor`. The
switch `modules.specialBlocks.doubleSlabs` and the permission `architect.place.doubleSlab` have no effect.

#### Six-sided logs

Architect no longer has a six-sided log feature. Use the vanilla wood blocks, such as `oak_wood`, which have bark on all
six sides. The permission `architect.place.sixSidedLog` and the switch `modules.specialBlocks.sixSidedLogs` have no
effect.

#### Dragon eggs

Where `modules.specialBlocks.dragonEgg` is on, a right-click never makes a dragon egg teleport. A left-click works only
in creative mode, where it breaks the egg, for players with `architect.interactDragonEgg` (Adventurer bundle) and build
permission. Anyone else's left-click is cancelled.

**Good to know**

- The key word must start the item's name and is case-sensitive: an item named `half oak door` places a normal door.
- The named items set block data from before Minecraft 1.13, which the server translates into today's blocks. A block
  can come out differently from what the name suggests; when it matters, use the build inventory.
- Nobody can open half a door that stands alone, in any world.
- `Placeable` carrots and potatoes do not work, and a sticky piston never counts as a `Table` or `Wheel`.
- `architect.interactDragonEgg` is off by default, yet the Adventurer bundle grants it.

### No physics

Block updates, or "physics", undo decorative work: a torch drops when its support goes, a plant without soil pops off,
fences reconnect and water flows. Architect stops these updates for chosen block states, world by world.

**How the list works**

- Each world uses its own list, the key `noPhysicsList` in `WorldConfig/<world>.yml`. A world without that key uses
  the whole list in `defaultWorldConfig.yml`; the two are never merged.
- The switch `modules.environment.noPhysicsListInverted` sets what the list means. Off: it is a NO physics list, and
  listed states get no updates. On: it is an ALLOW physics list, and only listed states get updates; all other blocks
  get none.
- A new `defaultWorldConfig.yml` turns every switch on, this one too. On a fresh install the list is therefore an ALLOW
  list, and an empty list means that no block gets updates. `/nophy list` shows which kind a world has.
- An entry matches every block of its type whose listed properties match. Properties you leave out match anything.
- A block that gets no updates is not destroyed by them either: a torch stays when the block behind it goes.
- Where the list is on, tripwire never triggers.

| Command | What it does |
|---|---|
| `/nophy list <world>\|-default` | Shows the list that world uses, or the default list. |
| `/nophy add <world>\|-default <state>` | Adds a block state. Write it without `minecraft:`, with or without properties: `torch`, `oak_door`, `oak_fence[east=true]`. |
| `/nophy remove <world>\|-default <state>` | Removes an entry, typed exactly as it was added. |
| `/nophy exception redstone <name>` | Makes a redstone exception area from your cuboid WorldEdit selection. |
| `/nophy exception water <name>` | Makes a water exception area in the same way. |
| `/nophy exception delete <name>` | Deletes an exception area. |
| `/nophy exception list [page]` | Lists the areas with their lowest corner. Hover over one to see its size; click it to put the delete command in the chat box. |
| `/nophy help [page]` | Help. The help writes the command as `/noPhy`; it is the same command. |

`<world>` must be a loaded world; `-default` stands for `defaultWorldConfig.yml`.

**Exception areas.** An exception area is a named box in one world where some blocks get their updates back, whatever
the list says.

- **Redstone** areas cover redstone wire, comparators, repeaters, standing redstone torches, redstone lamps, pistons
  (not sticky ones), iron doors and iron trapdoors, the doors, trapdoors and fence gates of oak, spruce, birch, jungle,
  acacia and dark oak, dispensers, droppers, hoppers, the 16 dyed shulker boxes, enchanting tables, beacons and
  undamaged anvils. Inside a redstone area builders may also open the containers whose inventory access is
  `EXCEPTION` (see [Inventory access](#inventory-access)).
- **Water** areas let water and lava flow.

Names are case-sensitive and must be new. To resize an area, delete it and make it again. Areas are kept in
`plugins/MCME-Architect/NoPhyExceptionAreas.txt`.

**Redstone power is frozen on the whole server**: in every world, inside exception areas too, whatever the switches say.
Levers and buttons do not switch, pressure plates do nothing, and doors, trapdoors, gates, lamps, rails, note blocks and
command blocks ignore power. This is on purpose. The MCME resource packs use redstone block states as decoration (an
unpowered repeater can be a basket), and any change of power would change those builds. To show a block as powered or
lit, set its state with the stick.

**Permissions:**

- `architect.noPhysicsList.view` (Adventurer bundle): `/nophy` itself and `list`.
- `architect.noPhysicsList.exception` (Designer bundle): the exception commands.
- `architect.noPhysicsList.edit` (Head Designer bundle; includes view): `add` and `remove`.

**Switch:** `modules.environment.noPhysicsListEnabled` turns the list, and `/nophy`, on in a world.

> [!WARNING]
> After an `/architect reload`, if `/nophy add` or `/nophy remove` on `-default` brings the default list back to the
> list Architect held before that reload (you add an entry and remove it again, say; or you undo a hand edit of the
> file), Architect deletes the whole `noPhysicsList` from `defaultWorldConfig.yml`. Keep a copy of the file before you edit the default list.

**Good to know**

- The first `/nophy add <world> …` on a world that uses the default list gives that world a list of its own, with only
  the new entry: the default entries stop applying there. Add every entry the world needs. For an inherited entry,
  `remove` answers that it is not on the list.
- The NO or ALLOW in `/nophy` replies is that of the world you stand in, not of the world you named.
  `/nophy exception list` does not show an area's type.
- Because neighbours no longer update, Architect does part of their work itself, wherever you may build. It connects
  newly placed fences, glass panes, walls, chests, stairs, chorus plants and redstone wire to their neighbours, each kind
  with its own switch (`modules.environment.noPhysicsConnectFences`, `…Glass`, `…Walls`, `…Chests`, `…Stairs`,
  `…Chorus`, `…RedstoneWire`); states on the world's `noConnection` list are left alone. Breaking a waterlogged block
  leaves a water source; a waterlogged double slab leaves air where `modules.environment.drainWaterloggedDoubleSlabs`
  is on.
- Admins: `/architect reload` reads the files again, but a hand edit of `noPhysicsList` takes effect only after the next
  `/nophy add` or `remove`, or a restart. Delete exception areas with the command: a line removed from
  `NoPhyExceptionAreas.txt` by hand comes back.

### Block states with the stick

With a stick you can change any property of a placed block, such as its facing, half, shape, rotation, open, lit,
waterlogged or age, or the sides of a fence, pane or wall, without WorldEdit. Architect sets the new state without
block updates, so the neighbours leave it alone.

1. Turn the stick on with `/switchstick` (aliases `/swst`, `/ss`). It is off for every player until they do this once.
   Architect remembers your choice; run the command again to turn the stick off.
2. Hold a stick in your main hand and left-click a block. Each left-click selects the block's next property. Chat shows
   "Selected Block State: <property> : <value>".
3. Right-click the block to step that property to its next value: "Set Block State: <property> : <value>".

**Permissions:** `architect.switchstick` for the command and `architect.cycleBlocks` for the clicks (both Adventurer
bundle), plus build permission at the block.

**Switch:** `modules.command.cycleBlocks`, for the command and the clicks.

**Refused states.** A world's `disabledBlockStates` list names states the stick may not make; Architect answers
"Block state: … is not allowed." A new default file lists one, a blue stained glass pane with no sides:

```yaml
disabledBlockStates:
  - minecraft:blue_stained_glass_pane[east=false,north=false,south=false,west=false]
```

**Good to know**

- Until you turn the stick on with `/switchstick`, a click only says "The stick is disabled. You can turn it on with
  /switchstick."
- Where the switch is on, every left- or right-click on a block with a stick in hand is cancelled, whether or not you
  may edit there: the stick cannot press buttons or break blocks. Doors are the exception: a right-click opens or
  closes a door as usual, so the stick cannot change a door's properties (except on doors the world's
  `noInteraction` list names).
- The selected property is a position in a list that Architect keeps for you. On another kind of block it can point at
  another property: left-click once to check before you right-click.
- The stick cannot change chiseled bookshelf slots, a jukebox's record or a lectern's book, and it sets `Level` only
  from 1 to 3.
- On an item block a right-click steps to its previous texture instead, even with the stick off. Banners, paintings
  and armor stands have their own stick editors, which `/switchstick` does not affect. On signs and player heads the
  block-state stick also acts on the same click.

### The block picker (flint and /weselect)

Flint is the inspection tool. It shows a block's exact data, fills it into WorldEdit commands, and hands you the
matching MCME block. It needs no permission.

| With flint in your main hand | What happens |
|---|---|
| Left-click a block (or the air, for the block you look at) | Chat shows the block's position, material and each property. Click a line to put your left-click preset, filled in with the block's data, into the chat box. |
| Sneak and left-click blocks | Adds each block's data to a list, separated by commas. Nothing is shown yet. |
| Sneak and left-click the air | Shows the list as one clickable line, then clears it. With a sneak preset, a click puts that preset with the list into the chat box; without one, it copies the list to your clipboard. |
| Right-click a block (or the air) | Puts two of the block's MCME item, for your resource pack, into your hotbar; with no free hotbar slot, and the item not already there, you get nothing. On a player head you get the head. Sneak to open the build inventory that holds the block instead. |

**Pick block.** Middle-click (pick block) with anything but flint in your hand does the same lookup, up to 6 blocks
away. On a painting or item frame you get a painting, the framed item or the frame. Sneak to open the block's build
inventory; a sneaking pick also sees water and lava, and opens the build inventory that holds them. Chests, trapped
chests and ender chests are left to the game.

**Presets with `/weselect`** (alias `/wes`). A preset is a command, usually a WorldEdit one, that the flint's clickable
lines fill in.

| Command | What it does |
|---|---|
| `/weselect <command…>` | Sets the preset for a normal left-click. |
| `/weselect shift <command…>` | Sets the preset for a sneaking click. |
| `/weselect show` | Shows both presets. |
| `/weselect reset` | Clears both. |
| `/weselect [help [page]]` | Help. |

In a preset, `#` marks where the block data goes. Without `#`, the data is added at the end, after a space. Two
examples:

1. `/weselect //set #`, then left-click a block with flint and click a line. The chat box now holds
   `//set minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]`.
2. `/weselect shift //replace stone #`, then sneak and left-click three blocks, sneak and left-click the air, and click
   the line: `//replace stone oak_planks,spruce_planks,birch_planks`, a WorldEdit random mix.

**Permission:** `architect.weselect` (Artist bundle) for `/weselect`. The flint and pick block need none.

**Switch:** `modules.specialBlocks.flint`, for the flint, pick block and `/weselect`.

**Good to know**

- Presets last until the server restarts.
- A preset cannot start with `help`, `reset`, `show` or `shift`.
- With no preset, a click on a line puts only the block data, with a space in front, into the chat box.
- If Architect cannot tell which resource pack you have, you get the vanilla block, and a warning says so.

### Protection and access

These rules decide who may open which inventories, stop clicks from changing decorative blocks, and look after books,
hanging entities and items. Build permission means that TheGaffer lets you build at the spot (see
[Build permission](#build-permission)).

#### Inventory access

Each world decides, per type of inventory, who may open it. Where `modules.specialBlocks.inventoryAccess` is on, the
world's `inventoryAccess` list gives each type one of four values:

| Value | Who may open it |
|---|---|
| `TRUE` | Everyone. |
| `FALSE` | Nobody; the click does nothing. |
| `BUILDER` | Players with `architect.inventory.open` (Adventurer bundle) and build permission at the block. |
| `EXCEPTION` | As `BUILDER`, and only for block types a redstone exception area covers, inside such an area (see [No physics](#no-physics)). |

A new file sets `ANVIL`, `CHEST`, `CRAFTING`, `CREATIVE`, `ENDER_CHEST`, `PLAYER`, `WORKBENCH` and `MERCHANT` to
`TRUE`; `BREWING`, `FURNACE`, `SMOKER` and `BLAST_FURNACE` to `BUILDER`; `BEACON`, `DISPENSER`, `DROPPER`,
`ENCHANTING` and `HOPPER` to `EXCEPTION`; and shulker boxes to `TRUE`. A type that is not listed is added the first
time someone opens one: `SMOKER` and `BLAST_FURNACE` as `BUILDER`, any other type as `TRUE`. Files written before
3.2.1 may already list smokers and blast furnaces as `'TRUE'`: set them to `BUILDER` to protect them like furnaces. An
inventory with no place in the world can always be opened; minecart and animal inventories follow their type's rule.

```yaml
inventoryAccess:
  CHEST: 'TRUE'
  FURNACE: BUILDER
  HOPPER: EXCEPTION
  SHULKER_BOX:
    default: 'TRUE'
```

**Look, but don't take.** Where `modules.protection.chestInventory` is on, a player without build permission at a
storage container may open it and look, but cannot take, move, swap or put items. Storage containers are chests,
barrels, shulker boxes, hoppers, droppers, dispensers, crafters, furnaces, brewing stands, lecterns, storage
minecarts, chest boats and the inventories of mounts. In creative, copying such a container with its contents
(Ctrl + middle-click) is refused there too. Ender chests, workstations and villager trades work as in vanilla.
`architect.inventory.ignoreProtection` (op by default, in no bundle) lifts this.

**Good to know**

- Quote `'TRUE'` and `'FALSE'`. Unquoted, YAML reads them as true/false values: the rule then fails with an error in
  the log, and everyone may open that kind of container.
- Shulker boxes use only `SHULKER_BOX.default`; entries per colour are not read.

#### No-interaction list

Where `modules.specialBlocks.blockPlayerInteraction` is on, a right-click on a block that matches the world's
`noInteraction` list is cancelled for everyone, ops included. This stops a click from changing blocks that the MCME
packs use as decoration; change them with the stick instead. Each entry is a block state: the properties it lists must
match, and the others match anything.

A new file lists acacia, jungle, dark oak and spruce fence gates; birch fence gates with `powered=true`; acacia,
jungle, dark oak and spruce doors with `powered=false`; repeaters; and comparators.

```yaml
noInteraction:
  - minecraft:repeater
  - minecraft:birch_fence_gate[powered=true]
```

An entry the server cannot read is skipped, with a warning in the log.

#### Invisible blocks

Placing a barrier, light or structure void block needs `architect.invisibleBlocks` (op by default, in no bundle), in
every world; there is no switch. These blocks are invisible, so they are hard to find and remove, and the game-mode
switcher shows builders the creative inventory's Operator Items tab, which offers them. Blocks from the build
inventory, such as the packs' light block, are allowed. Without the permission you see "You can't place invisible
blocks (barrier, light, structure void)."

#### Lecterns

These rules are always on:

- **Take Book** gives you a copy of the book. The book stays on the lectern unless you have build permission there or
  `architect.lecternEditor` (op by default, in no bundle).
- With build permission or `architect.lecternEditor`, right-click an empty lectern with a written or writable book to
  put a copy on it. One book is taken from your hand unless you are in creative mode.
- To change a book and quill on a lectern, take it with Take Book (with build permission or `architect.lecternEditor`),
  edit it and put it back. A powered lectern does not open its book for editing in 3.2.0.

**Good to know:** a player without build permission gets the protection message when they click a lectern or take a
book, but still reads it or gets the copy.

#### Hanging entities

These rules cover paintings, item frames (glow item frames too) and leash knots.

- In every world, a player who breaks one needs `architect.hangingEntityEditor` (Adventurer bundle) and build
  permission.
- Where `modules.protection.hangingEntity` is on, they do not break from obstruction, physics or explosions, and mobs
  and other entities cannot break them. Placing a painting or item frame, right-clicking an item frame (to put in or
  turn its item) and hitting an item frame then need `architect.hangingEntityEditor` and build permission too.

#### Item protection

Three switches take items, or their powers, away. On a new install all three are on.

| Switch | Effect |
|---|---|
| `modules.environment.dropBlocking` | Items you drop are deleted at once. |
| `modules.protection.blockOpItems` | Items lose every enchantment and every attribute modifier when you pick them up, drop them, change hotbar slot, or click in or close an inventory. Nobody is exempt, ops included. |
| `modules.protection.removeContainerItemsOnBreak` | Breaking a container empties it first, so nothing drops. Jukeboxes and decorated pots vanish without dropping what they hold. |

**Good to know**

- `modules.protection.blockOpItems` is read from the server's first world only, and then applies in every world.
- The world setting `allowedEnchantments` has no effect in 3.2.0: its names never match, so every enchantment is
  removed. Enchanted books keep the enchantments stored in them.

---

## Editing tools

The stick is also the editing tool for armor stands, banners, signs and paintings. You choose what a click should do
with the tool's command, then click with the stick. Every click with the stick that changes something needs build permission at the spot.

### Armor stands

The armor stand editor poses, dresses, moves, scales and switches the features of armor stands. It also copies and
pastes stands and keeps them as files.

1. Choose a mode: `/armor <mode> [part]`.
2. Hold a stick and click the stand. A right-click steps forward or switches; a left-click steps back.
3. In the item modes, hold the item instead of a stick: a right-click puts one of it on the stand, a left-click takes
   the stand's item away.

| Mode | What a click on a stand does |
|---|---|
| `he` | Helmet: puts on or takes off the head item. |
| `h` | Main hand: puts in or takes out the item. |
| `o` | Off hand: puts in or takes out the item. |
| `mx`, `my`, `mz` | Moves the stand along x, y or z. |
| `mo` | Moves the stand left or right, as you see it. |
| `x`, `y`, `z` `[part]` | Rotates a part around the x, y or z axis. |
| `r [part]` | Rotates a part around the line you look along. |
| `t` | Turns the whole stand. |
| `str [part]` | Straightens a part; with no part, all parts. |
| `b` | Base plate on or off. |
| `a` | Arms on or off. |
| `si` | Small or normal size. |
| `sc` | Scale: a right-click makes the stand bigger, a left-click smaller. |
| `v` | Visible or invisible. |
| `g` | Gravity on or off. |
| `l` | Locks or unlocks the stand. |
| `c` | Copy, the starting mode: click a stand to copy it. |
| `p [file]` | Paste; with a file name, loads that saved stand first. |
| `ma` | Marker: turned off, it only answers "This mode is deactivated." |

Only the start of the word counts: `/armor head` selects helmet mode, `/armor all` arms mode and `/armor move` the
left-right move. Parts are `h` head (the default), `la` left arm, `ra` right arm, `ll` left leg, `rl` right leg, `b` body
and `all`; `/armor parts` lists them, except `all`.

**Step size.** `/armor <1-360>` sets it, `/armor +` and `/armor -` change it by one (not below 2), and `/armor default`
sets 10, the starting value. Rotations and turns step in degrees, moves in hundredths of a block (10 is 0.1 block) and
scale in per cent.

**Copy and paste.**

- In mode `c`, click a stand: "Armor stand copied to clipboard."
- In mode `p`, right-click a block: the copy stands on top of it, at the same spot within the block and turned as the
  original was. Left-click a block instead to place it exactly where you stand, facing your way.
- `/armor paste` places the copy at once, at the same offset from you as when you copied it.
- `/armor clear` empties your armor stand clipboard.

**Files.** Saved stands are kept in `plugins/MCME-Architect/armorStands/`. Names may contain folders, such as
`castle/guard`.

| Command | What it does |
|---|---|
| `/armor save <name> <description…>` | Saves the stand you copied with mode `c`. The description is required. |
| `/armor files [folder] [page]` | Lists saved stands; clicking one puts `/armor p <name>` in the chat box. |
| `/armor p <name>` | Loads a saved stand into your clipboard and selects paste mode. |
| `/armor rename <old> <new>` | Renames a file. |
| `/armor delete <name>` | Deletes a file, or an empty folder. |
| `/armor place` | Selects paste mode, as `/armor p` does. |

`/armor` alone shows your mode, and `/armor help [page]` shows the help. `/armor rollback` is unfinished and does
nothing useful yet.

**Permissions:**

- `architect.armorStandEditor` (Adventurer bundle): all modes, copy and paste, and listing and loading files.
- `architect.armorStandEditor.trusted` (Artist bundle): saving, renaming and deleting your own files, and
  `/armor place`.
- `architect.armorStandEditor.delete` (Head Designer bundle): renaming and deleting other players' files.

**Switches:**

- `modules.command.armorStandEditor`: the editor. In such a world every new armor stand also gets gravity off and is
  made invulnerable.
- `modules.protection.armorStand`: protection. Fishing rods cannot hook stands, stands do not burn, and mobs and arrows
  cannot damage them. Placing a stand, and hitting or right-clicking one, need `architect.armorStandEditor` and build
  permission.
- The world settings `armorStand.minScale` and `armorStand.maxScale` (0.0625 and 16 at first) set the limits of scale
  mode. Scale never goes below 0.0625 or above 16.

**Good to know**

- A locked stand refuses every edit: "This armor stand is locked! Use /armor l to unlock." Unlocking also makes it
  visible.
- Copies keep position, equipment, pose, arms, base plate, gravity, size and visibility, but not scale, lock, name or
  glow. Files keep the same, except the position: a stand loaded from a file is pasted in the middle of the block,
  facing south.
- In the item modes a left-click deletes the stand's item: you do not get it back.
- `/armor str b` straightens every part, not only the body, and rotating with part `all` turns only the head.
- `/armor save` needs a stand copied with mode `c`; a file loaded with `/armor p` does not count.

### Banners

The banner editor changes the patterns and colours of a placed banner, turns a banner into a shield or a banner item,
and keeps designs as files.

1. Choose a mode, and where needed a pattern number, with `/banner`.
2. Hold a stick and click the banner: a left-click steps forward, a right-click back.

| Mode | What a click on a banner does |
|---|---|
| `l` (list) | Lists the base colour and every pattern with its number and colour. |
| `t` (texture) | Steps pattern n through all pattern types. |
| `c` (colour) | Steps pattern n through the 16 dye colours. Pattern 0 changes the base colour. |
| `a` (add) | Adds a white circle pattern on top; change it with `t` and `c`. |
| `r` (remove) | Removes pattern n. |
| `s` (shield) | Gives you a shield with this design. |
| `g` (get) | Gives you 16 banners with this design. |
| `o` (orientation) | Nothing (see Good to know). |

| Command | What it does |
|---|---|
| `/banner` | Shows your mode. |
| `/banner <mode> [n]` | Selects a mode. Only the first letter counts. `n` also sets the pattern number. |
| `/banner <n>` | Selects pattern number n: 0 is the base, 1 the first pattern, and so on. |
| `/banner save <file> <description…>` | Saves the banner in your main hand. |
| `/banner load <file>` | Gives you 16 of a saved banner. |
| `/banner files [folder] [page]` | Lists saved banners with their descriptions; clicking one puts `/banner load <name>` in the chat box. |
| `/banner delete <file>` | Deletes a saved banner, or an empty folder. |
| `/banner help [page]` | Help. |

Saved banners are kept in `plugins/MCME-Architect/banners/`. Names may contain folders, such as `castle/door`.

**Permissions:** `architect.bannerEditor` (Adventurer bundle) for the commands and clicks, with build permission at the
banner. `architect.bannerEditor.save` (Artist bundle) to save. You may delete your own files;
`architect.bannerEditor.delete` (Head Designer bundle) lets you delete other players' files too.

**Switch:** `modules.command.bannerEditor`.

**Good to know**

- Orientation, the starting mode, does nothing in 3.2.0: the stick cannot turn a banner.
- The pattern number stays set. After a remove, the patterns above it move down one number.
- Any word works as a mode if its first letter fits: `/banner colour` selects colour mode.
- Your mode and pattern number are forgotten when the server restarts.

### Signs

The sign editor edits sign text in place, with colours, hex colours, Tengwar or Angerthas lettering and glowing text.

1. Hold a stick and left-click a sign: standing, wall or hanging. You edit the side you face.
2. Chat shows the four lines. Click one, and the chat box gets `/sign <n> <current text>`. Change the text after the
   number and send it.
3. Or type `/sign <1-4> <text…>` yourself. Click the last line of the menu to turn glowing text on or off.

| Command | What it does |
|---|---|
| `/sign <1-4> <text…>` | Sets that line on the selected side. |
| `/sign glow true\|false` | Turns glowing text on or off on the selected side. |

| Code | Effect |
|---|---|
| `&0` to `&9`, `&a` to `&f` | Colours. Text is black until you choose one. |
| `&k`, `&l`, `&m`, `&n`, `&o` | Obfuscated, bold, strikethrough, underlined, italic. |
| `&r` | Back to plain black text. |
| `#RRGGBB` | Any colour, as a hex code. Such text always becomes a colour. |
| `&T`, `&A`, `&L` | Tengwar, Angerthas, and back to Latin letters. Capitals only: `&L` is not `&l`. The letters show with an MCME resource pack that has these fonts. |
| `\_` | A space, for a leading or double space. |

**Permission:** `architect.signEditor` (Adventurer bundle), plus build permission to select a sign.

**Switch:** `modules.command.signEditor`.

**Good to know**

- `&3` gives dark red and `&4` dark aqua: the other way round from the game's own codes.
- To empty a line, send `/sign <n> &r`.
- A line longer than 15 characters (10 on a hanging sign; codes do not count) gets a warning that it may be cut off,
  but is written anyway.
- `/sign glow` gives no reply, and your selection is forgotten when you log out or the server restarts.

### Paintings and hanging entities

With a stick you can switch a placed painting to another motif without breaking it.

- Right-click a painting with a stick: the next motif.
- Left-click (punch) it with a stick: the previous motif.

The order ends at the first and the last motif; it does not wrap round.

**Permission:** `architect.paintingEditor` (Adventurer bundle), plus build permission.

**Switch:** `modules.command.paintingEditor`.

Breaking a painting, item frame or leash knot needs `architect.hangingEntityEditor`; see
[Hanging entities](#hanging-entities) for that and the other protection rules.

**Good to know**

- A motif that does not fit the space is not applied, and the next click tries it again: you cannot step past it.
  Punch to go back, or give the painting more room.
- Where the painting editor is off, anyone who breaks a painting gets "Painting editor is not enabled for this world."

---

## Copy, paste and shaping

Architect's own clipboard, the randomiser, and file browsers for VoxelSniper and WorldEdit.

### Copy and paste

Architect has a clipboard of its own, apart from WorldEdit's `//copy` and `//paste`. You copy or cut a cuboid WorldEdit
selection, turn or mirror it, and paste it at the same offset from where you stand. Pastes and cuts can be undone and
redone.

1. Select a cuboid with WorldEdit, for example with `//pos1` and `//pos2`.
2. Stand where you want the reference point and run `/copy`.
3. Go to the new place and stand at the matching spot. Turn or mirror the clipboard if you like, then run `/paste`.
4. Not right? Run `/undo`.

| Command | What it does |
|---|---|
| `/copy` (`/cp`) | Copies your selection. Architect remembers where you stand. |
| `/cut` (`/ct`) | Copies, then empties the box: removes paintings, item frames and armor stands, and sets every block to air without block updates. |
| `/paste [-a] [-b]` (`/pst`, `/ps`) | Pastes the clipboard at the same offset from you as when you copied it. `-a` leaves out the clipboard's air, so what stands there stays. `-b` leaves the biomes alone. Type the flags exactly, in any order. |
| `/undo [n]` (`/un`, `/ud`) | Undoes your last n pastes and cuts; 1 if you give no number. |
| `/redo [n]` (`/re`, `/rd`) | Redoes your last n undone edits. |
| `/rot [degrees]` (`/rt`) | Turns the clipboard clockwise, seen from above, in quarter turns; 90 if you give no number. Other numbers are rounded down to a quarter turn: `/rot 135` turns 90, `/rot 45` does nothing, `/rot -90` turns 270. |
| `/flip [x\|y\|z]` (`/flp`, `/fl`) | Mirrors the clipboard along an axis, given in lower case. With no axis, looking steeply up or down mirrors along y, facing east or west along x, and facing north or south along z. |

**What is copied:** every block with its contents (sign text, chest contents, banner and head data), the paintings,
item frames and armor stands in the box, and the biome of each column. Mobs, minecarts, display entities and other
entities are not copied. A paste first removes the paintings, item frames and armor stands already in the target box,
and sets the blocks without block updates.

**Your clipboard is saved** as `plugins/MCME-Architect/clipboards/<your UUID>.cbd` after each `/copy` and `/cut`, and
loaded again when you join. Turns and flips are not saved: after a rejoin or a restart, the clipboard is back as you
copied it. The undo history is kept in memory only, up to 30 steps per player; the `config.yml` key
`CopyPasteUndoLimit` changes that number at the next start.

**Permissions:**

- `architect.copypaste` (Designer bundle): all seven commands.
- `architect.copypaste.limit.<n>`: the most blocks you may `/copy` or `/cut` at once. The highest `<n>` you hold
  counts. It is in no bundle and not listed in plugin.yml: grant it with your permissions plugin.
- `architect.copypaste.unlimited` (op by default, in no bundle): no limit.

Without a limit node or `.unlimited`, you may copy 0 blocks: "…You are allowed to copy 0 blocks only." So designers need
a limit node as well. Take a limit away by removing its node.

**Switch:** `modules.command.copypaste`. `/copy` and `/cut` need WorldEdit on the server.

> [!WARNING]
> A big paste or undo runs all at once and can make the server lag: paste large areas in parts.

**Good to know**

- Biomes are pasted over the whole height of each column, and their colours may only show after you rejoin. Custom
  (datapack) biomes, such as the `mcme:` ones, are not pasted at all, and `/undo` cannot bring back a custom biome that
  a paste replaced. Use `-b` to keep the biomes as they are.
- A new paste does not clear your redo list: `/redo` after a newer paste can overwrite it.
- Just after you join, `/paste` may say you have no clipboard while it is still loading.
- WorldEdit has an `/undo` and `/redo` too. If a plain `/undo` reaches WorldEdit, use `/mcme-architect:undo` and
  `/mcme-architect:redo`.
- In `/architect help`, a click on `/copy`, `/cut`, `/paste`, `/undo`, `/redo` or `/flip` runs the command at once.

### The randomiser

`/random` gives blocks of chosen types around you a random value of one number property, such as the growth stage of
crops, so that fields and gardens look natural.

1. Choose the blocks: `/random material <block:Property> […]`, for example `wheat:Age`. Each entry must be on the
   allow-list exactly as written; `/random showAllowed` shows the list.
2. Choose the values: `/random range <min> <max>` (4 to 7 at first). This also resets the chances to equal shares.
3. If you like, weight them: `/random prob <p1> <p2> …`, one percentage per value, in order.
4. Set the radius: `/random radius <r>`, from 1 to 150 (100 at first). Keep it small.
5. Run `/random`. Every matching block within the radius of you gets a random value.

| Command | What it does |
|---|---|
| `/random` | Randomises the blocks around you. |
| `/random material <block:Property> […]` | Sets the block types, and the property to change. |
| `/random range <min> <max>` | Sets the values to use. |
| `/random prob <p1> […]` | Sets the chance of each value, in per cent. |
| `/random radius <r>` | Sets the radius. |
| `/random show` | Shows your settings. |
| `/random showAllowed` | Shows the allow-list. |
| `/random allow <block:Property> […]` | Adds entries to the allow-list. |
| `/random deny <block:Property> […]` | Removes entries from the allow-list. |
| `/random help [page]` | Help. |

You can use these properties: `Age`, `Bites`, `Moisture`, `Distance`, `Delay`, `Stage`, `Pickles`, `Layers`, `Eggs`,
`Hatch`, `Power`, `Level`, `Charges`, `HoneyLevel` and `Candles`.

**Permissions:** `architect.randomiser.user` (Designer bundle) for everything except `allow` and `deny`, which need
`architect.randomiser.allowMaterials` (Head Designer bundle).

**Switch:** `modules.command.randomiser`. The allow-list is kept in `plugins/MCME-Architect/randomiser.yml`, under the
key `allowed Materials`; Architect reads it only at startup.

> [!WARNING]
> `/random` cannot be undone. Keep the radius small: at the default radius of 100 it checks about four million blocks
> at once, which can make the server lag.

**Good to know**

- `/random prob` needs exactly one number per value. Too few gives "You're missing arguments for this command.", too
  many an internal error. If they add up to less than 100, the last value gets the rest; above 100, the excess comes
  off the last values.
- `/random range` with the larger number first ends in an internal error, unless the numbers are one apart: then every
  block gets the second number.
- A value the block does not allow leaves the block as it was and writes an error to the log.
- Allow-list entries are case-sensitive: `wheat:Age` and `WHEAT:Age` are different entries. The starting entry
  `wheat:Age` works even when it is not on the list.
- Your settings last until the server restarts.

### VoxelSniper stencils

`/vv` and `/sl` let you browse and delete VoxelSniper stencils and stencil lists from chat, and build stencil lists out
of existing stencils. A stencil (`.vstencil`) is a structure saved by VoxelSniper; a stencil list (`.txt`) lists
stencils for VoxelSniper's stencil-list brush. Architect only handles the files, in `plugins/VoxelSniper/stencils/` and
`plugins/VoxelSniper/stencilLists/`.

| Command | What it does |
|---|---|
| `/vv [list] [folder] [page]` | Lists stencil lists. Click a folder to open it; click a list to put `/b sl <name>` in the chat box. |
| `/vv stencil [folder] [page]` | Lists stencils; a click on one puts `/b st <name>` in the chat box. |
| `/vv delete <file>` | Deletes a stencil or a stencil list, or an empty folder in `stencilLists/`. Give the extension: `trees/oak.vstencil`, `forest.txt`. |
| `/vv help [page]` | Help. |

`/sl` works on one current list per player, kept until the server restarts:

| Command | What it does |
|---|---|
| `/sl create <list>` | Starts a new, empty list, not saved yet. The name may contain folders. |
| `/sl load <list>` | Loads `stencilLists/<list>.txt` as your current list. |
| `/sl add <pattern>` | Adds stencils from `stencils/`. `*` is a wildcard: `trees/*`, `trees/oak*`. Only that one folder is searched, not the folders inside it. |
| `/sl remove <pattern>` | Removes matching stencils from the list. |
| `/sl show` | Shows the current list. |
| `/sl save` | Saves the list to `stencilLists/<list>.txt`, after asking if the file exists. |
| `/sl [help [page]]` | Help. |

**Permissions:** `architect.voxel.viewer` (Designer bundle) for `/vv`; `architect.voxel.delete` (Head Designer bundle;
includes the viewer) for `/vv delete`; `architect.voxel.stencilListEditor` (Designer bundle) for `/sl`.

**Switches:** `modules.command.voxelViewer` for `/vv` and `modules.command.stencilListEditor` for `/sl`.

**Good to know**

- `/sl load` cannot read a list written by `/sl save`: it ends in an internal error. To change such a list, build it
  again with `/sl create` and save over the old one.
- `/sl save` takes no name: the list is saved under its create or load name.
- A folder name on its own adds nothing: use `/sl add trees/*`, not `/sl add trees`.
- A pattern works as a regular expression once `*` becomes `.*`, so `.`, `+` and `(` have special meanings.
- `/vv delete` says "File deleted." even when the file could not be deleted.

### WorldEdit schematics

`/schlist [folder] [page]` lists the schematic files in `plugins/WorldEdit/schematics/`, folder by folder, so you can
find the name to load with WorldEdit. Click a folder to open it.

**Permission:** `architect.weSchemViewer` (Designer bundle).

**Switch:** `modules.command.weSchemViewer`.

**Good to know**

- Clicking a file puts `null <name>` in the chat box. Replace `null` with WorldEdit's command, such as `//schem load`.
- The folder is fixed. If WorldEdit keeps its schematics elsewhere, `/schlist` does not follow.
- One file without a dot in its name breaks the whole listing.

---

## Custom heads

The custom head manager keeps a shared collection of decorative player heads. Players submit heads: their own skin,
another player's, or a head they hold. Head Designers review them and accept them into categories. Builders can then
take accepted heads with `/get head`, pick them from blocks with a stick or flint, browse them in the head inventory,
or look at them on the head gallery in the world.

**Permissions:** `architect.customHeadManager.user` (Adventurer bundle) for help, warp, list, submit and picking heads
with a stick; `architect.customHeadManager.manager` (Head Designer bundle) for all other subcommands. A manager needs
the user node as well; the Head Designer bundle holds both.

**Switch:** `modules.command.customHeadManager`, for `/chead` and for picking heads with a stick.

### Submitting and finding heads

| Command | What it does |
|---|---|
| `/chead [help [page]]` | Help, with the manager subcommands marked. |
| `/chead warp` | Teleports you to the head gallery. |
| `/chead list [folder] [page]` | Browses the accepted heads; folders are categories. Clicking a head puts `/get head <category/name>` in the chat box. |
| `/chead submit <headName>` | Submits your own skin as a head. |
| `/chead submit <player\|UUID> <headName>` | Submits that player's current skin, looked up at Mojang. |
| `/chead submit handitem <headName>` | Submits the player head in your main hand. |

To take an accepted head, use `/get head <category/name>`, or `/get head <category>` for a whole category (permission
`architect.get.head`, Adventurer bundle). Or hold a stick and click a player head that stands on a block (not one on
a wall) to get a copy: "Given head: <name>". A right-click with flint or a pick-block (middle-click) does the same.

### Reviewing heads

1. A player submits a head. It waits as `customHeads/submitted/<headName>.yml`; if that name is taken, a number is
   added (`name1`, `name2`).
2. A Head Designer runs `/chead reviewList`, clicks a head, and gets it with `/chead review <name>` to look at it.
3. They accept it into a category with `/chead accept <name> <category/newName>`, or throw it away with
   `/chead reject <name>`.

| Command | What it does |
|---|---|
| `/chead reviewList [folder] [page]` | Browses the submitted heads; clicking one puts `/chead review <name>` in the chat box. |
| `/chead review <name>` | Gives you the submitted head. |
| `/chead accept <name> <category/newName>` | Moves it into the collection as `customHeads/accepted/<category>/<newName>.yml`. |
| `/chead reject <name>` | Deletes a submitted head. |
| `/chead rename <old> <new>` | Renames an accepted head, or moves it to another category. |
| `/chead delete <name>` | Deletes an accepted head. |
| `/chead setCollection` | Builds the head gallery where you stand, or moves it there. |
| `/chead reload` | Reads the collection from its files again and rebuilds the gallery. |
| `/chead upload [file] [-r]` | Imports heads from a file on the web; see [Importing heads](#importing-heads). |

All head files are in `plugins/MCME-Architect/customHeads/`: `accepted/` holds the collection and `submitted/` the heads
waiting for review. Empty folders left behind are removed.

### Categories

A category is a folder under `customHeads/accepted/`, and a folder inside it is a sub-category. A head's name is its
path without `.yml`, such as `furniture/chair_oak`. Choose the category when you accept a head, or move a head later
with `/chead rename`. The head inventory (`/inv h`) has one tab per top-level category, with the heads of its
sub-categories in it; heads directly in `accepted/` are left out. It changes only after `/architect reload` or
`/inv reload`.

### The head gallery

The gallery shows the whole collection in the world. Each head sits on a white wool post, two blocks from the next,
with an oak sign below it that names its category and the head. Each category gets its own square of heads; the
squares run west from the gallery point, and within a square the heads run west and south. `/chead setCollection`
builds the gallery where you stand and saves the spot in `config.yml` under `headGalleryLocation` (do not edit it by
hand).

Architect rebuilds the gallery after `/chead reload`, `/architect reload`, and every accept, rename and delete. Building
replaces whatever stands there: choose an empty spot.

### Importing heads

`/chead upload [file] [-r]` downloads a text file from the web address in the `config.yml` key `CustomHeadUploadPath`,
followed by the file name (`upload.txt` if you give none). Each line holds one head as an old-style head tag; anything
before the first `{` is ignored:

```text
{display:{Name:"<category/name>"},SkullOwner:{Id:"<uuid>",Properties:{textures:[{Value:"<base64 texture>"}]}}}
```

The heads go into the collection or, with `-r`, into the review list. At the end Architect says how many heads it read
and how many lines it skipped. If the key is missing, the first upload adds it, pointing at a folder of the plugin's
original GitHub repository: set it to your own address.

**Good to know**

- After a server restart there is no gallery until someone runs `/chead reload` or `/architect reload`. Until then
  `/chead warp` says there is no collection, and accepting, renaming or deleting does not update the wall. Run
  `/chead reload` before `/chead setCollection`, or the old wall stays where it was.
- Every `/chead submit` except `handitem` asks Mojang, which allows only so many requests a minute; too many give
  "Wait one minute at least before you try again.", or "Player name not found." for a name lookup.
- `/chead upload` reports the upload before the download has finished, and one line without `{` stops the import.

---

## Seeing and moving

Tools that change what you see and how you move: full brightness, a larger view distance, block refreshes, the
game-mode switcher and the parrot remover.

### Full brightness

`/fbt` (aliases `/fullbright`, `/nightvision`) turns endless night vision on or off, so that you can build in the dark.

- If you have night vision, from any source, `/fbt` removes it: "Full brightness off!"
- If not, and the switch is on in your world, you get night vision that never runs out and shows no particles: "Full
  brightness on!"

**Permission:** `architect.fullBrightness` (Adventurer bundle).

**Switch:** `modules.command.fullBrightness`.

**Good to know**

- Moving into a world where the switch is off takes your night vision away: "Fullbright is not allowed in this world!"
  Logging in there does not.
- Dying clears it: run `/fbt` again.
- Admins: `/fbt` works only if `modules.command.fullBrightness` is on for the pseudo-world `server` when the server
  starts, that is in `WorldConfig/server.yml` or else in `defaultWorldConfig.yml`. Otherwise `/fbt` only shows its
  usage line and is missing from `/architect help`, and `/architect reload` does not change that.

### View distance

`/viewdistance` (alias `/viewdist`) keeps the chunks your client has received, so builds stay in sight as you move away
from them.

| Command | What it does |
|---|---|
| `/viewdistance <number>` | Sets your own view distance, up to 500. |
| `/viewdistance default` | Drops your own value: you get the world's again. |

**This affects every player in a world where the switch is on**, whether or not they use the command. Architect stops
the server from telling their clients to drop chunks, and tells each client a view distance: the player's own, or else
the world's `viewDistance` setting (255 unless changed). The server still sends only the chunks within its own view
distance; the client keeps what it has.

**Permission:** `architect.viewdistance` (Adventurer bundle).

**Switch:** `modules.command.viewdistance`. The world setting `viewDistance` is the value for players without one of
their own. Needs ProtocolLib on the server. Players' own values are kept in
`plugins/MCME-Architect/viewDistances.dat`.

**Good to know**

- Admins: do not edit `viewDistances.dat` by hand. One broken line stops Architect from starting or reloading.

### Chunk update

Because the server cancels many block updates, your client sometimes shows a block differently from the server: a fence
that seems connected, or a door half that is not really there. Architect fixes this by sending you the real blocks
again.

**Automatic updates.** Where `modules.chunkupdate.auto` is on, two ticks after you place, break or click a block,
Architect sends you that block again. For a place or break it adds the blocks above and below it, the other half of a door or
tall plant, and any connected run of walls, fences, glass panes or concrete powder (or a row of fence gates); a click
resends just that block. Only you get these updates. Clicks with the stick and the flint do the same.

| Command | What it does |
|---|---|
| `/chunkupdate auto <world>\|-default [true\|false]` | Turns automatic updates on (`true`) or off (anything else, or nothing) in a world, or in `defaultWorldConfig.yml`. It takes effect at once. |
| `/chunkupdate [radius]` | Does nothing in 3.2.0; it answers "Manual chunk updates are not available. Rejoin to reload your chunks." |

**Permissions:** `architect.chunkupdate` (Artist bundle) for `/chunkupdate`. `/chunkupdate auto` also needs
`architect.chunkupdate.auto`, which is in no bundle and defaults to op, so only ops have it unless you grant it.

**Switches:** `modules.chunkupdate.manual` must be on in your world before `/chunkupdate` does anything at all;
`modules.chunkupdate.auto` controls the automatic updates.

### Game-mode switcher

Builders who are not op can use the game's own game-mode switcher: F3+F4, and F3+N for spectator mode. Each mode needs
a permission of its own, and the switcher works only in worlds where its switch is on.

1. An admin grants one or more of the permissions below.
2. Press F3+F4 and choose a mode, as an op would.
3. A permitted switch shows the game's "Set own game mode" message, where the world's `sendCommandFeedback` game rule
   is on. A refused one says "You can't switch to <mode> mode here."

**Permissions:** `architect.gamemodeSwitcher.creative`, `.survival`, `.adventure` and `.spectator`, or
`architect.gamemodeSwitcher` for all four. They are off by default, even for ops, and in no rank bundle: grant them with
your permissions plugin. Ops keep the game's own switcher. Players with `minecraft.command.gamemode` also get the
switcher, and the server handles their choice itself.

**Switch:** `modules.environment.gamemodeSwitcher`.

**Requirements:** ProtocolLib. If Architect cannot listen for the switcher's request, its log says so and the switcher
stays off. Each switch and each refusal is written to Architect's log.

**Good to know**

- With only the spectator permission, a builder cannot leave spectator mode again. Always grant another mode with it.
- The switcher also shows builders the creative inventory's Operator Items tab. Its barrier, light and structure void
  blocks still need `architect.invisibleBlocks` (see [Invisible blocks](#invisible-blocks)); its command blocks,
  structure blocks, jigsaw blocks and debug stick stay op-only.
- If the switcher stops opening, rejoin, respawn or change world.

### Parrots

`/parrot` removes the parrots sitting on your shoulders and answers "Parrots removed.", also when there were none. It
needs no permission and has no switch.

---

## Resource packs

MCME builds with one resource pack for each part of Middle-earth, such as Human, Rohan or Mordor. Architect sends
each player the right pack. An area of a world can be a resource pack region: walk into it, and you get its pack. You
can also load a pack yourself, and choose which build of a pack you get. Staff draw the regions, release new versions
of a pack and put them live.

### Switching packs

`/rp` loads a pack now, tells you which pack your spot uses, and lists the pack shortcuts.

| Command | What it does |
|---|---|
| `/rp <pack> [-force]` | Loads the pack. `<pack>` is the start of the pack's name, in any case: `/rp h` and `/rp human` both load Human. With `-force`, Architect sends the pack again even if you already have it. |
| `/rp r`, `/rp h`, `/rp l`, `/rp p`, `/rp d`, `/rp m` | Shortcuts for Rohan, Human, Lothlorien, Paths of the Dead, Dwarven (Moria) and Mordor. `/rp g` and `/rp e` load Human too: Human replaced the Gondor and Eriador packs. |
| `/rp check` | Tells you which pack the region you stand in uses, or that there is none. |
| `/rp [help [page]]` | Lists the shortcuts and every `/rp` subcommand; the admin ones are marked. |

**Permission:** `architect.resourcePackSwitcher` (Adventurer bundle). `/rp check` needs no permission.

**Good to know**

- `/rp <pack>` does not turn automatic switching off, so the next region you enter may switch you back. Run
  `/rp auto off` first to keep your pack.
- Your client must accept server resource packs. In the multiplayer list, select the server, click Edit and set
  Server Resource Packs to Enabled. Otherwise you see "Resource pack loading failed. Did you enable server resource
  packs…".
- "You are already using rp <pack>" means you have that pack already. Add `-force` to load it again, for example
  after a failed download.
- "Resource pack not found" means no pack on this server starts with what you typed: a shortcut only works for packs
  the server has. "Missing url configuration" means the pack has no file for your settings: tell staff.
- If `/rp` answers that the switcher "is not activated for this world", `/rp` is switched off in that world (see
  [Per-world settings](#per-world-settings)). `/rp check` still works there, and regions still switch packs.

### Your pack settings

Most packs come in several builds. Your settings decide which build you get, and whether your pack changes as you move
from region to region. Architect keeps them in a database that all servers share, so they go with you when you switch
servers.

| Command | What it does |
|---|---|
| `/rp auto [off]` | `/rp auto` turns automatic switching on: entering a region loads its pack. `/rp auto off` (or `false`, in lower case) turns it off; any other word turns it on. |
| `/rp reset` | In a region: turns automatic switching on and loads the region's pack again. Outside every region it changes nothing. |
| `/rp client <type>` | Sets your client type, `vanilla`, `sodium` or `lite`, and loads your current pack in that build. |
| `/rp variant <name>` | Sets your variant, such as `light` or `footprints`, and loads your current pack in that variant. |

**Permission:** `architect.resourcePackSwitcher`.

New players start with automatic switching on, client type `vanilla`, variant `light` and resolution 16 px.

**Which file you get.** Architect picks the file in five steps:

1. **Pack:** your region's pack, or the one you loaded.
2. **Client type:** yours, if the pack has it. Otherwise the first client type the pack lists.
3. **Resolution:** `16px`, or else the first one listed. 3.2.0 has no command to change the resolution.
4. **Variant:** yours, if the pack has it. Otherwise the first one listed.
5. **Version:** the newest version of the pack that is not newer than your Minecraft version. A client older than all
   of them gets the oldest. Architect reads your version from your connection, or from ViaVersion when the server has
   it.

**When you join the network**, Architect does three things (this needs MCME-Connect on the server):

1. It sets your client type: `sodium` if you play with the MCME modpack, `vanilla` if your client is not Fabric. A
   plain Fabric client keeps your choice.
2. It sends your region's pack, if automatic switching is on and you stand in a region. Otherwise it sends the pack
   you had last time.
3. It gives a hint when needed. On Fabric without the `sodium` client type, you get a clickable fix that runs
   `/rp client sodium`. On Forge or OptiFine, you get a warning to switch mip-mapping off, a note that OptiFine
   shaders do not work with MCME packs, and a clickable guide to shaders.

When you switch servers, the new server knows which pack your client has and does not send it again. Once your client
has tried the pack, you see "Resource pack loaded successfully." or a message that the download or the loading failed.

**Good to know**

- Your client type is set again each time you join the network. `/rp client lite` on a vanilla client lasts until
  your next join.
- If a pack lacks your client type or variant, you get the first one the pack lists, as the reply says.
- While automatic switching is on, `/rp client` and `/rp variant` also load the pack of the region you stand in,
  which replaces a pack you loaded by hand. With it off they reload your own pack, unless you switched automatic
  switching off inside a region: then they load that region's pack, wherever you are.
- Just after you log in, your settings may still be loading. `/rp` then says "Your resource pack settings are still
  loading, try again in a moment."

### Resource pack regions

A resource pack region is a named area of one world with a pack and a weight. A player with automatic switching on who
walks into the region gets its pack. Where regions overlap, the region with the highest weight wins. Staff make regions
from a WorldEdit selection and fine-tune them in a chat editor.

To make a region:

1. Make a WorldEdit selection: a cuboid, polygon, cylinder or ellipsoid, because other shapes are lost at the next
   restart. A polygonal selection covers the full height of the world from Y 0 up; a cuboid covers only the heights
   you selected.
2. Run `/rp create <name>`. Architect saves the region and opens the region editor in chat.
3. Type `rp <pack>` to set the pack, and `weight <n>` to set the weight.
4. Type `quit`.

| Command | What it does |
|---|---|
| `/rp list [filter]` | Lists the regions, or those whose name contains `filter`. Click a name to edit that region. |
| `/rp create <name>` | Makes a region from your WorldEdit selection and opens the editor. |
| `/rp edit <name>` | Opens the editor for a region. |
| `/rp remove <name>` | Deletes a region. It gives no reply when it worked. |

In the editor, type these words in chat, without a `/`. What you type there does not reach public chat.

| Input | What it does |
|---|---|
| `rp <pack>` | Sets the pack; `<pack>` is the start of its name. |
| `weight <n>` | Sets the weight, a whole number above 0. |
| `info` | Shows the type and the borders: a cuboid's corners, or a polygon's heights and numbered points. |
| `setborder` | Replaces the shape with your current WorldEdit selection. |
| `setminy [y]`, `setmaxy [y]` | Sets a polygon's lower or upper border; your own Y if you leave `y` out. |
| `addpoint <i> [x z]` | Inserts a polygon point as number `i`; a number past the end adds it at the end. Uses your own position if you leave `x z` out. |
| `setpoint <i> [x z]` | Moves polygon point `i`. |
| `removepoint <i>` | Deletes polygon point `i`. |
| `name <newName>` | Renames the region. |
| `quit` | Closes the editor. |

Every change is saved at once and redrawn on the web map (see [Map layers](#map-layers)). The point and height commands
work on polygonal regions only. The editor closes by itself after 2 minutes without input.

**Permission:** `architect.resourcePackAdmin` (Head Designer bundle; it includes `architect.resourcePackSwitcher`), in
a world where `/rp` is switched on. The server needs WorldEdit.

**Good to know**

- Regions switch every player who has automatic switching on, in every world, whatever their permissions. Leaving all
  regions keeps the pack you have.
- A new region has weight 0 and no pack, so it does nothing until you set `rp`. Give overlapping regions different
  weights: when weights are equal, it is not defined which region wins.
- A polygon made with `/rp create` starts at Y 0, so it misses anything below that: use `setminy` for deep builds.
  `setborder` keeps the heights of your selection.
- Region names are case-sensitive, and `create`, `edit` and `remove` must be typed in lower case.
- `/rp list` only ever shows its first page. In a long list, add a filter word.

### Releasing a pack

A pack's files are built from its sources by a release script, which publishes them as a GitHub release. The scripts
live in a folder on the server and come from the ResourcePackScripts project (see [Setting up packs](#setting-up-packs)).
`/rp release` runs the pack's script and reports in chat how it goes. Players get nothing until `/rp server` points the
pack at the new release.

1. Run `/rp release <pack> <tag> <title…>`, for example `/rp release Human v4.1.3 Better leaves`. The title may have
   spaces.
2. Chat shows the start:
   `Releasing Human v4.1.3 ("Better leaves"). Last time this took about 5 minutes. The result comes here.`
   The middle sentence appears once this server has finished a release of that pack.
3. Chat reports each step: `Human: Sodium zips done`, `Human: Vanilla zips done`, `Human: Lite zips done` (or
   `Human: zips done` for a pack without Sodium builds), then `Human: publishing on GitHub…`.
4. The closing message gives the result: see the table below.
5. After a published release, a line such as `Put it live: [26.2] [26.3] [Open on GitHub]` follows. Each version
   button fills in `/rp server <pack> <tag> <version>` for you to send. `[Open on GitHub]` opens the release page. If
   the release names no Minecraft versions, a single `[/rp server …]` button fills in the command for you to finish.
6. Send `/rp server` for each Minecraft version the release is for.

| The closing message says | What it means |
|---|---|
| `<pack> <tag> is released.` or `… is released, with <n> warnings:` | Done. Up to three warnings follow, and where to read the rest. |
| `… is released, but its checks found …` | Published, but the release checks found errors or warnings. See that run on the dashboard. |
| `<pack>'s dynmap pack is built and in place.` | The dynmap texture build finished. It publishes nothing. |
| `… didn't start: another release is running.` | Wait until that release ends, then try again. |
| `… didn't start: the release system on this server needs an admin.` | The release setup on the server needs fixing. |
| `… failed.` or `… failed with exit code <n>.` | The script failed. Its last error line follows, or else its last line of output. |
| `… was stopped after <n> minutes, during …` | The script ran past its time limit and was stopped. Check the release on GitHub. |
| `… couldn't be started: …` | The server could not start the script, usually because its folder is missing. Ask an admin. |

If you leave the server before the end, the closing message waits for you: you see it when you next join that server,
under `While you were away:`. A restart loses it, but Architect's log keeps every line the script printed.

| Command | What it does |
|---|---|
| `/rp release <pack> <tag> <title…>` | Runs the pack's release script, as above. |
| `/rp server <pack> <tag> <mcVersion>` | Puts release `<tag>` live for one Minecraft version, written `26.2` or `26_2`. Architect downloads each file of the release, works out its SHA-1, and writes `url` and `sha` into the pack's slots for that version in `config.yml`. Tab completes the packs and versions. |
| `/rp server remove <pack> <mcVersion>` | Removes that version's slot from every variant of the pack. It refuses to remove the pack's last slot. |
| `/rp calcsha <pack> [all]` | Downloads the files again and writes their `sha`: the newest slot of each variant, or every slot with `all` (in lower case). |

The files `/rp server` takes from the release:

| The pack has a `sodium` client type | Files | Slots they fill |
|---|---|---|
| Yes | `<Pack>-Vanilla.zip`, `<Pack>-Vanilla-Footprints.zip`, `<Pack>-Sodium.zip`, `<Pack>-Sodium-Footprints.zip`, `<Pack>-Lite.zip`, `<Pack>-Lite-Footprints.zip` | `vanilla`, `sodium` and `lite`, each at `16px` in `light` and `footprints` |
| No | `<Pack>.zip`, `<Pack>-Footprints.zip` | `vanilla.16px.light` and `vanilla.16px.footprints` |

A file that cannot be downloaded leaves its slot as it was, and the reply names it:
`Variant <variant> of <Pack> was not set for MC <version> and keeps its url and sha: …`.

**Permission:** `architect.resourcePackAdmin` (Head Designer bundle). These commands also work from the console.

**Good to know**

- Nothing is pushed to players who are online. A player gets the new files the next time Architect sends them the
  pack, for example when they walk into another region or join the network. `/rp <pack> -force` gets them at once.
- Until a player has the new files, Architect no longer recognises their old ones. The F key and `/inv` may not find
  their pack, and a player who joins the network outside every region gets no pack. `/rp <pack> -force` fixes both.
- If several servers share one `config.yml`, the others see the change after `/architect reload`.
- Releases run only on Linux servers. Architect starts a second release even while one runs; the release system then
  refuses it ("another release is running").
- If files of a release were replaced under the same tag, run `/rp calcsha <pack> all`: clients check each file
  against its `sha`.

### Setting up packs

This part is for the server admin. Packs and their release settings live in Architect's `config.yml`, and the
Minecraft versions in `protocolVersions.yml` next to it. `/architect reload` applies changes to both, but a version
removed from `protocolVersions.yml` stays known until a restart. Database settings need a server restart.

**Packs: `ServerResourcePacks`.** One entry per pack, five levels deep: pack, client type, resolution, variant and
Minecraft version.

```yaml
ServerResourcePacks:
  Human:                # the pack's name: /rp and regions match its start
    vanilla:            # client type: vanilla, sodium or lite
      16px:             # resolution
        light:          # variant: light or footprints
          '26_2':       # Minecraft version: a key of protocolVersions.yml, in quotes
            url: https://github.com/<owner>/<repo>/releases/download/<tag>/Human-Vanilla.zip
            sha: <SHA-1 of the zip, 40 hex digits>
```

- `/rp server` writes the version slots of the variants `light` and `footprints` at `16px`. Keep any other variant up
  to date by hand.
- A pack name matches by its start, and the first match in the file wins. List `Mordor` before `Mordor2`, or
  `/rp Mordor` loads `Mordor2`.
- Put version keys in quotes: without them, YAML reads `1_18_1` as the number 1181.
- Keep to the five-level shape. An older shape, with `url` and `sha` right under the variant for every version, still
  works, and `/rp server` turns it into version slots.
- A slot without a valid `sha` is sent without one, so clients load it unchecked; the log says so once.
  `/rp calcsha <pack> all` fills it in.
- Keep pack URLs to 100 characters or fewer: the database keeps each player's current pack URL in a column of that
  size.

**Minecraft versions: `protocolVersions.yml`.** It maps each version key to its network protocol number, newest
first: `'26_2': 776`, `'26_1': 775`, and so on. `/rp server` accepts only these versions, and Architect never sends a
slot under another key. Architect writes this file only when it is missing, so an upgrade does not add new Minecraft
versions. Add the lines by hand, or delete the file so the new jar writes it, then run `/architect reload`.

**Player settings: `rpSettingsDatabase`.** The MySQL database that keeps players' settings for all servers.

| Key | What it is |
|---|---|
| `user`, `password` | The database account. |
| `dbName` | The database. JDBC options may follow after a `?`. |
| `ip`, `port` | Host and port: `localhost` and `3306` if left out. |

- Architect makes the table `architect_rp` itself. All servers that use the same database share it.
- With `xxx` as `user` or `dbName`, as shipped, Architect uses no database: players get the default settings at every
  join, and nothing is kept. To run without a database, keep that placeholder; do not delete the section.
- If the database does not answer, server start waits up to 10 seconds. Architect then tries again every minute, and
  players who join meanwhile get the default settings. Once the database answers again, those defaults are saved over
  the players' stored settings at their next pack change, so their choices are lost.

**Releases: `gitHubRpReleases`.** One entry per pack, under the pack's name as in `ServerResourcePacks`.

```yaml
gitHubRpReleases:
  Human:
    owner: <owner>        # GitHub owner of the pack's release repository
    repo: <repo>          # the repository the release is published in
    script: <script>.sh   # the release script: a file in path
    path: <folder>        # the folder on the server that holds the script
    timeoutMinutes: 30    # optional: stop the script after this many minutes (default 30)
```

- `/rp release` needs `owner`, `repo`, `script` and `path`. `/rp server` needs `owner` and `repo`, even for a pack you
  never release from this server: without them, every download fails.
- Architect runs `sh <script> <pack> <owner> <repo> <tag> <title>` in `path`, on Linux only. The scripts come from the
  ResourcePackScripts project, and an admin installs them in that folder. Architect does not install them; the `.sh`
  files inside its jar are old copies it never uses.
- A script named `releaseDynmap.sh` counts as the dynmap texture build: it publishes nothing, and chat offers no
  `/rp server` buttons.
- The shipped example has a key `separateSodium`, which nothing reads. `/rp server` takes six files when the pack has a
  `sodium` client type, and two otherwise.

> [!WARNING]
> Architect uses the `config.yml` inside its jar as defaults for keys your file leaves out. That file has example
> `gitHubRpReleases` entries for packs named `Human`, `Dwarf` and `Rohan`, which point at a test setup. If your
> `config.yml` lacks an entry, or a key of one, for a pack of that name, `/rp release` and `/rp server` use the example
> values. Give every pack a complete entry of its own.

**Other plugins.**

- **MCME-Connect** must be installed. Without it, Architect sends no pack when a player joins the network, never reads
  players' stored settings, and shows no download messages. With a database, it then saves default settings over
  players' stored ones. Region switching still works.
- **ViaVersion** is optional. With it, Architect knows a client's real Minecraft version.
- **WorldEdit** is needed for regions.

**Per world.** The world switch `modules.command.resourcePackSwitcher` (on by default; see
[Per-world settings](#per-world-settings)) turns off the player commands of `/rp` and the region commands in that
world. `/rp check`, the release commands and region switching keep working.

**Clearing all settings.** `/rp dropdb [confirm]` needs `architect.resourcePackAdmin` and also works from the console.

> [!WARNING]
> `/rp dropdb confirm` deletes the table `architect_rp`, with the stored settings of every player, for every server that
> uses the database. Architect then makes a new, empty table. Without `confirm`, the command only says what it would
> do.

**Good to know**

- After `/architect reload`, regions come back after about 10 seconds.
- Each region is a file: `plugins/MCME-Architect/regions/<name>.reg`.
- Error replies of `/rp` point to Architect's own log, in `plugins/MCME-Architect/logs/`.

---

## Biome tuning

Biome tuning changes a custom biome's colours and atmosphere while the server runs, without a restart: sky, fog,
water, grass and leaves, light, climate, music, ambient sounds and particles. You work in an editor window or with chat
commands. An edit is live on the server at once. Players see it once their view is refreshed, and a save writes it into
the datapack.

You can tune any biome that a datapack in folder form defines, in the primary world's `datapacks` folder. Vanilla
`minecraft:` biomes cannot be tuned, because clients use their own copy of them, but you can copy their look. A biome
in a zip pack must be unzipped into a folder first. A biome that two packs define is refused.

### The biome editor

Open the editor with `/biometune` for the biome at your feet, with `/biometune <namespace:id>` for another biome, or
with an `[Editor]` button in chat.

The home window, `Biome editor: <id>`, shows:

- the biome's label, and `● unsaved` or `✓ saved`;
- swatches of the sky, fog, water, grass and foliage colours, and a strip from sky to horizon;
- a button for each of the six sections;
- `Preview in world`, which refreshes your own view; `Publish…` and `Save`, if you have those permissions; `Undo`,
  `Revert…` and `New biome…`.

In a section window, click a colour to open the colour picker. Sliders, switches and choices take effect when you
click `Apply`, or `Apply + preview in world`. Only the inputs you changed are written. Choosing `(not set)` removes a
value.

The values you can tune. The name in brackets is the one `/biometune set` takes.

| Section | Value | What it changes |
|---|---|---|
| Sky & fog colours | Sky (`sky_color`) | Colour of the sky. |
| | Fog (`fog_color`) | Colour of the fog and the horizon. |
| | Clouds (`cloud_color`) | Colour and opacity of the clouds. |
| Distances | Fog start, Fog end (`fog_start`, `fog_end`) | Where the fog starts and where it is thickest, in blocks. |
| | Sky fog end (`sky_fog_end`) | Fog distance for the sky. |
| | Cloud fog end (`cloud_fog_end`) | Fog distance for the clouds. |
| | Water fog start, Water fog end (`water_fog_start`, `water_fog_end`) | Fog distances under water. |
| | Cloud height (`cloud_height`) | Height of the clouds. |
| Water & vegetation | Water (`water_color`) | Colour of water. Every biome needs one, so it cannot be cleared. |
| | Water fog (`water_fog_color`) | Colour of the fog under water. |
| | Grass, Foliage, Dry foliage (`grass_color`, `foliage_color`, `dry_foliage_color`) | Fixed colours. Clear one to let the climate decide that colour again. |
| | Grass colour modifier (`grass_modifier`) | `none`, `dark_forest` or `swamp`. |
| Light & celestial | Sky light, Ambient light (`sky_light_color`, `ambient_light_color`) | Colour of the sky light and of the ambient light. |
| | Block light tint (`block_light_tint`) | Tint of the light from torches and other light blocks. |
| | Night vision (`night_vision_color`) | Colour used for night vision. |
| | Sky light factor (`sky_light_factor`) | Strength of the sky light, 0 to 100 %. |
| | Star brightness (at least) (`star_brightness`) | Lowest brightness of the stars, 0 to 100 %. |
| Climate | Temperature (`temperature`) | -1 to 2; the slider shows it times 100. |
| | Downfall (`downfall`) | 0 to 100 %. |
| | Precipitation (`precipitation`) | Whether rain or snow falls. |
| | Temperature modifier (`temperature_modifier`) | `none` or `frozen`. |
| Audio & particles | Music, Ambient sounds (`music`, `ambient_sounds`) | Background music and ambient sounds, as JSON. |
| | Ambient particles, Dripstone particle (`ambient_particles`, `dripstone_particle`) | Particles in the air, and the drip from dripstone, as JSON. |
| | Music volume (`music_volume`) | 0 to 100 %. |
| | Firefly bush sounds (also by day) (`firefly_bush_sounds`) | On or off. |

Five sky values are not in the editor and work only through `/biometune set`: `sunrise_color`, `sun_angle`,
`moon_angle`, `star_angle` and `moon_phase`. The Overworld's day and moon cycle overrides them, so they only show in
other dimensions.

**The colour picker** has sliders for `Hue`, `Saturation` and `Brightness`, plus `Alpha (opacity)` for clouds, and a
hex box that overrides the sliders. `Show` shows the new colour in the window only. `Apply` makes it live.
`Copy from biome…` takes the colour from another biome, vanilla biomes included. `Clear` removes the value, so the
default or the climate's colour applies.

**Music, sounds and particles** are edited as raw JSON in 3.2.0. The JSON editor shows the live value in a text box.
The game checks your JSON when you click `Apply`, and `Clear` removes the value. In the Audio & particles section,
`Copy from biome…` copies the music and sounds, the particles, or both, from another biome, such as
`minecraft:basalt_deltas`. Values that biome leaves unset are removed here too.

**Permission:** `architect.biomeTune`.

**Good to know**

- If someone else changed a value while your window was open, `Apply` writes nothing and says what changed. Your
  values stay on screen as `Not applied yet` lines: check them, then apply again.
- A value the editor cannot show, such as a number outside a slider's range, appears as text ("change it with
  /biometune set"). The window never overwrites it.
- Climate changes the world itself at once: where it is cold enough, rain falls as snow, and snow and ice can form.
- A window closes after 30 minutes. `Close` or Esc closes it sooner.
- A JSON value longer than 8192 characters can only be changed with `/biometune set` from the console.

### Live, saved and published

An edit goes through three stages.

| Stage | What it means | How you get there |
|---|---|---|
| Live | The server's biome has your values. Effects on the server, such as snow in a colder climate, start at once. | Any edit: `Apply`, `set`, `copy`, `undo`, `revert`. |
| Seen | A player's client has loaded the live values. | A refresh: `Preview in world` for you, `Publish…` for others. Players who join or come back to the server see them too. |
| Saved | The biome's file in the datapack holds the values, so they survive a restart. | `Save` or `/biometune save <biome>`. |

Unsaved edits live only in the server's memory, until the next restart. `/biometune status` and the home window's
`● unsaved` show which biomes have them.

**A refresh** makes a client load the server's biomes again. The player sees a short loading screen and is back in the
world after 1 to 3 seconds; an editor window they had open comes back. To the server, a refresh is a quit and a rejoin,
but Architect hides the quit and join messages. A refresh that has not finished after 15 seconds ends with the
disconnect message `Biome refresh did not finish - please rejoin.`

- **Preview** refreshes your own view, at most once every 5 seconds.
- **Publish** refreshes everyone in your world, or everyone on the server, 5 players per second. Each player first
  sees `Biome colours were updated by <name> - refreshing your view...`. The next publish on the server can start 30
  seconds later.

These times are the defaults; admins can change them (see Settings below).

**Save** writes the biome's own file, `data/<namespace>/worldgen/biome/<name>.json`, in the datapack that defines it.
First Architect keeps the old file as a backup in `plugins/MCME-Architect/biomeTuning/backups/`, up to 20 per biome.
Save refuses if the file was changed on disk since Architect loaded it. Then revert to load that version first, which
drops your unsaved edits.

**Undo** steps back one change, such as one `set`, one `copy` or one `Apply`. It keeps 20 steps per biome, also
across a save. **Revert** goes back to the saved file and clears the undo steps.

**Good to know**

- A restart loses every unsaved edit, so save first. Architect's log lists the unsaved biomes at shutdown.
- Edits change this server only. Other servers keep their own biomes.
- If a plugin on the server listens to Paper's old login event, a refresh can be refused: "Paper refused to
  reconfigure <name>…". Rejoin to see the live values.
- Only colours, atmosphere and climate go live. Changes to features, carvers or mob spawns made by hand in a biome file
  need a restart.
- Save rewrites the whole file in a standard JSON layout. Values that biome tuning does not handle are kept.

### Commands

`<biome>` is a biome id such as `mcme:custom_001`, or `here` for the biome at your feet. Ids are read in lower case,
and an id without a namespace means `minecraft:`.

| Command | What it does | Permission |
|---|---|---|
| `/biometune [here]`, `/biometune <namespace:id>`, `/biometune edit [biome]` | Opens the editor, on the biome at your feet if you name none. | `architect.biomeTune` |
| `/biometune info [biome]` | Shows the label, whether the biome is saved, its file, the values it sets and an `[Editor]` button. | `architect.biomeTune` |
| `/biometune set <biome> <property> <value…>` | Sets one value, live. | `architect.biomeTune` |
| `/biometune unset <biome> <property>` | Removes a value, so the default applies. | `architect.biomeTune` |
| `/biometune copy <from> <to> [group…]` | Copies groups of values from one biome to another as one undo step; every group if you name none. `<from>` may be vanilla. | `architect.biomeTune` |
| `/biometune undo <biome>` | Steps back one change. | `architect.biomeTune` |
| `/biometune revert <biome>` | Goes back to the saved file. | `architect.biomeTune` |
| `/biometune status` | Lists the biomes with unsaved edits, with `[Save]`, `[Revert]` and `[Info]` buttons. | `architect.biomeTune` |
| `/biometune preview` | Refreshes your own view. | `architect.biomeTune` |
| `/biometune spares` | Shows how many spare biomes are claimed and free, and lists the claimed ones. | `architect.biomeTune` |
| `/biometune claim <label…> [from <biome>]` | Claims the next free spare biome; see [New biomes](#new-biomes). | `architect.biomeTune` |
| `/biometune label <biome> <label…>` | Gives a biome a label. | `architect.biomeTune` |
| `/biometune save <biome>` | Writes the biome to its datapack file. | `architect.biomeTune.save` |
| `/biometune preview <player>` | Refreshes another player's view. | `architect.biomeTune.publish` |
| `/biometune publish [world\|server]` | Refreshes everyone in your world, or everyone on the server. | `architect.biomeTune.publish` |
| `/biometune spares add <count>` | Adds 1 to 100 spare biomes; they exist after the next restart. | `architect.biomeTune.admin` |
| `/biometune selftest [biome]` | Checks that biome tuning works on this server; with a biome, also tries a round trip on it. | `architect.biomeTune.admin` |
| `/biometune debug refresh <player> noinject\|bypass`, `/biometune debug legacylogin on\|off` | Test refreshes, for troubleshooting with a developer. | `architect.biomeTune.admin` |

From the console, name the biome, and use `/biometune preview <player>` and `/biometune publish server`. The editor
opens only in the game.

How to write a `<value>` for `set`:

| Kind | How to write it |
|---|---|
| Colour | `#rrggbb` or `#rgb`, with or without the `#`. |
| Colour with opacity (`cloud_color`, `sunrise_color`) | `#aarrggbb`, opacity first. Six digits mean fully opaque. |
| Number | A decimal number, such as `0.85`. |
| On or off | `true`, `on` or `yes`; `false`, `off` or `no`. |
| Choice | One of the listed choices. Tab completion offers them. |
| JSON (`music`, `ambient_sounds`, `ambient_particles`, `dripstone_particle`) | Valid JSON. |

- The game checks every change with its own biome rules before it goes live. If it refuses, nothing changes and the
  reply says why (`invalid biome: …`).
- `<property>` may also be a full attribute id, such as `minecraft:visual/fog_color`. An attribute that is neither in the
  editor's list nor one of the five sky values that only `set` takes is set as JSON.
- `set`, `unset` and `copy` answer with a line per change: the old and the new value, and the buttons
  `[Preview] [Publish] [Save] [Undo] [Revert]`.

The groups of `copy` are `sky` (sky, sunrise and cloud colours, cloud height, the light values, stars, sun and moon),
`fog` (fog colour and fog distances, apart from water fog), `water` (water and water fog, grass, foliage and dry foliage
colours, grass colour modifier), `climate`, `audio` (music, music volume, ambient sounds, firefly bush sounds),
`particles`, and `all`.

**Permissions:** the four nodes above all default to op. They are not in `architect.*` or in the rank bundles, so give
them by name.

**Good to know**

- `/biometune <biome>` without `edit` only works with a namespaced id, one with a colon. `/biometune plains` answers
  "Invalid subcommand."
- `/biometune publish` followed by any word other than `server` publishes to your world.
- The `[Publish]` and `[Save]` buttons in chat show even without those permissions. Clicking them then says you have
  no permission.
- If biome tuning cannot run on the server's Paper build, every subcommand answers "Biome tuning is unavailable on
  this server version: …".

### New biomes

A new look needs a biome of its own, and the game only learns new biomes at a server restart. So the server keeps spare
biomes: ready-made, neutral biomes with ids such as `mcme:custom_001`. A builder claims one, gives it a label and tunes
it, all without a restart.

1. Run `/biometune spares` to see how many spares are free, and which one is next.
2. Claim one with `/biometune claim <label…> [from <biome>]`, or with `New biome…` in the editor. With `from`, the
   spare takes that biome's look (vanilla biomes too) as a live, unsaved edit. Without it, the spare looks like plains.
   `New biome…` asks whether to start from the open biome's look or from neutral plains.
3. Tune the new biome: `New biome…` opens it in the editor; after `/biometune claim`, click `[Editor]` in the reply.
   Then save it.
4. Paint it into the world with WorldEdit: `//setbiome <spare>`.

**Labels** name biomes for people: `/biometune label <biome> <label…>`. A label is 1 to 32 characters on one line,
without `§`. Labels show in the editor and in `info`, `status` and `spares`. Every tunable biome can have one.

**Adding spares** is for admins: `/biometune spares add <count>` writes 1 to 100 new spare biome files into the target
pack. They exist after the next server restart. A spare has the climate and colours of plains and no features,
carvers or mobs, so it is safe to paint anywhere.

**Claims and labels** are kept in `mcme-biometune.json`, at the root of the target pack. The target pack is the pack
folder that `biomeTuning.targetPack` names (`bukkit` by default) in the world's `datapacks` folder. The file travels
with the pack.

**Good to know**

- A claim is for good: there is no command to undo it. To free a spare, an admin edits `mcme-biometune.json` by hand.
  The freed spare keeps whatever its file holds, so the next builder gets that look.
- Paint only with a spare you have claimed: a free spare later gets someone else's look. Free spares are locked, so
  nobody can change or label one before claiming it.
- The target pack folder must already exist in the world's `datapacks` folder, with a `pack.mcmeta`. Paper 26.2 no
  longer makes the `bukkit` pack. Without the folder, labels, claims and `spares add` fail.
- A new `targetPack` frees every claimed spare, unless `mcme-biometune.json` moves with the biomes.
- If `mcme-biometune.json` cannot be read, labels, claims, new spares, and all edits and saves of spare biomes
  (claimed ones too) wait until someone fixes it by hand. Do not delete it: that frees every claim.

### Settings

The `biomeTuning` section of `config.yml`. Architect reads it only when the server starts, so `/architect reload` does
not apply changes: restart the server.

| Key | Default | What it does |
|---|---|---|
| `undoDepth` | `20` | Undo steps kept per biome. |
| `backupsPerBiome` | `20` | Backups kept per biome file. |
| `sparePattern` | `'mcme:custom_###'` | Ids of spare biomes: a namespace, a colon, a prefix, then one `#` per digit (1 to 9 of them). Numbering starts at 1. |
| `targetPack` | `bukkit` | The pack folder, in the world's `datapacks` folder, for new spares and `mcme-biometune.json`. |
| `refresh.cooldownSeconds` | `5` | Seconds between two previews of the same player. |
| `refresh.timeoutSeconds` | `15` | A refresh not finished after this many seconds ends with the "please rejoin" disconnect. |
| `refresh.publishPlayersPerSecond` | `5` | Players a publish refreshes per second. |
| `refresh.publishCooldownSeconds` | `30` | Seconds before the next publish on the server. |

An invalid `sparePattern` or `targetPack` logs a warning, and the default applies. A missing, zero or negative number
uses its default.

Biome tuning writes its log lines (saves, claims, labels, refreshes) to Architect's own log file in
`plugins/MCME-Architect/logs/`, not to the server console, unless `logging.file.alsoServerLog` is `true`. When the
editor says "see Architect's log", look in that file.

---

## Map layers

Architect draws four layers of its own on the dynmap web map, so staff can see where its invisible rules apply. Every
layer starts hidden: switch it on in the map's layer list.

| Layer in the list | What it shows | Its popup |
|---|---|---|
| `RpRegions` | Resource pack regions, in purple. | Name, pack, weight and heights. |
| `itemBlockLimit` | Item-block limit regions, in red. | Name, item blocks allowed per chunk, and heights. |
| `No-physics exceptions` | No-physics exception areas: redstone areas in orange, water areas in blue. | Name, kind of area, heights, and which blocks keep their physics there. |
| `Item-block budget` | Chunks filling up against their item-block limit: yellow from 50 %, orange from 80 %, red when full. | The armor stands, item frames and paintings in the chunk against its limit, where the limit comes from, and when the count last changed. |

The map draws areas flat; the popup gives their heights. Polygons follow their points, cylinders and ellipsoids show
as ellipses, and anything else as a box.

The item-block budget counts every armor stand, item frame (glow item frames too) and painting in a chunk, not only
Architect's item blocks. A chunk's limit is read at its centre: the highest limit of the item-block regions over that
spot, at any height, or else the world's base limit. Changes reach the map within about 10 seconds. The counts are kept
in `plugins/MCME-Architect/mapLayers/itemBlockBudget.yml`.

### Refreshing the layers

The layers redraw by themselves when their data changes, at `/architect reload`, when dynmap starts again, and when a
world is loaded.

| Command | What it does |
|---|---|
| `/architect maplayers refresh` | Gathers every layer's data again, recounts the loaded chunks, looks up dynmap afresh and redraws every layer. |

**Permission:** `architect.maplayers` (Head Designer bundle), or the console.

It answers `Architect's map layers are drawn again, and loaded chunks recounted.`, or says that dynmap is not enabled
or that the map layers are not running.

### Map layer settings

The `mapLayers` section of `config.yml`. Every key is optional; the shipped file has the section commented out. A
layer can be a block of keys, or just `true` or `false` to switch it while keeping its other defaults. Colours are
written `'#rrggbb'`.

| Layer | Keys and defaults |
|---|---|
| `mapLayers.rpRegions` | `enabled: true`, `hidden: true`, `color: '#800080'`, `borderWidth: 2`, `borderOpacity: 0.15`, `areaOpacity: 0.25` |
| `mapLayers.itemBlockLimit` | The same keys, with `color: '#ff0000'`. |
| `mapLayers.noPhysics` | `enabled: true`, `hidden: true`, `redstoneColor: '#ff8c00'`, `waterColor: '#1e64ff'`, `borderWidth: 2`, `borderOpacity: 0.3`, `areaOpacity: 0.2` |
| `mapLayers.itemBlockBudget` | `enabled: true`, `hidden: true`, `warnPercent: 50`, `highPercent: 80`, `warnColor: '#ffd700'`, `highColor: '#ff8c00'`, `fullColor: '#ff0000'`, `areaOpacity: 0.35` |

`enabled` draws the layer. `hidden` keeps it switched off in the layer list until a viewer turns it on. `borderWidth`,
`borderOpacity` and `areaOpacity` (0 to 1) style the lines and the fill. A chunk shows on the budget layer from
`warnPercent` of its limit (1 to 100), and turns orange from `highPercent`.

**The older `dynmap` section.** Until `rpRegions` or `itemBlockLimit` has an entry of its own under `mapLayers`, that
layer reads the `dynmap` section instead: `dynmap.enabled` (off unless `true`), `dynmap.hide` (default `true`),
`dynmap.borderColor` and `dynmap.areaColor` (Bukkit colour objects), `dynmap.borderWidth`, `dynmap.borderOpacity` and
`dynmap.areaOpacity`. The other two layers never read it.

`/architect reload` applies changes to both sections.

### Without dynmap

dynmap is optional. Without it, Architect starts as normal, draws nothing, and logs
`dynmap is not enabled, so Architect's map layers are not drawn.` The budget layer still counts entities and saves its
file; on a server without a web map, `itemBlockBudget: false` under `mapLayers` saves that work. If drawing fails, Architect switches all its
layers off with one warning, until `/architect maplayers refresh`, or until dynmap or Architect starts again.

**Good to know**

- `dynmap.enabled` is not a master switch. It covers only the resource pack region and item-block limit layers; the
  other two are on by default.
- A `config.yml` with no `dynmap` section, and no `mapLayers` entries for those two layers, leaves them off.
- The budget tiles are a guide. The limit is read at the chunk's centre, while placing checks the exact spot, and
  players with `architect.itemBlock.region.ignore` can place in a full chunk.
- An armor stand moved from one loaded chunk to another, for example with `/tp` or the `/armor` editor, is not
  noticed. Both counts stay off by one until the chunks are counted again, for example by
  `/architect maplayers refresh`.
- Markers edited by hand in dynmap stay as edited until `/architect maplayers refresh`, `/architect reload`, a dynmap
  restart or a change to that region redraws them. The entity logger's own `Entities` layer is separate, and the
  command does not touch it.

---

## Running Architect

This chapter is for server admins: what Architect needs, how to install and upgrade it, its settings and files, where
it logs, how to reload it, and the `/architect` command.

### Requirements

**Required**

| Software | Version | What Architect needs it for |
|---|---|---|
| Paper | 26.2 | Architect is a Paper plugin for Minecraft 26.2 (`api-version: 26.2`). |
| Java | 25 | The jar is compiled for Java 25, the Java that Paper 26.2 runs on. |
| PluginUtils | 2.0.5 or later | MCME's shared plugin library: chat messages and clickable lists, WorldEdit selections, number and file helpers, and a second debug channel. plugin.yml declares it as a hard dependency, so the server does not enable Architect without it. Architect is built against 2.0.4, but on Minecraft 26.2 clickable chat lines (help pages, lists, buttons) work only from 2.0.5. |
| WorldEdit | 7.4.5, the version Architect is built against | Selections for `/copy`, `/cut`, `/nophy exception`, `/rp create` and `/itemblock create`, and the shapes of RP regions and item block regions. plugin.yml does not declare WorldEdit, so the server enables Architect without it. Treat it as required all the same: without it those commands answer `This feature needs WorldEdit, which is not installed on this server.`, and Architect cannot load the saved RP regions and item block regions. No-physics exception areas still load and work. |

**Optional**

| Plugin or service | What it adds | Without it |
|---|---|---|
| ProtocolLib 5.5.0 or later (a development build for Minecraft 26.x) | `/viewdistance`: Architect tells clients a view distance and stops them dropping chunks. The F3+F4 game mode switcher for builders who are not op. | Both features are off; `/viewdistance` still runs but fails with an internal error. Architect logs two warnings at start that begin with `ProtocolLib not found`. |
| dynmap | Architect's [map layers](#map-layers), and the entity logger's map markers. | Nothing is drawn, and Architect logs `dynmap is not enabled, so Architect's map layers are not drawn.` Architect picks dynmap up if it enables later. |
| ViaVersion | Each client's real Minecraft version, so the player gets the resource pack built for that version. | Architect uses the protocol version that Paper reports for the client. |
| MCME-Connect (Architect is built against 3.0.1) | Resource pack handling when a player joins the network: loading their pack settings, sending their pack, tracking whether the client loaded it, and clean-up when they leave. | Architect's resource pack listener cannot register, because it uses MCME-Connect's join event. None of the above happens, and with a database, players' stored settings get overwritten with defaults. RP regions still switch packs. |
| TheGaffer (3.0.0 or later for job statistics) | Build permission: before many edits, Architect asks TheGaffer whether the player may build at that spot. TheGaffer 3.0.0 and later also count the blocks Architect places in the job statistics. | Architect does not limit where players build, and records no statistics. |
| A MySQL database | Players' resource pack settings (automatic switching, client type, resolution, variant, current pack and its status) in the table `architect_rp`, shared by every server that uses the database. Set it up in `rpSettingsDatabase`. | With the shipped placeholders (`xxx`), players get the default settings at every join, and nothing is saved. |
| VoxelSniper | Its stencil folders, which `/vv` and `/sl` list and edit. | Those commands find no files. |
| Multiverse | `/architect world` suggests its `/mvtp` command. | The suggestion does nothing. |

- Architect does not bundle a MySQL JDBC driver: the server or another plugin must provide one. If the database does
  not answer, server start waits up to 10 seconds, and Architect tries again every minute. Players who join meanwhile
  get default settings, which are later saved over their stored ones. Changes to `rpSettingsDatabase` need a restart.
- plugin.yml also lists Essentials as a soft dependency. Architect does not use it.

### Installing and upgrading

#### Getting the jar

- **GitHub:** the releases of `MCME/MCME-Architect` on github.com. Release `v3.2.0` has `MCME-Architect-3.2.0.jar` and
  `MCME-Architect-3.2.0.jar.sha256`. Compare the jar's SHA-256 checksum with the `.sha256` file before you install it.
- **Maven repository:** `com.mcmiddleearth:MCME-Architect:3.2.0` in `https://repo.mcmiddleearth.com/releases`.

Jars built from source carry the build type and the commit in their name, for example
`MCME-Architect-3.2.0-DEV-<commit>.jar`. `/architect version` shows only `3.2.0`, so the file name is the only way to
tell such builds apart.

#### First start

1. Stop the server. Put the jar into `plugins/`, together with PluginUtils, WorldEdit and the optional plugins you want.
2. Start the server. Architect creates `plugins/MCME-Architect/` with `config.yml`, `WorldConfig/defaultWorldConfig.yml`,
   `WorldConfig/server.yml`, a `WorldConfig/<world>.yml` for each world as it loads, `protocolVersions.yml`, the log
   folder `logs/` and empty data folders.
3. Read Architect's log, `plugins/MCME-Architect/logs/architect_<start time>_0.log`. On a first start these lines are
   normal:
   - `Missing default world config file, creating defaultWorldConfig.yml`;
   - `View distances file not found (expected on first run)`;
   - a note that `rpSettingsDatabase` still holds the placeholders.
4. Stop the server, and set Architect up before builders log in:
   - `WorldConfig/defaultWorldConfig.yml` has every switch on. Review it (see [Per-world settings](#per-world-settings)).
   - `config.yml` holds examples. Replace the example pack under `ServerResourcePacks`, fill in `rpSettingsDatabase`
     (or keep `xxx` for no database), and replace the example entries under `gitHubRpReleases` and
     `inventoryDownload`, which point at example repositories and folders.
   - Biome tuning: the pack folder named in `biomeTuning.targetPack` (default `bukkit`) must exist in the main world's
     `datapacks/` folder, with a `pack.mcmeta`.
   - Build inventories: Architect does not create a folder per pack under `inventories/block/`; see
     [Blocks and inventories](#blocks-and-inventories).
   - Give your ranks their permission bundles; see [Permissions](#permissions).
5. Start the server again.

#### Upgrading

1. Back up `plugins/MCME-Architect/`.
2. Stop the server. Replace the old jar with the new one: keep only one Architect jar in `plugins/`.
3. Start the server, then check:
   - `/architect version` in the console shows the new version.
   - Architect's newest log in `plugins/MCME-Architect/logs/` shows no new warnings or errors.
   - `config.yml`: Architect never adds new keys to an existing file. A missing key uses its default, so new features
     start with default settings. Compare your file with the `config.yml` inside the new jar (the jar is a zip archive)
     and add what you want to change.
   - `defaultWorldConfig.yml`: the switch of a new feature is added as `true` the first time Architect checks it.
     Review the file after the upgrade.
   - `protocolVersions.yml`: Architect writes it only when it is missing, so an existing file does not learn newer
     Minecraft versions. Delete it before the start, so the new jar writes it, or copy the new lines from the jar.
   - Permissions: the nodes of new features may be in no bundle (biome tuning, for example).
4. Coming from 2.x: Architect now writes its messages to its own log files, not to the server console; see
   [Logs and debugging](#logs-and-debugging).

### `config.yml`

Architect copies its bundled `config.yml` into `plugins/MCME-Architect/` at the first start. It never overwrites your
settings afterwards. A few commands change single entries: they save the file again from the copy they just read, so
its layout (such as the indentation) can change.

| Section | What it is for | Chapter |
|---|---|---|
| `ServerResourcePacks` | The server resource packs: per pack, client type, resolution and variant, a download address and SHA-1 for each Minecraft version. | [Resource packs](#resource-packs) |
| `rpSettingsDatabase` | The MySQL database for players' pack settings: `user`, `password`, `dbName`, `ip`, `port`. The placeholder `xxx` means no database. | [Resource packs](#resource-packs) |
| `gitHubRpReleases` | Per pack: the GitHub `owner` and `repo`, and the release `script`, its folder `path` and `timeoutMinutes`, for `/rp release` and `/rp server`. | [Resource packs](#resource-packs) |
| `mapLayers` | Switches and styles of the four map layers. Commented out in the bundled file: every key is optional. | [Map layers](#map-layers) |
| `dynmap` | The older switch and style of two map layers (below). | [Map layers](#map-layers) |
| `entityLogger` | The entity logger's map markers (below). | [Logs and debugging](#logs-and-debugging) |
| `headGalleryLocation` | Where the custom head gallery stands (below). | [Custom heads](#custom-heads) |
| `inventoryDownload`, `inventoryUpload` | `script` and `path` of the scripts that `/inv download` and `/inv upload` run. The bundled file has only `inventoryDownload`. | [Blocks and inventories](#blocks-and-inventories) |
| `logging.file` | Architect's own log files (below). | [Logs and debugging](#logs-and-debugging) |
| `biomeTuning` | Live biome tuning: undo depth, backups, spare biomes, target pack and refresh timing. | [Biome tuning](#biome-tuning) |
| `EntityLimit` | `number` and `radius`. Read, but used by nothing in 3.2.0. | — |
| `CopyPasteUndoLimit` | Undo steps kept per player for `/undo` and `/redo` (default `30`). Not in the bundled file: add it to change it. | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `CustomHeadUploadPath` | The web address that `/chead upload` reads head lists from. Architect adds it at the first `/chead upload`. | [Custom heads](#custom-heads) |

**How Architect reads and writes the file**

- A key missing from your file reads as its value in the bundled `config.yml`, or else as the code's own default.
  This includes the bundled example entries under `gitHubRpReleases`, for packs named `Human`, `Dwarf` and `Rohan`:
  if one of your packs has such a name, give it a complete entry of its own.
- If your file has no `dynmap` section, or no `entityLogger` section, the switches of that section read as off.
- Several servers may share one `config.yml`. When Architect changes the file (for `/rp server`, `/rp server remove`,
  `/rp calcsha` and `/chead setCollection`, for example), it reads it from disk first, so it keeps what other servers
  changed. It never overwrites a file that is empty or not valid YAML; it logs the error instead. Other servers see a
  change after their next `/architect reload`.

**Core keys**

| Key | Default | Meaning |
|---|---|---|
| `logging.file.enabled` | `true` | Write Architect's messages to its own files in `logs/`. With `false`, Architect logs to the server log like other plugins, without debug lines. |
| `logging.file.alsoServerLog` | `false` | Also send Architect's messages to the server console and log. |
| `logging.file.maxSizeMB` | `10` | Size in MB at which a log file rotates; `0` means no limit. Keep it below `2048`: 2048 to 4095 and negative values stop Architect from enabling, and higher values give a wrong limit. |
| `logging.file.maxFiles` | `5` | Log files kept per server start (at least 1). |
| `logging.file.retentionDays` | `14` | At each start, Architect deletes its log files that were last changed more than this many days ago. |
| `entityLogger.enabled` | `true` | Draw the entity logger's counts on the dynmap map. Off unless it says `true`. It does not start the logger: `/architect eLog true` does. |
| `entityLogger.hide` | `true` | The `Entities` layer starts switched off in the map's layer list. |
| `entityLogger.borderWidth`, `entityLogger.borderOpacity`, `entityLogger.areaOpacity` | `0`, `0.5`, `0.5` | Line width and opacities (0 to 1) of the chunk markers. |
| `dynmap.enabled` | `true` | Draw the `RpRegions` and `itemBlockLimit` map layers, as long as `mapLayers.rpRegions` and `mapLayers.itemBlockLimit` are not set. Off unless it says `true`. |
| `dynmap.hide` | `true` | Those two layers start switched off in the layer list. |
| `dynmap.borderColor`, `dynmap.areaColor` | purple for RP regions, red for item block limits | Line and fill colour, in Bukkit's colour form (`==: Color` with `RED`, `GREEN` and `BLUE`). |
| `dynmap.borderWidth`, `dynmap.borderOpacity`, `dynmap.areaOpacity` | `2`, `0.15`, `0.25` | Line width and opacities. |
| `headGalleryLocation` | an example location | `world`, `x`, `y`, `z`, `pitch` and `yaw` of the custom head gallery. Set it with `/chead setCollection`, standing where the gallery should be, rather than by hand. |

- `logging.file` and `entityLogger` are read once per server start. `dynmap` and `headGalleryLocation` are read again at
  every `/architect reload`.
- Once `mapLayers.rpRegions` or `mapLayers.itemBlockLimit` is set, as a block of keys or as just `true` or `false`,
  that layer ignores the `dynmap` section.

### Per-world settings

Most of Architect's behaviour is switched per world, in the `WorldConfig` folder.

> [!WARNING]
> A fresh install turns every switch on, in every world. That includes `noPhysicsListInverted` (with the empty
> default list, no block gets physics updates), `dropBlocking` (items players drop are deleted) and `blockOpItems`
> (every enchantment and attribute modifier is stripped from items). That suits a build server, but few others.
> Review `defaultWorldConfig.yml` before builders log in.

#### Files and lookup

| File | What it holds |
|---|---|
| `WorldConfig/defaultWorldConfig.yml` | The defaults for every world. Created at the first start, with every switch on and the built-in default of every list. |
| `WorldConfig/<world>.yml` | Overrides for one world. Created when the world loads, holding only the header line `WorldConfiguration`. Every `*.yml` file in the folder counts as a world's file. |
| `WorldConfig/server.yml` | Not a world. Architect reads one key from it, at start: `modules.command.fullBrightness` decides whether `/fbt` exists at all. |

Architect looks each value up in the world's own file first, then in `defaultWorldConfig.yml`. A value missing from
both gets its built-in default, which Architect then writes into `defaultWorldConfig.yml`. How much a world file
overrides depends on the key:

| Key | A world file overrides |
|---|---|
| each `modules.…` switch, `viewDistance` | that key |
| `inventoryAccess` | single container types; types it leaves out come from the default file |
| `noPhysicsList`, `noInteraction`, `noConnection`, `disabledBlockStates` | the whole list: a world list replaces the default list |
| `allowedEnchantments`, `doubleSlabReplacements`, `armorStand` | the whole section |
| `itemBlock.baseLimit` | read from the world file only, never from the default file |

- Two settings count for the whole server and are read from the first loaded world (usually the main world):
  `modules.protection.blockOpItems` and `allowedEnchantments`.
- In commands that take a world name, `-default` stands for `defaultWorldConfig.yml`:
  `/nophy list|add|remove -default …` and `/chunkupdate auto -default …`.
- Edit the files, then run `/architect reload`. A few changes need a restart; see [Reloading](#reloading).
- A world file that is not valid YAML is used read-only: that world runs on the defaults, the file is never
  overwritten, and Architect logs `Failed to load world config for world <world> …`. Fix it and reload.

> [!WARNING]
> `defaultWorldConfig.yml` has no such protection. If it is not valid YAML at a start or a reload, the next world
> file Architect loads can save a fresh skeleton over it: your settings in it are lost, and every switch reads as on
> again. Keep a backup, and check the YAML before you reload.

The switches sit under `modules`, by group:

```yaml
modules:
  environment:
    dropBlocking: false
  protection:
    blockOpItems: false
```

#### Module switches

All 64 switches default to `true`. The tables say what each does when it is on.

**Special blocks (`modules.specialBlocks.*`)**

| Key | When on |
|---|---|
| `place` | Special blocks from Architect's build inventories are placed by their own rules, `/itemblock` works, and stacked slabs follow `doubleSlabReplacements`. Also: items never lose durability; mobs cannot trigger pressure plates or trample farmland; placed leaves, pumpkins and end portal frames get fixed states; item block stands cannot be changed. |
| `flint` | Flint shows a block's data (left-click) and gives the matching block item (right-click); the pick-block key gives the matching special block; `/weselect` works. |
| `inventoryAccess` | The `inventoryAccess` rules decide who may open which container. |
| `blockPlayerInteraction` | Right-clicks on blocks that match the `noInteraction` list are cancelled, for everyone. |
| `poweredDoors` | Architect places doors taken from the creative inventory itself: oak, spruce, jungle, acacia and dark oak doors powered, birch and iron doors unpowered. Doors of newer wood types stay as the game places them. |
| `halfDoors` | A door item named `Half…` places only the lower half (`architect.place.halfDoor`). A lone half door cannot be opened in any world, whatever this switch says. |
| `halfBeds` | A red bed named `Half…` places one half of a bed (`architect.place.halfBed`). |
| `redstoneTorch` | A redstone torch named `Unlit…` places a torch on the clicked face; in 3.2.0 it comes out lit (`architect.place.torch`). |
| `plants` | Plant items named `Placeable…` can be placed on any block (`architect.place.plant`). |
| `pistonExtenstions` (spelt this way) | A piston named `Table…` or `Wheel…` places a piston head (`architect.place.pistonExtension`). |
| `burningFurnace` | Furnaces that finish smelting are refilled, so they burn for ever. Clicking the input slot lights a furnace or puts it out, where the player may build. A furnace named `Burning…` is placed lit (`architect.place.burningFurnace`). |
| `dragonEgg` | Dragon eggs never teleport. Only players in creative mode with `architect.interactDragonEgg` and build permission can break one. |
| `doubleSlabs` | No effect in 3.2.0. |
| `sixSidedLogs` | No effect in 3.2.0. |

**Environment (`modules.environment.*`)**

| Key | When on |
|---|---|
| `playerSurvivalFly` | Players in survival mode may fly. |
| `playerDamageBlocking` | Players take no fall, magma block or campfire damage. |
| `dropBlocking` | Items that players drop are deleted. |
| `monsterSpawnBlocking` | No natural monster spawning. When off, Architect allows it at every world load and reload. |
| `animalSpawnBlocking` | No natural animal spawning. When off, Architect allows it at every world load and reload. |
| `fireSpreadBlocking` | Fire does not burn blocks away, and Architect sets the game rule `fire_spread_radius_around_player` to 0. When off, Architect sets it to 128 at every world load and reload. Either way it replaces a value you set yourself. |
| `weatherBlocking` | The weather never changes, except once after `/architect weather`. |
| `decayBlocking` | Leaves do not decay. |
| `formBlocking` | Blocks do not form (snow, ice, concrete and the like). Also stops block explosions (beds, respawn anchors), tree and mushroom growth, and farmland drying out or getting wet. |
| `fadeBlocking` | Blocks do not fade (ice and snow melting, coral dying and the like). |
| `noPhysicsListEnabled` | The no-physics list applies, and `/nophy` works. Tripwires do not react to entities. |
| `noPhysicsListInverted` | The no-physics list names the blocks that keep physics; every other block has none. |
| `noPhysicsConnectStairs`, `noPhysicsConnectFences`, `noPhysicsConnectWalls`, `noPhysicsConnectChorus`, `noPhysicsConnectChests`, `noPhysicsConnectGlass`, `noPhysicsConnectRedstoneWire` | Architect itself connects a placed block of that kind to its neighbours, and disconnects them when it is broken. Glass panes are only connected when placed; breaking one does not disconnect its neighbours. |
| `drainWaterloggedDoubleSlabs` | Breaking a waterlogged double slab leaves air, not water. |
| `gamemodeSwitcher` | Players with an `architect.gamemodeSwitcher` node can use F3+F4 without being op. Needs ProtocolLib. |

**Commands and tools (`modules.command.*`)**

| Key | When on |
|---|---|
| `copypaste` | `/copy`, `/cut`, `/paste`, `/undo`, `/redo`, `/flip` and `/rot`. |
| `randomiser` | `/random`. |
| `getSpecialBlocks` | `/get` and `/inv`. The swap-hands key (F) opens your pack's build inventory instead of swapping hands; sneak+F opens the held block's collection or the heads; sneak+Q swaps a special block for its next block; sneak+Ctrl+Q on two or more opens its collection. |
| `cycleBlocks` | The stick edits block states, and `/switchstick` works. While on, every block click made with a stick in the main hand is cancelled, so a stick cannot press buttons or break blocks; doors still open and close. |
| `signEditor` | `/sign`, and selecting a sign by left-clicking it with a stick. |
| `bannerEditor` | `/banner`, and editing banners with a stick. |
| `paintingEditor` | Changing a painting's motif with a stick. |
| `armorStandEditor` | `/armor`, and editing armor stands with a stick. New armor stands in the world get no gravity and cannot be damaged. |
| `armorStandRollback` | `/armor rollback`, which is unfinished in 3.2.0. |
| `customHeadManager` | `/chead`, and taking a copy of a head by clicking it with a stick. |
| `resourcePackSwitcher` | The `/rp` player commands and region commands in this world. `/rp check` and the admin commands that also run from the console keep working, and RP regions switch packs whatever this switch says. |
| `fullBrightness` | `/fbt` gives night vision in this world. When off, players who arrive in the world lose their night vision. In `server.yml`, it decides at start whether `/fbt` exists at all. |
| `viewdistance` | `/viewdistance`. With ProtocolLib, Architect also tells every player in the world a view distance (`viewDistance`, or their own) and stops their client from dropping chunks. |
| `weSchemViewer` | `/schlist`. |
| `voxelViewer` | `/vv`. |
| `stencilListEditor` | `/sl`. |
| `modules.commmand.itemTexture` (three m, a group of its own) | No effect in 3.2.0. |

**Protection (`modules.protection.*`)**

| Key | When on |
|---|---|
| `blockOpItems` | Items lose every enchantment and every attribute modifier when a player picks them up, drops them, selects them or moves them in an inventory. Read from the first loaded world only, for the whole server. In 3.2.0 no enchantment is kept (see `allowedEnchantments` below). |
| `removeContainerItemsOnBreak` | A broken container drops nothing: its contents are deleted. Jukeboxes and decorated pots lose their contents too. |
| `blockArrowShooting` | Nobody can shoot a bow or crossbow, mobs included. |
| `tnt` | TNT cannot be lit. |
| `armorStand` | Armor stands are protected: mobs, projectiles, fire and fishing rods cannot harm or pull them. Players need `architect.armorStandEditor` to hit them, and also build permission to place or use them. |
| `hangingEntity` | Paintings and item frames do not break from physics, explosions or mobs. Players need `architect.hangingEntityEditor` and build permission to place them, and to use or hit item frames. |
| `chestInventory` | Players without build permission (and without `architect.inventory.ignoreProtection`) can look into a storage container, but cannot move items into or out of it, or copy it with its contents in creative. Ender chests, workstations and trading are not affected. |
| `redstone` | Clicking redstone wire without build permission is undone. |
| `lilyPad` | Boats do not break lily pads, or change any other block. |

**Other groups**

| Key | When on |
|---|---|
| `modules.chunkupdate.auto` | After every block click, placement or break, Architect sends the nearby blocks again to the player who acted, so their client shows connections (fences, walls, panes, gates, doors) correctly. |
| `modules.chunkupdate.manual` | Allows `/chunkupdate`, which does nothing in 3.2.0. Also needed in your current world to use `/chunkupdate auto`. |
| `modules.voxelBiomeBrushFix` | No effect in 3.2.0. |

#### Other keys

Block-data entries use the full form `minecraft:<id>[<state>=<value>,…]`: the states you list must match, the others
match anything. Architect skips an entry it cannot read, with a warning in its log. `noPhysicsList` is the exception:
see its row.

| Key | Built-in default | Format and meaning |
|---|---|---|
| `noPhysicsList` | `[]` | List of block ids without `minecraft:`, optionally with states: `torch`, `oak_fence[east=true]`. An entry written with `minecraft:` is ignored. With `noPhysicsListInverted` on (the default), these blocks keep physics and all others lose it; with it off, these blocks lose physics. Edit it with `/nophy`. |
| `noConnection` | `minecraft:red_sandstone_wall`, `minecraft:andesite_wall`, `minecraft:prismarine_wall` | Block states that Architect's own connecting never connects. |
| `noInteraction` | 11 entries: acacia, jungle, dark oak and spruce fence gates; `minecraft:birch_fence_gate[powered=true]`; acacia, jungle, dark oak and spruce doors with `powered=false`; `minecraft:repeater`; `minecraft:comparator` | Block states nobody may right-click, while `specialBlocks.blockPlayerInteraction` is on. Doors on the list are also left out of Architect's double-door opening, and right-clicking such a block with a special block item places the item instead of using the block. |
| `disabledBlockStates` | `minecraft:blue_stained_glass_pane[east=false,north=false,south=false,west=false]` | Block states the stick never switches to, and that special blocks never connect into. |
| `inventoryAccess.<TYPE>` | see below | Who may open each type of container. |
| `allowedEnchantments.<Name>` | `Durability: 1` | Meant as the enchantments, each with a highest level, that `blockOpItems` keeps. In 3.2.0 no entry ever matches, so `blockOpItems` removes every enchantment: switch `blockOpItems` off to keep enchantments. Read from the first loaded world only. |
| `doubleSlabReplacements.<pack>.<name>` | an example for the pack `gondor` | `from` and `to`, both block data. When a player stacks two slabs into a double slab, and the slab that was there matches `from` exactly, the double slab becomes `to`. `<pack>` is the player's resource pack (or the RP region's), written in lower case. Needs `specialBlocks.place`. |
| `viewDistance` | `255` | The view distance Architect tells players in this world who have not set their own with `/viewdistance`; 255 means no limit in practice. With the defaults, every player in the world is told 255 and keeps every chunk it has received. Needs ProtocolLib and `command.viewdistance`. |
| `armorStand.minScale`, `armorStand.maxScale` | `0.0625`, `16.0` | Smallest and largest scale of the armor stand editor's scale mode. In a world file, set both keys: a missing one reads as 0. |
| `itemBlock.baseLimit` | `5` | Outside item block regions, placing item blocks stops once a chunk holds this many armor stands, item frames and paintings together. Read from the world's own file only; `/itemblock limit -base <n>` writes it for the world you stand in. |
| `DefaultConfiguration`, `WorldConfiguration` | a sentence | Header text. Never read. |

**`inventoryAccess`** applies while `specialBlocks.inventoryAccess` is on. Its keys are Bukkit inventory type names,
and each takes one of these values:

| Value | Who may open the container |
|---|---|
| `'TRUE'` | Everyone. |
| `'FALSE'` | Nobody. The click is refused without a message. |
| `BUILDER` | Players with `architect.inventory.open` and build permission at the container. |
| `EXCEPTION` | As `BUILDER`, and the container must stand in a redstone exception area of `/nophy`. Those areas cover dispensers, droppers, hoppers, dyed shulker boxes, enchanting tables, beacons and undamaged anvils. |

- Defaults: `ANVIL`, `CHEST`, `CRAFTING`, `CREATIVE`, `ENDER_CHEST`, `MERCHANT`, `PLAYER` and `WORKBENCH` are
  `'TRUE'`; `BREWING`, `FURNACE`, `SMOKER` and `BLAST_FURNACE` are `BUILDER`; `BEACON`, `DISPENSER`, `DROPPER`,
  `ENCHANTING` and `HOPPER` are `EXCEPTION`. `SHULKER_BOX` is a section whose `default` is `'TRUE'`; Architect reads
  only `default`, not single colours.
- A type missing from both files is added to `defaultWorldConfig.yml` as `'TRUE'` the first time someone opens one.
- Write the values in upper case, and quote `'TRUE'` and `'FALSE'`: unquoted, YAML reads them as true and false, which
  Architect cannot use.

**`doubleSlabReplacements`** example:

```yaml
doubleSlabReplacements:
  human:                   # the resource pack, in lower case
    mossyStone:            # any name
      from: minecraft:stone_slab[type=bottom,waterlogged=false]
      to: minecraft:mossy_stone_bricks
```

An entry without `from` or `to`, or with block data that does not parse, stops Architect from enabling and makes
`/architect reload` fail.

**`noPhysicsList` notes**

- The first `/nophy add <world> …` in a world that uses the default list starts a list of the world's own, holding
  only that entry: the default entries stop applying in that world. Add every entry the world needs. When a world's
  list becomes equal to the default list again, Architect deletes it, and the world inherits the default list.
- A list with a number in it (an old numeric block id) is replaced by an empty list when its file loads.
- Known issue: after an `/architect reload`, a `/nophy add -default` or `/nophy remove -default` that leaves the default
  list equal to the list Architect held before that reload (the same one, unless you edited the file by hand first)
  deletes the list from `defaultWorldConfig.yml`. Keep a backup of the file.

#### Behaviour without a switch

These rules apply in every world, and no setting turns them off:

- Redstone power is frozen on the whole server: every change of redstone current is undone, in every world and inside
  exception areas too. Levers, buttons and pressure plates do not switch, and doors, lamps, rails and the like ignore
  power. This is on purpose: MCME's resource packs use redstone block states as decoration.
- Turtle eggs cannot be trampled.
- Clicking a flower pot or a sign (dye, glow ink, wax), and opening a sign's edit screen, need build permission.
- Lecterns: the Take Book button gives a copy of the book. Only players with build permission there, or with
  `architect.lecternEditor`, take the book itself, put books on lecterns and write in a powered lectern's book.
- Placing barrier, light and structure void blocks needs `architect.invisibleBlocks`, except for items from
  Architect's build inventories.
- Right-clicking one door of a double door opens both.
- Right-clicking a light block with a light item raises its light level; sneaking lowers it. Build permission is
  needed.
- At join, each player's own time is reset to the world's time.

### Build worlds

Architect brings a world generator for empty build worlds. It has no commands, permissions or switches.

Set a new world's generator to `MCME-Architect`, or to `MCME-Architect:<name>_<size>` to choose the size, in
`bukkit.yml` or with your world plugin:

```yaml
worlds:
  <world>:
    generator: MCME-Architect:flat_1024
```

- The world is void except for one flat square: bedrock at the lowest level, ten layers of dirt, and grass on top
  (y −53 in a normal overworld).
- The square reaches about `<size>` blocks from 0,0 in each direction: 512 if you give no size, or if the text after
  the first `_` is not a number.
- There is no terrain, caves, ores, structures, trees or mobs, and only chunks generated from then on are affected.

### Data and files

Architect keeps its files in `plugins/MCME-Architect/`. Back up the whole folder before an upgrade.

| Path | Feature | Notes |
|---|---|---|
| `config.yml` | core, most features | See [`config.yml`](#configyml). |
| `WorldConfig/defaultWorldConfig.yml`, `WorldConfig/<world>.yml`, `WorldConfig/server.yml` | per-world settings | See [Per-world settings](#per-world-settings). |
| `logs/architect_<start>_<n>.log` | logging | Architect's own log files; see [Logs and debugging](#logs-and-debugging). |
| `protocolVersions.yml` | resource packs | Minecraft versions and their protocol numbers. Written only when missing. |
| `regions/<name>.reg` | RP regions | One file per region. Older `.json` region files found here are converted once, then deleted. |
| `rp-release-times.yml` | `/rp release` | How long the last successful release of each pack took. |
| `inventories/block/<pack>/` | build inventories | `categories.yml` and the block files of one pack. The folder name must equal the pack's key under `ServerResourcePacks`, including case. Architect creates `inventories/block/` but not the pack folders, and it rewrites the block files when it loads them. |
| `inventories/item/<pack>/` | item inventories | Loaded, but no command opens them in 3.2.0. |
| `inventories/saved/<pack>/<name>.yml` | `/inv create` | Saved inventories. |
| `itemSets.yml` | `/get` | Kits. |
| `itemBlockRegions/<name>.reg` | `/itemblock` | Item block limit regions. |
| `mapLayers/itemBlockBudget.yml` | map layers | Item block counts per chunk. |
| `NoPhyExceptionAreas.txt` | `/nophy exception` | One line per exception area. A `.tmp` file appears while saving, and a `.bak` copy is kept when a load had to skip a broken line. |
| `customHeads/accepted/`, `customHeads/submitted/` | `/chead` | The head collection (folders are categories) and the heads waiting for review. |
| `clipboards/<player UUID>.cbd` | copy and paste | Each player's clipboard, in a binary format. |
| `armorStands/` | `/armor` | Saved armor stands. |
| `banners/` | `/banner` | Saved banner designs. |
| `randomiser.yml` | `/random` | The list of block states that `/random` may change. |
| `SwitchStick/switchStickConfig.yml` | `/switchstick` | The players who switched their stick on. |
| `viewDistances.dat` | `/viewdistance` | One `<player UUID> <distance>` line per player. Do not edit it by hand: a broken line stops Architect from enabling. |
| `biomeTuning/backups/` | biome tuning | A backup of a biome's file at every save. |
| `entityLog.dat` | entity logger | The latest entity counts per chunk. |

Outside its folder, Architect also uses:

- `plugins/VoxelSniper/stencils/` and `plugins/VoxelSniper/stencilLists/` (`/vv`, `/sl`);
- `plugins/WorldEdit/schematics/` (`/schlist`);
- the main world's `datapacks/` folder: biome files, spare biomes and `mcme-biometune.json` in the target pack (biome
  tuning);
- the folders named in `gitHubRpReleases.<pack>.path`, `inventoryDownload.path` and `inventoryUpload.path`, where
  Architect runs scripts;
- `blockList.txt` in the server's main folder, written by `/architect placeAllBlockStates file`.

### Logs and debugging

#### Architect's log files

By default Architect writes everything it logs to its own files in `plugins/MCME-Architect/logs/`, not to the server
console or the server log: start-up lines, warnings, errors and debug lines. When a chat message sends you to
"Architect's log", it means these files.

- Every server start begins a new set, named after the start time: `architect_<yyyy-MM-dd_HH-mm-ss>_<n>.log`.
- `_0` is the file being written. When it reaches `logging.file.maxSizeMB`, the files move up one number and a new
  `_0` starts. At most `logging.file.maxFiles` files are kept per start.
- At each start, Architect deletes its log files that were last changed more than `logging.file.retentionDays` days
  ago.
- Each line reads `[yyyy-MM-dd HH:mm:ss] [LEVEL] message`, followed by the stack trace after an error.
- While the server runs, a lock file `…_0.log.lck` sits next to the current file. A crash can leave it behind; you can
  delete leftover `.lck` files while the server is stopped.
- With `logging.file.alsoServerLog: true`, Architect's lines also appear in the console and the server log.
- With `logging.file.enabled: false`, Architect logs to the server log like other plugins, and its debug lines are not
  logged.
- If Architect cannot create its folder or file, it falls back to the server log and says so there.
- When a command fails with "An internal error occurred", look in both the server log and Architect's newest log.

The `logging.file` keys are listed under [`config.yml`](#configyml). They are read at start only.

#### Debug output

Architect has two debug channels. Neither is saved: both are back to their defaults after a restart.

| | `/architect dev` | `/dev MCME-Architect` |
|---|---|---|
| Belongs to | Architect | PluginUtils |
| Who may use it | the console, `architect.info` or `architect.reload` | `pluginutil.developer` (default op) |
| Used by | most of Architect's features | resource pack join diagnostics, and a few other lines |
| Default level | 1 | 1 (the join diagnostics need level 2) |
| Where lines go | the chat of the developers on its list, Architect's log file, and the console when switched on | the chat of the developers on its list, and the console when switched on |

`/architect dev` takes one of these:

| Syntax | Effect |
|---|---|
| `/architect dev` | Adds you to the developers, who get debug lines in chat. |
| `/architect dev -r` | Removes you from the developers. |
| `/architect dev <level>` | Sets the level. Higher levels show more. |
| `/architect dev true\|false` | Switches the copy of debug lines to the console on or off. |

Each use prints the current level, the console setting and the developers; from the console, only `true`, `false` and
a level do anything. PluginUtils' command works the same way:
`/dev MCME-Architect [true|false|<level>|r]`.

- Level 2 shows the block inventory entries that were skipped while loading. Example: `/architect dev 2`,
  `/architect dev`, then `/inv reload`. Undo it with `/architect dev 1` and `/architect dev -r`.
- Level 40 shows every switch lookup, and floods the chat and the log.

#### Entity logger

The entity logger helps to find chunks with many entities. It counts armor stands, boats, arrows, dropped items,
paintings and item frames per chunk; the `all` column, which also sets the map colours, counts every entity.

- `/architect eLog true` starts it; `/architect eLog` stops it. It is off again after every restart.
- It counts a chunk when its entities load or unload, so chunks that were already loaded show up only after they load
  again.
- It writes `entityLog.dat` 25 seconds after you start it, then about every 100 seconds. The file has one line per
  chunk: `world;x;z;ArmorStand;Boat;Arrow;Item;Painting;ItemFrame;all`.
- With dynmap and `entityLogger.enabled`, it colours the counted chunks on the map, in the layer `Entities`: from blue
  for few entities through green, yellow and red to magenta for the busiest chunk. Stopping the logger removes them.

### Reloading

`/architect reload` (the console or `architect.reload`) reads Architect's settings and data again without a restart:

- `config.yml`, except the keys in the table below;
- every file in `WorldConfig/` (but see `noPhysicsList` below);
- the no-physics exception areas;
- the custom heads, and the head gallery, which it builds again;
- the block, item, saved and head inventories;
- the kits in `itemSets.yml`;
- the resource packs, `protocolVersions.yml` (a removed version stays known until a restart), the RP regions and the
  item block regions (the regions come back after about 10 seconds);
- `viewDistances.dat`;
- the map layers, which leave the map and are drawn again;
- the game mode switcher, for every online player.

It answers `Reload complete!`. If it meets a broken file on the way, such as a bad `doubleSlabReplacements` entry or a
bad line in `viewDistances.dat`, it stops part-way with an internal error. Fix the file and reload again, or restart.

**These need a server restart**

| Change | Why |
|---|---|
| `logging.file.*`, `biomeTuning.*` | Read when Architect enables. |
| `rpSettingsDatabase` | The database connection is set up once. |
| `entityLogger.*`, `CopyPasteUndoLimit`, `CustomHeadUploadPath` | Read once per server start. |
| `modules.command.fullBrightness` in `WorldConfig/server.yml` | Whether `/fbt` exists is decided at start. |
| `randomiser.yml`, `SwitchStick/switchStickConfig.yml` | Read once per server start. |
| Hand edits of `noPhysicsList` | Architect keeps the lists it has read. After `/architect reload` it reads them again only when someone changes a list with `/nophy add` or `/nophy remove`. |
| New spare biomes from `/biometune spares add` | The game loads biomes at start. |
| Adding ProtocolLib | Architect sets up `/viewdistance` and the game mode switcher only when it enables. |

**Data removed by hand comes back**

- An exception area deleted from `NoPhyExceptionAreas.txt` stays active after a reload, and is written back at the next
  save. Delete areas with `/nophy exception delete <name>`.
- A kit deleted from `itemSets.yml` stays in memory and is written back. Delete kits with `/get delete <set>`.
- A line deleted from `viewDistances.dat` stays in effect and is written back at the next save.

**Other reload commands**

| Command | Reloads |
|---|---|
| `/inv reload` | The block, item, saved and head inventories only. |
| `/chead reload` | The custom heads, and builds the gallery again. |
| `/biometune revert <biome>` | One biome's file, dropping its unsaved live edits. |
| `/architect reloaddata` | The server's data packs. Not Architect's own data. |

Reload Architect with `/architect reload` or a restart, not with `/reload` or a plugin manager: disabling Architect
without a restart leaves live biome edits in the game while Architect forgets them.

### The `/architect` command

`/architect` is Architect's admin command. Subcommand names are not case-sensitive. It offers no tab completion.

| Syntax | Who may use it | What it does |
|---|---|---|
| `/architect`, `/architect help [page]` | players with `architect.help` (everyone by default) | Lists the Architect commands you may use, ten per page, with clickable lines. Careful: clicking the lines of `/copy`, `/cut`, `/paste`, `/undo`, `/redo` and `/flip` runs that command at once. |
| `/architect version` | the console, `architect.reload` | Shows Architect's version, for example `Version: 3.2.0`. |
| `/architect reload` | the console, `architect.reload` | Reloads Architect's settings and data; see [Reloading](#reloading). |
| `/architect reloaddata` | the console, `architect.reload` | Reloads the server's data packs (loot tables, advancements and the like). Not Architect's own data. |
| `/architect world` | the console, `architect.info` or `architect.reload` | Lists the loaded worlds. In game, clicking a name puts `/mvtp <world>` (a Multiverse command) in your chat box. |
| `/architect blockdata` | players with `architect.info` or `architect.reload` | Shows the full block data of the block you look at, up to 100 blocks away. |
| `/architect dev [true\|false\|<level>\|-r]` | the console, `architect.info` or `architect.reload` | Architect's debug output; see [Logs and debugging](#logs-and-debugging). |
| `/architect checkNBT [player]` | the console, `architect.checknbt` | Shows the stored data of the item in your main hand, or of every item an online player carries. From the console, name a player. |
| `/architect weather clear\|rain\|thunder` | players with `architect.weather` | Changes the weather in your world once, past `weatherBlocking`. In 3.2.0 `thunder` gives rain only. |
| `/architect maplayers refresh` | the console, `architect.maplayers` | Draws Architect's map layers again and recounts the loaded chunks; see [Map layers](#map-layers). |
| `/architect eLog true`, `/architect eLog` | the console, `architect.reload` | Starts or stops the entity logger; see [Logs and debugging](#logs-and-debugging). |
| `/architect placeAllBlockStates [file]` | players with `architect.reload` | A developer tool. Places every state of every block type in a large grid next to you, with no undo: never use it in a build world. With `file`, it places nothing and writes the list to `blockList.txt` in the server's main folder. |

- `architect.reload` allows everything that `architect.info` allows.
- Senders that are neither the console nor a player, such as command blocks and RCON, can use only `checkNBT`, and
  `maplayers refresh` if they hold `architect.maplayers`.

---

## Command reference

Every command Architect registers in 3.2.0. Permissions are per subcommand where they differ; the
[Permissions](#permissions) chapter explains each node. Every command also works with the prefix `mcme-architect:`,
for example `/mcme-architect:undo`, which helps when another plugin has a command of the same name.

| Command | Syntax | What it does | Permission | Chapter |
|---|---|---|---|---|
| `/architect` | `/architect [help [page]]` | Lists the Architect commands you may use. | `architect.help` (everyone by default) | [Running Architect](#the-architect-command) |
| `/architect` (admin) | `/architect version`, `reload`, `reloaddata`, `world`, `blockdata`, `dev [true\|false\|<level>\|-r]`, `checkNBT [player]`, `weather clear\|rain\|thunder`, `eLog [true]`, `placeAllBlockStates [file]` | Version, reloading, worlds, block data, debug output, item data, weather, entity logger. | `architect.info`, `architect.reload`, `architect.checknbt` or `architect.weather`; see [The /architect command](#the-architect-command) | [Running Architect](#the-architect-command) |
| `/architect maplayers` | `/architect maplayers refresh` | Draws the map layers again. | `architect.maplayers` | [Map layers](#map-layers) |
| `/get` | `/get [help [page]]`, `/get list [page]`, `/get <set> [-o]` | Lists kits and gives one; with `-o`, into the same hotbar slots. | `architect.getCommand` | [Blocks and inventories](#blocks-and-inventories) |
| `/get` (kits) | `/get pcreate <set> [description]`, `/get create <set> [description]`, `/get publish <set>`, `/get unpublish <set>`, `/get delete <set>` | Saves your hotbar as a private or public kit; publishes, unpublishes or deletes a kit. | `architect.getCommand`, plus `architect.get.private` for `pcreate`, `architect.get.public` for `create`, `publish` and `unpublish`, and `architect.get.other` for another player's kit | [Blocks and inventories](#blocks-and-inventories) |
| `/get armor` | `/get armor <RRGGBB>` | Gives leather armor dyed that colour. | `architect.getCommand`, `architect.get.armor` | [Blocks and inventories](#blocks-and-inventories) |
| `/get head` | `/get head <head or category>` | Gives a head from the collection, or every head of a category. | `architect.getCommand`, `architect.get.head` | [Custom heads](#custom-heads) |
| `/inv` | `/inv [help [page]]`, `/inv b [rp:<pack>] [s:<text>]`, `/inv h [s:<text>]`, `/inv c [rp:<pack>]` | Opens the block inventory or a search of it, the head inventory, or the saved inventories. | `architect.inventory` | [Blocks and inventories](#blocks-and-inventories) |
| `/inv` (saved) | `/inv create [rp:<pack>] <name>`, `/inv delete [rp:<pack>] <name>` | Saves your inventory as a saved inventory, or deletes one. | `architect.inventory` and `architect.inventory.save`; another player's: `architect.inventory.other` | [Blocks and inventories](#blocks-and-inventories) |
| `/inv` (admin) | `/inv reload`, `/inv download <pack>`, `/inv upload <pack> <description>` | Reloads the inventory files; runs the download or upload script. | `architect.inventory.reload`, `architect.inventory.download`, `architect.inventory.upload` | [Blocks and inventories](#blocks-and-inventories) |
| `/itemblock` | `/itemblock help`, `stats <radius> [height]`, `glow <radius>\|off`, `remove <radius> [height]`, `list`, `create <name>`, `delete <name>`, `limit <name> <n>`, `limit -base <n>` | Item block counts, glow and clean-up (`remove` removes every named armor stand in the box), and item block limit regions. | `architect.itemBlock`, plus `architect.itemBlock.remove` for `remove`, `architect.itemBlock.region.create` for `create` and `delete`, and `architect.itemBlock.region.limit` for `limit` | [Blocks and inventories](#blocks-and-inventories) |
| `/nophy` | `/nophy list <world\|-default>`, `/nophy add\|remove <world\|-default> <block>`, `/nophy exception redstone\|water <name>`, `/nophy exception delete <name>`, `/nophy exception list [page]`, `/nophy help [page]` | Shows and edits the no-physics list; manages exception areas. | `architect.noPhysicsList.view`, plus `architect.noPhysicsList.edit` for `add` and `remove`, and `architect.noPhysicsList.exception` for `exception` | [Placing and physics](#placing-and-physics) |
| `/chunkupdate` | `/chunkupdate [radius]`, `/chunkupdate auto <world\|-default> [true]` | The radius form does nothing in 3.2.0. `auto` switches automatic block resends on (with `true`) or off. | `architect.chunkupdate`, plus `architect.chunkupdate.auto` for `auto` | [Seeing and moving](#seeing-and-moving) |
| `/switchstick` (`/swst`, `/ss`) | `/switchstick [help]` | Switches your stick's block-state editing on or off. It is off until you first run this. | `architect.switchstick` | [Placing and physics](#placing-and-physics) |
| `/weselect` (`/wes`) | `/weselect <command>`, `/weselect shift <command>`, `/weselect show`, `/weselect reset`, `/weselect help [page]` | Sets the command that flint clicks put in your chat box, for a click and a sneak-click; `#` becomes the block data. | `architect.weselect` | [Placing and physics](#placing-and-physics) |
| `/sign` | `/sign <1-4> <text>`, `/sign glow true\|false` | Changes a line of the sign you selected with a stick; switches glowing text. | `architect.signEditor` | [Editing tools](#editing-tools) |
| `/banner` | `/banner [help [page]]`, `/banner <n>`, `/banner <mode> [n]`, `/banner save <file> <description>`, `/banner load <file>`, `/banner delete <file>`, `/banner files [folder] [page]` | Chooses what a stick click does to a banner; saves and loads banner designs. | `architect.bannerEditor`, plus `architect.bannerEditor.save` for `save`, and `architect.bannerEditor.delete` to delete other players' files | [Editing tools](#editing-tools) |
| `/armor` | `/armor [<mode> [part]]`, `/armor <step>\|+\|-\|default`, `/armor parts`, `/armor paste`, `/armor clear`, `/armor save <name> <description>`, `/armor files [folder] [page]`, `/armor p <name>`, `/armor rename <old> <new>`, `/armor delete <name>`, `/armor help [page]` | Chooses what a stick click does to an armor stand; copies, saves and loads armor stands. | `architect.armorStandEditor`, plus `architect.armorStandEditor.trusted` for `save` and to rename or delete your own files, and `architect.armorStandEditor.delete` for other players' files | [Editing tools](#editing-tools) |
| `/copy` (`/cp`) | `/copy` | Copies your cuboid WorldEdit selection to Architect's clipboard. | `architect.copypaste`, and `architect.copypaste.limit.<n>` of at least the selection's size, or `architect.copypaste.unlimited` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/cut` (`/ct`) | `/cut` | Copies the selection, then clears it. | as `/copy` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/paste` (`/pst`, `/ps`) | `/paste [-a] [-b]` | Pastes the clipboard at the same offset from you as when you copied it. `-a` skips air, `-b` skips biomes. | `architect.copypaste` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/undo` (`/un`, `/ud`) | `/undo [n]` | Reverts your last `n` pastes and cuts (default 1). | `architect.copypaste` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/redo` (`/re`, `/rd`) | `/redo [n]` | Restores `n` undone edits (default 1). | `architect.copypaste` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/flip` (`/flp`, `/fl`) | `/flip [x\|y\|z]` | Mirrors the clipboard; without an axis, along the direction you look. | `architect.copypaste` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/rot` (`/rt`) | `/rot [degrees]` | Turns the clipboard around the vertical axis, in steps of 90 (default 90). | `architect.copypaste` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/random` | `/random`, `/random radius <r>`, `range <min> <max>`, `prob <p1> [p2] …`, `material <material:Attribute> […]`, `show`, `showAllowed`, `help [page]`; `allow\|deny <material:Attribute> […]` | Gives blocks around you random values of one block state, such as crop age. | `architect.randomiser.user`; `architect.randomiser.allowMaterials` for `allow` and `deny` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/vv` | `/vv [list [folder] [page]]`, `/vv stencil [folder] [page]`, `/vv delete <file>`, `/vv help [page]` | Lists VoxelSniper stencil lists and stencils; deletes files. | `architect.voxel.viewer`; `architect.voxel.delete` for `delete` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/sl` | `/sl create\|load <list>`, `/sl add\|remove <stencil or pattern>`, `/sl save`, `/sl show`, `/sl help [page]` | Builds VoxelSniper stencil lists. | `architect.voxel.stencilListEditor` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/schlist` | `/schlist [folder] [page]` | Lists the WorldEdit schematics on the server. | `architect.weSchemViewer` | [Copy, paste and shaping](#copy-paste-and-shaping) |
| `/chead` | `/chead [help [page]]`, `/chead warp`, `/chead list [folder] [page]`, `/chead submit [player or UUID\|handitem] <name>` | Visits and browses the head collection; submits heads. | `architect.customHeadManager.user` | [Custom heads](#custom-heads) |
| `/chead` (manage) | `/chead reviewList [folder] [page]`, `review <name>`, `accept <submitted> <category/name>`, `reject <name>`, `delete <name>`, `rename <old> <new>`, `setCollection`, `reload`, `upload [file] [-r]` | Reviews submitted heads, and manages the collection and its gallery. | `architect.customHeadManager.user` and `architect.customHeadManager.manager` | [Custom heads](#custom-heads) |
| `/fbt` (`/fullbright`, `/nightvision`) | `/fbt` | Switches endless night vision on or off. | `architect.fullBrightness` | [Seeing and moving](#seeing-and-moving) |
| `/viewdistance` (`/viewdist`) | `/viewdistance <n>\|default` | Sets the view distance Architect tells your client (at most 500), or goes back to the world's. | `architect.viewdistance` | [Seeing and moving](#seeing-and-moving) |
| `/parrot` | `/parrot` | Removes the parrots from your shoulders. | none | [Seeing and moving](#seeing-and-moving) |
| `/rp` | `/rp <pack> [-force]`, `/rp check`, `/rp auto [off]`, `/rp reset`, `/rp client <type>`, `/rp variant <name>`, `/rp help [page]` | Switches your resource pack and your pack settings; `check` names the pack for your spot. | `architect.resourcePackSwitcher` (`check` needs none) | [Resource packs](#resource-packs) |
| `/rp` (admin) | `/rp server <pack> <tag> <mcVersion>`, `/rp server remove <pack> <mcVersion>`, `/rp calcsha <pack> [all]`, `/rp release <pack> <tag> <title>`, `/rp dropdb [confirm]`, `/rp list [filter]`, `/rp create\|edit\|remove <region>` | Points pack slots at a GitHub release, hashes packs, runs a release, clears the settings table, manages RP regions. | `architect.resourcePackAdmin` | [Resource packs](#resource-packs) |
| `/biometune` | `/biometune [edit] [biome\|here]`, `info`, `set`, `unset`, `copy`, `undo`, `revert`, `status`, `preview`, `spares`, `claim`, `label` | Live biome tuning: the editor and its commands. | `architect.biomeTune` | [Biome tuning](#biome-tuning) |
| `/biometune` (save, publish, admin) | `/biometune save <biome>`; `/biometune publish [world\|server]`, `/biometune preview <player>`; `/biometune spares add <count>`, `selftest [biome]`, `debug …` | Writes a biome to its datapack file; refreshes other players; admin tools. | `architect.biomeTune.save` for `save`; `architect.biomeTune.publish` for `publish` and `preview <player>`; `architect.biomeTune.admin` for the rest | [Biome tuning](#biome-tuning) |

- If `/undo` or `/redo` reaches WorldEdit instead of Architect, use Architect's aliases (`/un`, `/re`) or
  `/mcme-architect:undo`.
- Most commands also need their world switch to be on; see [Per-world settings](#per-world-settings).

---

## Permissions

Every Architect node defaults to op, with three exceptions: `architect.help` is everyone's, and the game mode switcher
nodes and `architect.interactDragonEgg` default to nobody. Ranks get their nodes through four bundles; give the other
nodes one by one.

### Rank bundles

Each bundle includes the one below it, and all four default to op.

| Bundle | Includes | Adds |
|---|---|---|
| `architect.ranks.adventurer` | — | `architect.help`, `architect.getCommand`, `architect.get.armor`, `architect.get.head`, `architect.inventory`, `architect.inventory.open`, `architect.cycleBlocks`, `architect.switchstick`, `architect.signEditor`, `architect.bannerEditor`, `architect.paintingEditor`, `architect.armorStandEditor`, `architect.hangingEntityEditor`, `architect.customHeadManager.user`, `architect.noPhysicsList.view`, `architect.fullBrightness`, `architect.viewdistance`, `architect.resourcePackSwitcher`, `architect.place.halfDoor`, `architect.place.halfBed`, `architect.place.torch`, `architect.place.plant`, `architect.place.pistonExtension`, `architect.place.burningFurnace`, `architect.interactDragonEgg`. Also eight nodes with no effect: `architect.place.doubleSlab`, `architect.place.sixSidedLog`, `architect.get.doors`, `architect.get.food`, `architect.get.logs`, `architect.get.misc`, `architect.get.plants`, `architect.get.slabs`. |
| `architect.ranks.artist` | adventurer | `architect.bannerEditor.save`, `architect.armorStandEditor.trusted`, `architect.chunkupdate`, `architect.get.private`, `architect.weselect` |
| `architect.ranks.designer` | artist | `architect.info`, `architect.copypaste`, `architect.randomiser.user`, `architect.weSchemViewer`, `architect.voxel.viewer`, `architect.voxel.stencilListEditor`, `architect.get.public`, `architect.inventory.save`, `architect.noPhysicsList.exception` |
| `architect.ranks.headDesigner` | designer | `architect.reload`, `architect.weather`, `architect.maplayers`, `architect.resourcePackAdmin`, `architect.noPhysicsList.edit`, `architect.randomiser.allowMaterials`, `architect.voxel.delete`, `architect.customHeadManager.manager`, `architect.armorStandEditor.delete`, `architect.bannerEditor.delete`, `architect.get.other`, `architect.inventory.edit`, `architect.inventory.other`, `architect.inventory.reload`, `architect.inventory.upload`, `architect.inventory.download`, `architect.itemBlock`, `architect.itemBlock.remove`, `architect.itemBlock.region.create`, `architect.itemBlock.region.limit`, `architect.itemBlock.region.ignore` |
| `architect.*` | head designer | nothing more |

No bundle grants these nodes:

- biome tuning: `architect.biomeTune`, `architect.biomeTune.save`, `architect.biomeTune.publish`,
  `architect.biomeTune.admin`;
- the game mode switcher: `architect.gamemodeSwitcher` and its four children (default nobody, not even ops);
- `architect.invisibleBlocks`, `architect.lecternEditor`, `architect.inventory.ignoreProtection`,
  `architect.checknbt`;
- the copy size: `architect.copypaste.unlimited` and `architect.copypaste.limit.<n>`;
- `architect.chunkupdate.auto` and `architect.armorStandRollback`;
- `architect.itemTexture`, which has no effect.

Notes:

- Under Bukkit's own permission handling, `architect.*` is a plain node whose only child is the head designer bundle.
  It is not a wildcard, so it grants none of the nodes above. A permissions plugin that expands wildcards may treat it
  differently.
- The designer bundle grants `architect.copypaste` but no copy size, so `/copy` and `/cut` allow 0 blocks. Give each
  rank that copies an `architect.copypaste.limit.<n>` node too.
- `architect.interactDragonEgg` defaults to nobody, but the adventurer bundle grants it, so every rank (and every op,
  through the bundles) has it.

### All nodes

What each node allows in 3.2.0, as the code checks it. "Build permission" means TheGaffer's permission to build at
that spot. Bundle names are short: adventurer, artist, designer, head designer.

| Node | Default | Bundle | What it allows |
|---|---|---|---|
| `architect.*` | op | — | The head designer bundle, and so all four bundles. |
| `architect.armorStandEditor` | op | adventurer | `/armor`: every mode, copy and paste, listing and loading saved stands. Where armor stand protection is on, also hitting armor stands, and placing and using them (with build permission). |
| `architect.armorStandEditor.delete` | op | head designer | Renaming and deleting other players' saved armor stands. |
| `architect.armorStandEditor.trusted` | op | artist | Saving armor stands to files, renaming and deleting your own files. Includes `architect.armorStandEditor`. |
| `architect.armorStandRollback` | op | — | `/armor rollback`, which is unfinished in 3.2.0: it does not roll anything back. |
| `architect.bannerEditor` | op | adventurer | `/banner`, editing banners with a stick (with build permission), and deleting your own banner files. |
| `architect.bannerEditor.delete` | op | head designer | Deleting other players' banner files. |
| `architect.bannerEditor.save` | op | artist | `/banner save`. Includes `architect.bannerEditor`. |
| `architect.biomeTune` | op | — | The biome editor, and `/biometune` `info`, `set`, `unset`, `copy`, `undo`, `revert`, `status`, `preview` (your own view), `spares`, `claim` and `label`. |
| `architect.biomeTune.admin` | op | — | `/biometune spares add`, `selftest` and `debug`. |
| `architect.biomeTune.publish` | op | — | `/biometune publish`, and `/biometune preview <player>`. |
| `architect.biomeTune.save` | op | — | `/biometune save`: writing a biome to its datapack file. |
| `architect.checknbt` | op | — | `/architect checkNBT`. |
| `architect.chunkupdate` | op | artist | `/chunkupdate`, which does nothing in 3.2.0. Also needed for `/chunkupdate auto`. |
| `architect.chunkupdate.auto` | op | — | `/chunkupdate auto`, together with `architect.chunkupdate`. |
| `architect.copypaste` | op | designer | `/copy`, `/cut`, `/paste`, `/undo`, `/redo`, `/flip` and `/rot`. |
| `architect.copypaste.unlimited` | op | — | Copying and cutting selections of any size. |
| `architect.customHeadManager.manager` | op | head designer | `/chead` `reviewList`, `review`, `accept`, `reject`, `delete`, `rename`, `setCollection`, `reload` and `upload`. Needs `architect.customHeadManager.user` as well. |
| `architect.customHeadManager.user` | op | adventurer | `/chead` `help`, `warp`, `list` and `submit`, and taking a copy of a head by clicking it with a stick. |
| `architect.cycleBlocks` | op | adventurer | Editing block states with a stick, and stepping through item block models (with build permission). |
| `architect.fullBrightness` | op | adventurer | `/fbt`. |
| `architect.gamemodeSwitcher` | false | — | All four game modes with the F3+F4 switcher, without op. Includes the four nodes below. Players with Paper's `minecraft.command.gamemode` also get the switcher. |
| `architect.gamemodeSwitcher.adventure`, `architect.gamemodeSwitcher.creative`, `architect.gamemodeSwitcher.survival` | false | — | That game mode with the F3+F4 switcher, without op. |
| `architect.gamemodeSwitcher.spectator` | false | — | Spectator mode with F3+F4 or F3+N, without op. Give another mode's node with it: without one, a player cannot switch out of spectator mode. |
| `architect.get.armor` | op | adventurer | `/get armor`. |
| `architect.get.doors`, `architect.get.food`, `architect.get.logs`, `architect.get.misc`, `architect.get.plants`, `architect.get.slabs` | op | adventurer | No effect in 3.2.0. |
| `architect.get.head` | op | adventurer | `/get head`. |
| `architect.get.other` | op | head designer | Publishing, unpublishing and deleting other players' kits. |
| `architect.get.private` | op | artist | `/get pcreate`: private kits. |
| `architect.get.public` | op | designer | `/get create`, `publish` and `unpublish`. |
| `architect.getCommand` | op | adventurer | `/get`: listing and taking kits. |
| `architect.hangingEntityEditor` | op | adventurer | Breaking paintings and item frames (with build permission). Where hanging entity protection is on, also placing them, and using or hitting item frames. |
| `architect.help` | true | adventurer | `/architect help`. |
| `architect.info` | op | designer | `/architect blockdata`, `dev` and `world`, and the `/architect` line in the help. |
| `architect.interactDragonEgg` | false | adventurer | Breaking a dragon egg in creative mode (with build permission) where the dragon egg switch is on. |
| `architect.inventory` | op | adventurer | `/inv`: opening the block, head and saved inventories. |
| `architect.inventory.download` | op | head designer | `/inv download`. |
| `architect.inventory.edit` | op | head designer | The in-game inventory editor: Shift+right-click in a block inventory. |
| `architect.inventory.ignoreProtection` | op | — | Changing a container's contents without build permission where chest protection is on. |
| `architect.inventory.open` | op | adventurer | Opening containers whose `inventoryAccess` is `BUILDER` or `EXCEPTION` (with build permission). |
| `architect.inventory.other` | op | head designer | Deleting other players' saved inventories. |
| `architect.inventory.reload` | op | head designer | `/inv reload`. |
| `architect.inventory.save` | op | designer | `/inv create` and `/inv delete`. |
| `architect.inventory.upload` | op | head designer | `/inv upload`. |
| `architect.invisibleBlocks` | op | — | Placing barrier, light and structure void blocks, in every world. Items from Architect's build inventories need no permission. |
| `architect.itemBlock` | op | head designer | `/itemblock`; every subcommand needs it. |
| `architect.itemBlock.region.create` | op | head designer | `/itemblock create` and `/itemblock delete`. |
| `architect.itemBlock.region.ignore` | op | head designer | Placing item blocks in a chunk that has reached its limit. |
| `architect.itemBlock.region.limit` | op | head designer | `/itemblock limit`, for regions and for the world's base limit. |
| `architect.itemBlock.remove` | op | head designer | `/itemblock remove`. |
| `architect.itemTexture` | op | — | No effect in 3.2.0. |
| `architect.itemTexture.manager` | op | — | No effect in 3.2.0. |
| `architect.lecternEditor` | op | — | Taking a lectern's book (not a copy) and putting books on lecterns, without build permission. |
| `architect.maplayers` | op | head designer | `/architect maplayers refresh`. |
| `architect.noPhysicsList.edit` | op | head designer | `/nophy add` and `/nophy remove`. Includes `architect.noPhysicsList.view`. |
| `architect.noPhysicsList.exception` | op | designer | `/nophy exception`. Needs `architect.noPhysicsList.view` as well. |
| `architect.noPhysicsList.view` | op | adventurer | `/nophy`, `/nophy list` and `/nophy help`. |
| `architect.paintingEditor` | op | adventurer | Changing a painting's motif with a stick (with build permission). |
| `architect.place.burningFurnace` | op | adventurer | Placing a lit furnace from a furnace named `Burning…`. |
| `architect.place.doubleSlab` | op | adventurer | No effect in 3.2.0. |
| `architect.place.halfBed` | op | adventurer | Placing half beds from a red bed named `Half…`. |
| `architect.place.halfDoor` | op | adventurer | Placing half doors from a door named `Half…`. |
| `architect.place.pistonExtension` | op | adventurer | Placing piston heads from a piston named `Table…` or `Wheel…`. |
| `architect.place.plant` | op | adventurer | Placing plants named `Placeable…` on any block. |
| `architect.place.sixSidedLog` | op | adventurer | No effect in 3.2.0. |
| `architect.place.torch` | op | adventurer | Placing redstone torches from a torch named `Unlit…` (they come out lit in 3.2.0). |
| `architect.randomiser.allowMaterials` | op | head designer | `/random allow` and `/random deny`. Includes `architect.randomiser.user`. |
| `architect.randomiser.user` | op | designer | `/random`, except `allow` and `deny`. |
| `architect.ranks.adventurer`, `architect.ranks.artist`, `architect.ranks.designer`, `architect.ranks.headDesigner` | op | — | The rank bundles; see [Rank bundles](#rank-bundles). |
| `architect.reload` | op | head designer | `/architect reload`, `version`, `reloaddata`, `eLog` and `placeAllBlockStates`, and everything `architect.info` allows. |
| `architect.resourcePackAdmin` | op | head designer | `/rp server`, `calcsha`, `release` and `dropdb`, and the region commands `list`, `create`, `edit` and `remove`. Includes `architect.resourcePackSwitcher`. |
| `architect.resourcePackSwitcher` | op | adventurer | `/rp <pack>`, `auto`, `reset`, `client`, `variant` and `help`. |
| `architect.signEditor` | op | adventurer | `/sign`, and selecting a sign by left-clicking it with a stick (with build permission). |
| `architect.switchstick` | op | adventurer | `/switchstick`. |
| `architect.viewdistance` | op | adventurer | `/viewdistance`. |
| `architect.voxel.delete` | op | head designer | `/vv delete`. Includes `architect.voxel.viewer`. |
| `architect.voxel.stencilListEditor` | op | designer | `/sl`. |
| `architect.voxel.viewer` | op | designer | `/vv list` and `/vv stencil`. |
| `architect.weSchemViewer` | op | designer | `/schlist`. |
| `architect.weather` | op | head designer | `/architect weather`. |
| `architect.weselect` | op | artist | `/weselect`. |
| `pluginutil.developer` (PluginUtils) | op | — | PluginUtils' `/dev`, including `/dev MCME-Architect`. |

**A node the code checks that plugin.yml cannot declare.** Bukkit gives an undeclared node to ops only, unless a
permissions plugin grants it.

| Node | Default | What it allows |
|---|---|---|
| `architect.copypaste.limit.<n>` | not set | `/copy` and `/cut` of selections up to `<n>` blocks, for example `architect.copypaste.limit.50000`. The highest `<n>` among a player's nodes applies; with none, the limit is 0. Ops have no limit node, but they have `architect.copypaste.unlimited`. |

---

## Troubleshooting

Each entry gives the symptom, the cause and the fix.

### Server, logs and reloading

- **No Architect messages in the server console.** Cause: Architect writes to its own log files by default. Fix: read
  the newest `plugins/MCME-Architect/logs/architect_*.log`, or set `logging.file.alsoServerLog: true` and restart.
- **Architect does not enable, or `/architect reload` ends with an internal error.** Cause: one of
  `logging.file.maxSizeMB` from 2048 to 4095 (or below 0); a line in `viewDistances.dat` that does not start with a
  player UUID followed by a distance (a blank line counts); a `doubleSlabReplacements` entry without `from` or `to`, or with block data that does not
  parse; or PluginUtils missing. Fix: read the server log and Architect's newest log, correct the file, and restart.
- **A setting did not change after `/architect reload`.** Cause: some settings are read only at start: `logging.*`,
  `biomeTuning.*`, `rpSettingsDatabase`, `entityLogger.*`, `CopyPasteUndoLimit`, whether `/fbt` exists, and hand
  edits of `noPhysicsList`. Fix: restart the server; see [Reloading](#reloading).
- **`defaultWorldConfig.yml` lost its settings.** Cause: the file had a YAML error at a start or a reload, and Architect
  saved a fresh skeleton over it. Every switch then reads as on again. Fix: restore it from your backup, and check the
  YAML before the next reload.
- **Clickable chat lines (help pages, lists, buttons) do nothing.** Cause: PluginUtils is older than 2.0.5. Fix:
  update PluginUtils.
- **`/fbt` only prints `/fbt`.** Cause: `modules.command.fullBrightness` was off in `WorldConfig/server.yml` (or in
  `defaultWorldConfig.yml`) when the server started, so the command has no handler. Fix: switch it on there and
  restart.

### World settings

- **Dropped items vanish.** Cause: `modules.environment.dropBlocking` is on, which deletes dropped items. Fix: set it
  to `false` in that world's file (or in `defaultWorldConfig.yml`) and run `/architect reload`.
- **Enchantments disappear from items.** Cause: `modules.protection.blockOpItems` is on, and in 3.2.0
  `allowedEnchantments` never matches, so every enchantment is removed. It is read from the first loaded world only.
  Fix: set `blockOpItems: false` in the main world's file or in `defaultWorldConfig.yml`, and run `/architect reload`.
- **Blocks ignore their neighbours after a fresh install: torches, plants and sand stay put when the block below them
  is removed.** Cause: `noPhysicsListInverted` is on, so the no-physics list names the blocks that keep physics, and
  the list is empty. Fix: set `modules.environment.noPhysicsListInverted: false` (the list then names the blocks
  without physics), or `noPhysicsListEnabled: false`, and run `/architect reload`. Or list the blocks that should keep
  physics.
- **Levers, buttons, lamps and doors ignore redstone power.** Cause: Architect freezes redstone power on the whole
  server, on purpose: the resource packs use redstone block states as decoration. Fix: none in 3.2.0.
- **No mobs, no weather changes, no fire spread, no leaf decay.** Cause: the environment switches are on by default.
  Fix: switch off the ones you do not want; see [Module switches](#module-switches).
- **Players cannot open furnaces, smokers, blast furnaces, brewing stands, hoppers or dispensers.** Cause:
  `inventoryAccess` sets them to `BUILDER` or `EXCEPTION`. Fix: give `architect.inventory.open` and build permission,
  put `EXCEPTION` containers in a redstone exception area (`/nophy exception redstone <name>`), or set the type to
  `'TRUE'`.

### Building tools

- **`/copy` says "You are allowed to copy 0 blocks only."** Cause: the player has no `architect.copypaste.limit.<n>`
  node; no bundle grants one. Fix: grant `architect.copypaste.limit.<n>` with a large enough `<n>`, or
  `architect.copypaste.unlimited`.
- **`/undo` or `/redo` acts on WorldEdit's history instead.** Cause: WorldEdit has commands with the same names. Fix:
  use `/un` and `/re`, or `/mcme-architect:undo` and `/mcme-architect:redo`.
- **Commands answer "This feature needs WorldEdit, which is not installed on this server."** Cause: WorldEdit is not
  installed. Fix: install WorldEdit and restart.
- **The stick says "The stick is disabled. You can turn it on with /switchstick."** Cause: each player's stick starts
  off. Fix: run `/switchstick` once.
- **With a stick in hand, doors, buttons and block breaking do nothing.** Cause: where `modules.command.cycleBlocks` is
  on, every block click made with a stick is cancelled. Fix: hold something else, or switch the module off in that
  world.
- **A build inventory tab is empty or gone after an edit in the in-game editor.** Cause: in 3.2.0 the editor can write
  a value that cannot be read back. Changing an entry's item writes `itemMaterial: !!org.bukkit.Material {}`, and
  answering `yes` to listing a collection entry in its tab writes a `!!…SpecialBlock {}` value under
  `collection.indirect`. At the reload that follows, Architect skips the whole file and logs an error for it. Fix: open
  `inventories/block/<pack>/<tab>.yml`, replace the broken value with the material name (for example
  `itemMaterial: OAK_STAIRS`) or the base block's id, and run `/inv reload`. Until then, change items by hand rather
  than in the editor.
- **Item blocks are refused everywhere in a new item block region.** Cause: `/itemblock create` makes a region with
  limit 0. Fix: `/itemblock limit <name> <n>`.
- **`/chunkupdate` says "Manual chunk updates are not available."** Cause: the radius form does nothing in 3.2.0.
  Automatic resends (`modules.chunkupdate.auto`) still work. Fix: rejoin, so the client loads the chunks again.
- **`/inv upload` fails at once with `exitCode=-1`.** Cause: `config.yml` has no `inventoryUpload` section (the bundled
  file has none), or the server runs Windows. Fix: add `inventoryUpload` with `script` and `path`, on a Linux server.
- **`/chead warp` says there is no gallery after a restart.** Cause: Architect starts before the worlds load, so it
  cannot find the gallery's world at start. Fix: run `/chead reload` or `/architect reload` once after each restart.
- **Builders cannot use F3+F4.** Cause: the game mode switcher needs ProtocolLib, the switch
  `modules.environment.gamemodeSwitcher`, and an `architect.gamemodeSwitcher` node given explicitly (it defaults to
  nobody, and ops use the game's own switcher). Fix: check all three. When it works, Architect's log says
  `The game mode switcher for builders is on.` at start.

### Resource pack problems

- **No resource pack when joining the network.** Cause: MCME-Connect is missing, so Architect's join handling does not
  run. Fix: install MCME-Connect and restart. Also, after `/rp server` changes a pack's address, players who join
  outside every RP region get no pack until they enter a region or use `/rp <pack>`.
- **Players' pack choices are forgotten.** Cause: `rpSettingsDatabase` still holds the placeholders (`xxx`), the
  database does not answer (Architect's log says so), or its settings changed without a restart. Fix: set the
  database, check the connection, and restart.
- **`/rp server` answers "Unknown Minecraft version".** Cause: `protocolVersions.yml` is from an older Architect and
  does not know the version. Fix: delete the file and restart, or add the line by hand and run `/architect reload`.

### Biomes and maps

- **Biome changes do not show.** Cause: edits are live on the server, but a client sees them only after a refresh.
  Fix: `/biometune preview` for your own view, `/biometune publish` for everyone in your world, or rejoin. Save with
  `/biometune save <biome>`: unsaved edits are lost at restart. Other servers are not changed. If preview answers that
  Paper refused to reconfigure you, rejoin.
- **`/biometune` answers "Biome tuning is unavailable on this server version".** Cause: biome tuning could not start on
  this Paper build; the rest of Architect keeps working. Fix: read the problem in Architect's log. Biome tuning reads
  Minecraft's internals, so after a Paper update it may need a newer Architect.
- **Labels, claims or `/biometune spares add` fail with "there is no pack folder".** Cause: the folder named in
  `biomeTuning.targetPack` (default `bukkit`) does not exist in the main world's `datapacks/` folder; Paper 26.2 no
  longer creates it. Fix: create it with a `pack.mcmeta`, or set another pack and restart.
- **Map layers are missing.** Cause: dynmap is not enabled; or the layers are switched off in the map's layer list (all
  start hidden); or `config.yml` has no `dynmap` section and no `mapLayers` entries, which leaves the RP-region and
  item-block-limit layers off; or a drawing error switched all layers off. Fix: check Architect's log. After
  changing the `dynmap` or `mapLayers` sections, run `/architect reload`; after a drawing error, run
  `/architect maplayers refresh`.
