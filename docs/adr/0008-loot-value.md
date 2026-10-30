# A loot is open mode, life, a roll, and presentation

Phase 3 loads one loot file into one immutable value. The phase text only said that contents refer to item sources. The value is the open mode, the lock, the life, the loot end rules, the roll, and the presentation. Message overrides stay on the config snapshot, under the same loot id. The file is still read once.

Open mode is `drop` or `inventory`. The block does not choose it. Lock is seconds. Omitted, zero, and negative mean no lock. Life is seconds. Omitted means 360. A negative life is unending life. Loot end rules default off: allow an explosion, drop the block item, spill on timeout, remove when empty. Empty removal is stored on every loot and ignored for drop open.

A roll has a min, a max, and content entries. An entry is an item reference, an amount, a weight, and an optional slot. A weight below zero is always included, outside min and max. A missing slot means the roll places that entry. There is no shuffle count. Items are not resolved at load. A bad entry is skipped. A loot with no entries left is skipped. Other loots still load. The file is left unchanged.

Presentation is the display name, the description, and the block. Head data applies only when the resolved block id is `player_head`, ignoring case. An omitted display name is the loot id. A present blank display name stays blank. An omitted description is empty. An omitted loot-end key is false. Head data on any other block is ignored. Hologram lines, glow, laser, animations, summon search, the summon item, and commands are not on this value.

A value of the wrong shape warns at high severity. A bad display name uses the loot id. A bad description is empty. A bad block skips the loot. Bad head data is ignored. `entries` that is not a map skips the loot. A bad `item`, a blank item id, or a `source` that is not a string skips that entry. There is no slot ceiling in this phase.

A skipped loot still contributes its language and message overrides. Unreadable YAML contributes neither. A scalar of the wrong type, including a number that does not fit in a 32-bit integer, warns at high severity and follows the omitted or skip rule for that field. Only a negative integer life is unending life. `open_mode` is matched ignoring case.

An unknown key warns at high severity and is ignored. It does not skip the loot by itself. This covers the loot root, `loot_end`, `roll`, a content entry, and `item`. Entry ids, message strings, description lines, and `nbt` contents are data. The name catalog runs on the block and on vanilla item ids only.

## Considered options

- A flat list of item references. Rejected. This plugin's contents are a weighted roll. Phase 4 stores the rolled items so a restart does not roll again.
- Keep the shuffle count and ignore-slots flag. Rejected. A present slot is a fixed place. A missing slot is placed by the roll.
- Infer open mode from whether the block is a container. Rejected. Inventory open has to work when the block is not a container.
- Treat a negative life as a broken file. Rejected. A negative life is how an author says the box has no time end.
- Put the hologram, the summon item, and commands on this value. Rejected. Those belong to later phases.

## Consequences

Phase 4 persists the result of this roll. Phase 7 is what uses a slot, and only for inventory open. Phase 7 also decides which slots an inventory can hold. A later phase may add hologram and summon keys to the same file. Until those keys are known, they warn and are ignored.
