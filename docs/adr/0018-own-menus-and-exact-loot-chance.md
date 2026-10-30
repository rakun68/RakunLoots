# The plugin draws its own menus, and loot chance is exact

This amends ADR 0013 and ADR 0014.

Phase 9 brings the claim menu and the loots wiki. Neither existed before: the old wiki was an empty FastInv stub that nothing opened, and there was never a claim menu. Nothing carries over.

## Menus

The plugin draws its own menus, with no GUI library. A menu is a view in plugin types: slots mapped to icons and click actions. A thin Bukkit renderer turns it into an inventory whose holder marks it as a menu, and one listener cancels every click and drag in it. A menu opens and closes on the player's scheduler through the server adapter, so it runs on Folia. Tests run on the view, in memory.

Each menu has its own file in `menus/`, beside `loots/`. A file sets layout: rows, content slots, filler, and buttons with their items, slots, and sounds. Text stays in the language files, as ADR 0003 says. A file whose YAML cannot be read uses the shipped layout from the jar, with a severe warning, so a typo never keeps players from their unclaimed summon items. A reload closes every open menu.

The claim command opens the claim menu. `claim all` gives what the claim command gave before. A click takes one loot's items, as ADR 0013 says, and a claim all button takes everything that fits. The loots wiki has its own command under `rakunloots.wiki`, default true. It is read only.

## Loot chance

A loot's roll picks a count uniform in [min, max], capped at the drawable entries, then draws that many entries, weighted and without repeats. The wiki shows the chance that an entry lands in one loot box, worked out exactly from these rules.

A weighted draw without repeats orders the entries as independent exponential clocks do: entry j rings at a time with rate w_j, and the draws are the entries in the order they ring. Entry i is drawn among the first k when fewer than k other entries ring before it:

P(i in the first k) = ∫ w_i e^(-w_i t) · P(fewer than k others rang by t) dt, over t from 0 to infinity.

By time t, entry j has rung with chance 1 - e^(-w_j t), the same for every i. So one time grid serves every entry. At each point the plugin builds the full count distribution once, with equal weights grouped as binomial steps, then removes one entry per distinct weight. Removal runs forward when its chance is at most one half and backward above, dividing by the larger term to keep the calculation stable. It uses the count tail built in that direction; the smaller algebraic tail does not guarantee fewer recurrence steps. With n drawable entries and g distinct weights, the full grouped convolution and removals cost at most O(n^2 + g*n) per grid point. The total also depends on the quadrature grid needed for the accuracy bound. Counts in [min, max] share the distribution and quadrature; there is no separate integral per count. Every loot is worked out after each load, off the main thread.

An entry with a weight below zero is always included. An entry with weight zero is never drawn and is hidden. When the count reaches the drawable entries for every k, every drawable entry is certain.

The timer chance is a loot's share of its world's timer total, as `WorldTimers` picks it. `{chance:N}` sets the decimals of either chance. A chance above zero never shows as zero, and a chance below one never shows as 100.

## Considered options

- A GUI library, such as triumph-gui, InventoryFramework, or FastInv again. Rejected. Its version range and Folia support would become ours, for two menus, as ADR 0004 says of item libraries.
- One `menus.yml` for every menu. Rejected. Each file has one job, as ADR 0003 says.
- Turn a menu off when its file is broken. Rejected. Players would lose the claim menu to an owner's typo.
- Show the weight's share of the total. Rejected. It is wrong as soon as a box draws more than one entry.
- Simulate rolls with a fixed seed. Rejected. It is an estimate, it is slower than the exact sum, and rare entries need many rolls to show at all.
- Work out chances when a page first opens. Rejected. The exact sum is cheap enough to run for every loot after each load.

## Consequences

`menus/` joins the files the config module writes on first run. The claim command no longer gives at once. The roll rules now have a second reader: a change to `LootRoller` changes the chance math with it, and a test checks the math against every draw order of small loots.
