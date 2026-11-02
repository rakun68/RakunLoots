package me.rakkoon69.rakunloots.command.argument;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import me.rakkoon69.rakunloots.command.ArgumentContext;
import me.rakkoon69.rakunloots.command.ArgumentType;
import me.rakkoon69.rakunloots.command.ParseResult;
import me.rakkoon69.rakunloots.match.CloseMatcher;

/**
 * One of a fixed set of words, such as {@code now} or {@code --force}. Matching ignores case and gives back the word
 * as written here.
 */
public final class ChoiceArgument implements ArgumentType<String> {

    private final List<String> choices;

    public ChoiceArgument(String... choices) {
        if (choices.length == 0) {
            throw new IllegalArgumentException("a choice needs at least one word");
        }
        this.choices = List.of(choices);
    }

    @Override
    public String typeKey() {
        return "cmd_type_choice";
    }

    @Override
    public Map<String, String> typeValues() {
        return Map.of("choices", String.join(", ", choices));
    }

    @Override
    public Optional<String> fixedWord() {
        return choices.size() == 1 ? Optional.of(choices.get(0)) : Optional.empty();
    }

    @Override
    public ParseResult<String> parse(String token, ArgumentContext context) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(token)) {
                return ParseResult.ok(choice);
            }
        }
        return ParseResult.fail("cmd_reason_choice_unknown", Map.of("value", token),
                CloseMatcher.of(choices).closest(token));
    }

    @Override
    public List<String> complete(String partial, ArgumentContext context) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
