package me.rakkoon69.rakunloots.command;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.warning.WarningLevel;
import me.rakkoon69.rakunloots.warning.WarningLog;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /rkl warnings [low|high|severe] [page]} through the command registry as a fake sender, over the real warning
 * log and the shipped messages. Each test feeds warnings through the log, as the config load and the modules do.
 */
class WarningsCommandTest {

    private static final String BASE = "rakunloots.command";
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private WarningLevel level = WarningLevel.LOW;
    private final Logger logger = quietLogger();
    private final WarningLog log = new WarningLog(logger, () -> level);
    private final ConfigSnapshot snapshot = ShippedHologramConfig.load();
    private final CommandRegistry registry = new CommandRegistry(new FakeCommandSources(),
            key -> snapshot.message("", key));
    private final FakeCommandSender console = new FakeCommandSender("CONSOLE", BASE + ".warnings");
    private final FakeCommandSender steve = new FakeCommandSender("Steve", BASE + ".warnings").asPlayer(STEVE);

    WarningsCommandTest() {
        registry.register(CommandNode.builder("rkl").permissionBase(BASE)
                .child(WarningsCommand.node(log.record())).build());
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static String plain(String line) {
        return line.replaceAll("&[0-9a-fk-or]", "");
    }

    private List<String> run(FakeCommandSender sender, String line) {
        int before = sender.messages().size();
        registry.dispatch(sender, line);
        return sender.messages().subList(before, sender.messages().size()).stream()
                .map(WarningsCommandTest::plain).toList();
    }

    private void feedKinds(int count) {
        List<ConfigWarning> warnings = new ArrayList<>();
        ConfigWarning.Kind[] kinds = ConfigWarning.Kind.values();
        for (int i = 0; i < count; i++) {
            warnings.add(ConfigWarning.lootLow(kinds[i], "loot" + i));
        }
        log.config(warnings);
    }

    /**
     * Acceptance: the list is sorted severe, then high, then low, with the lines carrying severity, count, and kind.
     */
    @Test
    void theListIsSortedSevereThenHighThenLow() {
        log.config(List.of(ConfigWarning.oldName("grass", "short_grass"), ConfigWarning.unknownName("stoen"),
                ConfigWarning.missingEnglishKey("cmd_x")));

        List<String> shown = run(console, "rkl warnings");

        assertEquals(List.of(
                "[RakunLoots] Warnings since the last enable or reload at low or above: 3 kind(s).",
                "- SEVERE MISSING_ENGLISH_KEY x1: cmd_x",
                "- HIGH UNKNOWN_NAME x1: stoen",
                "- LOW OLD_NAME x1: grass",
                "Page 1 of 1"), withoutPrefix(shown));
    }

    /**
     * Acceptance: the severity filter shows that severity and above, default low.
     */
    @Test
    void theSeverityFilterShowsThatSeverityAndAbove() {
        log.config(List.of(ConfigWarning.oldName("grass", "short_grass"), ConfigWarning.unknownName("stoen"),
                ConfigWarning.missingEnglishKey("cmd_x")));

        List<String> high = run(console, "rkl warnings high");
        List<String> severe = run(console, "rkl warnings severe");
        List<String> low = run(console, "rkl warnings low");

        assertEquals(2, high.stream().filter(line -> line.startsWith("- ")).count());
        assertTrue(high.stream().noneMatch(line -> line.contains("OLD_NAME")), high.toString());
        assertEquals(List.of("- SEVERE MISSING_ENGLISH_KEY x1: cmd_x"),
                severe.stream().filter(line -> line.startsWith("- ")).toList());
        assertEquals(3, low.stream().filter(line -> line.startsWith("- ")).count());
        assertEquals(low, run(console, "rkl warnings"), "the default is low");
    }

    /**
     * Acceptance: one line per kind with the count and the first 3 subjects and how many more.
     */
    @Test
    void oneLineGroupsAKindWithItsCountAndFirstThreeSubjects() {
        log.config(List.of(ConfigWarning.unknownName("stoen"), ConfigWarning.unknownName("oak_plank"),
                ConfigWarning.unknownName("dirtt"), ConfigWarning.unknownName("sand1"),
                ConfigWarning.unknownName("sand2")));

        List<String> shown = run(console, "rkl warnings");

        assertTrue(shown.contains("- HIGH UNKNOWN_NAME x5: stoen, oak_plank, dirtt (+2)"), shown.toString());
        assertEquals(1, shown.stream().filter(line -> line.startsWith("- ")).count());
    }

    /**
     * Acceptance: a warning below the warning level is listed and marked, and one at the level is not marked.
     */
    @Test
    void aWarningBelowTheWarningLevelIsListedAndMarked() {
        level = WarningLevel.HIGH;
        log.config(List.of(ConfigWarning.oldName("grass", "short_grass"), ConfigWarning.unknownName("stoen")));

        List<String> shown = run(console, "rkl warnings");

        assertTrue(shown.contains("- HIGH UNKNOWN_NAME x1: stoen"), shown.toString());
        assertTrue(shown.contains("- LOW OLD_NAME x1: grass [below the warning level, not in the log]"),
                shown.toString());
    }

    /**
     * Acceptance: 10 lines a page, and the console gets the next page's command as text.
     */
    @Test
    void aPageHoldsTenLinesAndTheConsoleGetsTheNextPageCommand() {
        feedKinds(12);

        List<String> first = run(console, "rkl warnings");
        List<String> second = run(console, "rkl warnings low 2");

        assertEquals(10, first.stream().filter(line -> line.startsWith("- ")).count());
        assertEquals("Page 1 of 2", first.get(first.size() - 2));
        assertEquals("Next page: /rkl warnings low 2", first.get(first.size() - 1));
        assertEquals(2, second.stream().filter(line -> line.startsWith("- ")).count());
        assertEquals("Page 2 of 2", second.get(second.size() - 1), "the last page names no next page");
        assertEquals(List.of(), console.buttons(), "the console gets no buttons");
    }

    /**
     * Acceptance: a player gets clickable previous and next buttons, and no command text.
     */
    @Test
    void aPlayerGetsClickablePreviousAndNextButtons() {
        feedKinds(25);

        run(steve, "rkl warnings low 2");
        List<CommandButton> middle = steve.buttons();
        run(steve, "rkl warnings low");
        run(steve, "rkl warnings low 3");

        assertEquals(List.of(
                new CommandButton("&a[< Previous]", "/rkl warnings low 1"),
                new CommandButton("&a[Next >]", "/rkl warnings low 3")), middle);
        assertEquals(List.of(
                new CommandButton("&a[Next >]", "/rkl warnings low 2"),
                new CommandButton("&a[< Previous]", "/rkl warnings low 2")), steve.buttons().subList(2, 4));
        assertTrue(steve.messages().stream().noneMatch(line -> line.contains("Next page:")), steve.messages().toString());
    }

    /**
     * Acceptance: a page past the end shows the last page, and an empty record says so.
     */
    @Test
    void aPagePastTheEndShowsTheLastPageAndAnEmptyRecordSaysSo() {
        assertEquals(List.of("[RakunLoots] No warnings at low or above since the last enable or reload."),
                run(console, "rkl warnings"));

        feedKinds(11);

        assertEquals(1, run(console, "rkl warnings low 9").stream().filter(line -> line.startsWith("- ")).count());
    }

    /**
     * Acceptance: the permission is {@code rakunloots.command.warnings}, under {@code rakunloots.admin} in the shipped
     * plugin.yml, and a sender without it is told the command is unknown.
     */
    @Test
    void thePermissionGuardsTheCommandAndPluginYmlGrantsItToAdmins() throws IOException {
        log.config(List.of(ConfigWarning.unknownName("stoen")));
        FakeCommandSender nobody = new FakeCommandSender("Alex");

        List<String> shown = run(nobody, "rkl warnings");

        assertTrue(shown.stream().noneMatch(line -> line.contains("UNKNOWN_NAME")), shown.toString());
        assertEquals(List.of("[RakunLoots] Unknown command."), shown);
        assertEquals(Optional.of("rakunloots.command.warnings"),
                registry.root("rkl").orElseThrow().children().get(0).permission());
        try (InputStream in = WarningsCommandTest.class.getResourceAsStream("/plugin.yml")) {
            Map<String, Object> plugin = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> permissions = (Map<String, Map<String, Object>>) plugin.get("permissions");
            @SuppressWarnings("unchecked")
            Map<String, Boolean> children = (Map<String, Boolean>) permissions.get("rakunloots.admin").get("children");
            assertEquals(Boolean.TRUE, children.get("rakunloots.command.warnings"));
            assertTrue(permissions.containsKey("rakunloots.command.warnings"));
        }
    }

    /**
     * Acceptance: the help keys and every message exist in English, Vietnamese, and Simplified Chinese.
     */
    @Test
    void everyWarningsTextExistsInEveryShippedLanguage() {
        for (String language : List.of("en", "vi", "zh")) {
            var keys = ShippedHologramConfig.load(language, Map.of()).languageKeys(language);
            for (String key : List.of("cmd_warnings_none", "cmd_warnings_header", "cmd_warnings_line",
                    "cmd_warnings_below_level", "cmd_warnings_footer", "cmd_warnings_previous", "cmd_warnings_next",
                    "cmd_warnings_console_next", "command_warnings_summary", "command_warnings_description",
                    "command_warnings_example", "command_warnings_arg_severity", "command_warnings_arg_page")) {
                assertTrue(keys.contains(key), language + " has no " + key);
            }
        }
    }

    private static List<String> withoutPrefix(List<String> lines) {
        return lines.stream().map(line -> line.replace("{prefix}", "[RakunLoots]")).toList();
    }
}
