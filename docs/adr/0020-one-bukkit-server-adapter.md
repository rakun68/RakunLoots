# One Bukkit server adapter, with a chunk loader for Paper and Spigot

This amends ADR 0002 and ADR 0010. The server seam keeps one `ServerAdapter` interface. Its adapters become two: Bukkit and Folia.

The Paper and Spigot adapters had 34 overrides each and differed only in how an unloaded chunk loads. Paper uses the async chunk call. Spigot queues loads and runs at most one a tick on the main thread. One `BukkitServerAdapter` now holds everything they shared. That difference moves into a package-private chunk loader with two adapters: Paper and Spigot. `ServerAdapters` picks the chunk loader with the same probe it used to pick the adapter: Paper when `World#getChunkAtAsync` exists, Spigot otherwise. Folia is still picked first, by class name.

The Bukkit adapter, its helpers, and the Folia adapter's source set share the `server.bukkit` package, so the helpers stay package-private. Contract tests run once per chunk loader instead of once per adapter.

## Considered options

- Keep two full adapters. Rejected. About 450 duplicated lines, and a fix to one adapter was easy to miss in the other.
- One adapter with a runtime check at each chunk call. Rejected. Callers inside the adapter would branch on the server, the very thing ADR 0002 keeps out of the code.
- Make the Bukkit helpers public so Folia can stay in `server`. Rejected. It widens the server module's interface by seven types.
