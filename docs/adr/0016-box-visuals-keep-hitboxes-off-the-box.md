# Box visuals keep hitboxes off the box

This amends ADR 0014.

A phase 8 smoke test on Paper 26.2 showed that a box visual with a hitbox takes a player's right click and break away from the box. The client picks entities with its mouse ray as it picks blocks, and a client side entity from a packet is picked as a real one. A visual that sits on the box must therefore have no hitbox, or none a player can reach.

## Laser

A crystal laser put its end crystal at the box. The crystal's 2 by 2 by 2 hitbox covers the box and the block under it, so a player could neither click the box nor break the block below. A crystal at the top end of the beam would be too far from the player for the client to draw, so the crystal floats above the box instead and its beam goes up.

Where a laser's entity starts is the loot's `laser.start_offset`: blocks above the bottom of the box's block, from 0 to 64, with a default of 0. The beam runs from the entity up to the world's build limit. The crystal is visible and the client picks it like a real entity. It is wider than a block and needs 4, three blocks above the box's top face, to be out of a player's reach of 3 blocks. The shipped treasure chest uses 4. A player cannot pick GuardianBeam's invisible guardian or its squid, as the phase 8 smoke test on Paper 26.2 showed, so a guardian laser can start anywhere, and the shipped guardian loots use 0.

GuardianBeam's guardian is a client side hostile mob, and the Minecraft 26.2 client does not create a hostile mob while its world is peaceful: `EntityType.create` returns nothing when `canSpawn` fails. A guardian laser in a peaceful world therefore draws as a crystal laser, at a start offset of at least 4, with one low warning for each loot for each reload. The difficulty is read when the box's laser starts.

Where each laser puts its entities is one function, `LaserPlacement`, that a test checks against each entity's hitbox.

## Glow

GlowingBlocks draws a glow with an invisible fake shulker. The shulker's hitbox is bigger than a player head and covers a chest, so on Paper 26.2 a player's right click and break hit the shulker and never reached the box. From Minecraft 1.19.4, where block display entities exist, glow is a real server side `BlockDisplay` at the box's block instead, behind the same `GlowLibrary` seam. Below 1.19.4 glow stays GlowingBlocks, which is Paper only, with one low warning when it cannot be set up, as ADR 0014 says. `GlowLibraries` picks by whether the server has the `BlockDisplay` class, and loads the block display glow by name from the source set compiled against the newer API, as the Folia adapter is loaded.

The display shows the box's placed block data and glows in the loot's glow colour through the glow colour override. A display has no hitbox, so a click reaches the block. It is one entity every player sees, so the library tells the service it is shared by all players. The service shows it once for each box when the box is shown, and hides it once when the box goes out of view or ends, whether or not a player is online. Players who join, change world, or respawn need nothing.

The display is never persistent, so the server never saves it with a chunk. It carries the scoreboard tag `rakunloots_glow`, and a tagged display found in a chunk whose entities load, or in a chunk already loaded at enable, is removed, so a crash never leaves one behind. It is removed when the glow stops and at plugin stop. On Folia it is spawned and removed in a task on the region that owns the block. At plugin stop the plugin can no longer schedule, so a removal that cannot be scheduled runs where it is called.

To keep the display from z-fighting with the block, it sits at the block's own corner and is a hair larger than the block around its centre, with scale 1.002 and a translation of -0.001, so its faces are always in front of the block's. It takes the brightest block light and sky light around the block, since the light inside a solid block is dark. Both are taken once, when it spawns.

The block display glow works on Spigot, Paper, and Folia, so glow now runs on Spigot and Folia from 1.19.4. GlowingBlocks stays in the plugin for servers below that.

## Considered options

- Keep GlowingBlocks and shrink or move its shulker. Rejected. GlowingBlocks owns the shulker and has no hook for its size or place.
- Shrink a guardian's or crystal's hitbox with a scale attribute. Rejected. GuardianBeam builds the entities and has no hook for their attributes, and the scale attribute only exists from Minecraft 1.20.5.
- Put the crystal at the top end of the beam and aim it down at the box. Rejected. The client does not draw an entity hundreds of blocks away, so the beam would not show.
