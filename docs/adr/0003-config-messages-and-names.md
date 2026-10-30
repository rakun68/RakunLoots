# One config module, language folder, no migration

The plugin was never published, so old files do not load. One module is the only reader and writer. Each file has one job: globals, one file per language for default messages, world settings, and one file per loot. A loot file names the language it was written in and may replace one default message with one string.

The server shows one active language, chosen in config. English, Vietnamese, and Simplified Chinese ship. An owner may add a language by adding a file. A missing language file uses English. A missing key in the active language uses the English line. A missing English key is reported at severe severity and the lookup yields the key id. A loot message override wins in every language. A missing override uses the active language.

A loot language that is missing or differs from the active language is warned, and the file is left unchanged. The override still shows. One present override, including a blank string, raises that warning to high. No override stays low. A display name or other loot text does not raise it. Config can silence loot language warnings without raising the warning level.

The warning level is the minimum severity reported: low, high, or severe. The default is low.

A fresh file uses the running server's block and item names. An old name still resolves to the current one. The plugin warns at high severity, names the current id, and leaves the file for the user to edit.

## Considered options

- One `messages.yml` for every language. Rejected. The owner switches language by picking a file.
- Each player's client language. Rejected. A region runs one language.
- A per-language override map inside the loot file. Rejected. The override is one string, written in the language that server runs.
- Rewriting the loot language when it clashes. Rejected. The text would still be in the old language, and the warning would stop.
- A reader that accepts the old key names. Rejected. Nothing has shipped.
- One globals file for everything except per-loot files. Rejected. The option list will grow, and each file should have one job.
- Rewriting old names in place on load. Rejected. The user edits the file after the warning.

## Amendment: optional entries, the warnings file, and placeholders

From phase 8d, a fresh file is the shipped text. The phase 8 version test showed the shipped `blocks.yml` warning on its own server: `minecraft:grass` is `short_grass` from 1.20.3, and `#minecraft:tall_flowers` is unknown on 1.21.11 and 26.x. Rewriting old names when a file is first written needs a rename table for each version, and fixes only fresh files: a file written on 1.20 still warns after the server moves to 1.21.

A block list or block group entry may be an optional entry, written with a leading `?`. It is used only when the server knows that exact id or tag. An old name does not resolve for it, so `?minecraft:grass` on 26.2 is skipped, not read as `short_grass`. The shipped file names both the old and the new id as optional entries where a block was renamed. Optional entries the server lacks are listed in one low warning per load. A plain entry keeps the rules above: an old name resolves with a high warning.

From phase 8e, the jar ships every block id and tag of every supported version. An entry no supported version knows is a typo, warned high with up to 3 close matches. Past 20 typos in a load, the rest share one high line that names 10 and counts the others, with no close-match search. It is read only when an entry misses.

From phase 8e, `warnings.yml` sets each warning kind's severity, or `off`. It replaces the config switch that silenced loot language warnings. The warning level stays the threshold for the log.

From phase 8e, placeholders are written `{name}`. Every shipped string that holds one is quoted, since YAML reads a value that starts with `{` as a map.

### Considered options

- Rewrite renamed ids when a fresh file is written. Rejected. It needs a rename table for each version, and a file written on an older server still warns after an upgrade.
- Ship only entries every version knows. Rejected. New blocks would be missing on new servers.
- One low warning for each skipped optional entry. Rejected. The shipped file would print several lines on every reload.
