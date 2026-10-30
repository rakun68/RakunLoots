# Live loot boxes hold their chunk, unless the world skips unloaded chunks

This amends ADR 0006 and ADR 0014.

An admin removal of a loot box whose chunk was not loaded unregistered the box and left its block in the world. The server adapter's block removal never loads a chunk, so it missed, and nothing guarded the barrel any more. The world's `unloaded_chunks` setting in `worlds.yml` now decides how a live loot box and its chunk go together. The setting already says how the owner wants unloaded chunks treated, so the fix reads it instead of adding a second one.

Under `load` or `generate`, a live loot box keeps its chunk loaded for its whole life with a plugin chunk ticket. The ticket is added when the box is placed, and for each stored box registered at start. It is removed at every loot end, once the block is removed and the drops are dropped, and at plugin stop. So every loot end, tick, and timeout of such a box sees a loaded chunk. A plugin holds one ticket per chunk, so the lifecycle counts the boxes in each chunk, asks the server for the ticket when the first box holds the chunk, and gives it back when the last box lets go. A pending summon of ADR 0013 holds its spot's chunk the same way under `load` or `generate`, from the moment it reserves the spot until the box is placed or the summon is abandoned: the search under `load` can pick a chunk no player is near, and without the hold that chunk unloads during the summon delay and abandons the summon as `CHUNK_UNLOADED`. The box takes its own hold before the pending summon gives up its, so the chunk never goes unheld between them, and a pending summon and a box in one chunk share the one ticket through the per-chunk count. Under `skip` a pending summon holds nothing, as before. A hold loads a chunk only when it is already generated, and never generates one. A world that `worlds.yml` does not name has the default policy, `load`, so its boxes hold their chunk too.

Under `skip`, no ticket is held. An admin removal of a box in an unloaded chunk loads the chunk through the server adapter, removes the block only while it is still the box's block, and lets the chunk unload as any chunk does. Paper and Folia load it through the async chunk call. Spigot loads it on the main thread, through the same queue as a search read, so at most one chunk loads per tick. An admin removal makes this call under every policy. Under `load` and `generate` it finds the chunk loaded. The other loot ends happen where the chunk is loaded: a break, a click, an explosion, and a changed block come from events in it, and a timeout waits for its chunk to load, as ADR 0006 says.

A reload that changes a world's policy applies it to that world's live boxes. Skip gives their tickets back, and load or generate takes them. A change between load and generate changes nothing.

The server adapter gains `holdChunk`, `releaseChunk`, and `removeBlockLoadingChunk`. On Paper and Spigot, the ticket is added and removed on the main thread. A release called on the main thread runs before it returns, so a stopping plugin needs no scheduler. On Folia, the ticket is added and removed in a task on the region that owns the chunk, as ADR 0010 sets for chunk work. The chunk is loaded first through the async chunk call, which any thread may start, so no region task is queued at a chunk that is not loaded. A release that cannot be scheduled because the plugin is stopping runs on the calling thread. folia-api does not say which thread may change a chunk ticket, so the adapter takes the strict rule.

## Considered options

- Only load the chunk for an admin removal. Rejected. It fixes the one end an admin orders, and leaves the box in an unloaded chunk without a life: its timeout waits until a player happens to load the chunk, its animations and glow stop, and a deleted loot's boxes outlive their life, which ADR 0014 refuses.
- Only chunk tickets, under every policy. Rejected. `skip` is how the owner says chunks must not be loaded for loot boxes. A ticket for every stored box would load all of them at start. A hold that fails, as for a chunk that is not generated, would still leave the removal with the old bug.
- Chunk tickets as a separate opt-in flag. Rejected. The flag could disagree with `unloaded_chunks`, as a `skip` world with the flag on, and off by default it leaves the bug on every server that does not set it.

## Consequences

A world on `load` or `generate` never unloads the chunk of a live loot box, so it never reports that chunk unloaded, and a box is never out of view while it lives. A server with many loot boxes in many chunks keeps that many chunks loaded. The shipped `worlds.yml` says `load`, so a new server holds tickets by default, and an owner who wants chunks to unload sets `skip`. Smart summon cannot skip, as ADR 0011 says, so a smart summon world always holds.

At start on Spigot, the holds of stored boxes load their chunks at most one per tick, so a server with many stored boxes loads them over many ticks. A box's life and lock count down the same meanwhile.

## Amendment: the search hands its chunk to the pending summon, and the shipped policy is generate

The phase 8 version test still saw pending summons abandoned as `CHUNK_UNLOADED`: about 1 in 8 on Paper and Spigot with no player near, and nearly every one on Folia. The pending summon took its hold asynchronously after the search returned, so the chunk the search had loaded could unload first. From phase 8d, once a failing test confirms that cause, the search takes the hold in the same read that loaded the chunk and hands it to the pending summon. A summon with no delay waits for the hold before it writes the block. The per-chunk count is unchanged.

The shipped `worlds.yml` now says `generate`. Under `load`, a fresh world has few generated chunks in the ring, so a summon almost always found no spot. Its comment recommends `load` or `generate`, and says `skip` works only with `mode: spawn` and a spawn ring where chunks stay loaded.

### Considered options

- Ignore a chunk unload while the pending summon's hold is still on its way. Rejected. It hides the event, and on Folia the unload and the hold run on different threads.
- Keep `load` and fall back to `generate` when no spot is found. Rejected. It hides a second policy inside the first.
