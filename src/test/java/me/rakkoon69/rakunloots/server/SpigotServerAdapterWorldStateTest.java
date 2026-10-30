package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class SpigotServerAdapterWorldStateTest extends MockBukkitWorldStateTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new SpigotServerAdapter(plugin);
    }
}
