# SQLite keeps live loot boxes, YAML stays the config

One server. A restart keeps the rolled items, owner, lock deadline, location, loot id, and remaining life. SQLite is the default. MySQL is optional for hosts who already run it. Each server's rows stay its own. YAML stays the editable loot, messages, and world settings.

Before reload, copy the SQLite file and keep a few copies. MySQL backup stays with the host.

## Considered options

- Several servers sharing one set of live loot boxes through MySQL. Rejected. That needs a claim lock and a second failure domain.
- MySQL required. Rejected. A required database server fights a lightweight public download.
- Saving only the loot id and rerolling contents on restart. Rejected. A restart must not reroll a loot box players already found.
