package me.rakkoon69.rakunloots.config;

import me.rakkoon69.rakunloots.warning.WarningKind;
import me.rakkoon69.rakunloots.warning.WarningSettings;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static me.rakkoon69.rakunloots.config.ConfigValues.quoteLine;
import static me.rakkoon69.rakunloots.config.ConfigValues.yaml;

/**
 * Parses {@code warnings.yml}: a flat map of a warning kind's stable name to {@code low}, {@code high}, {@code severe},
 * or {@code off}. A missing file, or a kind the file does not list, keeps the default. A name no module raises warns
 * low and is left out. A value that is not one of the four warns high and leaves the kind at its default. A file that
 * is not valid YAML warns high and leaves every kind at its default.
 */
final class WarningsConfig {

    private WarningsConfig() {
    }

    static WarningSettings parse(String content, Collection<? extends WarningKind> knownKinds,
                                 List<ConfigWarning> warnings) {
        if (content == null || content.isBlank()) {
            return WarningSettings.none();
        }
        Object loaded;
        try {
            loaded = yaml().load(content);
        } catch (YAMLException ex) {
            warnings.add(ConfigWarning.warningsParseRejected(quoteLine(content, ex)));
            return WarningSettings.none();
        }
        if (!(loaded instanceof Map<?, ?> root)) {
            return WarningSettings.none();
        }
        Set<String> knownNames = new HashSet<>();
        for (WarningKind kind : knownKinds) {
            knownNames.add(kind.stableName());
        }
        Map<String, WarningSettings.Setting> settings = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!knownNames.contains(name)) {
                warnings.add(ConfigWarning.unknownWarningKind(name));
                continue;
            }
            Optional<WarningSettings.Setting> setting = setting(entry.getValue());
            if (setting.isEmpty()) {
                warnings.add(ConfigWarning.unusableWarningSeverity(name));
                continue;
            }
            settings.put(name, setting.get());
        }
        return WarningSettings.of(settings);
    }

    /**
     * YAML 1.1 reads an unquoted {@code off} as false, so false means off.
     */
    private static Optional<WarningSettings.Setting> setting(Object raw) {
        if (Boolean.FALSE.equals(raw)) {
            return Optional.of(WarningSettings.Setting.OFF);
        }
        if (!(raw instanceof String text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(WarningSettings.Setting.valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
