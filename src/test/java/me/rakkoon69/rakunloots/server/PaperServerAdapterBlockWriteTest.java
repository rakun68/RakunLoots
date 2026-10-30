package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class PaperServerAdapterBlockWriteTest extends MockBukkitBlockWriteTest {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new PaperServerAdapter(plugin);
    }
}
