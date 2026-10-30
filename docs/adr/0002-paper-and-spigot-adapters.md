# Paper and Spigot are the server adapters

Amended by ADR 0020: Paper and Spigot share one Bukkit adapter, and their difference is a chunk loader.

Loot, messages, and loot end do not take Bukkit types. Item sources may. The item API is the same on Paper and Spigot, so a second item adapter would not shrink the core. World, chunk, and block access still sit behind one seam with two adapters: Paper and Spigot. Both are compiled against the 1.18 API. A newer adapter is added only when a call we need is missing on 1.18.

Summon search does its heavy work off the main thread. The Paper adapter uses async chunk loads. The Spigot adapter hops block reads back to the main thread.

## Considered options

- A separate adapter for 1.18, 1.20.5, 1.21, and 26.2 from the start. Rejected. Those adapters would be empty until an API call actually breaks, and every loot change would be repeated in each one.
- Version checks inside the loot box lifecycle. Rejected. Callers would have to know which server they are on.
- Item sources stay free of Bukkit, and vanilla additions wait for a later phase. Rejected. Those additions are part of phase 2, and the item API is not the Paper and Spigot split.
