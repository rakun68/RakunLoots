package me.rakkoon69.rakunloots.server.bukkit;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockUnsafeValues;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog over the Bukkit material list on the test class path (Minecraft 1.18): a current name stays as written,
 * so the shipped files load quietly, another spelling comes back as the namespaced id, and an unknown name is empty.
 */
class BukkitNameCatalogTest {

    private final BukkitNameCatalog catalog = new BukkitNameCatalog();

    /**
     * A server whose unsafe values leave the structure registry empty, so the 1.18 {@link Registry} class can
     * initialise and its sound registry can answer without a real server.
     */
    private static final class RegistryServerMock extends ServerMock {
        private final MockUnsafeValues unsafe = new MockUnsafeValues() {
            @Override
            public <T extends Keyed> Registry<T> registryFor(Class<T> type) {
                return null;
            }
        };

        @Override
        public MockUnsafeValues getUnsafe() {
            return unsafe;
        }
    }

    @Test
    void aCurrentNameStaysAsWritten() {
        assertEquals(Optional.of("barrel"), catalog.currentId("barrel"));
        assertEquals(Optional.of("minecraft:barrel"), catalog.currentId("minecraft:barrel"));
        assertEquals(Optional.of("diamond"), catalog.currentId("diamond"));
    }

    @Test
    void anotherSpellingComesBackAsTheNamespacedId() {
        assertEquals(Optional.of("minecraft:barrel"), catalog.currentId("BARREL"));
    }

    @Test
    void anUnknownNameIsEmpty() {
        assertEquals(Optional.empty(), catalog.currentId("not_a_block"));
        assertEquals(Optional.empty(), catalog.currentId("oraxen:thing"));
    }

    @Test
    void aTagTheServerCannotGiveIsEmpty() {
        assertEquals(java.util.Set.of(), catalog.tagMembers("minecraft:no_such_tag"));
    }

    @Test
    void aSoundKeyTheServerKnowsIsPresentAndAnUnknownOneIsNot() {
        MockBukkit.mock(new RegistryServerMock());
        try {
            assertTrue(catalog.soundExists("minecraft:ui.button.click"));
            assertFalse(catalog.soundExists("minecraft:no_such_sound"));
            assertFalse(catalog.soundExists("not a sound"));
        } finally {
            MockBukkit.unmock();
        }
    }
}
