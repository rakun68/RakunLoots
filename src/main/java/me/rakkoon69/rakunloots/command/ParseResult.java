package me.rakkoon69.rakunloots.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an argument type made of one token: a value, or a failure with a reason message key, the placeholder values
 * for it, and the close matches to offer.
 */
public final class ParseResult<T> {

    private final T value;
    private final String reasonKey;
    private final Map<String, String> values;
    private final List<String> suggestions;

    private ParseResult(T value, String reasonKey, Map<String, String> values, List<String> suggestions) {
        this.value = value;
        this.reasonKey = reasonKey;
        this.values = values;
        this.suggestions = suggestions;
    }

    public static <T> ParseResult<T> ok(T value) {
        return new ParseResult<>(Objects.requireNonNull(value, "value"), null, Map.of(), List.of());
    }

    public static <T> ParseResult<T> fail(String reasonKey, Map<String, String> values) {
        return fail(reasonKey, values, List.of());
    }

    public static <T> ParseResult<T> fail(String reasonKey, Map<String, String> values, List<String> suggestions) {
        return new ParseResult<>(
                null,
                Objects.requireNonNull(reasonKey, "reasonKey"),
                Map.copyOf(values),
                List.copyOf(suggestions)
        );
    }

    public boolean succeeded() {
        return reasonKey == null;
    }

    public T value() {
        return value;
    }

    public String reasonKey() {
        return reasonKey;
    }

    public Map<String, String> values() {
        return values;
    }

    public List<String> suggestions() {
        return suggestions;
    }
}
