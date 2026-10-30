# RakunLoots

A Minecraft server plugin for Paper that summons loot boxes into your worlds. It also runs on Spigot and Folia,
Minecraft 1.18 to 26.2.

A loot box drops into your world with a particle animation, floats a hologram above it, glows through walls, and
shoots a beam into the sky so players can find it. The first player to reach it takes the loot. You decide what is
inside, how rare each item is, where it can land, how long it stays, and who may open it.

Everything is set in YAML files. No coding needed.

## Contents

- [Features](#features)
- [Installing](#installing)
- [How a loot box works](#how-a-loot-box-works)
- [Where loot boxes come from](#where-loot-boxes-come-from)
- [Commands](#commands)
- [Permissions](#permissions)
- [Config files](#config-files)
- [Making your own loot](#making-your-own-loot)
- [World settings](#world-settings)
- [Holograms](#holograms)
- [Languages and messages](#languages-and-messages)
- [Menus](#menus)
- [Warnings](#warnings)
- [Storage](#storage)
- [Reloading and updating](#reloading-and-updating)
- [Version compatibility](#version-compatibility)
- [Optional plugins](#optional-plugins)
- [For developers](#for-developers)
- [License](#license)

## Features

- **Loot boxes on a timer.** Each world summons a random loot every few minutes, near active players or around spawn.
- **Summon items.** Give players an item (a beacon, a compass, anything) that calls a loot box down next to them.
- **Two ways to open.** A box spills its items on the ground, or opens as a shared chest-style inventory.
- **Owner lock.** The player who summoned the box gets a head start. Others must wait for the lock to run out.
- **Six placement rules.** On the surface, in caves, buried, deep in rock, underwater, or floating in the air.
- **Weighted loot rolls.** Set how many items a box gets, how likely each one is, and which items always appear.
- **Custom items.** Use vanilla items with your own names, lore, and enchantments, or items from MMOItems, ItemEdit,
  ExecutableItems, Oraxen, and ItemsAdder.
- **Visuals.** Particle animations, holograms, a coloured glow through walls, and a guardian or end crystal beam.
- **Loots wiki.** Players browse every loot in their world and see the exact chance of each item.
- **Claim menu.** Summon items a player missed, because their inventory was full or they were offline, wait for them.
- **Survives restarts.** Live loot boxes, their contents, and their timers come back after a restart.
- **Three languages built in.** English, Vietnamese, and Simplified Chinese. Add your own.
- **No required dependencies.** Holograms, glow, and beams work with the plugin alone.

## Installing

1. Stop your server.
2. Put `RakunLoots-<version>-all.jar` into the `plugins` folder.
3. Start the server. RakunLoots writes its files to `plugins/RakunLoots/`.
4. Join the world. With the shipped settings, a loot box arrives in `world` every 7 minutes while a player is online.
5. Try `/rkl summon airdrops` to summon one right away, or `/rkl give airdrops` to get its summon item.

The server needs Java 17 or newer. See [Version compatibility](#version-compatibility).

## How a loot box works

**Summon.** The plugin finds a spot, plays the loot's summon animation there, and then places the box's block.
While the animation plays, no other box can take that spot.

**Lock.** For the first seconds of its life, only the owner may open or break the box. A player with
`rakunloots.bypass.lock` may always open it. A locked box also survives explosions. A box from a world timer has no
owner, so nobody but bypass players opens it until the lock ends.

**Open.** Right-click the box.

- `drop` mode: the items spill onto the ground and the box disappears.
- `inventory` mode: a shared inventory opens. Several players can take from it. The block does not have to be a chest.

**Life.** Each box has a life in seconds. When it runs out, the box leaves. A loot can spill its items when that
happens, or let them vanish. A negative life, such as `-1`, never ends.

**Ending.** A box also ends when a player breaks it, when an explosion hits it (if its loot allows it), when another
plugin replaces its block, when its inventory is emptied (if its loot says so), or when an admin removes it.

**Visuals.** While it lives, a box can show:

- a hologram with its name, owner, lock time, and life left,
- a glow through walls in one of 16 colours,
- a beam to the sky, from a guardian or an end crystal,
- particle animations when it is summoned, while it lives, when it unlocks, when it opens, and when it ends.

**Chunks.** A live loot box keeps its chunk loaded so its timer keeps running. You can turn this off per world.

## Where loot boxes come from

| Source | Who owns the box | Where it lands |
| --- | --- | --- |
| World timer | Nobody | Near a random active player, or around world spawn |
| Summon item | The player who used it | Around that player |
| `/rkl summon` | Nobody | Like a timer summon, or around a named player |
| `/rkl timer <world> now` | Nobody | Runs that world's timer summon right now |

A summon item is used up when the player right-clicks it. If no spot is found, the player gets the item back.

## Commands

The admin command is `/rkl` (alias `/rkloots`). Players only see and tab-complete the commands they may run.
Arguments in `<>` are required, in `[]` optional.

| Command | What it does |
| --- | --- |
| `/rkl help [command]` | Lists the commands you can run, or explains one in detail. |
| `/rkl list` | Lists every loaded loot id. |
| `/rkl give <loot> [player] [amount]` | Gives summon items. What does not fit waits in the claim menu. |
| `/rkl summon <loot> [world or player]` | Summons a loot now. Without a target it searches your own world. |
| `/rkl timer <world> now [--force]` | Resets a world's timer and runs a timer summon now. `--force` skips the player count and box limit. |
| `/rkl remove all` | Removes every loot box on the server. |
| `/rkl remove world <world>` | Removes every loot box in one world. |
| `/rkl remove loot <loot>` | Removes every box of one loot. |
| `/rkl remove near <radius>` | Removes every box within that many blocks of you. |
| `/rkl warnings [low or high or severe] [page]` | Lists config problems found since the last reload. |
| `/rkl reload` | Reloads every config file. |

Removed boxes drop nothing.

Player commands:

| Command | What it does |
| --- | --- |
| `/claimsummon` (alias `/rklclaim`) | Opens the claim menu with the summon items you are owed. |
| `/claimsummon all` | Takes every summon item you are owed at once. |
| `/loots` (alias `/rklloots`) | Opens the loots wiki. |

All command names and aliases are set in `config.yml` under `admin_command`, `claim_command`, and `wiki_command`.
Changing a name needs a restart.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `rakunloots.admin` | op | Every admin command below. |
| `rakunloots.command.help` | op | `/rkl help` |
| `rakunloots.command.reload` | op | `/rkl reload` |
| `rakunloots.command.list` | op | `/rkl list` |
| `rakunloots.command.give` | op | `/rkl give` |
| `rakunloots.command.summon` | op | `/rkl summon` |
| `rakunloots.command.timer` | op | `/rkl timer` |
| `rakunloots.command.warnings` | op | `/rkl warnings` |
| `rakunloots.command.remove` | op | `/rkl remove` (plus one of the four below) |
| `rakunloots.command.remove.all` | op | `/rkl remove all` |
| `rakunloots.command.remove.world` | op | `/rkl remove world` |
| `rakunloots.command.remove.loot` | op | `/rkl remove loot` |
| `rakunloots.command.remove.near` | op | `/rkl remove near` |
| `rakunloots.claim` | everyone | `/claimsummon` |
| `rakunloots.wiki` | everyone | `/loots` |
| `rakunloots.bypass.lock` | op | Open and break any locked loot box. |

The defaults for `rakunloots.claim` and `rakunloots.wiki` are set with `permission_default` in `config.yml`:
`true`, `false`, `op`, or `not op`.

## Config files

```text
plugins/RakunLoots/
├── config.yml        General settings: holograms, commands, storage, banned blocks, debug
├── worlds.yml        Per-world timers, limits, and which loots appear
├── animations.yml    Particle animations, referenced by id from loot files
├── blocks.yml        Named block groups, such as @solid or @plants
├── warnings.yml      How serious each kind of config problem is
├── loots/            One file per loot. The file name is the loot id
├── languages/        en.yml, vi.yml, zh.yml, and any you add
└── menus/            claim.yml and wiki.yml, the layout of both menus
```

Every shipped file is commented line by line. When in doubt, read the comments in the file itself.

Colour codes use `&`, such as `&6&l`. Hex colours use `<#fad778>`.

## Making your own loot

Copy a file in `loots/`, rename it, and edit it. The file name (without `.yml`) is the loot id. Run `/rkl reload`.

Seven example loots ship with the plugin. Each one shows different options:

| Loot | Shows |
| --- | --- |
| `airdrops` | Every key, explained. Start here. |
| `treasure_chest` | Inventory mode, cave placement, custom vanilla items, message overrides, custom hologram lines. |
| `buried_stash` | Buried placement, no lock, visuals turned off. |
| `deep_vault` | Deep in rock, a long lock and life, an enchanted book. |
| `sunken_treasure` | Underwater placement. |
| `sky_lantern` | A box floating in mid-air. |
| `head_trophy` | A player head with a custom skin as the box. |

### A small loot

```yaml
display_name: '&6&lGold Crate'
description:
  - '&7A crate full of gold.'
language: en

open_mode: drop        # drop or inventory
lock: 30               # seconds only the owner may open it
life: 600              # seconds before it leaves, -1 for never (logs one low warning)
summon_delay: 60       # ticks the summon animation plays before the block appears
block: barrel

roll:
  min: 1               # how many weighted entries are picked, at least
  max: 3               # and at most
  entries:
    always_gold:
      weight: -1       # below zero: always included, on top of min/max
      amount: 8
      item:
        id: gold_ingot
    rare_sword:
      weight: 10       # higher weight, more likely
      amount: 1
      item:
        id: diamond_sword
        name: '&bCrate Blade'
        enchants:
          - 'sharpness:3'
    bread:
      weight: 40
      amount: 6
      item:
        id: bread

summon_item:
  id: nether_star
  name: '&6Gold Crate Beacon'

summon_settings:
  summon_type: top_surface
  y_range: '-64:320'
  player_ring:
    min: 4
    max: 16

hologram:
  enabled: true
  height: 1.5

glow:
  enabled: true
  color: GOLD

laser:
  type: GUARDIAN
  view_distance: 50

animations:
  summon: [RED_SPHERE]
  open: [WHITE_SPHERE]
```

Then add it to a world in `worlds.yml`, for example `gold_crate: 25`, so the world timer can pick it.

### Placement rules

`summon_settings.summon_type` picks where a box may land. Each rule has its own options, shown in the shipped loots.

| Rule | Lands | Example loot |
| --- | --- | --- |
| `top_surface` | On the highest block, as if dropped from the sky | `airdrops` |
| `any_surface` | On any solid floor with room above, caves included | `treasure_chest` |
| `buried` | Just under the top surface | `buried_stash` |
| `deep_buried` | Anywhere inside solid ground | `deep_vault` |
| `underwater` | In water | `sunken_treasure` |
| `anywhere` | Any free spot, mid-air included | `sky_lantern` |

`replaceable_blocks` and `avoid_surfaces` take block ids, block tags like `'#minecraft:flowers'`, and block groups
from `blocks.yml` like `'@plants'`. Quote every entry.

Some blocks cannot be a loot box, such as liquids, carpets, redstone, and doors. The list is `banned_loot_blocks` in
`config.yml`, which points at the `@banned_loot_blocks` group in `blocks.yml`.

### Items from other plugins

Any item in a roll or a summon item can come from another plugin. Set `source` to the plugin's source name:

```yaml
item:
  source: mmoitems
  id: SWORD:CUTLASS
```

| Source | Plugin |
| --- | --- |
| `vanilla` (default) | None. Supports `name`, `lore`, `enchants`, `flags`, `custom_model_data`, `unbreakable`, `damage`, `nbt` |
| `mmoitems` | MMOItems |
| `itemedit` | ItemEdit |
| `executableitems` | ExecutableItems |
| `oraxen` | Oraxen |
| `itemsadder` | ItemsAdder |

The id format is whatever that plugin uses for its items.

### Loot end rules

```yaml
loot_end:
  allow_explosion: true   # an explosion may end the box once it is unlocked
  drop_block: true        # the box drops its block as an item when it ends
  timeout_spill: true     # when its life runs out, the items fall instead of vanishing
  empty_removal: true     # inventory mode: the box ends once emptied and closed
```

Any flag left out is off.

## World settings

`worlds.yml` holds one section per world. A world not listed gets no timer summons.

| Key | Meaning |
| --- | --- |
| `time_between_loot_spawn` | Seconds between timer summons. At least 5. |
| `loot_spawn_limit` | Most live loot boxes in this world at once. |
| `minimum_player_to_spawn_loot` | Players needed before the timer summons. |
| `also_count_players_from` | Other worlds whose players count toward that minimum. |
| `loots` | Which loots the timer picks, as `loot_id: chance`. A higher number is picked more often. |
| `broadcast_remove` | Tell the server when a box leaves. |
| `public_location_*_offset` | How rough the location in the summon broadcast is, so players must search. |
| `summon_search.mode` | `smart`: near a random active player. `spawn`: in a ring around world spawn. |
| `summon_search.unloaded_chunks` | `generate` (shipped), `load`, or `skip`. See the file comments before changing it. |

**Smart summon** picks a random active player and lands the box in a ring around them. Players who have not moved
for `idle_seconds` are skipped. Players near the middle of the group are a little more likely to be picked.

## Holograms

The plugin draws holograms itself, with no other plugin needed, on every supported version.

`hologram_provider` in `config.yml` takes `auto` (default), `packets`, `decentholograms`, `holographicdisplays`,
`fancyholograms`, or `none`. `auto` uses the plugin's own holograms and only falls back to another plugin when it
cannot. Loot box holograms are never saved into another plugin's files.

Each loot can set its own hologram height, view range, and lines with `{name}`, `{owner}`, `{lock}`, and `{life}`.

## Languages and messages

The server language is English unless you add a `language:` line to `config.yml`, such as `language: vi`.
Shipped: `en`, `vi`, `zh`. To add one, copy `languages/en.yml` to a new file such as `languages/de.yml`, translate it,
and set `language: de`. A missing key falls back to English.

A loot file can reword any message for that loot alone under `messages:`. See `loots/treasure_chest.yml`.

## Menus

- **Claim menu** (`/claimsummon`): one icon per loot a player is owed summon items for. Click to take them.
- **Loots wiki** (`/loots`): every loot in the player's world, rarest first. Click one to see its contents and the
  exact chance of each item. A loot can hide itself with `wiki: false`, or pick its icon with `wiki.icon`.

Both layouts, including rows, slots, filler, buttons, and click sounds, are in `menus/claim.yml` and `menus/wiki.yml`.

## Warnings

When a config file has a mistake, the plugin keeps running and tells you what it skipped. A misspelled name gets a
suggestion with the closest matches.

- `/rkl warnings` lists every problem since the last reload, most serious first.
- Add `warning-level: high` (or `low`, `severe`) to `config.yml` to choose which problems reach the console. The
  default is `low`, which shows all of them.
- `warnings.yml` sets how serious each kind of problem is, or turns it `off`. Change it only if you know a warning is
  harmless on your server.
- `debug: true` in `config.yml` logs every summon, timer run, box end, and admin command to the console.

## Storage

Live loot boxes and unclaimed summon items are saved to SQLite by default, with no setup. The SQLite file is backed up
on start, and `backups_kept` copies are kept in the `backups` folder.

To use MySQL, set `storage.type: mysql` and fill in `host`, `port`, `database`, `user`, and `password`. Several servers
can share one database: give each its own `table_prefix`.

## Reloading and updating

Config changes use RakunLoots's own reload, `/rkl reload` with the default admin command (see `admin_command` in
`config.yml`). It closes every open menu while the plugin is enabled.

Updating, replacing, or removing the jar requires a full server stop and start. Live plugin unload/reload tools are
unsupported, especially on Folia with menus open. After an unsupported live disable, items in open menus can remain
accessible until the server stops. Restarting the server is the only safe way to apply a jar change.

## Version compatibility

One jar runs on every supported version. The server needs Java 17 or newer.

| | Supported |
| --- | --- |
| Minecraft | 1.18 to 26.2 |
| Paper | Yes |
| Spigot | Yes |
| Folia | Yes, built against the Folia 1.20.1 API |
| Purpur and other Paper forks | Should work, not tested |
| 1.17 and older | No |

### Tested on

Tested means the plugin was started on a real server and passed an automated in-game test: every shipped loot summoned
and placed, commands, holograms, lock, removal, reload, and restart, with no RakunLoots error or warning.

| Minecraft | Paper | Spigot | Folia |
| --- | --- | --- | --- |
| 26.2 | ✅ Tested | ✅ Tested | ✅ Tested |
| 26.1.2 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.21.11 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.21.8 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.21.5 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.21.4 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.21.1 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.20.6 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.19.4 | ☑️ Tested, earlier build | ⬜ Not tested | ⬜ Not tested |
| 1.18.2 | ☑️ Tested, earlier build | ⬜ Not tested | ➖ No Folia build |
| Other 1.18 to 1.21 releases | ⬜ Not tested | ⬜ Not tested | ⬜ Not tested |

- ✅ **Tested**: the current build passed the full test, fresh install and after reload, on Paper 26.2 build 129,
  Spigot 26.2, and Folia 26.2 build 7.
- ☑️ **Tested, earlier build**: passed the full test before the claim menu and loots wiki were added. Not yet re-run
  on the current build.
- ⬜ **Not tested**: the plugin is built for this version, but nobody has run it there yet.

The plugin's own holograms are also checked by automated tests against the server code of every Minecraft release
from 1.18 to 26.2.

### Feature limits by version

| Feature | Limit |
| --- | --- |
| Holograms (built in) | Every supported version. Text displays from 1.19.4, armor stands below. |
| Glow | Every server from 1.19.4. Below 1.19.4, Paper only. Spigot 1.18 to 1.19.3 runs without glow. |
| Beam (laser) | Off on Folia, with one low warning. |
| Guardian beam | Shows as a crystal beam in a peaceful world. |

## Optional plugins

None are required. RakunLoots uses each one only when it is installed.

| Plugin | Used for | Limit |
| --- | --- | --- |
| MMOItems, ItemEdit, ExecutableItems, Oraxen, ItemsAdder | Items in loot rolls and summon items | |
| DecentHolograms | Holograms | 1.18 and newer. On Folia, only a version that declares Folia support. |
| HolographicDisplays | Holograms | Up to 1.20.4. Never on Folia. Uses its own view range. |
| FancyHolograms | Holograms | Paper 1.21.4 and newer on Java 25. Never on Spigot. On Folia, only a version that declares Folia support. |

---

## For developers

### Requirements

- **JDK 17 or 21** to run Gradle. The wrapper is Gradle 8.10.2, which does not run on JDK 25.
- **Internet access.** Gradle downloads the JDKs it needs (17, 21, and 25) through the Foojay toolchain resolver
  when they are not installed.
- **Disk space and time.** Each of the 18 packet adapters in `packet-adapters/` compiles against a Spigot server
  built by Spigot BuildTools (build 201, checksum pinned). The first build runs BuildTools once per adapter and installs
  each server into your local Maven repository (`~/.m2`). This takes a long time. Later builds reuse it.

### Building

```bash
./gradlew shadowJar
```

On Windows, use `gradlew.bat shadowJar`.

The plugin jar is `build/libs/RakunLoots-1.0-SNAPSHOT-all.jar`. Use the `-all` jar. The jar without `-all` lacks the
shaded libraries and packet adapters and does not run on a server.

### Testing

```bash
./gradlew check
```

`check` runs the core tests, the Folia tests, the FancyHolograms tests, the Java 17 loader test, and every packet
adapter's tests. It also fails the build when any class in the shipped jar is newer than Java 21. MySQL tests are
skipped when no MySQL server is configured.

To build and test in one go, as the release gate does:

```bash
./gradlew check shadowJar
```

### Running it on a test server

1. Build the jar.
2. Download a Paper, Spigot, or Folia server of a supported version.
3. Copy `build/libs/RakunLoots-1.0-SNAPSHOT-all.jar` into the server's `plugins` folder.
4. Start the server with a Java version that server needs (Java 17 for 1.18 to 1.20.4, 21 for 1.20.5 to 1.21.x, 25
   for 26.x).
5. Check the console for `[RakunLoots]` lines, then run `/rkl warnings` in game.
6. After a rebuild, stop the server, replace the jar, and start it again. Do not hot-reload the plugin.

### Project layout

| Path | Contents |
| --- | --- |
| `src/main` | The plugin, compiled to Java 17 against the Paper 1.18.2 API. |
| `src/folia` | The Folia server adapter and the block display glow, compiled against folia-api 1.20.1. |
| `src/fancy` | The FancyHolograms provider, compiled on JDK 25 to Java 21 bytecode. |
| `packet-adapters/<revision>` | One packet hologram adapter per Minecraft server revision, `v1_18_R1` to `v26_2`. |
| `src/main/resources` | Every shipped config, loot, language, and menu file. |
| `CONTEXT.md` | The project's glossary. Read it before naming anything. |
| `docs/adr/` | Architecture decisions, such as why the floor is Java 17 and why the 26.x code ships as Java 21. |
| `docs/rewrite/phases.md` | The rewrite plan, phase by phase. |

Issues are tracked on [GitHub](https://github.com/rakun68/RakunLoots/issues).

## License

[MIT](LICENSE).
