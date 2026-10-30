package me.rakkoon69.rakunloots.hologram.packet.v26_2;

import me.rakkoon69.rakunloots.hologram.HologramPosition;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minecraft 26.2. A hologram is one fake text display holding every line, joined by new lines, with billboard set to
 * center so it always faces the player. A text display draws its text upward from its position, so the position, where
 * the lowest line sits, is where it spawns. It spawns with the add entity packet, then its metadata as a list of data
 * values. Each line's legacy colour codes become a text component through CraftBukkit's own converter, one line at a
 * time so a colour never runs on into the next line; the client gets it as NBT.
 *
 * <p>From 1.21.2 the teleport packet takes a position, a movement, and a rotation. A hologram never moves, so this
 * adapter sends no teleport.
 *
 * <p>Minecraft ships unobfuscated from 26.1, and Spigot, Paper, and Folia all run it in Mojang's names with CraftBukkit
 * in its unversioned package, so the plugin jar ships this adapter as compiled, and one class runs on all three.
 */
public final class NmsPacketAdapter implements PacketAdapter {

    // Display and TextDisplay keep these data ids private; the tests check each against the server's own. From 1.20.2
    // each is one higher than on 1.20.1, since Display gained the teleport interpolation duration before them.
    static final EntityDataAccessor<Byte> BILLBOARD = new EntityDataAccessor<>(15, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Component> TEXT = new EntityDataAccessor<>(23, EntityDataSerializers.COMPONENT);
    /**
     * Display.BillboardConstraints.CENTER's id, which the enum keeps private.
     */
    static final byte BILLBOARD_CENTER = 3;

    private final AtomicInteger entityCounter;

    /**
     * Finds the server's entity id counter, so it fails here, at selection, on a server this revision does not fit.
     */
    public NmsPacketAdapter() {
        this.entityCounter = entityCounter();
    }

    /**
     * The server's entity id counter, private, which 26.2 moved from Entity to ServerLevel. Every 26.x server runs
     * Mojang's names, so it is found by its name.
     */
    private static AtomicInteger entityCounter() {
        try {
            Field counter = ServerLevel.class.getDeclaredField("ENTITY_COUNTER");
            if (!Modifier.isStatic(counter.getModifiers()) || counter.getType() != AtomicInteger.class) {
                throw new IllegalStateException("ServerLevel.ENTITY_COUNTER is not a static AtomicInteger on this "
                        + "server");
            }
            counter.setAccessible(true);
            return (AtomicInteger) counter.get(null);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("ServerLevel has no ENTITY_COUNTER, the entity id counter on 26.2", e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read ServerLevel's id counter", e);
        }
    }

    @Override
    public int entityCount(int lineCount) {
        if (lineCount < 1) {
            throw new IllegalArgumentException("a hologram has at least one line, not " + lineCount);
        }
        return 1;
    }

    @Override
    public int nextEntityId() {
        return entityCounter.incrementAndGet();
    }

    @Override
    public Object spawn(List<Integer> entityIds, HologramPosition position, List<String> lines) {
        Objects.requireNonNull(position, "position");
        int entityId = entityIdOf(entityIds, lines);
        // No rotation, no data, no velocity, and no head rotation.
        return new Packets(List.of(
                new ClientboundAddEntityPacket(entityId, UUID.randomUUID(), position.x(), position.y(), position.z(),
                        0, 0, EntityTypes.TEXT_DISPLAY, 0, Vec3.ZERO, 0),
                new ClientboundSetEntityDataPacket(entityId, List.of(
                        DataValue.create(TEXT, text(lines)),
                        DataValue.create(BILLBOARD, BILLBOARD_CENTER)))));
    }

    @Override
    public Object meta(List<Integer> entityIds, List<String> lines) {
        int entityId = entityIdOf(entityIds, lines);
        return new Packets(List.of(
                new ClientboundSetEntityDataPacket(entityId, List.of(DataValue.create(TEXT, text(lines))))));
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
     * The lines as one text, top line first, joined by new lines. Each line is converted on its own, so its colour
     * codes reach only its own text; an empty line stays an empty line.
     */
    static Component text(List<String> lines) {
        MutableComponent text = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                text.append("\n");
            }
            Component line = CraftChatMessage.fromStringOrNull(lines.get(index));
            if (line != null) {
                text.append(line);
            }
        }
        return text;
    }

    /**
     * The one entity a text display hologram is.
     */
    private static int entityIdOf(List<Integer> entityIds, List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram has at least one line");
        }
        if (entityIds.size() != 1) {
            throw new IllegalArgumentException(entityIds.size() + " entities for one text display");
        }
        return entityIds.get(0);
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
