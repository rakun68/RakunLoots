package me.rakkoon69.rakunloots.hologram.packet.v26_2;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The adapter against Spigot 26.2's server code, which runs in Mojang's names as Paper's and Folia's do. Each packet
 * it builds is read back through the packet's own fields, and each metadata value is matched with the server's own
 * data accessor, found by its name. A text is read back as the styled pieces the client draws. No server runs; the
 * registries are bootstrapped as a server does at start.
 */
class NmsPacketAdapterTest {

    private static final UUID WORLD = UUID.randomUUID();

    private final NmsPacketAdapter adapter = new NmsPacketAdapter();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The server code these tests read the packets back with is 26.2's, a release this revision covers.
     */
    @Test
    void theServerCodeIs26_2() {
        assertEquals("26.2", SharedConstants.getCurrentVersion().name());
    }

    /**
     * Acceptance: spawn is one text display through the add entity packet, with the hologram's one id, at the
     * position, since a text display draws its text upward from its position, which is where the lowest line sits.
     * Its metadata, as a list of data values, is the lines joined by new lines, top line first, and billboard center.
     */
    @Test
    void spawnIsOneTextDisplayWithTheJoinedLinesAndBillboardCenterAtThePosition()
            throws ReflectiveOperationException {
        List<String> lines = List.of("§6Top", "Middle", "§cBottom");
        HologramPosition position = new HologramPosition(WORLD, 10.5, 66.0, -3.5);

        List<Packet<?>> packets = packets(adapter.spawn(List.of(101), position, lines));

        assertEquals(2, packets.size());
        ClientboundAddEntityPacket spawn = assertInstanceOf(ClientboundAddEntityPacket.class, packets.get(0));
        assertEquals(101, spawn.getId());
        assertSame(EntityTypes.TEXT_DISPLAY, spawn.getType());
        assertEquals(10.5, spawn.getX());
        assertEquals(66.0, spawn.getY());
        assertEquals(-3.5, spawn.getZ());

        ClientboundSetEntityDataPacket data = assertInstanceOf(ClientboundSetEntityDataPacket.class, packets.get(1));
        assertEquals(101, data.id());
        Map<EntityDataAccessor<?>, Object> expected = new LinkedHashMap<>();
        expected.put(textAccessor(), List.of(
                new Piece("Top", "gold", false), new Piece("\n", null, false),
                new Piece("Middle", null, false), new Piece("\n", null, false),
                new Piece("Bottom", "red", false)));
        expected.put(displayAccessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID"), centerId());
        assertEquals(expected, values(data));
    }

    /**
     * Acceptance: meta for new lines sets only the text display's text to the new lines joined. An empty line stays a
     * line of its own.
     */
    @Test
    void metaSetsTheTextToTheNewLinesJoined() throws ReflectiveOperationException {
        List<Packet<?>> packets = packets(adapter.meta(List.of(101), List.of("§aNew top", "")));

        assertEquals(1, packets.size());
        ClientboundSetEntityDataPacket data = assertInstanceOf(ClientboundSetEntityDataPacket.class, packets.get(0));
        assertEquals(101, data.id());
        assertEquals(Map.of(textAccessor(),
                        List.of(new Piece("New top", "green", false), new Piece("\n", null, false))),
                values(data));
    }

    /**
     * Acceptance: destroy removes the text display.
     */
    @Test
    void destroyRemovesTheTextDisplay() {
        List<Packet<?>> packets = packets(adapter.destroy(List.of(101)));

        assertEquals(1, packets.size());
        ClientboundRemoveEntitiesPacket destroy =
                assertInstanceOf(ClientboundRemoveEntitiesPacket.class, packets.get(0));
        assertEquals(IntList.of(101), destroy.getEntityIds());
    }

    /**
     * Acceptance: a coloured line becomes the matching component: a legacy colour, a hex colour, and bold, each part
     * reset from the one before as legacy codes are. A colour never runs on into the next line.
     */
    @Test
    void aColouredLineBecomesTheMatchingComponent() {
        assertEquals(List.of(new Piece("Loot ", "red", false), new Piece("box", "#FFAA00", true)),
                pieces(NmsPacketAdapter.text(List.of("§cLoot §x§f§f§a§a§0§0§lbox"))));
        assertEquals(List.of(new Piece("Loot", "red", true), new Piece("\n", null, false),
                        new Piece("plain", null, false)),
                pieces(NmsPacketAdapter.text(List.of("§c§lLoot", "plain"))));
    }

    /**
     * Acceptance: from 1.20.3 the client gets the text as NBT. The meta packet, written through its stream codec as the
     * server writes it for the client, carries the text with the component serializer's id as an NBT compound, not a
     * JSON string, and that compound reads back through the server's own component codec as the coloured pieces.
     */
    @Test
    void theClientGetsTheTextAsNbt() throws ReflectiveOperationException {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Packet<?> meta = packets(adapter.meta(List.of(101), List.of("§cLoot §lbox", "plain"))).get(0);
        ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,
                assertInstanceOf(ClientboundSetEntityDataPacket.class, meta));

        assertEquals(101, buffer.readVarInt());
        assertEquals(textAccessor().id(), buffer.readUnsignedByte());
        assertEquals(EntityDataSerializers.getSerializedId(EntityDataSerializers.COMPONENT), buffer.readVarInt());
        CompoundTag nbt = assertInstanceOf(CompoundTag.class, buffer.readNbt(NbtAccounter.unlimitedHeap()));
        assertEquals(List.of(new Piece("Loot ", "red", false), new Piece("box", "red", true),
                        new Piece("\n", null, false), new Piece("plain", null, false)),
                pieces(ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow()));
        assertEquals(ClientboundSetEntityDataPacket.EOF_MARKER, buffer.readUnsignedByte());
        assertEquals(0, buffer.readableBytes());
    }

    /**
     * Ids come from the server's own entity id counter, so a fake entity never shares a real one's id. From 26.2 it is
     * ServerLevel's, no longer Entity's. A hologram of any line count is one entity.
     */
    @Test
    void entityIdsComeFromTheServersOwnCounterAndAHologramIsOneEntity() throws ReflectiveOperationException {
        Field field = ServerLevel.class.getDeclaredField("ENTITY_COUNTER");
        field.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) field.get(null);

        int first = adapter.nextEntityId();
        int second = adapter.nextEntityId();

        assertEquals(first + 1, second);
        assertEquals(second, counter.get());
        assertEquals(1, adapter.entityCount(1));
        assertEquals(1, adapter.entityCount(3));
    }

    @Test
    void aWrongIdCountOrNoLinesIsRefused() {
        HologramPosition position = new HologramPosition(WORLD, 0, 64, 0);

        assertThrows(IllegalArgumentException.class, () -> adapter.spawn(List.of(1, 2), position, List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> adapter.meta(List.of(), List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> adapter.spawn(List.of(1), position, List.of()));
        assertThrows(IllegalArgumentException.class, () -> adapter.destroy(List.of()));
        assertThrows(IllegalArgumentException.class, () -> adapter.entityCount(0));
    }

    /**
     * Send refuses a packet it did not build, and does nothing for a player who is not online.
     */
    @Test
    void sendRefusesAForeignPacketAndSkipsAnOfflinePlayer() {
        if (Bukkit.getServer() == null) {
            Bukkit.setServer(noPlayers());
        }
        Object destroy = adapter.destroy(List.of(101));

        assertThrows(IllegalArgumentException.class, () -> adapter.send(UUID.randomUUID(), "destroy"));
        assertDoesNotThrow(() -> adapter.send(UUID.randomUUID(), destroy));
    }

    private static List<Packet<?>> packets(Object built) {
        return assertInstanceOf(NmsPacketAdapter.Packets.class, built).list();
    }

    /**
     * Each value of the packet's data value list by the server's accessor with that id, after checking the value
     * travelled with that accessor's serializer. A text is compared as its styled pieces.
     */
    private static Map<EntityDataAccessor<?>, Object> values(ClientboundSetEntityDataPacket data)
            throws ReflectiveOperationException {
        Map<Integer, EntityDataAccessor<?>> server = new LinkedHashMap<>();
        EntityDataAccessor<?> text = textAccessor();
        EntityDataAccessor<?> billboard = displayAccessor("DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
        server.put(text.id(), text);
        server.put(billboard.id(), billboard);

        Map<EntityDataAccessor<?>, Object> values = new LinkedHashMap<>();
        for (DataValue<?> item : data.packedItems()) {
            EntityDataAccessor<?> accessor = server.get(item.id());
            assertEquals(accessor.serializer(), item.serializer(), "serializer of " + accessor);
            Object value = item.value();
            if (accessor == text) {
                value = pieces((Component) value);
            }
            values.put(accessor, value);
        }
        return values;
    }

    /**
     * A piece of text as the client draws it: its characters, its colour's name, and whether it is bold.
     */
    record Piece(String text, String color, boolean bold) {
    }

    private static List<Piece> pieces(Component component) {
        List<Piece> pieces = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                TextColor color = style.getColor();
                pieces.add(new Piece(text, color == null ? null : color.serialize(), style.isBold()));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return pieces;
    }

    /**
     * The server's own id for billboard center, which its enum keeps package private.
     */
    private static byte centerId() throws ReflectiveOperationException {
        Method getId = Display.BillboardConstraints.class.getDeclaredMethod("getId");
        getId.setAccessible(true);
        return (byte) getId.invoke(Display.BillboardConstraints.CENTER);
    }

    private static EntityDataAccessor<?> textAccessor() throws ReflectiveOperationException {
        return accessor(Display.TextDisplay.class, "DATA_TEXT_ID");
    }

    private static EntityDataAccessor<?> displayAccessor(String name) throws ReflectiveOperationException {
        return accessor(Display.class, name);
    }

    private static EntityDataAccessor<?> accessor(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (EntityDataAccessor<?>) field.get(null);
    }

    /**
     * A Bukkit server with no players online, enough for {@code Bukkit.getPlayer}.
     */
    private static Server noPlayers() {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPlayer", "getPlayerExact" -> null;
                    case "getOnlinePlayers" -> new ArrayList<>();
                    case "getLogger" -> Logger.getLogger("NmsPacketAdapterTest");
                    case "getName", "getVersion", "getBukkitVersion" -> "test";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "no players";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
