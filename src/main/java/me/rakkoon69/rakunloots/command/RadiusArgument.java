package me.rakkoon69.rakunloots.command;

import java.util.List;
import java.util.Map;

/**
 * A distance in blocks, from 1 up to a maximum. A decimal such as 12.5 is allowed. It completes nothing.
 */
public final class RadiusArgument implements ArgumentType<Double> {

    private static final int MIN_BLOCKS = 1;

    private final int maxBlocks;

    public RadiusArgument(int maxBlocks) {
        if (maxBlocks < MIN_BLOCKS) {
            throw new IllegalArgumentException("max " + maxBlocks + " is below the minimum radius");
        }
        this.maxBlocks = maxBlocks;
    }

    @Override
    public String typeKey() {
        return "cmd_type_radius";
    }

    @Override
    public Map<String, String> typeValues() {
        return Map.of("min", Integer.toString(MIN_BLOCKS), "max", Integer.toString(maxBlocks));
    }

    @Override
    public ParseResult<Double> parse(String token, ArgumentContext context) {
        double value;
        try {
            value = Double.parseDouble(token);
        } catch (NumberFormatException notANumber) {
            return ParseResult.fail("cmd_reason_radius_invalid", Map.of("value", token));
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < MIN_BLOCKS || value > maxBlocks) {
            return ParseResult.fail("cmd_reason_radius_range", Map.of("value", token));
        }
        return ParseResult.ok(value);
    }

    @Override
    public List<String> complete(String partial, ArgumentContext context) {
        return List.of();
    }
}
