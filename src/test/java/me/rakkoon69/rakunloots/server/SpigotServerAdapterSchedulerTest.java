package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class SpigotServerAdapterSchedulerTest extends MockBukkitSchedulerTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new SpigotServerAdapter(plugin);
    }
}
