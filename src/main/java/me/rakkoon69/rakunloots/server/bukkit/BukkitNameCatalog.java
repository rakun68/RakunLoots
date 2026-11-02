package me.rakkoon69.rakunloots.server.bukkit;

import me.rakkoon69.rakunloots.server.NameCatalog;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The running server's block and item names for the config loader. A written id that is a current name comes back as
 * written, so {@code barrel} and {@code minecraft:barrel} both stay quiet. Another spelling of a current name, such as
 * upper case, comes back as the namespaced lower case id, which the loader warns about at high severity. A name the
 * server renamed comes back as the current id. A name the server does not know is empty.
 */
public final class BukkitNameCatalog implements NameCatalog {

    private static final String NAMESPACE = "minecraft:";
    /**
     * Renames the Bukkit enum made, by the old id and the current id. The old id is tried only when the server lacks
     * it, so a server that still has it keeps it.
     */
    private static final Map<String, String> RENAMES = Map.of("grass", "short_grass");

    @Override
    public Optional<String> currentId(String writtenId) {
        String text = writtenId.trim();
        Material material = Material.matchMaterial(text);
        if (material != null && !material.isLegacy()) {
            String path = material.getKey().getKey();
            String bare = text.startsWith(NAMESPACE) ? text.substring(NAMESPACE.length()) : text;
            return Optional.of(bare.equals(path) ? writtenId : material.getKey().toString());
        }
        String bare = text.toLowerCase(java.util.Locale.ROOT);
        bare = bare.startsWith(NAMESPACE) ? bare.substring(NAMESPACE.length()) : bare;
        String renamed = RENAMES.get(bare);
        if (renamed != null) {
            Material current = Material.matchMaterial(NAMESPACE + renamed);
            if (current != null && !current.isLegacy()) {
                return Optional.of(current.getKey().toString());
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean soundExists(String key) {
        NamespacedKey soundKey;
        try {
            soundKey = NamespacedKey.fromString(key);
        } catch (RuntimeException invalid) {
            return false;
        }
        if (soundKey == null) {
            return false;
        }
        try {
            return Registry.SOUNDS.get(soundKey) != null;
        } catch (RuntimeException | LinkageError noRegistry) {
            for (Sound sound : Sound.values()) {
                if (sound.getKey().equals(soundKey)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public Set<String> tagMembers(String namespacedTag) {
        try {
            NamespacedKey key = NamespacedKey.fromString(namespacedTag);
            if (key == null) {
                return Set.of();
            }
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
            if (tag == null) {
                return Set.of();
            }
            Set<String> ids = new LinkedHashSet<>();
            for (Material member : tag.getValues()) {
                ids.add(member.getKey().toString());
            }
            return ids;
        } catch (RuntimeException unsupported) {
            return Set.of();
        }
    }
}
