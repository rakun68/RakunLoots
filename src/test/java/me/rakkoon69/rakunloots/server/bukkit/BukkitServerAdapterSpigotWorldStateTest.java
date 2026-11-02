package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.ServerAdapter;

import org.bukkit.plugin.Plugin;

class BukkitServerAdapterSpigotWorldStateTest extends MockBukkitWorldStateTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new BukkitServerAdapter(plugin, new SpigotChunkLoader(plugin));
    }
}
