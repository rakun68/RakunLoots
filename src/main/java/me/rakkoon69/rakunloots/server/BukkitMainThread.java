package me.rakkoon69.rakunloots.server;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Runs reads, and player work, on the Bukkit main thread for the Paper and Spigot adapters.
 */
final class BukkitMainThread {

    private BukkitMainThread() {
    }

    /**
     * Runs the read in one main-thread task, on a later tick. The future completes on the main thread with what the
     * read returned, or fails with what it threw. It also fails when the task cannot be scheduled, as for a disabled
     * plugin.
     */
    static <T> CompletableFuture<T> supply(Plugin plugin, Supplier<T> read) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    result.complete(read.get());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException notScheduled) {
            result.completeExceptionally(notScheduled);
        }
        return result;
    }

    /**
     * Sends a chat message to an online player. Call it on the main thread, which owns every player on Paper and
     * Spigot.
     *
     * @return whether the player got it; false when the player is not online
     */
    static boolean sendMessage(Server server, UUID playerId, String message) {
        Player player = server.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        sendLines(player, message);
        return true;
    }

    /**
     * Sends a message as one chat line per line. A client shows a line break inside one chat message as a single
     * line. Colors do not carry across chat lines, so each line starts with the colors the lines before left on. Call
     * it on the thread that owns the player.
     */
    static void sendLines(Player player, String message) {
        String carried = "";
        for (String line : message.split("\r?\n", -1)) {
            String text = carried + line;
            player.sendMessage(text);
            carried = ChatColor.getLastColors(text);
        }
    }
}
