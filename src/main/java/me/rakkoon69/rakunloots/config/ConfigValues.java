package me.rakkoon69.rakunloots.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads YAML and the scalar and list values every config file shares.
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * The line, counted from 1, where a YAML error was found, when that line holds {@code {}: an unquoted value such as
     * {@code name: {name} Chest} reads as a map and then fails on the text after it. Empty when the error has no
     * position or its line holds no brace.
     */
    public static OptionalInt quoteLine(String content, YAMLException error) {
        if (!(error instanceof MarkedYAMLException marked)) {
            return OptionalInt.empty();
        }
        String[] lines = content.split("\r?\n", -1);
        for (Mark mark : new Mark[]{marked.getProblemMark(), marked.getContextMark()}) {
            if (mark != null && mark.getLine() < lines.length && lines[mark.getLine()].indexOf('{') >= 0) {
                return OptionalInt.of(mark.getLine() + 1);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * YAML 1.2 booleans only ({@code true}/{@code false}). {@code yes}/{@code 1} stay scalars.
     */
    public static Yaml yaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        Representer representer = new Representer(dumperOptions);
        Constructor constructor = new Constructor(loaderOptions);
        Resolver resolver = new Resolver() {
            @Override
            protected void addImplicitResolvers() {
                addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$"), "tTfF");
                addImplicitResolver(Tag.NULL, Pattern.compile("^(?:~|null|Null|NULL| )$"), "~nNu\0");
                addImplicitResolver(Tag.NULL, Pattern.compile("^$"), null);
                addImplicitResolver(Tag.INT, INT, "-+0123456789");
                addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
                addImplicitResolver(Tag.MERGE, MERGE, "<");
                addImplicitResolver(Tag.TIMESTAMP, TIMESTAMP, "0123456789");
                addImplicitResolver(Tag.YAML, YAML, "!&*");
            }
        };
        return new Yaml(constructor, representer, dumperOptions, loaderOptions, resolver);
    }

    public static void warnUnknownKeys(
            String lootId,
            Map<String, Object> map,
            Set<String> known,
            List<ConfigWarning> warnings
    ) {
        for (String key : map.keySet()) {
            if (!known.contains(key)) {
                warnings.add(ConfigWarning.unknownKey(lootId, key));
            }
        }
    }

    /**
     * Whole number inside the 32-bit integer range. 10.5 is not an integer.
     */
    public static OptionalInt strictInt(Object value) {
        if (value instanceof Integer i) {
            return OptionalInt.of(i);
        }
        if (value instanceof Long l) {
            if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(l.intValue());
        }
        if (value instanceof Short s) {
            return OptionalInt.of(s.intValue());
        }
        if (value instanceof Byte b) {
            return OptionalInt.of(b.intValue());
        }
        if (value instanceof Double d) {
            if (d % 1 != 0 || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(d.intValue());
        }
        if (value instanceof Float f) {
            if (f % 1 != 0 || f < Integer.MIN_VALUE || f > Integer.MAX_VALUE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(f.intValue());
        }
        if (value instanceof String s) {
            try {
                return OptionalInt.of(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ex) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    public static Optional<Boolean> strictBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) {
                return Optional.of(true);
            }
            if ("false".equalsIgnoreCase(s.trim())) {
                return Optional.of(false);
            }
        }
        return Optional.empty();
    }

    public static Optional<Integer> intValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        try {
            return Optional.of(Integer.parseInt(String.valueOf(value).trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static List<String> blockEntriesOrDefault(Object raw, List<String> defaults) {
        if (raw == null) {
            return defaults;
        }
        List<String> entries = blockEntries(raw);
        return entries.isEmpty() && !(raw instanceof List<?>) ? defaults : entries;
    }

    public static List<String> blockEntries(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String trimmed = String.valueOf(entry).trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return List.copyOf(entries);
    }

    /**
     * The root of a file whose YAML error has no warning to carry it. The error names {@code file} and the line.
     */
    public static Map<String, Object> parseRoot(String file, String content) {
        try {
            return parseRoot(content);
        } catch (YAMLException error) {
            throw ConfigParseException.of(file, content, error);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseRoot(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        Object loaded = yaml().load(content);
        if (loaded instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    public static Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String s) {
            return Optional.of(s);
        }
        return Optional.of(String.valueOf(value));
    }

    public static Optional<String> textValue(Object value) {
        if (value instanceof String s) {
            return Optional.of(s);
        }
        return Optional.empty();
    }
}
