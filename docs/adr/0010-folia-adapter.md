# Folia is a third server adapter

Amended by ADR 0020: Paper and Spigot share one Bukkit adapter, and their difference is a chunk loader.

This amends ADR 0002. World, chunk, and block access, player positions, and scheduling sit behind one seam with three adapters: Paper, Spigot, and Folia.

The seam hands back plugin types only: a world key, block positions as ints, and block ids as namespaced strings. A read returns one read-only chunk view, built from a chunk snapshot. Paper loads the chunk async. Spigot takes the view on the main thread. Folia takes it on the region that owns the chunk. Player positions are one snapshot per world. Folia gathers them from each player's entity scheduler.

The scheduler seam runs work at a location, for a player, globally, or async, with delay and repeat. Phase 6 onward schedules through it only.

An unloaded chunk follows a policy passed by the caller: skip it, load it when it is already generated, or load and generate it.

The Folia adapter is its own source set, compiled against folia-api 1.20.1, Java 17 bytecode. Enable loads it by class name only when `io.papermc.paper.threadedregions.RegionizedServer` exists. The scheduler classes are on plain Paper too, so they do not identify Folia. Paper is picked when `World#getChunkAtAsync` exists. Spigot is the rest.

## Considered options

- Paper and Spigot only, and refuse to start on Folia. Rejected. Folia servers are a target.
- Reflection for Folia inside the 1.18 source set. Rejected. Every call would be a string, and the compiler would not check it.
- Reads only in phase 5, scheduling later. Rejected. Phase 6 needs to run at a location on Folia, and phase 7 would need a second threading design.

## Consequences

`folia-supported: true` goes into `plugin.yml` in phase 8, after the old summon path is gone. The old path calls the Bukkit scheduler, which throws on Folia. Folia also waits on DecentHolograms, a hard depend. Phase 7 checks whether it runs on Folia and decides what the hologram does there.

## Status: not experimental

The phase 8 version test on Folia 26.2 build 7 found Folia experimental: nearly every summon was abandoned because its chunk unloaded, and glow failed on the region shutdown thread at stop. Phase 8d fixed both. `rltest` then passed 3 runs in a row on Folia 26.2 with no `CHUNK_UNLOADED` and no ERROR, so the Folia adapter answers `experimental()` with false and enable logs no experimental warning. The laser stays off on Folia with one low warning, because GuardianBeam uses the Bukkit scheduler. That is a stated limit.
