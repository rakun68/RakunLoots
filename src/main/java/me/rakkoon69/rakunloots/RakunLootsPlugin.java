package me.rakkoon69.rakunloots;

import me.rakkoon69.rakunloots.animation.AnimationRegistry;
import me.rakkoon69.rakunloots.animation.AnimationService;
import me.rakkoon69.rakunloots.animation.ParticleSink;
import me.rakkoon69.rakunloots.claim.ClaimJoinListener;
import me.rakkoon69.rakunloots.claim.ClaimMenu;
import me.rakkoon69.rakunloots.claim.UnclaimedSummonItems;
import me.rakkoon69.rakunloots.command.AdminCommands;
import me.rakkoon69.rakunloots.command.BukkitCommandRegistrar;
import me.rakkoon69.rakunloots.command.BukkitCommandSources;
import me.rakkoon69.rakunloots.command.ClaimCommand;
import me.rakkoon69.rakunloots.command.CommandRegistry;
import me.rakkoon69.rakunloots.command.WikiCommand;
import me.rakkoon69.rakunloots.config.ConfigFolder;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.DefaultAccess;
import me.rakkoon69.rakunloots.config.StorageSettings;
import me.rakkoon69.rakunloots.hologram.BukkitHologramServer;
import me.rakkoon69.rakunloots.hologram.HologramProvider;
import me.rakkoon69.rakunloots.hologram.HologramProviderChoice;
import me.rakkoon69.rakunloots.hologram.HologramProviders;
import me.rakkoon69.rakunloots.hologram.HologramService;
import me.rakkoon69.rakunloots.hologram.OwnerNames;
import me.rakkoon69.rakunloots.hologram.decent.DecentHologramsProvider;
import me.rakkoon69.rakunloots.hologram.fancy.FancyHologramsProviderLoader;
import me.rakkoon69.rakunloots.hologram.holographic.HolographicDisplaysProvider;
import me.rakkoon69.rakunloots.hologram.packet.HologramViewerListener;
import me.rakkoon69.rakunloots.hologram.packet.PacketAdapters;
import me.rakkoon69.rakunloots.hologram.packet.PacketHologramProvider;
import me.rakkoon69.rakunloots.items.ItemSourceRegistry;
import me.rakkoon69.rakunloots.warning.WarningLevel;
import me.rakkoon69.rakunloots.warning.WarningLog;
import me.rakkoon69.rakunloots.warning.WarningSettings;
import me.rakkoon69.rakunloots.menu.MenuListener;
import me.rakkoon69.rakunloots.menu.MenuRenderer;
import me.rakkoon69.rakunloots.menu.MenuService;
import me.rakkoon69.rakunloots.lootbox.LootBoxLifecycle;
import me.rakkoon69.rakunloots.lootbox.SummonService;
import me.rakkoon69.rakunloots.lootbox.WorldBroadcasts;
import me.rakkoon69.rakunloots.lootbox.WorldTimers;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxGuardListener;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxInteractionListener;
import me.rakkoon69.rakunloots.lootbox.listener.LootBoxPhysicsGuardListener;
import me.rakkoon69.rakunloots.lootbox.listener.SummonChunkListener;
import me.rakkoon69.rakunloots.server.BukkitNameCatalog;
import me.rakkoon69.rakunloots.server.ServerAdapter;
import me.rakkoon69.rakunloots.server.ServerAdapters;
import me.rakkoon69.rakunloots.storage.LootBoxStore;
import me.rakkoon69.rakunloots.storage.LootBoxStoreException;
import me.rakkoon69.rakunloots.storage.LootBoxStores;
import me.rakkoon69.rakunloots.summon.SummonSearch;
import me.rakkoon69.rakunloots.summonitem.SummonItemGiveBack;
import me.rakkoon69.rakunloots.summonitem.SummonItemListener;
import me.rakkoon69.rakunloots.summonitem.SummonItemMaker;
import me.rakkoon69.rakunloots.summonitem.SummonItemUse;
import me.rakkoon69.rakunloots.visuals.BoxVisualsListener;
import me.rakkoon69.rakunloots.visuals.BoxVisualsService;
import me.rakkoon69.rakunloots.visuals.GlowLibraries;
import me.rakkoon69.rakunloots.visuals.GuardianBeamLaser;
import me.rakkoon69.rakunloots.wiki.LootChanceCache;
import me.rakkoon69.rakunloots.wiki.WikiMenu;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;

import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Builds the plugin's modules in order on enable and stops them in reverse on disable: the server adapter, the config,
 * storage, the lifecycle, the summon service and its search, the world timers, the hologram, animation, and box
 * visuals services, the listeners, and the commands. Every warning and failure goes to one {@link WarningLog}.
 */
public class RakunLootsPlugin extends JavaPlugin {

    private static final String PLUGIN_VERSION = "1.0.0-SNAPSHOT";

    private volatile ConfigSnapshot config;
    private WarningLog log;
    private ItemSourceRegistry itemSources;
    private ServerAdapter adapter;
    private LootBoxStore store;
    private LootBoxLifecycle lifecycle;
    private SummonSearch search;
    private SummonService summons;
    private WorldTimers timers;
    private HologramService holograms;
    private HologramViewerListener viewerListener;
    private AnimationService animations;
    private BoxVisualsService visuals;
    private MenuService menus;
    private LootChanceCache chanceCache;
    private BukkitCommandRegistrar registrar;

    public RakunLootsPlugin() {
    }

    /**
     * The constructor a test server loads a plugin through.
     */
    protected RakunLootsPlugin(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder,
                               File file) {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onEnable() {
        try {
            enable();
        } catch (RuntimeException | LinkageError | LootBoxStoreException failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "RakunLoots could not start", failure);
            disable();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("RakunLoots " + PLUGIN_VERSION + " enabled.");
    }

    @Override
    public void onDisable() {
        disable();
        getLogger().info("RakunLoots " + PLUGIN_VERSION + " disabled.");
    }

    /**
     * The plugin's item source registry. Another plugin may register after enable.
     */
    public ItemSourceRegistry itemSources() {
        return itemSources;
    }

    /**
     * The config as last loaded.
     */
    public ConfigSnapshot config() {
        return config;
    }

    private void enable() throws LootBoxStoreException {
        saveDefaults();
        log = new WarningLog(getLogger(), () -> config == null ? WarningLevel.LOW : config.warningLevel(),
                () -> config != null && config.debug(),
                () -> config == null ? WarningSettings.none() : config.warningSettings());
        itemSources = ItemSourceRegistry.create();
        adapter = ServerAdapters.select(this);
        if (adapter.experimental()) {
            getLogger().warning("Support for this server (Folia) is experimental. Loot boxes may fail to summon, and "
                    + "some visuals are off. Do not run it on a live server yet.");
        }
        chanceCache = new LootChanceCache(adapter.async());
        ConfigSnapshot snapshot = loadConfig();
        MenuRenderer menuRenderer = new MenuRenderer(itemSources, key -> config.message("", key), log);
        menus = new MenuService(adapter, menuRenderer);

        store = openStore(snapshot.storage());
        try {
            store.backup(snapshot.storage().backupsKept());
        } catch (LootBoxStoreException failure) {
            log.failure("The storage backup failed", failure);
        }

        Random random = new Random();
        SummonItemMaker maker = new SummonItemMaker(itemSources);
        SummonItemGiveBack giveBack = new SummonItemGiveBack(adapter, this::config, store, maker, log::failure);
        lifecycle = new LootBoxLifecycle(adapter, snapshot, itemSources, store, random, log);
        search = new SummonSearch(adapter, snapshot, System::currentTimeMillis, random);
        search.start();
        summons = new SummonService(adapter, snapshot, lifecycle, search, giveBack, log);
        WorldBroadcasts broadcasts = new WorldBroadcasts(adapter, snapshot, log, random);
        lifecycle.addObserver(broadcasts);
        timers = new WorldTimers(adapter, snapshot, lifecycle, summons, broadcasts, log, random);

        BukkitHologramServer hologramServer = new BukkitHologramServer(getServer());
        HologramProvider provider = pickHologramProvider(snapshot, hologramServer);
        holograms = new HologramService(snapshot, provider, OwnerNames.of(getServer()),
                reloaded -> pickHologramProvider(reloaded, hologramServer));
        animations = new AnimationService(snapshot, AnimationRegistry.create(), ParticleSink.of(adapter),
                adapter.global(), log);
        visuals = new BoxVisualsService(snapshot, adapter, () -> GlowLibraries.create(this, adapter),
                () -> new GuardianBeamLaser(this), log);
        lifecycle.addObserver(holograms);
        lifecycle.addObserver(animations);
        lifecycle.addObserver(visuals);

        lifecycle.start().whenComplete((done, failure) -> {
            if (failure != null) {
                log.failure("The loot box lifecycle could not start", failure);
            }
        });
        timers.start();

        UnclaimedSummonItems claims = new UnclaimedSummonItems(adapter, this::config, store, maker, log::failure);
        ClaimMenu claimMenu = new ClaimMenu(adapter, this::config, claims, maker, menus, menuRenderer, log::failure);
        WikiMenu wikiMenu = new WikiMenu(adapter, this::config, itemSources, menus, menuRenderer, chanceCache,
                log::failure);
        SummonItemUse use = new SummonItemUse(maker, this::config, summons, giveBack, log::failure);
        registerEvents(use, claims);
        registerCommands(giveBack, claims, claimMenu, wikiMenu);
    }

    /**
     * Opens the storage the config names. A test overrides it to watch the storage.
     */
    protected LootBoxStore openStore(StorageSettings settings) throws LootBoxStoreException {
        return LootBoxStores.open(settings, getDataFolder().toPath());
    }

    private Map<HologramProviderChoice, HologramProvider> hologramProviders(BukkitHologramServer hologramServer) {
        Map<HologramProviderChoice, HologramProvider> providers = new EnumMap<>(HologramProviderChoice.class);
        offer(providers, HologramProviderChoice.PACKETS,
                () -> new PacketHologramProvider(PacketAdapters.select(hologramServer), adapter));
        offer(providers, HologramProviderChoice.DECENTHOLOGRAMS, () -> DecentHologramsProvider.of(getServer()));
        offer(providers, HologramProviderChoice.FANCYHOLOGRAMS, () -> FancyHologramsProviderLoader.load(getServer()));
        offer(providers, HologramProviderChoice.HOLOGRAPHICDISPLAYS,
                () -> HolographicDisplaysProvider.of(this, adapter));
        return providers;
    }

    /**
     * A provider that cannot even be built is left out, which the choice reads as one that cannot run.
     */
    private void offer(Map<HologramProviderChoice, HologramProvider> providers, HologramProviderChoice name,
                       java.util.function.Supplier<HologramProvider> build) {
        try {
            providers.put(name, build.get());
        } catch (RuntimeException | LinkageError failure) {
            getLogger().fine(name + " hologram provider could not be built: " + failure);
        }
    }

    /**
     * Picks the provider for {@code hologram_provider}, with the same fallback and warnings at enable and at a reload
     * that changed it. The packet provider's viewer listener follows the pick: the old one is unregistered, and a
     * new one is registered when packets are picked.
     */
    private HologramProvider pickHologramProvider(ConfigSnapshot snapshot, BukkitHologramServer hologramServer) {
        if (viewerListener != null) {
            HandlerList.unregisterAll(viewerListener);
            viewerListener = null;
        }
        HologramProvider provider = HologramProviders.choose(snapshot.hologramProvider(),
                hologramProviders(hologramServer), log);
        if (provider instanceof PacketHologramProvider packets) {
            packets.tracker().ifPresent(tracker -> {
                viewerListener = new HologramViewerListener(tracker);
                register(viewerListener);
            });
        }
        return provider;
    }

    private void registerEvents(SummonItemUse use, UnclaimedSummonItems claims) {
        register(new LootBoxGuardListener(lifecycle));
        register(new LootBoxPhysicsGuardListener(lifecycle));
        // Before the summon item listener, so a click on a box does not also summon.
        register(new LootBoxInteractionListener(lifecycle));
        register(new SummonItemListener(use));
        register(new SummonChunkListener(summons));
        register(new BoxVisualsListener(visuals));
        register(new ClaimJoinListener(claims));
        register(new MenuListener(menus));
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommands(SummonItemGiveBack giveBack, UnclaimedSummonItems claims, ClaimMenu claimMenu,
                                  WikiMenu wikiMenu) {
        BukkitCommandSources sources = new BukkitCommandSources(getServer(), this::config);
        CommandRegistry registry = new CommandRegistry(sources, key -> config.message("", key), log::debug);
        registrar = new BukkitCommandRegistrar(this, registry);
        ConfigSnapshot snapshot = config;
        registrar.register(AdminCommands.root(snapshot.adminCommand(), new AdminCommands.Parts(
                this::reloadConfigFiles, adapter, sources, lifecycle, summons, timers, holograms, animations, visuals,
                search::reload, menus::reload, giveBack::give, log.record())));
        registrar.register(ClaimCommand.node(snapshot.claimCommand(), claims, claimMenu));
        registrar.register(WikiCommand.node(snapshot.wikiCommand(), wikiMenu));
    }

    /**
     * Reads the data folder and keeps the result as the current config, then logs its warnings at the new level and
     * gives the claim permission the default the config names.
     */
    private ConfigSnapshot loadConfig() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigFolder.read(getDataFolder().toPath()),
                new BukkitNameCatalog(), WarningKinds.all());
        config = snapshot;
        if (chanceCache != null) {
            chanceCache.load(snapshot);
        }
        // Enable and reload both come here, and the warnings command lists what happened since the last one.
        log.record().clear();
        log.config(snapshot.rawWarnings());
        applyPermissionDefault(ClaimCommand.PERMISSION, "take the summon items you are owed with the claim command",
                snapshot.claimCommand().permissionDefault());
        applyPermissionDefault(WikiCommand.PERMISSION, "open the loots wiki",
                snapshot.wikiCommand().permissionDefault());
        return snapshot;
    }

    /**
     * Sets the default of a command permission, which plugin.yml registers, and recalculates the permissibles so
     * players already online see it. A server that did not register the permission gets it added.
     */
    private void applyPermissionDefault(String node, String description, DefaultAccess access) {
        PluginManager plugins = getServer().getPluginManager();
        PermissionDefault value = switch (access) {
            case EVERYONE -> PermissionDefault.TRUE;
            case NOBODY -> PermissionDefault.FALSE;
            case OPERATORS -> PermissionDefault.OP;
            case NON_OPERATORS -> PermissionDefault.NOT_OP;
        };
        Permission permission = plugins.getPermission(node);
        if (permission == null) {
            plugins.addPermission(new Permission(node, description, value));
            return;
        }
        permission.setDefault(value);
        plugins.recalculatePermissionDefaults(permission);
    }

    /**
     * The reload's loader: the config as the files say now.
     */
    private ConfigSnapshot reloadConfigFiles() {
        return loadConfig();
    }

    private void saveDefaults() {
        for (String resource : ConfigFolder.DEFAULT_RESOURCES) {
            if (!Files.exists(getDataFolder().toPath().resolve(resource))) {
                saveResource(resource, false);
            }
        }
    }

    /**
     * Stops what enable built, in reverse. The lifecycle's stop flushes the storage once; the store closes after it.
     */
    private void disable() {
        if (registrar != null) {
            registrar.unregisterAll();
            registrar = null;
        }
        HandlerList.unregisterAll(this);
        stop(menus == null ? null : () -> menus.closeAll());
        stop(visuals == null ? null : visuals::stop);
        stop(animations == null ? null : animations::stop);
        stop(holograms == null ? null : holograms::stop);
        stop(timers == null ? null : timers::stop);
        stop(summons == null ? null : summons::stop);
        stop(search == null ? null : search::stop);
        stop(lifecycle == null ? null : lifecycle::stop);
        if (store != null) {
            try {
                store.close();
            } catch (LootBoxStoreException failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "The storage could not close", failure);
            }
        }
        visuals = null;
        menus = null;
        chanceCache = null;
        animations = null;
        holograms = null;
        viewerListener = null;
        timers = null;
        summons = null;
        search = null;
        lifecycle = null;
        store = null;
        adapter = null;
        itemSources = null;
    }

    private void stop(Runnable stop) {
        if (stop == null) {
            return;
        }
        try {
            stop.run();
        } catch (RuntimeException failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "A module could not stop", failure);
        }
    }
}
