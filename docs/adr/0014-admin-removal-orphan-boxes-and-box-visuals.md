# Admin removal, boxes whose loot is gone, and box visuals

This amends ADR 0006 and ADR 0013.

Amended by ADR 0015: an admin removal removes the block of a box in an unloaded chunk too. It loads the chunk for the removal under `skip`, and finds it held under `load` and `generate`.

Amended by ADR 0016: no laser entity sits at the box, and glow is a block display entity from Minecraft 1.19.4 and GlowingBlocks below, since a hitbox at the box takes a click from the block.

Amended by ADR 0018: the plugin draws its own menus, the loots wiki returns under `rakunloots.wiki`, and loot chance is exact.

An admin removal is a loot end. It drops nothing, leaves no block item, and removes the block only while it still matches. The admin command removes all loot boxes, one world's, one loot's, or those within a radius of the sender.

A reload that edits a loot applies the edit to its live loot boxes from the next tick. Their rolled contents and placed block stay. A reload that removes a loot leaves its live loot boxes in the world. Each runs on: it counts down its stored life and lock, opens as inventory open with its stored stacks, and ends with every loot end rule off. It has no hologram. The plugin warns high about it once per reload. It is never frozen, so a deleted loot never leaves a box that outlives its life. A box found at start whose loot is missing registers and runs on the same way. A box whose world is missing stays flagged in storage, as before.

The claim menu leaves the rewrite. All menus, the claim menu and the loots wiki included, come in the phase after phase 8. Phase 8 gives unclaimed summon items through the claim command only. The loots wiki and `rakunloots.wiki` are removed with the old path.

Glow and laser keep their libraries, GlowingEntities and GuardianBeam, shaded and relocated as before. They do not move into the packet version adapters. A box whose glow or laser fails goes on without it, with one low warning per reload. Glow never stops the plugin from enabling. GlowingBlocks throws on any server that is not Paper, so on Spigot glow uses a Spigot path, or is off with one low warning.

## Considered options

- Freeze a box whose loot is gone until the loot returns. Rejected. A deleted loot would leave boxes that never end.
- Store a snapshot of the loot on each box, and compare hashes on reload. Rejected. Every box would carry a copy of its loot, and an edit could never reach a live box. The loot hash is kept only on the summon item, so an outdated summon item is rebuilt on use or join.
- Draw glow and laser through the packet version adapters. Rejected for now. The libraries already cover the versions the plugin runs on, and the adapters would need glow and beam packets for every revision.
