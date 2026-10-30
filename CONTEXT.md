# RakunLoots

Custom summonable loot for Paper and Spigot servers.

## Language

**Loot**:
The admin-authored definition of one summonable drop: its open mode, lock, life, loot end rules, roll, presentation, summon rules, and any message overrides.
_Avoid_: crate, kit

**Loot box**:
One summoned loot sitting in a world. Players take from it. A hopper cannot. The store listing may say airdrop. The summoned object is still a loot box.
_Avoid_: airdrop, crate

**Open mode**:
How a player takes the contents of a loot box. Each loot has one open mode. The block does not choose it. Drop open spills the items into the world and removes the box. Inventory open shows one shared inventory, even when the block is not a container.
_Avoid_: crate GUI

**Lock**:
A period during which only the owner, or a player with the bypass permission, may take from a loot box. A locked box also resists explosions, even when its loot allows them. A loot may have no lock.
_Avoid_: owner lock

**Owner**:
The player a loot box's lock admits. A player who summons a loot box owns it. A server summon may name an owner or leave the box with none. With no owner, only the bypass permission opens a locked box.
_Avoid_: summoner

**Rolled contents**:
The items one loot box received from its roll. A restart keeps them. It never rolls again.
_Avoid_: loot items, drops

**Life**:
The time a loot box may last before the end of its life.
_Avoid_: lifespan

**Unending life**:
A life with no end of life.
_Avoid_: infinite lifespan

**Loot end**:
The moment a loot box leaves the world. A break, an allowed explosion, a changed block, the end of its life, an empty inventory that nobody has open when that loot says so, or an admin removal. Unending life has no end of life.
_Avoid_: despawn

**Admin removal**:
A loot end an admin orders. It drops nothing and leaves no block item. It removes the box's block even when the chunk is not loaded.
_Avoid_: clear, delete

**Chunk hold**:
A live loot box keeping its chunk loaded with a plugin chunk ticket, from placement or start to its loot end or the plugin's stop. A world whose `unloaded_chunks` is `load` or `generate` holds. `skip` does not hold, and an admin removal there loads the chunk for the removal alone.
_Avoid_: chunk loader, force load

**Changed block**:
A loot box whose block is no longer the block it was placed with, such as after another plugin replaces it. It ends as a break, without the block item. A change of facing or other block state is not a changed block. Vanilla changes, mobs, pistons, and fire cannot change the block.
_Avoid_: missing box, ghost box

**Loot end rules**:
What a loot allows when a loot box ends: an explosion, dropping the block item, spilling the contents at the end of its life, and removing an empty inventory.
_Avoid_: despawn settings

**Roll**:
The rule that chooses which content entries a summoned loot box receives, and how many.
_Avoid_: loot table, shuffle

**Content entry**:
One item reference in a roll, with an amount and a weight. A weight below zero is always included, outside the roll's count. A slot, when set, fixes where it sits.
_Avoid_: drop, loot item

**Presentation**:
The display name, description, and block of a loot. A player head may carry head data.
_Avoid_: skin

**Hologram**:
Text lines shown above a loot box: its name, owner, remaining lock, and remaining life. It exists only while its box does. It is not a block or an item and never saves to the world.
_Avoid_: nametag, floating text

**Hologram provider**:
What draws the holograms. This plugin draws its own by default. DecentHolograms, HolographicDisplays, or FancyHolograms may draw them instead when installed. A provider that cannot run on the server is never chosen.
_Avoid_: hologram hook

**Item source**:
A named way to resolve an item id. This plugin ships sources for vanilla items, MMOItems, ItemEdit, ExecutableItems, Oraxen, and ItemsAdder. Another plugin may add a source under its own name. It cannot take a name this plugin already ships. The first registration of a name keeps it.
_Avoid_: hook, bridge

**Item reference**:
A source name plus an id that points at one item. A missing or blank source name is the vanilla source. A vanilla reference may also carry vanilla additions. Another source ignores those additions and uses its own item.
_Avoid_: custom, type, item type

**Vanilla additions**:
Item data written on a vanilla item reference. It includes the display name, lore, enchantments, custom model data, item flags, and the rest of that item's data. They change that vanilla item only.
_Avoid_: custom item, custom source

**Language**:
A named set of default messages. English, Vietnamese, and Simplified Chinese ship with the plugin. An owner may add another.
_Avoid_: locale, player language

**Active language**:
The language the server shows, chosen in config. A missing language file uses English.
_Avoid_: server locale

**Loot language**:
The language named at the top of one loot file. It is the language that file was written in.
_Avoid_: loot locale

**Fallback language**:
English. The default messages used when the active language lacks a key.
_Avoid_: default locale

**Default message**:
Text in the active language that a loot uses when that loot does not override that key.
_Avoid_: lang, locale

**Message override**:
One string in a loot file that replaces one default message for every active language. A missing override uses the active language.
_Avoid_: custom message, translation

**Warning level**:
The minimum severity the server reports. The severities are low, high, and severe. A problem below the level is silent.
_Avoid_: verbosity, log level

**Warning kind**:
One kind of problem the plugin reports, such as an unknown name or a failed glow. The warnings file gives each kind its severity, or turns it off.
_Avoid_: error code, warning type

**Warnings file**:
The file that sets each warning kind's severity. A kind it leaves out keeps its default.
_Avoid_: warning config

**Loot language warning**:
A report that a loot file's language is missing or is not the active language. The warnings file can turn these off without raising the warning level.
_Avoid_: language error

**World settings**:
The summon rules for one world: timing, limits, and which loots can appear.
_Avoid_: schedule, worlds file

**World timer**:
The countdown in one world's settings that starts timer summons. It starts full on enable and on reload. An admin can reset it and summon at once.
_Avoid_: world schedule, spawn timer

**Summon rules**:
What one loot asks of the spot it lands on: its placement rule, the height range, and which blocks it may replace or must avoid.
_Avoid_: summon settings, summon options

**Placement rule**:
The kind of spot a loot box lands on. Top surface drops it on the highest surface, as if from the sky. Any surface allows a floor under cover, such as a cave. Buried hides it under the top surface. Deep buried hides it anywhere inside solid ground. Underwater puts it in water. Anywhere allows mid-air. Each placement rule has its own options.
_Avoid_: summon type

**Summon ring**:
The area around a center where a summon may land: a square band between a minimum and a maximum distance from that center. A minimum of zero is a filled square.
_Avoid_: offset, radius

**Block group**:
A named set of blocks, kept in its own file. A group may contain other groups. Many ship with the plugin, such as solid, non-solid, and plants. Any block list in a loot may name a group. A group that contains itself, directly or through others, is refused with a severe warning.
_Avoid_: block list, tag

**Optional entry**:
A block or tag in a block list or block group, marked with `?`, that is used only on a server that knows that exact name, and skipped without a warning of its own elsewhere. It lets one file serve every supported version.
_Avoid_: soft entry, version entry

**Banned loot block**:
A block no loot may use as its presentation block, such as air, liquids, carpets, redstone, falling blocks, and two-block blocks. A config list names them, and by default names a shipped block group the owner may edit. A loot that uses one is refused with a high warning. Summon rules may still name these blocks.
_Avoid_: blacklist, restricted block

**Summon search**:
How a server summon picks where to look in a world: around the world spawn, or smart summon. The world's unloaded chunk setting decides whether it picks a random spot and reads it, or scans loaded chunks and picks among the valid spots.
_Avoid_: summon mode, location finder

**Smart summon**:
A summon search that picks one active player and summons in a ring around them. Every active player has a near-equal chance. Players nearer the median of all active players are a little more likely to be picked, and the spot leans toward that median.
_Avoid_: crowd summon

**Summon item**:
An item that summons its loot around the player who uses it. The player owns the loot box. Using it takes the item.
_Avoid_: key, airdrop item

**Summon delay**:
The time a loot's summon animation plays before its block appears.
_Avoid_: spawn delay

**Animation**:
Particles a loot shows at one moment of a loot box: its summon, while it lives, when it unlocks, when it opens, and when it ends. The summon animation plays during the summon delay.
_Avoid_: effect

**Pending summon**:
A summon waiting out its summon delay. It holds its spot, so no other summon lands there. It ends when the block is written or the summon is abandoned. An abandoned summon item summon gives the item back.
_Avoid_: queued summon

**Unclaimed summon items**:
Summon items a player is owed but has not taken, such as one given back when the inventory was full or the player was offline. They are kept per player as a loot and an amount, and survive a restart. The claim command gives them.
_Avoid_: keys, virtual inventory, mailbox

**Claim menu**:
The menu the claim command opens. It lists a player's unclaimed summon items by loot, and a click takes one loot's items, as many as fit.
_Avoid_: claim GUI, mailbox

**Loots wiki**:
A read-only menu that lists the loots players can find. A loot's page shows its contents with their loot chance, and how it lives and opens.
_Avoid_: loot book, crate preview

**Loot chance**:
The exact chance that one content entry lands in one loot box, worked out from the roll. An entry below weight zero is always included.
_Avoid_: drop rate, weight

**Timer chance**:
The chance a world timer summon picks a loot: its share of that world's timer total.
_Avoid_: spawn chance, loot weight

**Idle player**:
A player who has not moved for a set time. Smart summon ignores idle players, unless every player in that world is idle.
_Avoid_: AFK
