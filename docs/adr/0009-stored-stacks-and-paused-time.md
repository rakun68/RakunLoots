# Rolled contents are stored stacks and time pauses offline

Phase 4 saves one loot box. It keeps the rolled contents, the owner, the remaining lock, the location, the loot id, and the remaining life. The phase text said lock deadline. A deadline keeps running while the server is down. Both timers are remaining time instead.

Rolled contents are the built item stacks, serialized with Bukkit object streams. Paper and Spigot read the same bytes. Each stack carries an optional slot. The roll sets only fixed slots. Phase 7 writes back concrete slots when it places the box. A stack that fails to load is skipped with a high warning. The box and its other stacks stay.

Remaining life and remaining lock count down only while the server runs. Players cannot reach a box while the server is down. Unending life is stored as unending.

The box keeps only its rolled contents, owner, location, and timers. Open mode, lock length, life, loot end rules, and presentation come from the current loot. A shorter life after a reload caps the remaining life. A longer one does not raise it. A row whose loot is missing or whose world is not loaded is kept and flagged. It is never deleted silently.

Phase 7 adds the placed block: the block id the box was placed as. A changed block is checked against it, not against the current loot, so a loot edit does not end every live box of that loot. No server runs this store before phase 8, so no row needs migration.

## Considered options

- Store item references and resolve them on load. Rejected. An MMOItems or ItemsAdder change would alter a box players already found.
- Store a lock deadline and an end-of-life clock time. Rejected. A box could expire or unlock while nobody can reach it.
- Snapshot the whole loot at summon. Rejected. An admin edit should reach live boxes, and the row stays small.
- Drop rows whose loot or world is missing. Rejected. A typo in a loot file would delete players' boxes.

## Consequences

An item source change does not reach saved boxes. An admin removes stale boxes with the phase 8 command. Stored bytes follow Bukkit's serialization across server upgrades.
