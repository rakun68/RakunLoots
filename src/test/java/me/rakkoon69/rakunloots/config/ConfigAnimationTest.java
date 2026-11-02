package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.config.loot.LootAnimations;
import me.rakkoon69.rakunloots.server.NameCatalog;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.animation.AnimationRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code animations.yml} loads through the config module, and a loot names its animations by id for each moment.
 */
class ConfigAnimationTest {

    private static final NameCatalog ACCEPTING = written -> Optional.of(written);

    /**
     * Knows the shipped block groups' tags, as the server's catalog does, so the shipped files load with no warning.
     */
    private static final NameCatalog SERVER_LIKE = new NameCatalog() {
        @Override
        public Optional<String> currentId(String writtenId) {
            if (writtenId.startsWith("@") || writtenId.startsWith("#")) {
                return Optional.empty();
            }
            return Optional.of(writtenId);
        }

        @Override
        public Set<String> tagMembers(String namespacedTag) {
            return Set.of("minecraft:" + namespacedTag.replace(':', '_'));
        }
    };

    private static final Set<ConfigWarning.Kind> ANIMATION_KINDS = Set.of(ConfigWarning.Kind.UNKNOWN_ANIMATION,
            ConfigWarning.Kind.UNUSABLE_ANIMATION, ConfigWarning.Kind.UNUSABLE_ANIMATIONS,
            ConfigWarning.Kind.YAML_PARSE_REJECTED, ConfigWarning.Kind.UNKNOWN_KEY);

    /**
     * Acceptance: the shipped {@code animations.yml} keeps its format and loads, and the shipped airdrop names the
     * old file's animations for each moment, all of which resolve to an entry whose type the registry has.
     */
    @Test
    void theShippedAnimationsAndTheShippedAirdropResolve() throws IOException {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .globals(resource("/config.yml"))
                .worlds(resource("/worlds.yml"))
                .blocks(resource("/blocks.yml"))
                .animations(resource("/animations.yml"))
                .language("en", resource("/languages/en.yml"))
                .language("vi", resource("/languages/vi.yml"))
                .language("zh", resource("/languages/zh.yml"))
                .loot("airdrops", resource("/loots/airdrops.yml"))
                .build(), SERVER_LIKE, WarningKinds.all());

        assertEquals(List.of(), snapshot.warnings().stream().map(ConfigWarning::kind).toList());
        assertEquals(List.of("RED_SPHERE", "BLUE_SPHERE", "GREEN_SPHERE", "WHITE_SPHERE", "GOLD_BURST", "SPARKLE",
                "CLOUD_RING"), List.copyOf(snapshot.animations().keySet()));
        AnimationDefinition red = snapshot.animation("RED_SPHERE").orElseThrow();
        assertEquals("SPHERE", red.type());
        assertEquals(26, red.variables().get("loops"));
        assertEquals(0.8, red.variables().get("radius"));
        assertEquals("210:60:60", ((Map<?, ?>) ((Map<?, ?>) red.variables().get("particles")).get("outer_line"))
                .get("color"));
        assertEquals("EXPLOSION", snapshot.animation("BLUE_SPHERE").orElseThrow().type());
        assertEquals("EXPLOSION", snapshot.animation("GREEN_SPHERE").orElseThrow().type());
        assertEquals("SPHERE", snapshot.animation("WHITE_SPHERE").orElseThrow().type());

        LootAnimations airdrops = snapshot.lootAnimations("airdrops").orElseThrow();
        assertEquals(List.of("RED_SPHERE"), airdrops.ids(AnimationMoment.SUMMON));
        assertEquals(List.of("BLUE_SPHERE"), airdrops.ids(AnimationMoment.ALIVE));
        assertEquals(List.of("GREEN_SPHERE"), airdrops.ids(AnimationMoment.UNLOCKED));
        assertEquals(List.of("WHITE_SPHERE"), airdrops.ids(AnimationMoment.OPEN));
        assertEquals(List.of("WHITE_SPHERE"), airdrops.ids(AnimationMoment.REMOVE));
        AnimationRegistry registry = AnimationRegistry.create();
        for (AnimationMoment moment : AnimationMoment.values()) {
            for (String id : airdrops.ids(moment)) {
                String type = snapshot.animation(id).orElseThrow().type();
                assertTrue(registry.find(type).isPresent(), moment + " " + id + " " + type);
            }
        }
    }

    @Test
    void severalIdsForOneMomentKeepTheirOrderAndAMomentLeftOutHasNone() {
        ConfigSnapshot snapshot = load(sphere("A") + sphere("B"), loot("""
                animations:
                  open:
                    - B
                    - A
                    - B
                """));

        LootAnimations animations = snapshot.lootAnimations("loot").orElseThrow();
        assertEquals(List.of("B", "A", "B"), animations.ids(AnimationMoment.OPEN));
        assertEquals(List.of(), animations.ids(AnimationMoment.SUMMON));
        assertEquals(List.of(), describe(snapshot));
    }

    @Test
    void aLootWithNoAnimationsBlockNamesNoAnimationAndWarnsNothing() {
        ConfigSnapshot snapshot = load(sphere("A"), loot(""));

        assertEquals(LootAnimations.NONE, snapshot.lootAnimations("loot").orElseThrow());
        assertEquals(List.of(), describe(snapshot));
    }

    @Test
    void anIdAnimationsYmlLacksWarnsHighAtLoadWithTheMomentAndTheId() {
        ConfigSnapshot snapshot = load(sphere("A"), loot("""
                animations:
                  summon:
                    - A
                    - MISSING
                  remove:
                    - GONE
                """));

        assertEquals(List.of("UNKNOWN_ANIMATION HIGH loot animations.summon MISSING",
                "UNKNOWN_ANIMATION HIGH loot animations.remove GONE"), describe(snapshot));
        assertEquals(List.of("A", "MISSING"),
                snapshot.lootAnimations("loot").orElseThrow().ids(AnimationMoment.SUMMON), "the id stays, so the "
                        + "service warns again when it would play");
    }

    @Test
    void withNoAnimationsFileGivenNoIdIsChecked() {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder()
                .loot("loot", loot("animations:\n  summon:\n    - ANY\n")).build(), ACCEPTING, WarningKinds.all());

        assertEquals(List.of("ANY"), snapshot.lootAnimations("loot").orElseThrow().ids(AnimationMoment.SUMMON));
        assertEquals(List.of(), describe(snapshot));
        assertEquals(Map.of(), snapshot.animations());
    }

    @Test
    void shapesThatCannotBeUsedWarnHighAndAreLeftOut() {
        ConfigSnapshot snapshot = load(sphere("A"), loot("""
                animations:
                  summon: A
                  alive:
                    - A
                    - 5
                  tick:
                    - A
                  open:
                    - A
                """));

        assertEquals(Set.of("UNUSABLE_ANIMATIONS HIGH loot animations.summon null",
                "UNUSABLE_ANIMATIONS HIGH loot animations.alive null",
                "UNKNOWN_KEY HIGH loot animations.tick null"), Set.copyOf(describe(snapshot)));
        assertEquals(3, describe(snapshot).size());
        LootAnimations animations = snapshot.lootAnimations("loot").orElseThrow();
        assertEquals(List.of(), animations.ids(AnimationMoment.SUMMON));
        assertEquals(List.of(), animations.ids(AnimationMoment.ALIVE));
        assertEquals(List.of("A"), animations.ids(AnimationMoment.OPEN), "the other moments stay");

        ConfigSnapshot notASection = load(sphere("A"), loot("animations: [A]\n"));
        assertEquals(List.of("UNUSABLE_ANIMATIONS HIGH loot animations null"), describe(notASection));
    }

    @Test
    void anEntryThatIsNotATypedSectionWarnsHighAndIsLeftOut() {
        ConfigSnapshot snapshot = load("""
                GOOD:
                  type: sphere
                NO_TYPE:
                  variables: {}
                NOT_A_SECTION: 5
                BAD_VARIABLES:
                  type: SPHERE
                  variables: [1, 2]
                """, loot(""));

        assertEquals(List.of("GOOD"), List.copyOf(snapshot.animations().keySet()));
        assertEquals("SPHERE", snapshot.animation("GOOD").orElseThrow().type(), "the type is upper case");
        assertEquals(Map.of(), snapshot.animation("GOOD").orElseThrow().variables());
        assertEquals(Set.of("UNUSABLE_ANIMATION HIGH null NO_TYPE", "UNUSABLE_ANIMATION HIGH null NOT_A_SECTION",
                "UNUSABLE_ANIMATION HIGH null BAD_VARIABLES"), Set.copyOf(describe(snapshot)));
    }

    @Test
    void animationsYmlThatIsNotYamlWarnsHighAndHoldsNothing() {
        ConfigSnapshot snapshot = load("A: [unclosed\n", loot("animations:\n  open:\n    - A\n"));

        assertEquals(Map.of(), snapshot.animations());
        assertTrue(describe(snapshot).contains("YAML_PARSE_REJECTED HIGH null animations"), describe(snapshot)
                .toString());
    }

    private static ConfigSnapshot load(String animations, String loot) {
        return ConfigLoader.load(ConfigSources.builder().animations(animations).loot("loot", loot).build(),
                ACCEPTING, WarningKinds.all());
    }

    private static String sphere(String id) {
        return id + ":\n  type: SPHERE\n  variables:\n    loops: 1\n";
    }

    private static String loot(String extra) {
        return "language: en\nopen_mode: drop\nblock: barrel\nroll:\n  entries:\n    one:\n      item:\n"
                + "        id: diamond\n" + extra;
    }

    /**
     * Each animation warning, as kind, severity, loot, key, and written id. The tiny fixtures here raise other
     * warnings, such as for missing languages and block tags, that are not under test.
     */
    private static List<String> describe(ConfigSnapshot snapshot) {
        return snapshot.warnings().stream()
                .filter(warning -> ANIMATION_KINDS.contains(warning.kind()))
                .map(warning -> warning.kind() + " " + warning.severity() + " " + warning.lootId().orElse("null")
                        + " " + warning.key().orElse("null") + warning.writtenId().map(id -> " " + id)
                        .orElse(warning.lootId().isPresent() ? " null" : ""))
                .toList();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ConfigAnimationTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
