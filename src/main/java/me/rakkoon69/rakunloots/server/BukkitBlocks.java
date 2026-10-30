package me.rakkoon69.rakunloots.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Block writes, reads, drops, and block items for the server adapters. The checks run on any thread; the block work runs on the
 * thread that owns the block, and never loads a chunk.
 */
final class BukkitBlocks {

    private BukkitBlocks() {
    }

    /**
     * A block to set, checked before any task is scheduled.
     *
     * @param head the head data of a player head, if any
     */
    record Placement(Material type, Optional<Head> head) {
    }

    /**
     * The head data of a player head, and the skin it names.
     */
    record Head(String headData, URL skin) {
    }

    /**
     * Resolves a namespaced block id.
     *
     * @throws IllegalArgumentException when the id is not a block
     */
    static Material blockType(String blockId) {
        Objects.requireNonNull(blockId, "blockId");
        NamespacedKey key = NamespacedKey.fromString(blockId);
        Material type = key == null ? null : Material.matchMaterial(key.toString());
        if (type == null || !type.isBlock()) {
            throw new IllegalArgumentException("not a block id: " + blockId);
        }
        return type;
    }

    /**
     * Checks a block to set and the head data it carries.
     *
     * @throws IllegalArgumentException when the id is not a block, or the head data is set on another block or is
     *                                  not a textures value with a skin
     */
    static Placement placement(String blockId, Optional<String> headData) {
        Objects.requireNonNull(headData, "headData");
        Material type = blockType(blockId);
        if (headData.isEmpty()) {
            return new Placement(type, Optional.empty());
        }
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) {
            throw new IllegalArgumentException("head data is only for a player head, not " + blockId);
        }
        return new Placement(type, Optional.of(new Head(headData.get(), skin(headData.get()))));
    }

    /**
     * The skin URL in head data: a base64 textures value, as Mojang's profile service writes it.
     *
     * @throws IllegalArgumentException when it is not base64 JSON with {@code textures.SKIN.url}
     */
    private static URL skin(String headData) {
        try {
            String json = new String(Base64.getDecoder().decode(headData.trim()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
            JsonElement url = skin == null ? null : skin.get("url");
            if (url == null || !url.isJsonPrimitive()) {
                throw new IllegalArgumentException("head data names no skin");
            }
            return new URL(url.getAsString());
        } catch (JsonParseException | IllegalStateException | ClassCastException | MalformedURLException failure) {
            throw new IllegalArgumentException("head data is not a textures value", failure);
        }
    }

    /**
     * Sets the block, and the head's skin. Call it on the thread that owns the block. The block is set without
     * physics, so it does not fall or connect. When the head's skin cannot be applied, the block is set back to what
     * it was and the failure is thrown. The skin goes through Bukkit's profile API, which Paper deprecates
     * for its own; Spigot has only Bukkit's.
     *
     * @param world the world, or null when it is not loaded
     * @return whether the block was set; false when the world or the chunk is not loaded
     */
    @SuppressWarnings("deprecation")
    static boolean set(Server server, World world, BlockPosition position, Placement placement) {
        if (!loaded(world, position)) {
            return false;
        }
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        BlockData before = block.getBlockData();
        block.setType(placement.type(), false);
        if (placement.head().isPresent()) {
            try {
                BlockState state = block.getState();
                if (!(state instanceof Skull skull)) {
                    throw new IllegalStateException("a player head has no skull state: " + state);
                }
                skull.setOwnerProfile(profile(server, placement.head().get()));
                skull.update(true, false);
            } catch (RuntimeException failure) {
                block.setBlockData(before, false);
                throw failure;
            }
        }
        return true;
    }

    /**
     * Sets the block, and the head's skin, while it is still the expected type. Call it on the thread that owns the
     * block.
     *
     * @param world the world, or null when it is not loaded
     * @return whether the block was set; false when it is another block, or the world or the chunk is not loaded
     */
    static boolean replace(Server server, World world, BlockPosition position, Material expected,
                           Placement placement) {
        if (!loaded(world, position)
                || world.getBlockAt(position.x(), position.y(), position.z()).getType() != expected) {
            return false;
        }
        return set(server, world, position, placement);
    }

    /**
     * One plain item of the block, and a head's skin. Safe on any thread. The skin goes through Bukkit's profile
     * API, as for {@link #set}.
     *
     * @return the item, or empty when the block has no item
     */
    @SuppressWarnings("deprecation")
    static Optional<ItemStack> item(Server server, Placement placement) {
        if (!placement.type().isItem()) {
            return Optional.empty();
        }
        ItemStack item = new ItemStack(placement.type());
        if (placement.head().isPresent()) {
            if (!(item.getItemMeta() instanceof SkullMeta meta)) {
                throw new IllegalStateException("a player head item has no skull meta: " + item);
            }
            meta.setOwnerProfile(profile(server, placement.head().get()));
            item.setItemMeta(meta);
        }
        return Optional.of(item);
    }

    /**
     * A profile that shows the skin. Its UUID comes from the head data, so one head data always makes one profile.
     */
    @SuppressWarnings("deprecation")
    private static PlayerProfile profile(Server server, Head head) {
        PlayerProfile profile = server.createPlayerProfile(
                UUID.nameUUIDFromBytes(head.headData().getBytes(StandardCharsets.UTF_8)), null);
        profile.getTextures().setSkin(head.skin());
        return profile;
    }

    /**
     * Reads the block id. Call it on the thread that owns the block.
     *
     * @param world the world, or null when it is not loaded
     * @return the block id, or empty when the world or the chunk is not loaded
     */
    static Optional<String> read(World world, BlockPosition position) {
        if (!loaded(world, position)) {
            return Optional.empty();
        }
        return Optional.of(world.getBlockAt(position.x(), position.y(), position.z()).getType().getKey().toString());
    }

    /**
     * Sets the block to air while it is still the given type. Call it on the thread that owns the block.
     *
     * @param world the world, or null when it is not loaded
     * @return whether the block was removed; false when it is another block, or the world or the chunk is not
     * loaded
     */
    static boolean remove(World world, BlockPosition position, Material type) {
        if (!loaded(world, position)) {
            return false;
        }
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        if (block.getType() != type) {
            return false;
        }
        block.setType(Material.AIR);
        return true;
    }

    /**
     * Sets the block to air while it is still the given type, and loads its chunk for that when it is not loaded,
     * as the server's own block call does. The caller has already seen the chunk generated, or loaded, so this
     * generates nothing. Call it on the thread that owns the block.
     *
     * @param world the world, or null when it is not loaded
     * @return whether the block was removed; false when it is another block or the world is not loaded
     */
    static boolean removeLoading(World world, BlockPosition position, Material type) {
        if (world == null) {
            return false;
        }
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        if (block.getType() != type) {
            return false;
        }
        block.setType(Material.AIR);
        return true;
    }

    /**
     * Copies the stacks, leaving out air, so the caller may change its own after the call.
     */
    static List<ItemStack> copies(List<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        return stacks.stream()
                .map(stack -> Objects.requireNonNull(stack, "stack"))
                .filter(stack -> !stack.getType().isAir())
                .map(ItemStack::clone)
                .toList();
    }

    /**
     * Drops each stack at the block, spread as a broken block's items are. Call it on the thread that owns the
     * block.
     *
     * @param world the world, or null when it is not loaded
     * @return whether the stacks were dropped; false when the world or the chunk is not loaded
     */
    static boolean drop(World world, BlockPosition position, List<ItemStack> stacks) {
        if (!loaded(world, position)) {
            return false;
        }
        Location corner = new Location(world, position.x(), position.y(), position.z());
        for (ItemStack stack : stacks) {
            world.dropItemNaturally(corner, stack);
        }
        return true;
    }

    private static boolean loaded(World world, BlockPosition position) {
        return world != null && world.isChunkLoaded(position.x() >> 4, position.z() >> 4);
    }
}
