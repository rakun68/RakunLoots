# The plugin draws its own holograms with packets

This amends ADR 0006. DecentHolograms is no longer a hard depend. RakunLoots runs with no other plugin installed.

A hologram sits behind one seam with several providers. The plugin's own provider sends fake entity packets only to players in range. No entity exists in the world, so nothing saves, nothing is left behind after a crash, and anti-lag plugins cannot remove it. Armor stand packets are used below 1.19.4 and text display packets from 1.19.4. Viewer tracking belongs to the plugin: join, quit, teleport, world change, and range.

Packets are written against server internals. Each NMS revision gets its own adapter subproject, from `v1_18_R1` to the newest release, each with an in-memory test, all shaded into one jar and picked at runtime. Adapters up to 1.21.11 compile with Mojang names through paperweight and ship reobfuscated, so one jar runs on Spigot and Paper. Minecraft ships unobfuscated from 26.1, and Paper 26.1 dropped Spigot-mapped plugins, so 26.x adapters compile with Mojang names and are not reobfuscated. Each adapter targets the Java level its server requires. The core stays on Java 17. A new Minecraft release needs a new adapter before the plugin draws holograms there.

DecentHolograms, HolographicDisplays, and FancyHolograms are soft depends and may draw instead. `hologram_provider` in `config.yml` picks one. `auto`, the default, uses the plugin's own provider when this version has an adapter, and otherwise the first installed soft depend that can run on this server. A named provider that is missing falls back to `auto` with a high warning. A provider that cannot run on the server, such as HolographicDisplays on Folia, is never chosen. On Folia a provider can run only when its plugin declares `folia-supported: true`. Each provider also checks its own Minecraft version range. HolographicDisplays is archived upstream and stops at 1.20.4. Its API calls go to the main thread. FancyHolograms needs Paper 1.21.4 and Java 25, so its provider lives in its own source set compiled for Java 25 and loads by class name only when FancyHolograms is present. Holograms are never saved into another plugin's files.

## Considered options

- Keep DecentHolograms as a hard depend. Rejected. Its Folia support is unreleased, and a hard depend that cannot load stops the whole plugin.
- Real marker armor stands. Rejected. They are world entities that other plugins can remove or save.
- Shade PacketEvents. Rejected. It is GPL-3.0, and the shipped jar would become a GPL work.
- HologramLib or EntityLib. Rejected. Both start above the 1.18 floor in ADR 0001.

## Consequences

Every Minecraft release is maintenance work for the packet adapters. Code learned from GPL projects is rewritten, never copied.
