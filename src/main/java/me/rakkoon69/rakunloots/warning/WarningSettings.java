package me.rakkoon69.rakunloots.warning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code warnings.yml} says: for some warning kinds, the severity the owner wants, or that the kind is off. A kind
 * the file does not list is raised at the severity the module gave it, which is its default.
 */
public final class WarningSettings {

    /**
     * What the file may say about one kind.
     */
    public enum Setting {
        LOW,
        HIGH,
        SEVERE,
        OFF;

        /**
         * The severity this setting gives, or empty for {@link #OFF}.
         */
        public Optional<Severity> severity() {
            return switch (this) {
                case LOW -> Optional.of(Severity.LOW);
                case HIGH -> Optional.of(Severity.HIGH);
                case SEVERE -> Optional.of(Severity.SEVERE);
                case OFF -> Optional.empty();
            };
        }
    }

    private static final WarningSettings NONE = new WarningSettings(Map.of());

    private final Map<String, Setting> byName;

    private WarningSettings(Map<String, Setting> byName) {
        this.byName = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    /**
     * Settings that list no kind: every warning keeps the severity it was raised at.
     */
    public static WarningSettings none() {
        return NONE;
    }

    /**
     * @param byName the setting of each kind the file lists, by stable name
     */
    public static WarningSettings of(Map<String, Setting> byName) {
        return byName.isEmpty() ? NONE : new WarningSettings(byName);
    }

    /**
     * The severity a warning of this kind has after the file is applied: the file's when it lists the kind, else the one
     * it was raised at. Empty when the file turns the kind off.
     */
    public Optional<Severity> judge(WarningKind kind, Severity raised) {
        return judge(kind.stableName(), raised);
    }

    private Optional<Severity> judge(String kindName, Severity raised) {
        Setting setting = byName.get(kindName);
        return setting == null ? Optional.of(raised) : setting.severity();
    }

    /**
     * The setting the file gives a kind, by stable name.
     */
    public Optional<Setting> setting(String name) {
        return Optional.ofNullable(byName.get(name));
    }
}
