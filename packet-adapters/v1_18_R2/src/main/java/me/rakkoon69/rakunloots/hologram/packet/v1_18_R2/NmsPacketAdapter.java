package me.rakkoon69.rakunloots.hologram.packet.v1_18_R2;

import io.netty.buffer.Unpooled;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapter;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddMobPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData.DataItem;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R2.util.CraftChatMessage;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minecraft 1.18.2. A hologram is one fake armor stand per line: invisible, marker, no gravity, small, with
 * its line as a visible custom name. Each stand spawns with the living entity spawn packet and its metadata. A line's
 * legacy colour codes become a text component through CraftBukkit's own converter, which the client gets as JSON.
 *
 * <p>Written against Mojang's names; the plugin jar ships it remapped to Spigot's, the names the server runs.
 */
public final class NmsPacketAdapter implements PacketAdapter {

    // Entity keeps these data ids private; the tests check each against the server's own.
    static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Optional<Component>> CUSTOM_NAME =
            new EntityDataAccessor<>(2, EntityDataSerializers.OPTIONAL_COMPONENT);
    static final EntityDataAccessor<Boolean> CUSTOM_NAME_VISIBLE =
            new EntityDataAccessor<>(3, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> NO_GRAVITY = new EntityDataAccessor<>(5, EntityDataSerializers.BOOLEAN);

    /**
     * Entity's shared flag 5, invisible, as its bit.
     */
    static final byte INVISIBLE = 1 << 5;
    static final byte SMALL_MARKER = (byte) (ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_MARKER);

    /**
     * How far apart the stands of two lines are.
     */
    static final double LINE_SPACING = 0.25;
    /**
     * How far below a marker stand's name its bottom sits: the client draws a name 0.5 above an entity's height,
     * which is 0 for a marker, and a name is 9 pixels of 0.025 blocks, 0.225, tall down from there. A stand this far
     * below a line's height puts the bottom of its name there.
     */
    static final double NAME_BOTTOM = 0.5 - 9 * 0.025;

    private final AtomicInteger entityCounter;

    /**
     * Finds the server's entity id counter, so it fails here, at selection, on a server this revision does not fit.
     */
    public NmsPacketAdapter() {
        this.entityCounter = entityCounter();
    }

    /**
     * Entity's own id counter, private, found as its only static AtomicInteger since its name differs between Mojang's
     * and Spigot's names.
     */
    private static AtomicInteger entityCounter() {
        List<Field> counters = Arrays.stream(Entity.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == AtomicInteger.class)
                .toList();
        if (counters.size() != 1) {
            throw new IllegalStateException("Entity has " + counters.size()
                    + " static AtomicInteger fields, and the entity id counter is the only one on 1.18.2");
        }
        try {
            Field counter = counters.get(0);
            counter.setAccessible(true);
            return (AtomicInteger) counter.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read Entity's id counter", e);
        }
    }

    @Override
    public int entityCount(int lineCount) {
        if (lineCount < 1) {
            throw new IllegalArgumentException("a hologram has at least one line, not " + lineCount);
        }
        return lineCount;
    }

    @Override
    public int nextEntityId() {
        return entityCounter.incrementAndGet();
    }

    @Override
    public Object spawn(List<Integer> entityIds, HologramPosition position, List<String> lines) {
        Objects.requireNonNull(position, "position");
        fits(entityIds, lines);
        int armorStand = Registry.ENTITY_TYPE.getId(EntityType.ARMOR_STAND);
        List<Packet<?>> packets = new ArrayList<>();
        for (int index = 0; index < entityIds.size(); index++) {
            int entityId = entityIds.get(index);
            FriendlyByteBuf spawn = buffer();
            spawn.writeVarInt(entityId);
            spawn.writeUUID(UUID.randomUUID());
            spawn.writeVarInt(armorStand);
            spawn.writeDouble(position.x());
            spawn.writeDouble(position.y() - NAME_BOTTOM + index * LINE_SPACING);
            spawn.writeDouble(position.z());
            // Yaw, pitch, and head yaw, then no velocity.
            spawn.writeByte(0);
            spawn.writeByte(0);
            spawn.writeByte(0);
            spawn.writeShort(0);
            spawn.writeShort(0);
            spawn.writeShort(0);
            packets.add(new ClientboundAddMobPacket(spawn));
            packets.add(entityData(entityId, List.of(
                    new DataItem<>(SHARED_FLAGS, INVISIBLE),
                    new DataItem<>(NO_GRAVITY, true),
                    new DataItem<>(ArmorStand.DATA_CLIENT_FLAGS, SMALL_MARKER),
                    new DataItem<>(CUSTOM_NAME_VISIBLE, true),
                    new DataItem<>(CUSTOM_NAME, name(lineOf(lines, index))))));
        }
        return new Packets(packets);
    }

    @Override
    public Object meta(List<Integer> entityIds, List<String> lines) {
        fits(entityIds, lines);
        List<Packet<?>> packets = new ArrayList<>();
        for (int index = 0; index < entityIds.size(); index++) {
            packets.add(entityData(entityIds.get(index),
                    List.of(new DataItem<>(CUSTOM_NAME, name(lineOf(lines, index))))));
        }
        return new Packets(packets);
    }

    @Override
    public Object destroy(List<Integer> entityIds) {
        if (entityIds.isEmpty()) {
            throw new IllegalArgumentException("no entities to destroy");
        }
        return new Packets(List.of(
                new ClientboundRemoveEntitiesPacket(entityIds.stream().mapToInt(Integer::intValue).toArray())));
    }

    @Override
    public void send(UUID playerId, Object packet) {
        Objects.requireNonNull(playerId, "playerId");
        if (!(packet instanceof Packets packets)) {
            throw new IllegalArgumentException("not a packet of the " + NmsPacketAdapter.class.getName() + ": "
                    + packet);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return;
        }
        ServerGamePacketListenerImpl connection = craftPlayer.getHandle().connection;
        if (connection == null) {
            return;
        }
        for (Packet<?> each : packets.list()) {
            connection.send(each);
        }
    }

    /**
     * A line's text as a custom name; an empty line has none, so its stand shows nothing.
     */
    static Optional<Component> name(String line) {
        return Optional.ofNullable(CraftChatMessage.fromStringOrNull(line));
    }

    /**
     * The line of the stand at this index, where index 0 is the lowest stand and the last line.
     */
    private static String lineOf(List<String> lines, int index) {
        return lines.get(lines.size() - 1 - index);
    }

    private static void fits(List<Integer> entityIds, List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram has at least one line");
        }
        if (entityIds.size() != lines.size()) {
            throw new IllegalArgumentException(entityIds.size() + " entities for " + lines.size() + " lines");
        }
    }

    private static ClientboundSetEntityDataPacket entityData(int entityId, List<DataItem<?>> items) {
        FriendlyByteBuf data = buffer();
        data.writeVarInt(entityId);
        SynchedEntityData.pack(items, data);
        return new ClientboundSetEntityDataPacket(data);
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /**
     * The packets one build made, sent in order.
     */
    static final class Packets {

        private final List<Packet<?>> list;

        Packets(List<Packet<?>> list) {
            this.list = List.copyOf(list);
        }

        List<Packet<?>> list() {
            return list;
        }
    }
}
