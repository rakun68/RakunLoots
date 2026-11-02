package me.rakkoon69.rakunloots;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module layers of ADR 0019, checked on the plugin's own classes. A module is a top-level package with its
 * subpackages, and no module uses one on a higher layer. The plugin class, in the root package, sits on top.
 */
class ModuleLayersTest {

    private static final String BASE = "me.rakkoon69.rakunloots";

    /**
     * Each module's layer, lowest first, as ADR 0019 orders them.
     */
    private static final Map<String, Integer> LAYERS = Map.ofEntries(
            Map.entry("warning", 1), Map.entry("text", 1), Map.entry("match", 1),
            Map.entry("server", 2),
            Map.entry("items", 3),
            Map.entry("config", 4),
            Map.entry("roll", 5),
            Map.entry("storage", 6),
            Map.entry("lootbox", 7),
            Map.entry("hologram", 8), Map.entry("animation", 8), Map.entry("visuals", 8), Map.entry("menu", 8),
            Map.entry("summon", 9),
            Map.entry("wiki", 10),
            Map.entry("command", 11)
    );

    private static final int PLUGIN_LAYER = 12;

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void thePluginsOwnClassesAreChecked() {
        assertTrue(classes.contain(RakunLootsPlugin.class));
        assertFalse(classes.contain(ModuleLayersTest.class), "test classes are not the plugin's");
    }

    @Test
    void everyModuleHasALayer() {
        TreeSet<String> unlayered = new TreeSet<>();
        for (JavaClass type : classes) {
            module(type).filter(module -> !LAYERS.containsKey(module)).ifPresent(unlayered::add);
        }
        assertEquals(new TreeSet<>(), unlayered, "modules missing from the layer order");
    }

    @Test
    void aModuleUsesOnlyModulesOnLowerLayers() {
        TreeSet<String> upward = new TreeSet<>();
        for (JavaClass origin : classes) {
            int from = layer(origin);
            Optional<String> fromModule = module(origin);
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!target.getPackageName().startsWith(BASE) || module(target).equals(fromModule)) {
                    continue;
                }
                if (layer(target) >= from) {
                    upward.add(name(origin) + " -> " + name(target));
                }
            }
        }
        assertEquals(new TreeSet<>(), upward, "imports against the layer order");
    }

    @Test
    void noCycleBetweenModules() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    /**
     * A {@code bukkit} subpackage is reached only from its own module and the plugin class.
     */
    @Test
    void aBukkitSubpackageIsUsedOnlyByItsModuleAndThePluginClass() {
        TreeSet<String> outside = new TreeSet<>();
        for (JavaClass origin : classes) {
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                Optional<String> targetModule = module(target);
                if (targetModule.isEmpty() || !isBukkitSubpackage(target)) {
                    continue;
                }
                if (module(origin).isPresent() && !module(origin).equals(targetModule)) {
                    outside.add(name(origin) + " -> " + name(target));
                }
            }
        }
        assertEquals(new TreeSet<>(), outside, "bukkit subpackages used outside their module");
    }

    /**
     * Checks the rules against classes known to break them, so a rule that passes everything fails here.
     */
    @Test
    void theRulesCatchABrokenLayout() {
        assertEquals(Optional.of("server"), module(classes.get(BASE + ".server.ServerAdapter")));
        assertEquals(Optional.empty(), module(classes.get(RakunLootsPlugin.class)));
        assertTrue(layer(classes.get(BASE + ".lootbox.LootBoxLifecycle"))
                < layer(classes.get(BASE + ".summon.SummonService")));
        assertTrue(isBukkitSubpackage(classes.get(BASE + ".server.bukkit.ServerAdapters")));
        assertFalse(isBukkitSubpackage(classes.get(BASE + ".server.ServerAdapter")));
    }

    /**
     * The top-level package under the base, or empty for a class in the base package itself or outside the plugin.
     */
    private static Optional<String> module(JavaClass type) {
        String packageName = type.getPackageName();
        if (!packageName.startsWith(BASE + ".")) {
            return Optional.empty();
        }
        String rest = packageName.substring(BASE.length() + 1);
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }

    private static int layer(JavaClass type) {
        return module(type).map(module -> LAYERS.getOrDefault(module, 0)).orElse(PLUGIN_LAYER);
    }

    private static boolean isBukkitSubpackage(JavaClass type) {
        List<String> parts = List.of(type.getPackageName().split("\\."));
        return parts.contains("bukkit") && parts.indexOf("bukkit") > 3;
    }

    private static String name(JavaClass type) {
        return type.getName().substring(BASE.length() + 1);
    }
}
