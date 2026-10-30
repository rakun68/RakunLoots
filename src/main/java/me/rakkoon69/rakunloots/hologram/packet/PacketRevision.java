package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.MinecraftVersion;

import java.util.Objects;

/**
 * One NMS revision's packet adapter: the Minecraft releases it covers, both ends included, and the class that
 * implements it.
 *
 * @param name      the revision, such as {@code v1_18_R1}
 * @param from      the first release it covers
 * @param to        the last release it covers
 * @param className the {@link PacketAdapter} implementation, loaded by name only on a server it covers
 */
public record PacketRevision(String name, MinecraftVersion from, MinecraftVersion to, String className) {

    public PacketRevision {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(className, "className");
        if (to.isBelow(from)) {
            throw new IllegalArgumentException(name + " ends at " + to + " before it starts at " + from);
        }
    }

    public boolean covers(MinecraftVersion version) {
        return !version.isBelow(from) && !version.isAbove(to);
    }
}
