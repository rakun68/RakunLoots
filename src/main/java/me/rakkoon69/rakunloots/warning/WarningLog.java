package me.rakkoon69.rakunloots.warning;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes every warning the modules raise to the plugin's logger: each kind with the fields it carries, at the log
 * level its severity maps to (low is info, high is warning, severe is severe), and only when the config's warning level
 * admits it. It is the one sink the lifecycle, the visuals, the animations, the holograms, and the config loads report
 * to. Called from any thread; {@link Logger} is safe on any thread.
 */
public final class WarningLog implements Warnings {

    private final Logger logger;
    private final Supplier<WarningLevel> level;
    private final BooleanSupplier debug;
    private final Supplier<WarningSettings> settings;
    private final WarningRecord record = new WarningRecord();

    /**
     * A log with debug mode off.
     *
     * @param level the current warning level, read for every warning so a reload reaches the next one
     */
    public WarningLog(Logger logger, Supplier<WarningLevel> level) {
        this(logger, level, () -> false);
    }

    /**
     * @param level the current warning level, read for every warning so a reload reaches the next one
     * @param debug whether debug mode is on, read for every debug line so a reload reaches the next one
     */
    public WarningLog(Logger logger, Supplier<WarningLevel> level, BooleanSupplier debug) {
        this(logger, level, debug, WarningSettings::none);
    }

    /**
     * @param settings what {@code warnings.yml} says now, read for every warning so a reload reaches the next one
     */
    public WarningLog(Logger logger, Supplier<WarningLevel> level, BooleanSupplier debug,
                      Supplier<WarningSettings> settings) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.level = Objects.requireNonNull(level, "level");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Every warning raised since the last {@link WarningRecord#clear()}, whether or not the warning level let it
     * through to the logger.
     */
    public WarningRecord record() {
        return record;
    }

    @Override
    public boolean debugEnabled() {
        return debug.getAsBoolean();
    }

    /**
     * Logs one line at info, prefixed {@code [debug]}, while debug mode is on. Nothing otherwise.
     */
    @Override
    public void debug(String line) {
        if (debugEnabled()) {
            logger.info("[debug] " + line);
        }
    }

    /**
     * Logs each warning of a config load or collection.
     */
    public void config(Iterable<? extends Warning> warnings) {
        for (Warning warning : warnings) {
            warn(warning);
        }
    }

    @Override
    public void warn(Warning warning) {
        Objects.requireNonNull(warning, "warning");
        WarningKind kind = warning.kind();
        Severity raised = warning.severity();
        Optional<Severity> judged = settings.get().judge(kind, raised);
        if (judged.isEmpty()) {
            return;
        }
        Severity severity = judged.get();
        boolean passed = level.get().admits(severity);
        record.record(kind, severity, warning.subject(), passed);
        if (!passed) {
            return;
        }
        Level logLevel = switch (severity) {
            case LOW -> Level.INFO;
            case HIGH -> Level.WARNING;
            case SEVERE -> Level.SEVERE;
        };
        String fields = warning.fields();
        String message = fields.isEmpty() ? kind.stableName() : kind.stableName() + " " + fields;
        Optional<Throwable> cause = warning.cause();
        // A low warning's message already names its cause; only a high or severe one carries the stack trace.
        if (cause.isPresent() && severity != Severity.LOW) {
            logger.log(logLevel, message, cause.get());
        } else {
            logger.log(logLevel, message);
        }
    }

    /**
     * A failure sink for a service that reports what failed and why: logs at severe with the failure.
     */
    @Override
    public void failure(String what, Throwable cause) {
        logger.log(Level.SEVERE, what, cause);
    }
}
