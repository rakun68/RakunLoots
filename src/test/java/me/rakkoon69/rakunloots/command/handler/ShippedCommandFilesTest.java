package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.CommandRegistry;
import me.rakkoon69.rakunloots.command.HelpCommand;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.hologram.ShippedHologramConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped {@code config.yml}, {@code plugin.yml}, and language files against the command system: the command names,
 * the permissions under {@code rakunloots.admin}, and every message key the system uses, in every language.
 */
class ShippedCommandFilesTest {

    /**
     * Every key {@link CommandRegistry} and {@link HelpCommand} send, argument types included, and the help keys of the
     * shipped {@code help} node.
     */
    private static final Set<String> KEYS = Set.of(
            "cmd_unknown", "cmd_use_help", "cmd_failure_typed", "cmd_failure_reason", "cmd_failure_suggestions",
            "cmd_reason_subcommand_unknown", "cmd_reason_subcommand_missing", "cmd_reason_missing",
            "cmd_reason_too_many", "cmd_reason_loot_unknown", "cmd_reason_world_unknown",
            "cmd_reason_player_unknown", "cmd_reason_player_offline", "cmd_reason_integer_invalid",
            "cmd_reason_integer_low", "cmd_reason_integer_high", "cmd_reason_radius_invalid",
            "cmd_reason_radius_range", "cmd_reason_choice_unknown", "cmd_reason_help_unknown",
            "cmd_type_subcommand", "cmd_type_nothing", "cmd_type_loot", "cmd_type_world", "cmd_type_player",
            "cmd_type_online_player", "cmd_type_integer", "cmd_type_radius", "cmd_type_choice", "cmd_type_command",
            "cmd_help_header", "cmd_help_entry", "cmd_help_usage", "cmd_help_description", "cmd_help_argument",
            "cmd_help_example", "cmd_help_permission", "cmd_help_permission_everyone",
            "cmd_help_divider", "cmd_help_others", "cmd_help_subcommands", "cmd_help_footer",
            "command_claim_summary", "command_wiki_summary",
            "cmd_remove_done", "cmd_remove_near_needs_player", "cmd_remove_world_not_loaded",
            "cmd_reason_world_or_player_unknown", "cmd_type_world_or_player",
            "cmd_reload_done", "cmd_reload_failed", "cmd_list", "cmd_list_none",
            "cmd_give_done", "cmd_give_needs_player", "cmd_give_player_unknown",
            "cmd_summon_done", "cmd_summon_abandoned", "cmd_summon_failed", "cmd_summon_needs_target",
            "cmd_summon_world_not_loaded", "cmd_summon_player_not_online",
            "cmd_timer_done", "cmd_timer_abandoned", "cmd_timer_failed", "cmd_timer_world_unknown",
            "cmd_timer_world_not_loaded", "cmd_timer_below_minimum_players", "cmd_timer_at_spawn_limit",
            "cmd_timer_no_loot", "cmd_claim_needs_player", "cmd_wiki_needs_player",
            "cmd_warnings_none", "cmd_warnings_header", "cmd_warnings_line", "cmd_warnings_below_level",
            "cmd_warnings_footer", "cmd_warnings_previous", "cmd_warnings_next", "cmd_warnings_console_next",
            "command_help_summary", "command_help_description", "command_help_example", "command_help_arg_command",
            "command_warnings_summary", "command_warnings_description", "command_warnings_example",
            "command_warnings_arg_severity", "command_warnings_arg_page"
    );

    @Test
    void everyCommandMessageHasATextInEveryShippedLanguage() {
        ConfigSnapshot snapshot = ShippedHologramConfig.load();

        for (String language : List.of("en", "vi", "zh")) {
            Set<String> have = snapshot.languageKeys(language);
            for (String key : KEYS) {
                assertTrue(have.contains(key), language + " has no " + key);
            }
        }
        assertEquals(snapshot.languageKeys("en"), snapshot.languageKeys("vi"));
        assertEquals(snapshot.languageKeys("en"), snapshot.languageKeys("zh"));
    }

    /**
     * A message that embeds another full message, which has its own prefix, puts it on its own line in every language.
     */
    @Test
    void anAbandonedMessageBreaksTheLineBeforeTheReasonInEveryLanguage() {
        for (String language : List.of("en", "vi", "zh")) {
            ConfigSnapshot snapshot = ShippedHologramConfig.load(language, Map.of());
            for (String key : List.of("cmd_summon_abandoned", "cmd_timer_abandoned")) {
                String raw = snapshot.message("", key);
                assertTrue(raw.endsWith("\n{reason}"), language + " " + key + ": " + raw);
            }
        }
    }

    @Test
    void theListOfKeysAboveIsEveryKeyTheCommandSourcesName() throws IOException {
        Set<String> named = new java.util.TreeSet<>();
        try (var sources = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/me/rakkoon69/rakunloots/command"))
                .filter(java.nio.file.Files::isRegularFile)) {
            for (java.nio.file.Path source : (Iterable<java.nio.file.Path>) sources::iterator) {
                var matcher = java.util.regex.Pattern.compile("\"(cmd_[a-z_]+)\"")
                        .matcher(java.nio.file.Files.readString(source));
                while (matcher.find()) {
                    named.add(matcher.group(1));
                }
            }
        }

        assertEquals(new java.util.TreeSet<>(KEYS.stream().filter(key -> key.startsWith("cmd_")).toList()), named);
    }

    @Test
    void theShippedHelpNodeHelpKeysAreTheOnesTheNodeAsks() {
        CommandNode root = CommandNode.builder("rkl").permissionBase("rakunloots.command")
                .child(HelpCommand.node()).build();
        CommandNode help = root.children().get(0);

        assertEquals("command_help_summary", help.helpKey("summary"));
        assertEquals("command_help_description", help.helpKey("description"));
        assertEquals("command_help_example", help.helpKey("example"));
        assertEquals("command_help_arg_command", help.argumentHelpKey(help.arguments().get(0)));
    }

    @Test
    void theShippedPluginYmlDeclaresEachCommandPermissionAsAChildOfAdmin() throws IOException {
        Map<String, Object> plugin = new Yaml().load(resource("/plugin.yml"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> permissions = (Map<String, Map<String, Object>>) plugin.get("permissions");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> children = (Map<String, Boolean>) permissions.get("rakunloots.admin").get("children");

        CommandNode root = CommandNode.builder("rkl").permissionBase("rakunloots.command")
                .child(HelpCommand.node())
                .child(WarningsCommand.node(new me.rakkoon69.rakunloots.warning.WarningRecord())).build();
        for (CommandNode node : root.children()) {
            String permission = node.permission().orElseThrow();
            assertTrue(permissions.containsKey(permission), permission + " is not declared");
            assertEquals(Boolean.TRUE, children.get(permission), permission + " is not a child of rakunloots.admin");
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ShippedCommandFilesTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
