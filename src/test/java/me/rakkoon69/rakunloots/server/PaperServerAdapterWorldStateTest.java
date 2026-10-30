package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class PaperServerAdapterWorldStateTest extends MockBukkitWorldStateTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new PaperServerAdapter(plugin);
    }
}
