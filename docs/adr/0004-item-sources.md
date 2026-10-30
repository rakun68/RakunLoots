# This plugin owns item sources

An item source is a named way to resolve an item id. Shipped names are reserved: `vanilla`, `mmoitems`, `itemedit`, `executableitems`, `oraxen`, `itemsadder`. Another plugin may register a different name and resolve that id with its own rules. A register call for a shipped name is refused. Both names can appear in one loot file.

Resolve items after enable, so a plugin that depends on us can register during its own enable. Shipped sources are compile-only libraries. More sources can be added later as adapters.

## Considered options

- ItemBridge as the single soft hook. Rejected. We own each shipped source. A Spigot ItemBridge was last pushed in June 2024, and a different ItemBridge from 2025 already wraps some of these plugins. The reason to skip both is ownership, not abandonment.
- Direct hooks and a public register method that can replace a shipped name. Rejected. The two logics need two names.
- UniItem or Stacked as the implementation. Rejected. Their version range would become ours.
