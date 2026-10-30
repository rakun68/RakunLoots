# Loot box lifecycle

Amended by ADR 0012: DecentHolograms is a soft depend, and the plugin draws its own holograms.

Amended by ADR 0013: a pending summon holds its spot through the summon delay, and a claim menu for unclaimed summon items is the one menu inside the rewrite.

Amended by ADR 0014: admin removal is a loot end, a box whose loot is gone runs on, glow and laser keep their libraries, and the claim menu moves to the menus phase.

Amended by ADR 0015: a live loot box keeps its chunk loaded with a plugin chunk ticket unless its world's `unloaded_chunks` is `skip`, and an admin removal under `skip` loads the chunk to remove the block.

A loot box is the live summon. The store listing may say airdrop. The plugin word stays loot box.

Each loot has one open mode. Drop open spills the items into the world and removes the box. Inventory open shows one shared inventory, even when the block is not a container. Player A taking an item removes it for Player B.

A loot end is a break, an allowed explosion, the end of its life, or an empty inventory that nobody has open when that loot says so. Break and an allowed explosion drop what is left. The block item drops only when that loot says so. Default off, so a chest loot does not leave a chest. A timeout and an empty removal never drop the block.

Timeout spill is off by default. Off removes the box and the items are gone. On drops what is left, then removes the box. An open inventory is closed first. Empty removal is off by default, and applies to inventory open only. On, it waits until the inventory is shut and nothing is left.

A hopper, including a hopper minecart, cannot take from a loot box. Pistons and fire cannot move or destroy one. Sneak does not bypass a lock. The bypass permission does. DecentHolograms is a hard depend. One admin command reloads and removes live loot boxes.

Player item summon, per-world timer summon, container right-click, lock, and hologram stay. Tests for loot, messages, the name catalog, lifecycle, and item sources run in memory.

## Considered options

- A private roll per player. Rejected. That is a crate. External crates are a later second summon outcome, and the seam waits until the first crate source exists.
- Two open gestures on one loot. Rejected. One loot has one open mode.
- Hoppers may empty a loot box. Rejected. Empty removal would then delete it with nobody watching.

## Consequences

Menus, the loot editor, and external crates are out of this rewrite. The editor, when it exists, writes through the config module.
