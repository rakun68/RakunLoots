package me.rakkoon69.rakunloots.lootbox.listener;

import me.rakkoon69.rakunloots.lootbox.SummonService;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Objects;

/**
 * Tells the summon service a chunk unloaded, so each pending summon whose spot is in it is abandoned at once and a
 * summon never loads its chunk. Runs on the thread that owns the chunk.
 */
public final class SummonChunkListener implements Listener {

    private final SummonService summons;

    public SummonChunkListener(SummonService summons) {
        this.summons = Objects.requireNonNull(summons, "summons");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        summons.chunkUnloaded(chunk.getWorld().getUID(), new ChunkPosition(chunk.getX(), chunk.getZ()));
    }
}
