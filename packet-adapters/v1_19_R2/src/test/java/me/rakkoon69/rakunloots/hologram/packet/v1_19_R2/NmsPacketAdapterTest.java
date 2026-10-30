package me.rakkoon69.rakunloots.hologram.packet.v1_19_R2;

import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.IntList;
import me.rakkoon69.rakunloots.hologram.HologramPosition;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
 * The adapter against Spigot 1.19.3's Mojang-mapped server code. Each packet it builds is read back through the
 * packet's own fields, and each metadata value is matched with the server's own data accessor, found by its Mojang
 * name. No server runs; the registries are bootstrapped as a server does at start.
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
     * The server code these tests read the packets back with is 1.19.3's, the release this revision covers.
     */
    @Test
    void theServerCodeIs1_19_3() {
        assertEquals("1.19.3", SharedConstants.getCurrentVersion().getName());
    }

    /**
     * Acceptance: spawn is one armor stand per line through the add entity packet, lowest line's id first, at the
     * position's x and z, each with its metadata as a list of data values: invisible, no gravity, small, marker,
     * custom name visible, and its line as the custom name. The lowest stand's name ends at the position's height: the
     * client draws a name 0.5 above a marker, whose height is 0, and 9 pixels of 0.025 blocks tall, so the stand sits
     * 0.275 below. Each line above is 0.25 higher.
     */
    @Test
    void spawnIsOneInvisibleSmallMarkerArmorStandPerLineStackedUpward() throws ReflectiveOperationException {
        List<String> lines = List.of("§6Top", "Middle", "§cBottom");
        HologramPosition position = new HologramPosition(WORLD, 10.5, 66.0, -3.5);

        List<Packet<?>> packets = packets(adapter.spawn(List.of(101, 102, 103), position, lines));

        assertEquals(6, packets.size());
        for (int index = 0; index < 3; index++) {
            ClientboundAddEntityPacket spawn = assertInstanceOf(ClientboundAddEntityPacket.class, packets.get(2 * index));
            assertEquals(101 + index, spawn.getId());
            assertSame(EntityType.ARMOR_STAND, spawn.getType());
            assertEquals(10.5, spawn.getX());
            assertEquals(66.0 - 0.275 + 0.25 * index, spawn.getY(), 1e-9);
            assertEquals(-3.5, spawn.getZ());

            ClientboundSetEntityDataPacket data =
                    assertInstanceOf(ClientboundSetEntityDataPacket.class, packets.get(2 * index + 1));
            assertEquals(101 + index, data.id());
            Map<EntityDataAccessor<?>, Object> expected = new LinkedHashMap<>();
            expected.put(entityAccessor("DATA_SHARED_FLAGS_ID"), (byte) (1 << staticInt("FLAG_INVISIBLE")));
            expected.put(entityAccessor("DATA_NO_GRAVITY"), true);
            expected.put(ArmorStand.DATA_CLIENT_FLAGS,
                    (byte) (ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_MARKER));
            expected.put(entityAccessor("DATA_CUSTOM_NAME_VISIBLE"), true);
            expected.put(entityAccessor("DATA_CUSTOM_NAME"), json(lines.get(2 - index)));
            assertEquals(expected, values(data));
        }
    }

    /**
     * Acceptance: meta for new lines sets only each stand's custom name, lowest line's id first. An empty line has no
     * custom name, so its stand shows nothing.
     */
    @Test
    void metaSetsEachStandsCustomNameToItsNewLine() throws ReflectiveOperationException {
        List<Packet<?>> packets = packets(adapter.meta(List.of(101, 102), List.of("§aNew top", "")));

        assertEquals(2, packets.size());
        ClientboundSetEntityDataPacket lowest = assertInstanceOf(ClientboundSetEntityDataPacket.class, packets.get(0));
        ClientboundSetEntityDataPacket top = assertInstanceOf(ClientboundSetEntityDataPacket.class, packets.get(1));
        assertEquals(101, lowest.id());
        assertEquals(Map.of(entityAccessor("DATA_CUSTOM_NAME"), Optional.empty()), values(lowest));
        assertEquals(102, top.id());
        assertEquals(Map.of(entityAccessor("DATA_CUSTOM_NAME"), json("§aNew top")), values(top));
    }

    /**
     * Acceptance: destroy removes every entity of the hologram.
     */
    @Test
    void destroyRemovesEveryStand() {
        List<Packet<?>> packets = packets(adapter.destroy(List.of(101, 102, 103)));

        assertEquals(1, packets.size());
        ClientboundRemoveEntitiesPacket destroy =
                assertInstanceOf(ClientboundRemoveEntitiesPacket.class, packets.get(0));
        assertEquals(IntList.of(101, 102, 103), destroy.getEntityIds());
    }

    /**
     * Acceptance: a coloured line becomes the matching JSON component: a legacy colour, a hex colour, and bold, each
     * part reset from the one before as legacy codes are.
     */
    @Test
    void aColouredLineBecomesTheMatchingJsonComponent() {
        Component name = NmsPacketAdapter.name("§cLoot §x§f§f§a§a§0§0§lbox").orElseThrow();

        assertEquals(JsonParser.parseString("""
                        {"text": "", "extra": [
                          {"text": "Loot ", "color": "red", "bold": false, "italic": false, "underlined": false,
                           "strikethrough": false, "obfuscated": false},
                          {"text": "box", "color": "#FFAA00", "bold": true, "italic": false, "underlined": false,
                           "strikethrough": false, "obfuscated": false}
                        ]}"""),
                JsonParser.parseString(Component.Serializer.toJson(name)));
    }

    /**
     * Ids come from the server's own entity id counter, so a fake entity never shares a real one's id.
     */
    @Test
    void entityIdsComeFromTheServersOwnCounter() throws ReflectiveOperationException {
        Field field = Entity.class.getDeclaredField("ENTITY_COUNTER");
        field.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) field.get(null);

        int first = adapter.nextEntityId();
        int second = adapter.nextEntityId();

        assertEquals(first + 1, second);
        assertEquals(second, counter.get());
        assertEquals(3, adapter.entityCount(3));
    }

    @Test
    void aWrongIdCountOrNoLinesIsRefused() {
        HologramPosition position = new HologramPosition(WORLD, 0, 64, 0);

        assertThrows(IllegalArgumentException.class, () -> adapter.spawn(List.of(1), position, List.of("a", "b")));
        assertThrows(IllegalArgumentException.class, () -> adapter.meta(List.of(1, 2), List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> adapter.spawn(List.of(), position, List.of()));
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
     * travelled with that accessor's serializer. A custom name is compared as its JSON.
     */
    private static Map<EntityDataAccessor<?>, Object> values(ClientboundSetEntityDataPacket data)
            throws ReflectiveOperationException {
        Map<Integer, EntityDataAccessor<?>> server = new LinkedHashMap<>();
        for (String name : List.of("DATA_SHARED_FLAGS_ID", "DATA_NO_GRAVITY", "DATA_CUSTOM_NAME_VISIBLE",
                "DATA_CUSTOM_NAME")) {
            EntityDataAccessor<?> accessor = entityAccessor(name);
            server.put(accessor.getId(), accessor);
        }
        server.put(ArmorStand.DATA_CLIENT_FLAGS.getId(), ArmorStand.DATA_CLIENT_FLAGS);

        Map<EntityDataAccessor<?>, Object> values = new LinkedHashMap<>();
        for (DataValue<?> item : data.packedItems()) {
            EntityDataAccessor<?> accessor = server.get(item.id());
            assertEquals(accessor.getSerializer(), item.serializer(), "serializer of " + accessor);
            Object value = item.value();
            if (accessor == server.get(entityAccessor("DATA_CUSTOM_NAME").getId())) {
                value = ((Optional<?>) value).map(component -> Component.Serializer.toJson((Component) component));
            }
            values.put(accessor, value);
        }
        return values;
    }

    private static Optional<String> json(String line) {
        return NmsPacketAdapter.name(line).map(Component.Serializer::toJson);
    }

    private static EntityDataAccessor<?> entityAccessor(String name) throws ReflectiveOperationException {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (EntityDataAccessor<?>) field.get(null);
    }

    private static int staticInt(String name) throws ReflectiveOperationException {
        Field field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
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
