package me.rakkoon69.rakunloots.lootbox;

import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.LoadedWorld;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the debug lines of the summon service, the world timers, and the lifecycle. A line that names a world asks
 * the server adapter for the world's name, and only while debug mode is on, so a server with debug off does no extra
 * work.
 */
public final class DebugLines {

    private DebugLines() {
    }

    /**
     * Writes the line the function builds from the world's name, when debug mode is on. A world that is not listed is
     * named by its key. Never throws.
     */
    public static void inWorld(ServerAdapter adapter, Warnings sink, UUID worldKey, Function<String, String> line) {
        if (!sink.debugEnabled()) {
            return;
        }
        try {
            adapter.loadedWorlds().handle((worlds, failure) -> {
                String name = worldKey.toString();
                if (worlds != null) {
                    for (LoadedWorld world : worlds) {
                        if (world.key().equals(worldKey)) {
                            name = world.name();
                        }
                    }
                }
                sink.debug(line.apply(name));
                return null;
            });
        } catch (RuntimeException ignored) {
            // A debug line never breaks what it describes.
        }
    }

    public static String at(BlockPosition position) {
        return "x=" + position.x() + " y=" + position.y() + " z=" + position.z();
    }

    public static String source(SummonSource source) {
        return source.summonItemPlayer().map(player -> "summon item " + player)
                .orElse(source.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }
}
