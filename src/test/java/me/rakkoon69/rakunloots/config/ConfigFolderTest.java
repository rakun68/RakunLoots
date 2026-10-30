package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data folder as the plugin saves it from the jar loads through the real loader, animations.yml included.
 */
class ConfigFolderTest {

    @TempDir
    Path folder;

    @Test
    void theShippedDefaultsSavedToAFolderLoadWithTheirAnimations() throws IOException {
        for (String resource : ConfigFolder.DEFAULT_RESOURCES) {
            Path target = folder.resolve(resource);
            Files.createDirectories(target.getParent());
            try (InputStream in = ConfigFolderTest.class.getResourceAsStream("/" + resource)) {
                Files.write(target, in.readAllBytes());
            }
        }

        ConfigSources sources = ConfigFolder.read(folder);
        ConfigSnapshot snapshot = ConfigLoader.load(sources, new NameCatalog() {
            @Override
            public Optional<String> currentId(String writtenId) {
                return Optional.of(writtenId);
            }

            @Override
            public boolean soundExists(String key) {
                return "minecraft:ui.button.click".equals(key);
            }
        }, WarningKinds.all());

        assertTrue(sources.animations().isPresent());
        assertEquals(Set.of("claim", "wiki"), sources.menus().keySet());
        assertEquals(Set.of("en", "vi", "zh"), sources.languages().keySet());
        assertEquals(java.util.Set.of("airdrops", "buried_stash", "deep_vault", "head_trophy", "sky_lantern",
                "sunken_treasure", "treasure_chest"), sources.loots().keySet());
        assertTrue(snapshot.animation("RED_SPHERE").isPresent());
        assertEquals(6, snapshot.claimMenu().rows());
        assertEquals(49, snapshot.claimMenu().buttons().get("claim_all").slot());
        assertEquals(6, snapshot.wikiListMenu().rows());
        assertEquals(49, snapshot.wikiLootMenu().buttons().get("info").slot());
        for (String lootId : sources.loots().keySet()) {
            assertTrue(snapshot.loot(lootId).isPresent(), lootId);
        }
    }

    @Test
    void aMissingFileIsLeftOut() {
        ConfigSources sources = ConfigFolder.read(folder);

        assertEquals(Optional.empty(), sources.globals());
        assertEquals(Optional.empty(), sources.animations());
        assertTrue(sources.loots().isEmpty());
        assertTrue(sources.menus().isEmpty());
    }
}
