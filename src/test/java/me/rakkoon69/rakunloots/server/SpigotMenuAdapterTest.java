package me.rakkoon69.rakunloots.server;

import org.bukkit.plugin.Plugin;

class SpigotMenuAdapterTest extends MenuAdapterContract {

    @Override
    ServerAdapter newAdapter(Plugin plugin) {
        return new SpigotServerAdapter(plugin);
    }
}
