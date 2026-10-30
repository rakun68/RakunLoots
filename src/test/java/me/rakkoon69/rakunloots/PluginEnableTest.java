package me.rakkoon69.rakunloots;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.StorageSettings;
import me.rakkoon69.rakunloots.menu.MenuIcon;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.menu.MenuView;
import me.rakkoon69.rakunloots.storage.FlushResult;
import me.rakkoon69.rakunloots.storage.LoadResult;
import me.rakkoon69.rakunloots.storage.LootBoxRecord;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxStoreException;
import me.rakkoon69.rakunloots.storage.StreamableServerMock;
import me.rakkoon69.rakunloots.storage.UnclaimedSummonItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enable on MockBukkit with only the shipped defaults: the new modules run, the commands are in the server's command
 * map, no old type is on the class path, and disable stops everything and flushes the storage once.
 */
class PluginEnableTest {

    /**
     * Every class the old summon path had, which the cutover removed.
     */
    private static final List<String> OLD_TYPES = List.of(
            "commands.RakunLootsCommands", "commands.RakunLootsCommandTabCompleter", "configuration.Data",
            "configuration.LootKey", "configuration.Message", "configuration.Settings", "gui.LootsWikiGUI",
            "hook.PluginHooks", "listeners.LootBreakListener", "listeners.LootOpenListener",
            "listeners.LootSummonListener", "loots.Loot", "loots.LootBox", "loots.options.AnimationOptions",
            "loots.options.BlockOptions", "loots.options.ContentOptions", "loots.options.HologramOptions",
            "loots.options.SummonOptions", "loots.options.block.BlockOptions", "loots.options.content.ContentOptions",
            "loots.options.content.LootItem", "loots.options.hologram.HologramOptions",
            "loots.options.summoning.SummonOptions", "managers.LootsManager", "managers.WorldManager",
            "mechanics.Mechanics", "mechanics.animations.Animation", "mechanics.animations.AnimationUtils",
            "mechanics.animations.AnimationsManager", "mechanics.animations.EnhancedParticle",
            "mechanics.animations.impl.ExplodeAnimation", "mechanics.animations.impl.SphereAnimation",
            "mechanics.blocks.BlockDataHandler", "mechanics.blocks.BlockGlowHandler", "mechanics.items.ConfigItem",
            "mechanics.items.ItemsHandler", "mechanics.items.LootItem", "mechanics.laser.LaserHandler",
            "mechanics.particles.ParticleHandler", "mechanics.summoning.LootBoxCreation",
            "mechanics.summoning.SummonHandler", "mechanics.summoning.SummonLocationFinder",
            "placeholders.PlaceholdersHandler", "schedule.WorldSchedule", "utils.InventoryMaker", "utils.MathUtils",
            "utils.Pair");

    private Handler watcher = new Handler() {
        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private ServerMock server;
    private RakunLootsPlugin plugin;
    private final AtomicInteger flushes = new AtomicInteger();
    private final AtomicInteger closes = new AtomicInteger();
    private final List<String> all = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<LogRecord> severe = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void start() {
        server = StreamableServerMock.mock();
        WatchingPlugin.test = this;
    }

    @AfterEach
    void stop() {
        java.util.logging.Logger.getLogger("").removeHandler(watcher);
        MockBukkit.unmock();
    }

    private void enable() {
        watcher = new Handler() {
            @Override
            public void publish(LogRecord record) {
                all.add(record.getMessage());
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    severe.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger.getLogger("").addHandler(watcher);
        plugin = MockBukkit.loadWith(WatchingPlugin.class, new PluginDescriptionFile("RakunLoots", "1.0.0-SNAPSHOT",
                RakunLootsPlugin.class.getName()));
    }

    @Test
    void enablesOnTheNewModulesWithOnlyTheShippedDefaults() throws IOException {
        enable();

        assertTrue(plugin.isEnabled());
        ConfigSnapshot config = plugin.config();
        assertTrue(config.loot("airdrops").isPresent(), "the shipped loot loaded from the data folder");
        assertTrue(config.animation("RED_SPHERE").isPresent(), "animations.yml loaded from the data folder");
        for (String file : List.of("config.yml", "worlds.yml", "blocks.yml", "animations.yml", "languages/en.yml",
                "languages/vi.yml", "languages/zh.yml", "loots/airdrops.yml")) {
            assertTrue(Files.isRegularFile(plugin.getDataFolder().toPath().resolve(file)), file + " saved by default");
        }
        assertTrue(all.stream().anyMatch(message -> message.contains("enabled")), "the log reaches the test: " + all);
        assertEquals(List.of(), severe.stream().map(LogRecord::getMessage).toList());
        assertNotNull(plugin.itemSources());
        // The admin root and the claim command are in the server's command map under their config names.
        assertNotNull(server.getCommandMap().getCommand("rkl"));
        assertEquals(server.getCommandMap().getCommand("rkl"), server.getCommandMap().getCommand("rkloots"));
        assertNotNull(server.getCommandMap().getCommand("claimsummon"));
        assertEquals(server.getCommandMap().getCommand("claimsummon"), server.getCommandMap().getCommand("rklclaim"));
    }

    /**
     * Debug mode through the enabled plugin: off by default it logs nothing extra, reload picks up debug: true so an
     * admin command's outcome reaches the console at info with the [debug] prefix, and reload picks up false again.
     */
    @Test
    void debugModeLogsAdminCommandOutcomesAfterAReloadPicksItUpAndNothingWhenOff() throws IOException {
        enable();
        java.nio.file.Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        String original = Files.readString(config);
        assertTrue(original.contains("debug: false"), "the shipped file carries the key");
        org.bukkit.entity.Player console = server.addPlayer();
        console.addAttachment(plugin, "rakunloots.command.list", true);
        console.addAttachment(plugin, "rakunloots.command.reload", true);

        server.dispatchCommand(console, "rkl list");
        assertEquals(List.of(), debugLines(), "off by default");

        Files.writeString(config, original.replace("debug: false", "debug: true"));
        server.dispatchCommand(console, "rkl reload");
        assertTrue(plugin.config().debug());
        all.clear();
        server.dispatchCommand(console, "rkl list");
        List<String> on = debugLines();
        assertEquals(1, on.size(), on.toString());
        assertTrue(on.get(0).startsWith("[debug] command /rkl list by " + console.getName() + ": "), on.get(0));

        Files.writeString(config, original);
        server.dispatchCommand(console, "rkl reload");
        assertFalse(plugin.config().debug());
        all.clear();
        server.dispatchCommand(console, "rkl list");
        assertEquals(List.of(), debugLines(), "a reload turned it off again");
    }

    private List<String> debugLines() {
        return all.stream().filter(message -> message.startsWith("[debug]")).toList();
    }

    @Test
    void noOldTypeIsOnTheClassPath() {
        enable();

        for (String old : OLD_TYPES) {
            String name = "me.rakkoon69.rakunloots." + old;
            org.junit.jupiter.api.Assertions.assertThrows(ClassNotFoundException.class,
                    () -> Class.forName(name, false, RakunLootsPlugin.class.getClassLoader()), name);
        }
        assertNull(RakunLootsPlugin.class.getResource("/wiki.yml"));
        assertNull(RakunLootsPlugin.class.getResource("/messages.yml"));
        assertTrue(plugin.isEnabled());
    }

    @Test
    void disableStopsTheCommandsAndFlushesTheStorageOnce() {
        enable();
        assertEquals(0, flushes.get());

        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        assertFalse(plugin.isEnabled());
        assertEquals(1, flushes.get(), "one flush, from the lifecycle's stop");
        assertEquals(1, closes.get());
        assertNull(server.getCommandMap().getCommand("rkl"));
        assertNull(server.getCommandMap().getCommand("claimsummon"));
    }

    /**
     * Acceptance: disable closes a menu a player still has open before the plugin lets go of it.
     */
    @Test
    void disablingClosesEveryOpenMenu() throws Exception {
        enable();
        PlayerMock player = server.addPlayer();
        MenuService menus = (MenuService) field("menus");
        java.util.concurrent.CompletableFuture<Void> opened = menus.open(player.getUniqueId(),
                new MenuView("Menu", 3, Map.of(0, new MenuIcon(new ItemStack(Material.DIAMOND),
                        java.util.Optional.empty(), java.util.Optional.empty()))));
        for (int tick = 0; tick < 50 && !opened.isDone(); tick++) {
            server.getScheduler().performOneTick();
        }
        opened.join();
        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof me.rakkoon69.rakunloots.server.MenuHolder);

        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
        assertTrue(top == null || !(top.getHolder() instanceof me.rakkoon69.rakunloots.server.MenuHolder),
                "the menu was closed before the adapter left");
    }

    private Object field(String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = RakunLootsPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    /**
     * The shipped plugin.yml declares Folia support, keeps DecentHolograms soft, and has no command entries the
     * command map replaces and none of the removed permissions.
     */
    @Test
    @SuppressWarnings("unchecked")
    void pluginYmlDeclaresFoliaAndASoftDecentHologramsAndNoRemovedPermissions() throws IOException {
        try (InputStream in = PluginEnableTest.class.getResourceAsStream("/plugin.yml")) {
            Map<String, Object> yml = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(Boolean.TRUE, yml.get("folia-supported"));
            assertNull(yml.get("depend"));
            assertTrue(((List<String>) yml.get("softdepend")).contains("DecentHolograms"));
            assertNull(yml.get("commands"));
            Map<String, Object> permissions = (Map<String, Object>) yml.get("permissions");
            assertTrue(permissions.containsKey("rakunloots.wiki"));
            Map<String, Object> wiki = (Map<String, Object>) permissions.get("rakunloots.wiki");
            assertEquals(Boolean.TRUE, wiki.get("default"));
            assertFalse(permissions.containsKey("rakunloots.bypass_locked"));
        }
    }

    /**
     * The plugin with its storage watched. The server's own plugin.yml reader needs the SnakeYAML Bukkit bundles, and
     * the plugin shades a newer one, so the description is given and plugin.yml is checked as text below.
     */
    public static final class WatchingPlugin extends RakunLootsPlugin {

        static PluginEnableTest test;

        public WatchingPlugin(org.bukkit.plugin.java.JavaPluginLoader loader, PluginDescriptionFile description,
                              java.io.File dataFolder, java.io.File file) {
            super(loader, description, dataFolder, file);
        }

        @Override
        protected LootBoxStore openStore(me.rakkoon69.rakunloots.config.StorageSettings settings)
                throws LootBoxStoreException {
            return test.new CountingStore(super.openStore(settings));
        }
    }

    /**
     * A store that counts the flushes and closes it is given.
     */
    private final class CountingStore implements LootBoxStore {

        private final LootBoxStore delegate;

        CountingStore(LootBoxStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void save(LootBoxRecord box) throws LootBoxStoreException {
            delegate.save(box);
        }

        @Override
        public void delete(UUID boxId) throws LootBoxStoreException {
            delegate.delete(boxId);
        }

        @Override
        public void markChanged(LootBoxRecord box) {
            delegate.markChanged(box);
        }

        @Override
        public void queueDelete(UUID boxId) {
            delegate.queueDelete(boxId);
        }

        @Override
        public CompletableFuture<FlushResult> flush(Executor executor) {
            flushes.incrementAndGet();
            return delegate.flush(executor);
        }

        @Override
        public LoadResult loadAll(ConfigSnapshot config, Predicate<UUID> worldLoaded) throws LootBoxStoreException {
            return delegate.loadAll(config, worldLoaded);
        }

        @Override
        public void addUnclaimedSummonItems(UUID playerId, String lootId, int amount) throws LootBoxStoreException {
            delegate.addUnclaimedSummonItems(playerId, lootId, amount);
        }

        @Override
        public List<UnclaimedSummonItem> unclaimedSummonItems(UUID playerId, ConfigSnapshot config)
                throws LootBoxStoreException {
            return delegate.unclaimedSummonItems(playerId, config);
        }

        @Override
        public int claimUnclaimedSummonItems(UUID playerId, String lootId, int amount) throws LootBoxStoreException {
            return delegate.claimUnclaimedSummonItems(playerId, lootId, amount);
        }

        @Override
        public void backup(int backupsKept) throws LootBoxStoreException {
            delegate.backup(backupsKept);
        }

        @Override
        public void close() throws LootBoxStoreException {
            closes.incrementAndGet();
            delegate.close();
        }
    }
}
