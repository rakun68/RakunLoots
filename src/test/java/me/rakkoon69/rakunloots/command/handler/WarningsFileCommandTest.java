package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandRegistry;
import me.rakkoon69.rakunloots.command.FakeCommandSender;
import me.rakkoon69.rakunloots.command.FakeCommandSources;
import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.server.NameCatalog;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import me.rakkoon69.rakunloots.warning.WarningLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code warnings.yml} from a config load to the log and to {@code /rkl warnings}, wired the way the plugin wires it:
 * the log reads the settings and the level from the snapshot, and is fed the load's raw warnings.
 * {@code warning-level: loud} raises UNKNOWN_WARNING_LEVEL (high) and {@code debug: maybe} raises UNUSABLE_DEBUG (high).
 */
class WarningsFileCommandTest {

    private static final String BASE = "rakunloots.command";

    /**
     * The kinds these tests raise. A load with no blocks file and no worlds file raises more, which they ignore.
     */
    private static final List<String> OURS = List.of("UNKNOWN_WARNING_LEVEL", "UNKNOWN_WARNING_KIND", "UNUSABLE_DEBUG");

    private final List<String> logged = new ArrayList<>();
    private final CommandRegistry registry = new CommandRegistry(new FakeCommandSources(),
            key -> ShippedHologramConfig.load().message("", key));
    private final FakeCommandSender console = new FakeCommandSender("CONSOLE", BASE + ".warnings");

    /**
     * Loads the config, feeds its warnings to a log, and returns the command's lines.
     */
    private List<String> shown(String globals, String warningsFile) {
        ConfigSources.Builder sources = ConfigSources.builder().globals(globals)
                .language("en", "still_locked: locked\n");
        if (warningsFile != null) {
            sources.warnings(warningsFile);
        }
        ConfigSnapshot snapshot = ConfigLoader.load(sources.build(), NameCatalog.empty(), WarningKinds.all());
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (OURS.stream().anyMatch(record.getMessage()::contains)) {
                    logged.add(record.getLevel() + " " + record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        WarningLog log = new WarningLog(logger, snapshot::warningLevel, () -> false, snapshot::warningSettings);
        log.config(snapshot.rawWarnings());
        registry.register(CommandNode.builder("rkl").permissionBase(BASE)
                .child(WarningsCommand.node(log.record())).build());
        registry.dispatch(console, "rkl warnings");
        return console.messages().stream().map(line -> line.replaceAll("&[0-9a-fk-or]", ""))
                .filter(line -> line.startsWith("- ")).filter(line -> OURS.stream().anyMatch(line::contains)).toList();
    }

    /**
     * Acceptance: a changed severity reaches the log's level and the command's line.
     */
    @Test
    void aChangedSeverityIsSeenInTheLogAndTheCommand() {
        List<String> raised = shown("language: en\nwarning-level: loud\n", "UNKNOWN_WARNING_LEVEL: severe\n");

        assertEquals(List.of("- SEVERE UNKNOWN_WARNING_LEVEL x1"), raised);
        assertEquals(List.of("SEVERE UNKNOWN_WARNING_LEVEL"), logged);
    }

    /**
     * A kind the file does not change keeps the default, in both.
     */
    @Test
    void aKindTheFileDoesNotChangeKeepsItsDefaultInTheLogAndTheCommand() {
        List<String> plain = shown("language: en\nwarning-level: loud\n", null);

        assertEquals(List.of("- HIGH UNKNOWN_WARNING_LEVEL x1"), plain);
        assertEquals(List.of("WARNING UNKNOWN_WARNING_LEVEL"), logged);
    }

    /**
     * Acceptance: an off kind is hidden from the log and from the command, and the kinds around it are not.
     */
    @Test
    void anOffKindIsHiddenFromTheLogAndTheCommand() {
        List<String> shown = shown("language: en\nwarning-level: loud\ndebug: maybe\n",
                "UNKNOWN_WARNING_LEVEL: off\n");

        assertEquals(List.of("- HIGH UNUSABLE_DEBUG x1: debug"), shown);
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).contains("UNUSABLE_DEBUG") && !logged.get(0).contains("UNKNOWN_WARNING_LEVEL"),
                logged.toString());
    }

    /**
     * Acceptance: an unknown kind warns low, in the log and in the command, and a kind the file leaves out keeps its
     * default.
     */
    @Test
    void anUnknownKindWarnsLowAndAMissingKindKeepsItsDefault() {
        List<String> shown = shown("language: en\nwarning-level: loud\n", "NOT_A_KIND: high\n");

        assertEquals(List.of("- HIGH UNKNOWN_WARNING_LEVEL x1", "- LOW UNKNOWN_WARNING_KIND x1: NOT_A_KIND"), shown);
        assertEquals(2, logged.size());
    }

    /**
     * The command lists a warning the warning level keeps out of the log, marked, since the log judges the load's raw
     * warnings. A kind lowered to low at level high is that case.
     */
    @Test
    void aKindLoweredBelowTheLevelIsListedButNotLogged() {
        List<String> shown = shown("language: en\nwarning-level: high\ndebug: maybe\n", "UNUSABLE_DEBUG: low\n");

        assertEquals(1, shown.size());
        assertTrue(shown.get(0).startsWith("- LOW UNUSABLE_DEBUG x1"), shown.toString());
        assertTrue(shown.get(0).contains("below the warning level"), shown.toString());
        assertEquals(List.of(), logged);
    }
}
