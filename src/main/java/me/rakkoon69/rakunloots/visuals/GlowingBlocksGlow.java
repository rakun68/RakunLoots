package me.rakkoon69.rakunloots.visuals;

import fr.skytasul.glowingentities.GlowingBlocks;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Glow through GlowingBlocks, shaded and relocated. Building it throws {@link UnsupportedOperationException} on a
 * server that is not Paper, and another exception when GlowingEntities cannot map this server's Minecraft version; the
 * box visuals service turns glow off for the server on either.
 *
 * <p>Every glow call runs on the thread that owns the player, since GlowingBlocks reads the player's position and sends
 * their packets; on Folia that is the player's region.
 */
public final class GlowingBlocksGlow implements GlowLibrary {

    private final GlowingBlocks glowingBlocks;
    private final ServerAdapter server;

    public GlowingBlocksGlow(Plugin plugin, ServerAdapter server) {
        this.glowingBlocks = new GlowingBlocks(Objects.requireNonNull(plugin, "plugin"));
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public CompletableFuture<Void> show(UUID worldKey, BlockPosition position, UUID playerId, GlowColor color) {
        ChatColor chat = ChatColor.valueOf(color.name());
        return onPlayer(worldKey, position, playerId,
                (location, player) -> glowingBlocks.setGlowing(location, player, chat));
    }

    @Override
    public CompletableFuture<Void> hide(UUID worldKey, BlockPosition position, UUID playerId) {
        return onPlayer(worldKey, position, playerId, (location, player) -> glowingBlocks.unsetGlowing(location, player));
    }

    @Override
    public void close() {
        glowingBlocks.disable();
    }

    private CompletableFuture<Void> onPlayer(UUID worldKey, BlockPosition position, UUID playerId, Call call) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.forPlayer(playerId).run(() -> {
            try {
                Player player = Bukkit.getPlayer(playerId);
                World world = Bukkit.getWorld(worldKey);
                if (player != null && world != null) {
                    call.run(new Location(world, position.x(), position.y(), position.z()), player);
                }
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    @FunctionalInterface
    private interface Call {

        void run(Location location, Player player) throws ReflectiveOperationException;
    }
}
