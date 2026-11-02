package me.rakkoon69.rakunloots.command.handler;

import me.rakkoon69.rakunloots.command.CommandArgument;
import me.rakkoon69.rakunloots.command.CommandButton;
import me.rakkoon69.rakunloots.command.CommandInvocation;
import me.rakkoon69.rakunloots.command.CommandNode;
import me.rakkoon69.rakunloots.command.argument.ChoiceArgument;
import me.rakkoon69.rakunloots.command.argument.IntegerArgument;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.WarningRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code warnings [severity] [page]} node: every warning kind raised since the last enable or reload, severe first,
 * then high, then low, one line per kind with its count and its first subjects. The severity names the lowest severity
 * to show and defaults to {@code low}. A kind below the warning level is listed and marked. A page holds
 * {@value #PAGE_SIZE} lines. A player gets clickable previous and next buttons; the console gets the next page's
 * command as text.
 */
final class WarningsCommand {

    /**
     * Lines on one page.
     */
    public static final int PAGE_SIZE = 10;

    private static final String LOW = "low";
    private static final String HIGH = "high";
    private static final String SEVERE = "severe";

    private WarningsCommand() {
    }

    static CommandNode node(WarningRecord record) {
        return CommandNode.builder("warnings")
                .argument(CommandArgument.optional("severity", new ChoiceArgument(LOW, HIGH, SEVERE)))
                .argument(CommandArgument.optional("page", new IntegerArgument(1, Integer.MAX_VALUE)))
                .handler(invocation -> show(invocation, record))
                .build();
    }

    private static void show(CommandInvocation invocation, WarningRecord record) {
        Severity minimum = severity(invocation.find("severity", String.class).orElse(LOW));
        List<WarningRecord.Entry> entries = new ArrayList<>(record.entries().stream()
                .filter(entry -> entry.severity().compareTo(minimum) >= 0)
                .toList());
        if (entries.isEmpty()) {
            invocation.reply("cmd_warnings_none", Map.of("severity", minimum.name().toLowerCase(Locale.ROOT)));
            return;
        }
        // The sort is stable, so kinds of one severity stay in the order they were first raised.
        entries.sort(Comparator.comparing(WarningRecord.Entry::severity).reversed());
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.min(invocation.find("page", Integer.class).orElse(1), pages);
        String severityWord = minimum.name().toLowerCase(Locale.ROOT);

        invocation.reply("cmd_warnings_header", Map.of("total", Integer.toString(entries.size()),
                "severity", severityWord));
        for (WarningRecord.Entry entry : entries.subList((page - 1) * PAGE_SIZE,
                Math.min(entries.size(), page * PAGE_SIZE))) {
            invocation.reply("cmd_warnings_line", line(invocation, entry));
        }

        Map<String, String> footer = Map.of("page", Integer.toString(page), "pages", Integer.toString(pages));
        String base = "/" + invocation.label() + " warnings " + severityWord + " ";
        if (invocation.sender().playerId().isPresent()) {
            List<CommandButton> buttons = new ArrayList<>();
            if (page > 1) {
                buttons.add(new CommandButton(invocation.message("cmd_warnings_previous", Map.of()),
                        base + (page - 1)));
            }
            if (page < pages) {
                buttons.add(new CommandButton(invocation.message("cmd_warnings_next", Map.of()), base + (page + 1)));
            }
            invocation.reply("cmd_warnings_footer", footer, buttons);
            return;
        }
        invocation.reply("cmd_warnings_footer", footer);
        if (page < pages) {
            invocation.reply("cmd_warnings_console_next", Map.of("command", base + (page + 1)));
        }
    }

    private static Map<String, String> line(CommandInvocation invocation, WarningRecord.Entry entry) {
        StringBuilder tail = new StringBuilder();
        if (!entry.subjects().isEmpty()) {
            tail.append(": ").append(String.join(", ", entry.subjects()));
            int more = entry.count() - entry.subjects().size();
            if (more > 0) {
                tail.append(" (+").append(more).append(')');
            }
        }
        String mark = entry.passedLevel() ? "" : invocation.message("cmd_warnings_below_level", Map.of());
        return Map.of(
                "severity", color(entry.severity()) + entry.severity().name(),
                "kind", entry.kind(),
                "count", Integer.toString(entry.count()),
                "tail", tail.toString(),
                "mark", mark);
    }

    private static String color(Severity severity) {
        return switch (severity) {
            case LOW -> "&e";
            case HIGH -> "&c";
            case SEVERE -> "&4";
        };
    }

    private static Severity severity(String word) {
        return switch (word) {
            case HIGH -> Severity.HIGH;
            case SEVERE -> Severity.SEVERE;
            default -> Severity.LOW;
        };
    }
}
