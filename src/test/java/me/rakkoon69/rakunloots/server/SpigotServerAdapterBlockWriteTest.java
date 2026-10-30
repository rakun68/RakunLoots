package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class SpigotServerAdapterBlockWriteTest extends MockBukkitBlockWriteTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new SpigotServerAdapter(plugin);
    }
}
