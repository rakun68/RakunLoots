package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramServer;
import me.rakkoon69.rakunloots.hologram.MinecraftVersion;

import java.util.List;
import java.util.Objects;

/**
 * Picks the packet adapter for the Minecraft release the server runs, and loads it by class name, so no other
 * revision's classes load. Each adapter runs on Spigot, Paper, and Folia alike.
 */
public final class PacketAdapters {

    /**
     * Every revision this build ships, oldest first. Each version adapter adds its row, and its subproject in
     * {@code packet-adapters}.
     */
    static final List<PacketRevision> REVISIONS = List.of(
            revision("v1_18_R1", "1.18", "1.18.1"),
            revision("v1_18_R2", "1.18.2", "1.18.2"),
            revision("v1_19_R1", "1.19", "1.19.2"),
            revision("v1_19_R2", "1.19.3", "1.19.3"),
            revision("v1_19_R3", "1.19.4", "1.19.4"),
            revision("v1_20_R1", "1.20", "1.20.1"),
            revision("v1_20_R2", "1.20.2", "1.20.2"),
            revision("v1_20_R3", "1.20.3", "1.20.4"),
            revision("v1_20_R4", "1.20.5", "1.20.6"),
            revision("v1_21_R1", "1.21", "1.21.1"),
            revision("v1_21_R2", "1.21.2", "1.21.3"),
            revision("v1_21_R3", "1.21.4", "1.21.4"),
            revision("v1_21_R4", "1.21.5", "1.21.5"),
            revision("v1_21_R5", "1.21.6", "1.21.8"),
            revision("v1_21_R6", "1.21.9", "1.21.10"),
            revision("v1_21_R7", "1.21.11", "1.21.11"),
            revision("v26_1", "26.1", "26.1.2"),
            revision("v26_2", "26.2", "26.3"));

    private PacketAdapters() {
    }

    /**
     * A revision whose adapter is the class {@code NmsPacketAdapter} in the package named after it.
     */
    private static PacketRevision revision(String name, String from, String to) {
        return new PacketRevision(name, MinecraftVersion.parse(from), MinecraftVersion.parse(to),
                PacketAdapters.class.getPackageName() + "." + name + ".NmsPacketAdapter");
    }

    /**
     * Picks the adapter for this server's Minecraft version from the revisions this build ships.
     */
    public static PacketAdapterMatch select(HologramServer server) {
        Objects.requireNonNull(server, "server");
        return select(server.minecraftVersion(), REVISIONS, PacketAdapters.class.getClassLoader());
    }

    /**
     * Picks the first revision that covers the version and creates its adapter. No revision, or one whose class does
     * not load or create, is no match, with the reason naming the version.
     */
    static PacketAdapterMatch select(MinecraftVersion version, List<PacketRevision> revisions, ClassLoader loader) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(revisions, "revisions");
        Objects.requireNonNull(loader, "loader");
        for (PacketRevision revision : revisions) {
            if (revision.covers(version)) {
                return load(version, revision, loader);
            }
        }
        return PacketAdapterMatch.none("there is no packet adapter for Minecraft " + version);
    }

    private static PacketAdapterMatch load(MinecraftVersion version, PacketRevision revision, ClassLoader loader) {
        try {
            PacketAdapter adapter = Class.forName(revision.className(), true, loader)
                    .asSubclass(PacketAdapter.class)
                    .getConstructor()
                    .newInstance();
            return PacketAdapterMatch.matched(adapter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return PacketAdapterMatch.none("the packet adapter " + revision.name() + " for Minecraft " + version
                    + " cannot load: " + failure);
        }
    }
}
