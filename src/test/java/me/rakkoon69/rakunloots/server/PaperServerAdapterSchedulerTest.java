package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class PaperServerAdapterSchedulerTest extends MockBukkitSchedulerTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new PaperServerAdapter(plugin);
    }
}
