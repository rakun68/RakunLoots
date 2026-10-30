package me.rakkoon69.rakunloots.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Turns one built item stack into bytes and back with Bukkit object streams, which Paper and Spigot read the same.
 */
final class StackCodec {

    private StackCodec() {
    }

    static byte[] encode(ItemStack item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
        }
        return bytes.toByteArray();
    }

    static ItemStack decode(byte[] data) throws IOException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            Object read = in.readObject();
            if (read instanceof ItemStack item) {
                return item;
            }
            throw new IOException("stored bytes are not an item stack");
        } catch (ClassNotFoundException ex) {
            throw new IOException("stored bytes name an unknown class", ex);
        }
    }
}
