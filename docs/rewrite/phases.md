# Rewrite phases

Run one phase at a time: grill with docs, then spec, then tickets, then implement. Start a phase only after the one before it is in the tree.

Read `CONTEXT.md` and every ADR in `docs/adr/` before grilling a phase. The ADR for that phase is named below. A phase that contradicts an ADR stops and says so.

New modules land in `me.rakkoon69.rakunloots`, beside the current summon path. The current summon path keeps booting until phase 8 removes it.

Tests for each phase run in memory, at that phase's interface. A server is not required for those tests.

## Phase 1. Config, messages, and names

ADR 0003.

Load globals, the active language, world settings, and one loot file's message overrides. A missing key uses English. A loot override wins. A loot language clash warns and leaves the file unchanged. A fresh file uses the running server's names. An old name resolves, warns, and leaves the file unchanged.

Done. In-memory tests prove fallback, override, the loot-language warning, and the old-name warning.

## Phase 2. Item sources

ADR 0002, ADR 0004, and ADR 0007.

Ship the resolve interface, vanilla, and register. A hit carries the built item. Vanilla additions are applied there. Refuse a register call that uses a shipped name. Then ship MMOItems, ItemEdit, ExecutableItems, Oraxen, and ItemsAdder as adapters.

Done when a test resolves vanilla and a fake registered name, a shipped name cannot be replaced, a bad vanilla id misses, and an unknown enchant is skipped while the item remains.

## Phase 3. Loot

ADR 0003, ADR 0006, and ADR 0008.

Load one loot into an immutable value: open mode, lock, life, loot end rules, roll, and presentation. Message overrides stay on the config snapshot. A roll is weighted item references, not a flat list. Hologram, animations, summon search, the summon item, and commands stay off this value.

Done. In-memory tests prove a load of each open mode, roll correction, skipping a bad loot, and name-catalog resolution of the block and vanilla item ids.

## Phase 4. Live loot box storage

ADR 0005 and ADR 0009.

Roll one loot and save the result. Save and load one loot box: rolled contents, owner, remaining lock, location, loot id, remaining life. SQLite by default. MySQL as the second adapter. Copy the SQLite file before a reload and keep a few copies.

Done when a test saves a loot box, loads it, and gets the same rolled contents.

## Phase 5. Server adapters

ADR 0001, ADR 0002, and ADR 0010.

Paper and Spigot adapters compiled against the 1.18 API. A Folia adapter in its own source set, compiled against folia-api 1.20.1. World, chunk, and block access, player positions, and scheduling stay behind those adapters. Item sources may use Bukkit item types. A read returns one chunk view in plugin types. An unloaded chunk is skipped, loaded when generated, or generated, as the caller asks. Paper loads chunks off the main thread. Spigot reads on the main thread. Folia reads on the region that owns the chunk, and gathers player positions on each player's scheduler. Block writes wait for phase 7.

Done when a test drives all three adapters through a fake chunk loader and a recording scheduler, and sees those three threading rules.

## Phase 6a. Block groups

ADR 0003 and ADR 0011.

Load `blocks.yml` with the shipped groups. A block list names blocks, `@group`, or `#namespace:tag`. Names resolve through the name catalog. A group may contain groups. A cycle refuses every group in it with a severe warning. A loot list that names a refused group drops that entry with a high warning.

Done when in-memory tests prove nesting, tag entries, old-name resolution, cycle refusal, and a loot list naming a refused group.

## Phase 6b. Placement rules

ADR 0011.

Load summon rules per loot id, beside the loot value. Six placement rules: top surface, any surface, buried, deep buried, underwater, and anywhere, each with its own options. Per-rule `attempts` default in `config.yml`, and a loot may override it. `summon_type` takes a rule name only. Given one column of chunk views and the occupied check, a rule returns a spot or nothing.

Done when in-memory tests check each rule and its options on fake columns, and an occupied spot is refused.

## Phase 6c. Summon search

ADR 0002, ADR 0010, and ADR 0011.

Given a world, a loot, and a ring center or a server summon, return a spot or a miss with a reason. `worlds.yml` gains `summon_search`: spawn ring or smart summon, `unloaded_chunks` (`skip`, `load`, or `generate`, default `load`), and the smart summon settings. Load and generate pick a random column. Skip scans shuffled loaded chunks in the ring. Smart summon ignores idle players, weights players toward the median, and pulls the spot toward it. Old world keys load with a warning. The search runs on the async scheduler. Spigot loads at most one chunk per tick. No block is written.

Done when a test finds a spot on a fake world through a fake server adapter under each chunk setting, smart summon's pick and pull hold their bounds over a seeded random, idle players are skipped, and each miss reason appears.

## Phase 7a. Loot box lifecycle

ADR 0006, ADR 0009, and ADR 0010.

Summon, open, break, and loot end go through one interface in plugin types. Thin Bukkit listeners translate events into it. Phase 7 builds and tests the lifecycle and does not register it at enable. Phase 8 wires it in.

Summon takes a loot, a spot, and an optional owner. It rolls, writes the block, registers the box, and saves it. The world timer and the summon item wait for phase 8.

One registry maps world and block position to a box. It is filled from storage and from summons. The server adapter gains block writes on all three adapters: set a block, remove a block, drop stacks, and open or close the shared inventory. Folia runs them on the region that owns the block.

Drop open and inventory open. Inventory open always shows the shared inventory, even for a container block. The placed block holds no items. The title is the loot's display name. The size is the smallest multiple of 9 that fits the fixed slots and the stack count, up to 54. Unfixed stacks go into random free slots.

Lock and bypass. A player without access who clicks or breaks a locked box is cancelled and gets `still_locked`, with no particles. `rakunloots.bypass.lock` bypasses, and replaces the old `rakunloots.bypass_locked`. Sneak does not. A locked box resists explosions, even when its loot allows them.

Block item drop, as a plain presentation block, with head data kept. Timeout spill. Empty removal. Hoppers, hopper minecarts, pistons, fire, and mobs cannot take, move, or change the box. Vanilla block changes at the box, such as fading, forming, spreading, falling, and flowing, are cancelled.

A changed block ends the box as a break, without the block item, and warns high with the box id, loot id, position, and the block found. It is checked when the chunk loads, and on click, break, end of life, and explosion. The box's block is compared by id only, against the block stored at placement. A block is removed only while it still matches.

One global 1 second tick counts down life and lock and hands each loot end to the box's location. A box whose life ends in an unloaded chunk keeps its row at remaining life 0. It ends when its chunk loads. A timeout never loads a chunk.

Storage gains the placed block, with no migration, and a queued delete that the next flush writes. One async task every 60 seconds marks every live box and flushes. Disable flushes once.

`config.yml` gains `banned_loot_blocks`, a block list, default `@banned_loot_blocks`. `blocks.yml` ships that group: non-solid blocks, liquids, tall plants, doors, beds, falling blocks, and technical blocks. A loot whose presentation block is in the list is refused with a high warning. Summon rules may still name those blocks.

Done when in-memory tests cover both open modes, each loot end including a changed block, the lock against explosions and bypass, the queued save, a restart that keeps the rolled items and the placed block, a life that ends in an unloaded chunk, and a refused banned loot block.

## Phase 7b. Hologram seam and providers

ADR 0012.

One hologram seam. The loot file gains `hologram:` beside the loot, as summon rules do: `enabled`, `height`, `view_range`, and `lines`. The `lines` use `{name}`, `{owner}`, `{lock}`, and `{life}`. The line text uses default messages and message overrides. `hologram_unlocked`, `hologram_no_owner`, and `hologram_unending` fill a placeholder with no value. `config.yml` gains `hologram.view_range`, default 48 blocks. Lines refresh on the lifecycle's 1 second tick. The shipped `loots/airdrops.yml` gets its hologram back.

The lifecycle gains a box observer: shown, ticked, out of view, and ended. The hologram service subscribes to it, so the lifecycle knows nothing about holograms.

DecentHolograms, HolographicDisplays, and FancyHolograms are soft depend providers. Each reports whether it can run on this server. On Folia that needs `folia-supported: true` in the provider plugin. Each provider also checks its own version range. HolographicDisplays calls run on the main thread. The FancyHolograms provider sits in its own source set compiled for Java 25 and loads by class name only when FancyHolograms is present. No hologram is saved into another plugin's files. `hologram_provider` in `config.yml` takes `auto`, `packets`, `decentholograms`, `holographicdisplays`, `fancyholograms`, or `none`. `auto` is the default. It uses the packet provider when this version has an adapter, and otherwise the first installed provider that can run. A named provider that is missing falls back to `auto` with a high warning.

Done when each soft depend provider is driven through a fake plugin, `auto` picks and skips correctly on Folia, on Spigot, and across each provider's version range, and a missing named provider falls back with a warning.

## Phase 7c. Packet holograms

ADR 0001 and ADR 0012.

The plugin's own provider sends fake entity packets to players in range. Armor stands below 1.19.4. Text displays from 1.19.4. One Gradle subproject per NMS revision, from `v1_18_R1` to `26_2`, shaded into one jar and picked at runtime. Up to 1.21.11 they are built with `paperweight-userdev` from Mojang-mapped source and ship reobfuscated, so they run on Spigot and Paper. 26.x adapters use Mojang names and no reobfuscation. Each adapter targets its server's Java level. Viewer tracking sits outside the adapters: join, quit, teleport, world change, and range.

Done when each version adapter's tests build correct spawn, meta, and destroy packets against that version's dev bundle, and the tracker sends spawn and destroy on range, join, teleport, and world change through a fake packet sink.

## Phase 8a. Pending summons, the world timer, and the summon item

ADR 0006, ADR 0013, and ADR 0014.

No phase 8 issue patches the old summon path. At `4d49210` the plugin does not enable on Paper 26.2 or Spigot 26.2: the old loader cannot read the new `loots/airdrops.yml`, and the shaded GlowingBlocks throws on Spigot. Phase 8c removes the old path, which fixes both.

A summon reserves its spot when the search returns it, before the summon delay starts, and holds it until the block is written or the summon is abandoned. Every summon follows this: the summon item, the world timer, and admin summons. The delay never runs before the reservation. A summon is abandoned when its spot is already held, the block changed, the server refuses the block, the roll fails, the chunk unloads during the delay, or a reload or stop comes during the delay. It never searches again.

The world timer moves onto the new modules: `time_between_loot_spawn`, `minimum_player_to_spawn_loot` with `also_count_players_from`, and `loot_spawn_limit`. The world timer starts full on enable and on reload, and is not stored. The spawn limit counts every live loot box in that world and every pending summon there, and applies to timer summons only. The location broadcast keeps `public_location_vertical_offset`, `public_location_horizontal_offset`, and `broadcast_remove`, through default messages. The owner remove message comes back.

The summon item comes back to the loot file. A right click with the main hand summons its loot in the loot's `player_ring` around the player, who owns the box. The off hand is ignored. There is no cooldown, permission, or world limit. The item carries its loot id and the hash of the loot's summon item. An outdated summon item is rebuilt from the loot's current summon item, at the same amount, on use and on join. The item is taken at the click. A search that finds no spot, or an abandoned summon item summon, gives it back: to the inventory when the player is online and it fits, otherwise as an unclaimed summon item.

Storage gains unclaimed summon items: player, loot id, and amount. The claim command, with its name and aliases in `config.yml`, gives as many as fit and leaves the rest. A claim lowers the stored amount before it gives the items. A row whose loot is missing is kept and flagged. On join, a player with unclaimed summon items gets a message naming the command.

A box whose loot is missing runs on, as ADR 0014 says: stored life and lock, inventory open with its stored stacks, every loot end rule off, no hologram, one high warning per reload. At start, a stored box whose loot is missing registers and runs on the same way.

Done when two summon item summons with a delay, whose searches pick the same block on a fake world, place one loot box, and the second summon item goes back to the player. That test fails against a version that waits out the delay before it reserves. Done when a block changed during the delay, a chunk unloaded during the delay, and a reload during the delay each place nothing and give the item back, a full inventory and an offline player each get an unclaimed summon item, a timer summon on a held spot is refused, the spawn limit counts pending summons, an outdated summon item is rebuilt, a box whose loot was removed counts down and ends, and a claim gives what fits, keeps the rest, and survives a restart.

## Phase 8b. Animations, glow, and laser

ADR 0014.

Animations come back for all five moments: summon, while the box lives, unlock, open, and end. The summon animation plays during the summon delay, at the pending summon's spot. `animations.yml` keeps its format and its `SPHERE` and `EXPLOSION` types. A loot names its animations by id. Animation types sit in a registry by name, so a new type is one class and one registration. The animation service follows the lifecycle as an observer, beside the hologram service, so the lifecycle knows nothing about animations. The lifecycle reports pending summons, abandoned summons, and opens to its observers.

Glow and laser come back on GlowingEntities and GuardianBeam, shaded and relocated as before. They last the box's life and follow the box observer. A box whose glow or laser fails goes on without it, with one low warning per reload. Glow never stops the plugin from enabling. GlowingBlocks throws on any server that is not Paper, so on Spigot glow uses a Spigot path, or is off with one low warning. This phase checks both libraries on Spigot, Folia, and 26.x, and records the result in the handoff.

Done when each moment plays its animation through a recording particle sink, an abandoned summon stops its summon animation, an animation id missing from `animations.yml` warns high and plays nothing, glow and laser start on shown and stop on out of view and ended through a fake, a failing glow or laser leaves the box running with one low warning, and a glow library that throws at setup, as GlowingBlocks does on Spigot, leaves enable running with one low warning.

## Phase 8c. Commands and cutover

ADR 0006 and ADR 0014.

One command system. A command is a node in a tree with a name, aliases, a permission, typed arguments, and help keys. Argument types parse their value and complete it: loot id, world, player, online player, integer, and radius. A new command is one node and one registration, with no completer of its own. Permissions are `rakunloots.command.<path>`, under `rakunloots.admin`. Tab completion offers only what the sender can run. A failed command shows the typed command with the failing argument underlined, and the reason with the expected type. A loot, world, or player miss adds up to 3 close matches. All text comes from default messages. The admin root and the claim command register through the server's command map, with names and aliases in `config.yml`.

Admin commands: `help [command]`, `reload`, `list`, `give <loot> [player] [amount]`, `summon <loot> [world|player]`, `remove all|world <world>|loot <loot>|near <radius>`, and `timer <world> now [--force]`. `help` with no command lists what the sender can run. `help <command>` shows its usage, what it does, each argument and its type, an example, and its permission. `give` to an offline player or into a full inventory writes unclaimed summon items. `remove` ends each box as an admin removal. `timer <world> now` resets that world's timer and runs one timer summon with its usual checks, and reports why when it is refused. `--force` skips the minimum player count and the spawn limit.

Plugin enable uses the new modules only. The old summon path, static registries, the loots wiki, `wiki.yml`, `rakunloots.wiki`, `rakunloots.bypass_locked`, and unused libraries are removed in this phase. `plugin.yml` gains `folia-supported: true`, and DecentHolograms moves from `depend` to `softdepend`. Each key phase 6 took out of the shipped `loots/airdrops.yml` is back, except `commands`.

Done when enable, reload, and every admin command run on the new modules, and the old path has no caller. Done when each argument type completes and refuses through a fake sender, a failed command underlines the failing argument and gives its reason, `help <command>` shows every part of that command's help, a sender without a permission neither sees nor runs that command, `remove` of each scope ends the right boxes as admin removals, and `timer <world> now` refuses on the spawn limit without `--force` and summons with it. Done when RakunLoots enables with no ERROR on Paper 26.2 and on Spigot 26.2 with only the shipped defaults, checked on the RakunProbe test servers. Done when the Paper half of the RakunProbe smoke test passes: `probe holo adapter`, spawn, meta, and destroy.

## Phase 8d. Version compatibility

ADR 0001, ADR 0010, ADR 0015, and ADR 0017.

The phase 8 version test ran RakunLoots on one Paper server per minor version from 1.18.2 to 26.2, on Spigot 26.2, and on Folia 26.2, through RakunProbe's `rltest`. The api-version, the Spigot command map, and the empty subcommand list are fixed. This phase fixes the rest.

The remapper. Paper 1.20.6, 1.21.1, and 1.21.4 cannot remap the plugin, because the 26.x packet adapters and the FancyHolograms provider are Java 25 class files. Their source compiles to Java 21 bytecode on JDK 25, as ADR 0017 says. A build test fails when a shipped class is newer than Java 21. A Paper 1.21.5 test server joins the version test.

Unloaded chunk abandons. A pending summon is abandoned as `CHUNK_UNLOADED` about 1 in 8 times on Paper and Spigot when no player is near, and nearly every time on Folia. A failing test confirms the cause first. The likely cause is the gap between the summon search loading the chunk and the pending summon's hold landing. The search then takes the hold in the same read that loaded the chunk and hands it to the pending summon, so the chunk never goes unheld, as the ADR 0015 amendment says. A summon with no delay waits for that hold before it writes the block.

The shipped `worlds.yml` sets `unloaded_chunks: generate`, because `load` finds no spot on a fresh world. Its comment says `load` or `generate` is recommended, that `skip` works only with `mode: spawn` and a spawn ring where chunks stay loaded, and that under `skip` a box in an unloaded chunk waits for a player to load it before its life can end.

Folia. The laser is off on Folia with one low warning, because GuardianBeam uses the Bukkit scheduler. Glow cleanup is skipped while a region shuts down, since the region drops its entities anyway. The experimental flag of ADR 0010 comes off when `rltest` passes 3 runs in a row on Folia 26.2 with no `CHUNK_UNLOADED` and no ERROR.

Block names. A block list or block group entry may be an optional entry: `?minecraft:x` or `?#minecraft:tag`. An optional entry is used only when the server knows that exact id or tag. An old name does not resolve for it, and an unknown one is skipped. Each load gives one low warning that lists the optional entries skipped on this server. The shipped `blocks.yml` names version-dependent blocks as optional entries, such as `?minecraft:grass`, `?minecraft:short_grass`, and `?#minecraft:tall_flowers`. A fresh file is the shipped text, as the ADR 0003 amendment says. A plain entry keeps its old-name and unknown-name warnings.

Usage. A choice argument with one value shows as that word, so `timer <world> now [--force]`.

The warnings command. `<root> warnings [low|high|severe] [page]` lists the warnings since the last enable or reload, from the config load and from runtime, most severe first. The severity filter shows that severity and above, default low. One line per warning kind gives its severity, count, and first 3 subjects, such as `UNKNOWN_NAME x5: stoen, oak_plank, dirtt (+2)`. A warning below the warning level is listed and marked. 10 lines a page, with clickable previous and next buttons for players and the next page's command for the console. Permission `rakunloots.command.warnings`. The list is cleared on reload, keeps one entry per warning kind, and is never stored.

Done when a build test refuses a class newer than Java 21, a test reproduces the unloaded chunk abandon and then passes, a summon with no delay waits for its hold, the shipped `worlds.yml` says `generate`, glow cleanup at region shutdown throws nothing, the laser on Folia is off with one low warning, an optional entry the server lacks is skipped and listed in one low warning, an optional old name does not resolve, the shipped `blocks.yml` loads with no high warning on every test server, the timer usage reads `now`, and the warnings command sorts, filters, groups, pages, and marks warnings below the level through a fake sender. Done when `rltest` passes 3 runs in a row on Paper 26.2, Spigot 26.2, and Folia 26.2, and once on each Paper server from 1.18.2 to 26.1.2 with 1.21.5, with no ERROR from RakunLoots and no RakunLoots WARN on a fresh install except Folia's experimental warning while it stays. The owner's client checklist, holograms, glow, laser, particles, the head trophy skin, and inventory layouts on each version, follows and does not block the phase.

## Phase 8e. Warnings file, typo detection, and placeholders

ADR 0003.

The warnings file. `warnings.yml` gives each warning kind a severity: `low`, `high`, `severe`, or `off`. A fresh file lists every kind at its default, under a header that says changing them is at the owner's own risk, since a lowered or off warning can hide a broken loot or a lost box. A kind missing from the file uses its default. An unknown kind warns low. The loot language silence switch in `config.yml` goes, and `LOOT_LANGUAGE_CLASH: off` replaces it. The warning level stays the log's threshold. The warnings command shows each warning at the severity the file gives it, and an `off` kind is neither logged nor listed.

Typo detection. The jar ships every block id and block tag of every supported version, built by `probe dump blocks` on each RakunProbe test server and merged by a script, and refreshed when a version joins. It is read only when an entry misses on this server, and dropped when the load ends. An entry, optional or plain, that another version knows goes into the one grouped low line of phase 8d. An entry no version knows is a typo: one high warning each, with up to 3 close matches.

The close match search is shared with commands. An exact hit is a hash lookup. Candidates are bucketed by length, and ids and tags are separate pools. Tolerance is 2 edits, with a swapped pair counted as 1. The edit distance is banded to the tolerance and stops early when a row passes it. Two arrays are reused for the whole search, and the best 3 are kept in place with no sort. A candidate that contains what was typed counts as closest, and ties go by name.

Placeholders. Every placeholder is `{name}` in place of `<name>`, in every shipped file, message key, and hologram line. Every shipped string that holds a placeholder is quoted. A YAML error on a line with `{` tells the owner to quote the value.

Done when tests show that every placeholder in every shipped file and message key uses `{}`, that an unquoted `{name}` value gives the quote hint, that `warnings.yml` changes one kind's severity, `off` hides it from the log and the command, and an unknown kind warns low, that a typo gets a high warning with its close matches, that an id from another version lands in the grouped low line, and that the shipped id list holds every id and tag of the test servers. The `rltest` smoke test on Paper 26.2 still passes.

## Phase 9. Menus

ADR 0013, ADR 0014, and ADR 0018.

The claim menu and the loots wiki, on one menu module. Admin menus are not part of this phase.

The menu module. The plugin draws its own menus, with no GUI library. A menu is a view in plugin types: slots mapped to icons and click actions. A thin Bukkit renderer turns the view into an inventory whose holder marks it as a menu, and one listener cancels every click and drag in it, shift clicks and number keys included. A menu opens and closes on the player's scheduler through the server adapter, so it runs on Folia. A reload and a plugin stop close every open menu.

Menu files. Each menu has its own file in `menus/`: `menus/claim.yml` and `menus/wiki.yml`. A file sets the menu's rows, its content slots, its filler item, and each button's item, slot, and optional sound. Titles and lore come from the language files, with `{name}` placeholders. A missing file is written from the shipped text. A file whose YAML cannot be read uses the shipped layout from the jar, with a severe warning. An unknown key warns high and is ignored. A button sound is a key such as `minecraft:ui.button.click`, which the server adapter resolves on every version. An unknown sound warns high and plays nothing. A button with no sound plays none. The shipped files give each button a sound. When the entries outnumber the content slots, previous and next buttons page them. A button with no page to go to shows the filler.

The claim menu. The claim command opens it. `claim all` gives every unclaimed summon item that fits, as the claim command does today, under the same `rakunloots.claim` permission. A player owed nothing gets `claim_nothing` and no menu. Each loot shows its summon item, stacked up to 64, with a lore line that gives the full amount owed. A click takes that loot's items, as many as fit. A claim all button takes every loot's. The messages are the claim command's. The menu refreshes in place and closes when nothing is left. A player's clicks are ignored while that player's claim is running. A row whose loot is missing shows a barrier that says the loot is gone and the items are kept, and a click on it does nothing. Rows follow loot id order.

The loots wiki. Its own command opens it, with the name and aliases in `config.yml`, default `loots`, under `rakunloots.wiki`, default true. The list page shows one icon per loaded loot. A loot key `wiki: false` hides a loot. The icon is the loot's presentation block, with head data for a player head, unless the loot key `wiki.icon` names an item reference. Loots sort rarest first by their world timer chance in the viewer's current world. Loots that world's timer cannot summon go last. Ties go by loot id. A click opens the loot's page. The wiki is read only.

A loot page shows the loot's contents, an info item, and a back button. Each content entry is its real item, built through its item source, at its amount, with language lines for its chance and amount added to the lore. An entry whose item source misses shows a barrier named after its item reference. Contents sort rarest first. An entry with a weight below zero shows `always`. An entry with weight zero is hidden. The info item shows the description, the open mode, the lock, the life, `unending` for unending life, the min to max item count, and each world whose timer can summon the loot, with that timer chance. Each line is a language key, and a blank line drops out.

Loot chance. The chance a content entry lands in one loot box is exact, worked out from the roll rules of `LootRoller`, as ADR 0018 says. The timer chance is the loot's share of its world's timer total, over loots that are loaded and above zero, as `WorldTimers` picks. Every loot's chances are worked out after each load, off the main thread. `{chance}` shows 1 decimal and `{chance:N}` shows N decimals, from 0 to 6. A chance above zero that rounds to zero shows `<` and the smallest step, such as `<0.01%`. A chance below one never rounds up to 100, and caps at the largest step below it, such as `99.99%`. An N outside 0 to 6 warns high and uses 1.

The chance calculation chooses its removal direction for numerical stability, not a promised smaller-side step count. For n drawable entries and g distinct weights, its full grouped convolution and removals cost at most O(n^2 + g*n) per quadrature point; the grid must be chosen to keep the required accuracy. This corrects the original cost estimate in ADR 0018. The 1e-9 absolute-error requirement and the 100-entry, 100-distinct-weight, max-50 calculation under 50 ms remain acceptance requirements.

Done when in-memory tests cover the menu files with their shipped fallback and severe warning, an unknown key, an unknown sound, paging, a claim click and claim all, clicks ignored while a claim runs, the barrier rows of a missing loot and a missing item source, wiki order by timer chance and contents order by chance, `wiki: false` and `wiki.icon`, every menu closed on reload, `{chance:N}` with its bounds, and loot chance against equal weights, one entry, a count at or above the drawable entries, entries below weight zero, and a brute-force count of every draw order on small loots. Done when `rltest` passes on Paper 26.2, Spigot 26.2, and Folia 26.2. The owner's client check of both menus follows and does not block the phase.

## Phase 10. Package structure

ADR 0002, ADR 0003, ADR 0010, ADR 0019, and ADR 0020.

No behaviour changes, no new runtime dependency, and no file format change. Lightweight comes first, so nothing is added only for structure. A split that would make package-private types public uses one public entry type instead. A seam gets an interface only when it has two adapters, and a test fake counts as one.

A module is a top-level package with its subpackages. A module's interface is its root package. The modules form layers, and each one imports only modules below it:

1. `warning`, `text`, `match`: leaves.
2. `server`.
3. `items`.
4. `config`.
5. `roll`, `storage`.
6. `lootbox`.
7. `hologram`, `animation`, `visuals`, `menu`.
8. `summon`.
9. `wiki`, `command`.
10. The plugin class.

Inside a module, a root package and its subpackages may import each other. The layout rule: a module splits implementations into a subpackage when it mixes a seam with two or more shipped adapters, or when it holds more than about 15 files. Bukkit code that only the module itself and the plugin class reach sits in a `bukkit` subpackage. Tests move with their classes. Test fakes that several modules share, such as `FakeServerAdapter`, move to one test package, and the packet adapter subprojects' tests follow.

One warning module. A `warning` package replaces `log`, `config/WarningKinds`, and the per-module sink interfaces. It holds the severity, the warning level, the warning settings, the warning record, and the warning log. Each module's `Kind` enum implements one `WarningKind` interface that gives its stable `warnings.yml` name and default severity, so the severity lists in `WarningKinds` move onto the constants. Each module's warning implements one `Warning` interface that gives its kind, raised severity, subject, log fields, and cause, so the log has one `warn(Warning)` and no per-module overloads. `ItemWarning.Severity` becomes the one severity. One `Warnings` sink, with `warn`, `debug`, and `failure`, replaces `LifecycleWarnings`, `VisualsWarnings`, `MenuWarnings`, and the method references. One class in the plugin's root package lists every module's kinds, in today's `warnings.yml` order. The config load takes that list explicitly. The log uses each warning's kind, and the warnings command uses the kind names in the warning record; neither takes a redundant catalog. The stable names stay as they are, the shipped warnings file test keeps their order, and a duplicate name still fails.

A text leaf. `MessageText.color`, `MessageText.formatPercent`, and `TimeText` move to a `text` package that imports no other module. The `render` calls, which read a `ConfigSnapshot`, move into `config`.

`NameCatalog` moves from `config` to `server`, beside `BukkitNameCatalog`, so `server` imports no module above it. `UnloadedChunkPolicy` stays in `server`. `SenderLocation` moves from `command` to `server`, beside `PlayerPosition`.

One Bukkit server adapter. `PaperServerAdapter` and `SpigotServerAdapter` become one `BukkitServerAdapter` in `server/bukkit`, with the package-private Bukkit helpers. The Folia adapter's source set moves to the same package, so it keeps package-private access, and `ServerAdapters` names its new class. They differ only in how an unloaded chunk loads, so that is the seam: a package-private chunk loader with a Paper adapter, which uses the async chunk call, and a Spigot adapter, which keeps the one-load-a-tick queue. `ServerAdapters` picks the chunk loader as it picks the adapter today. `ServerAdapter` stays one interface. `MenuHolder` stays in `server`, since `menu` reads it. The contract tests run once per chunk loader instead of once per adapter. ADR 0020 records it: three server adapters become two, Bukkit and Folia, and the Paper and Spigot difference moves into the chunk loader.

Summon as one module. `summon` holds the summon: `SummonService`, `SummonTarget`, `SummonReport`, `WorldTimers`, and `WorldBroadcasts`, moved from `lootbox`. The spot search moves to `summon/search`, with `SummonResult`, and its `MissReason` becomes `SearchMiss`. `summonitem` and `claim` merge into `summon/item`: the summon item, its use and give back, unclaimed summon items, the claim menu, the claim join listener, and the `SummonItemFactory` seam, which tests fake. `lootbox` never imports `summon`. Each type in `LootBoxLifecycle`'s interface or `BoxObserver` stays in or moves to `lootbox`: `Spot`, `OccupiedCheck`, `ChunkHolds`, `SummonOutcome`, `SummonRefusal`, `SummonSource`, and `SummonGiveBack`.

Config value types. Config stays the one reader and writer, as ADR 0003 says. Every value type a config file defines moves into `config`, so config imports no module above it: `HologramSettings`, `HologramProviderChoice`, `VisualsSettings`, `GlowColor`, `LaserType`, `SummonRules`, `PlacementRule`, `PlantsMode`, `HeightRange`, `SummonRing`, and `UnderwaterPosition`. `ConfigLoadSupport` splits by file kind into four subpackages, each with its parser and its value types: `config/loot` (`Loot`, `Roll`, `ContentEntry`, `OpenMode`, `LootEndRules`, `LootAnimations`), `config/summon` (summon rules and their parts), `config/menu`, and `config/block` (block groups, block list resolution, known block names). The parsers of one small file each stay at the root. `ConfigLoader` keeps the one public load and absorbs the pass-through to `ConfigLoadSupport`.

The command framework apart from the commands. The parser, the command tree, and the argument type interface stay in `command`. The argument types move to `command/argument`. The RakunLoots commands move to `command/handler`, package-private behind one public entry that registers them and gives the claim and wiki permissions. The Bukkit registrar, sender, and sources move to `command/bukkit`.

Implementation subpackages. The shipped animation types move to `animation/type`, and the shipped item sources to `items/source`. Each has one public factory that the registry calls, and the implementations stay package-private. `visuals` stays one package, since it is under the size limit and glow and laser each have one adapter.

Out of this phase: splitting `ServerAdapter` into narrower interfaces, and deepening `LootBoxLifecycle`.

Order: the warning module, the text leaf, and the `server` moves first, since they break most cycles. Then the Bukkit adapter, summon, config, command, and the implementation subpackages. The ArchUnit test comes last.

Done when every main and test class is in its new package, the old packages are gone, the shipped jar is no larger than before, an ArchUnit test (test scope only) finds no import against the layer order, no cycle between modules, and no `bukkit` subpackage imported outside its module and the plugin class, every existing test passes in the root project, the Folia and FancyHolograms source sets, and every packet adapter subproject, and `rltest` passes on Paper 26.2, Spigot 26.2, and Folia 26.2.

## Later

Not a phase until one of these is picked on purpose.

- A giveaway of summon items to every player, online or not, as unclaimed summon items.
- A loot editor. It writes through the config module.
- An external crate as a second summon outcome.
- More summon animations.
- A loot's `commands`, once a moment to run them is chosen.
- A summon item cooldown, permission, or world limit.
- Glow and laser through the packet version adapters.
- Admin menus, such as a list of live loot boxes to remove or visit.
