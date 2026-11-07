# Summon search, placement rules, and block groups

Phase 6 splits summon placement into three parts. A block group names a set of blocks. A placement rule decides whether one column holds a valid spot. A summon search decides which columns to try.

Block groups live in `blocks.yml`. The plugin writes the shipped groups on first run, and the owner may edit them. In any block list, `@name` is a group, a plain id is a block, and `#minecraft:flowers` is a vanilla tag. Every name resolves through the name catalog. A group may contain other groups. A cycle refuses every group in it with a severe warning. A loot list that names a refused group drops that entry with a high warning, and the loot still loads.

Summon rules are a separate value per loot id, beside the loot value. The loot value stays as ADR 0008 left it. There are six placement rules: top surface, any surface, buried, deep buried, underwater, and anywhere. Each has its own options and its own `attempts`. `config.yml` holds a default per rule, and a loot may override it. `summon_type` takes a rule name only. A number, such as an old 1 to 4, warns high and uses top surface.

The world's `unloaded_chunks` setting picks the search method. Under `load` or `generate`, the search picks a random column spread evenly over the ring and reads its chunk. It tries that column first. When it holds no spot, the search scans every column of that chunk inside the ring and picks a random valid one, so one attempt is one chunk read here too. A chunk scanned empty is not scanned again in the same search. Under `skip`, it lists the loaded chunks in the ring, shuffles them, and scans one chunk per attempt. It returns a random valid spot from the first chunk that has one. `only_loaded_chunks` is gone. It is `unloaded_chunks: skip`.

Every search area is a summon ring: a square band between a minimum and a maximum distance. A server summon uses the spawn ring or smart summon. Smart summon needs `load` or `generate`. With `skip`, it warns high and runs as `load`. It ignores idle players, unless all of them are idle. With no players, `when_all_absent` summons in the spawn ring or does nothing. A player's pick weight runs linearly from `1 + pick_bias` for the player nearest the median down to 1 for the farthest. The median is the per-axis median x and z of the active players. The spot's angle leans toward the median. `median_pull` is the percent of summons that land on the half of the ring facing the median. Distance is spread evenly by area.

Idle tracking samples player positions every 30 seconds on the global scheduler. A player is idle after staying within 1 block for `idle_seconds`.

The search runs on the async scheduler. Chunk views are snapshots, so every column check stays off server threads. Only loading and generating a chunk touch one. On Spigot that is the main thread, so Spigot loads at most one chunk per tick. The limit is fixed in code.

Phase 6 returns a spot or a miss with a reason. It writes no block. The caller supplies a check for spots already holding a loot box. Phase 7 re-checks the chosen column on its owning thread before it writes.

## Considered options

- Keep smart summon as it was. Rejected. A failed search fell back to column 0, 0 by accident, and nothing kept the odds between players fair.
- A separate attempt budget for scans under `skip`. Rejected. One attempt is one scanned chunk, so a second setting adds nothing.
- Hard-coded block lists per placement rule. Rejected. Names change across versions, and owners reuse the same lists across loots.
- Summon rules on the loot value. Rejected. The loot value excludes summon search on purpose.

## Consequences

`worlds.yml` gains a `summon_search` section. The old keys `smart_summon`, `only_loaded_chunks`, and `max_distance_to_world_spawn_point_x/z` still load with an old-key warning, and the file is left unchanged. The old x and z become one square ring whose max is the larger of the two.
