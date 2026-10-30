package me.rakkoon69.rakunloots.warning;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One warning a module raises: its kind, its raised severity, its subject for the warnings command, its formatted
 * fields for the log, and an optional failure cause.
 */
public interface Warning {

    WarningKind kind();

    Severity severity();

    default Optional<String> subject() {
        return Optional.empty();
    }

    default String fields() {
        return "";
    }

    default Optional<Throwable> cause() {
        return Optional.empty();
    }

    static String field(String name, Optional<?> value) {
        return value.map(v -> name + "=" + v).orElse("");
    }

    static String join(String... fields) {
        List<String> parts = new ArrayList<>();
        for (String field : fields) {
            if (field != null && !field.isEmpty()) {
                parts.add(field);
            }
        }
        return String.join(" ", parts);
    }
}
