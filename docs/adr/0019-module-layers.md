# Modules form layers, and a split never widens an interface

A module is a top-level package under `me.rakkoon69.rakunloots` with its subpackages. Its interface is its root package. The modules form layers, and each one imports only modules below it: `warning`, `text`, and `match` at the bottom; then `server`; `items`; `config`; `roll`; `storage`; `lootbox`; `hologram`, `animation`, `visuals`, and `menu`; `summon`; `wiki`; `command`; and the plugin class on top. `roll` sits below `storage` because a saved loot box keeps its rolled contents, and `wiki` sits below `command` because a command opens the wiki. Modules on one layer never import each other. Inside one module, the root and its subpackages may import each other. An ArchUnit test, in test scope only, checks the order.

The owner ranks lightweight and fast above maintainable. A package costs nothing at runtime, but a Java subpackage cannot see package-private types. A split that only moves files would make implementations public and widen the module's interface. A module therefore splits implementations into a subpackage only when it mixes a seam with two or more shipped adapters, or holds more than about 15 files. The subpackage exposes one public entry type, and its adapters stay package-private. A seam gets an interface only when it has two adapters, and a test fake counts as one.

Config is the one reader and writer of the config files, as ADR 0003 says. So every value type a config file defines lives in `config`, and the feature modules read it from there. The one exception is the warning control types, `WarningLevel` and `WarningSettings`. They live in `warning`, which sits below `config`, because the warning log judges every warning with them and `warning` imports no module. `config` still parses `warnings.yml` and builds them.

## Considered options

- Only forbid cycles between modules. Rejected. Two modules could still import each other through a third in a different order each phase, and nothing would say which way an import should go.
- Literal Java package rules, with no cycle between any two packages. Rejected. A subpackage of adapters and the registry that builds them always import each other, and forbidding that forces the adapters public.
- Split `config` into one subpackage per file kind, nine in all. Rejected. Most would hold one or two classes and make their helpers public for no gain. Config splits only where a file kind has a parser and several value types: loot, summon rules, menus, and block groups.
- Each feature parses its own file. Rejected. It contradicts ADR 0003.
- Leave the packages flat. Rejected. `config` had 42 files and `command` 37, and `config` imported ten feature modules that imported it back.
