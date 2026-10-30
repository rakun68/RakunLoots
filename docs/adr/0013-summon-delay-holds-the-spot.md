# A pending summon holds its spot, and a lost summon item is kept unclaimed

This amends ADR 0006. A claim menu is the one menu inside the rewrite.

Amended by ADR 0014: admin removal is a loot end, a box whose loot is gone runs on, glow and laser keep their libraries, and the claim menu moves to the menus phase.

Amended by ADR 0018: the claim command opens the claim menu, and `claim all` gives what fits.

A loot's summon delay lets its summon animation play before the block appears. The old path picked a spot, waited out the delay, and only then registered the box and wrote the block. Nothing held the spot while it waited. A second summon item or a timer summon could pick the same block, and several loot boxes landed in one block. Two loot boxes in one block must never happen.

A summon reserves its spot the moment the search returns it, before the delay starts. The reservation lasts until the block is written or the summon is abandoned. That wait is a pending summon. The occupied check counts a pending summon's spot as taken, so no other search picks it. When the delay ends, the block is written only while it is still the block the search found. The delay never runs before the reservation. Every summon follows this rule: the summon item, the world timer, and admin commands.

Only the same block is a collision. Boxes may still touch or stack.

A summon is abandoned when its spot is already held, the block changed during the delay, the server refuses the block, the roll fails, the spot's chunk unloads during the delay, or a reload or stop comes during the delay. Abandoning releases the spot. It never searches again.

The summon item is taken at the click. An abandoned summon item summon gives that summon item back. A server summon has nothing to give back and is only refused. The item goes to the player's inventory when they are online and it fits. Otherwise it becomes an unclaimed summon item.

Unclaimed summon items are summon items a player is owed but has not taken. Storage keeps them per player as a loot id and an amount, in SQLite or MySQL beside the loot boxes. The item is built from the loot's current summon item when it is claimed, so a loot edit reaches it. A row whose loot is missing is kept and flagged, never deleted. A player takes them with the claim command or the claim menu. The claim command's name and aliases are set in `config.yml`. It gives as many as fit and leaves the rest. The claim menu lists them, and a click takes one loot's items, as many as fit. On join, a player with unclaimed summon items gets a message that names the command. A claim lowers the stored amount before it gives the items, so a crash can never duplicate one.

## Considered options

- Wait out the delay, then summon. Rejected. That is the gap that put several boxes in one block.
- Search again when the spot is taken or changed. Rejected. A refusal with the item back is simpler to trust than a second spot the player did not see the animation at.
- Keep the reservation across a chunk unload and place the box when the chunk loads. Rejected. The pending summon would have to be saved, and a timeout never loads a chunk.
- Drop a refund at the player's feet when the inventory is full. Rejected. Another player can take it, and an offline player gets nothing.
- Store unclaimed summon items as item stacks. Rejected. A summon item edit would not reach items already owed.
- Take the summon item only when the box is placed. Rejected. Clicks during the delay could start more summons than the player has items.
- A minimum distance between loot boxes. Rejected for now. The bug is two boxes in one block.

## Consequences

Phase 8 adds the pending summon, the claim command, the claim menu, the join message, and the unclaimed summon item table. A giveaway of summon items to every player, online or not, can write unclaimed summon items later. It is not part of phase 8.
