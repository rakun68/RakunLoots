# Vanilla additions stay on the vanilla source

An item reference is a source name plus an id. A missing or blank source name is vanilla. A MMOItems id is a type and an item id, split on the first colon. Name and lore accept `&` codes and `<#RRGGBB>`, the same translation the plugin already uses.

Vanilla additions are a display name, lore, enchantments, custom model data, item flags, unbreakable, damage, and nbt. They apply only when the source is vanilla. Any other source ignores them. Nbt is applied first. The explicit keys then win. A missing key leaves that part of the vanilla item. An empty name, lore, enchants list, or flags list clears that part.

A bad id is a miss with a high warning. One bad addition is skipped with a high warning, and the item still builds. That includes an unknown enchant, an unknown flag, a bad custom model data, a bad damage value, a bad unbreakable value, and an nbt entry the library rejects.

## Considered options

- A bad enchant fails the whole item. Rejected. The id is valid. The bad line is the mistake.
- A nested block per source, such as `MMOItems_value`. Rejected. A plugin that registers its own source would need a YAML key we cannot know ahead of time.
