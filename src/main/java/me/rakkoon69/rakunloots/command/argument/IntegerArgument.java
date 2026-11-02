package me.rakkoon69.rakunloots.command.argument;

import me.rakkoon69.rakunloots.command.ArgumentContext;
import me.rakkoon69.rakunloots.command.ArgumentType;
import me.rakkoon69.rakunloots.command.ParseResult;

import java.util.List;
import java.util.Map;

/**
 * A whole number between two bounds, both allowed. It completes nothing.
 */
public final class IntegerArgument implements ArgumentType<Integer> {

    private final int min;
    private final int max;

    public IntegerArgument(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " is above max " + max);
        }
        this.min = min;
        this.max = max;
    }

    @Override
    public String typeKey() {
        return "cmd_type_integer";
    }

    @Override
    public Map<String, String> typeValues() {
        return Map.of("min", Integer.toString(min), "max", Integer.toString(max));
    }

    @Override
    public ParseResult<Integer> parse(String token, ArgumentContext context) {
        long value;
        try {
            value = Long.parseLong(token);
        } catch (NumberFormatException notANumber) {
            return ParseResult.fail("cmd_reason_integer_invalid", Map.of("value", token));
        }
        if (value < min) {
            return ParseResult.fail("cmd_reason_integer_low", Map.of("value", token));
        }
        if (value > max) {
            return ParseResult.fail("cmd_reason_integer_high", Map.of("value", token));
        }
        return ParseResult.ok((int) value);
    }

    @Override
    public List<String> complete(String partial, ArgumentContext context) {
        return List.of();
    }
}
