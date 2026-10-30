package me.rakkoon69.rakunloots;

import me.rakkoon69.rakunloots.animation.AnimationWarning;
import me.rakkoon69.rakunloots.config.ConfigWarning;
import me.rakkoon69.rakunloots.hologram.HologramWarning;
import me.rakkoon69.rakunloots.lootbox.LifecycleWarning;
import me.rakkoon69.rakunloots.menu.MenuWarning;
import me.rakkoon69.rakunloots.roll.RollWarning;
import me.rakkoon69.rakunloots.storage.LootBoxWarning;
import me.rakkoon69.rakunloots.visuals.VisualsWarning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every warning kind this plugin raises, in the order {@code warnings.yml} lists them.
 */
public final class WarningKinds {

    private static final List<WarningKind> ALL;
    private static final Map<String, WarningKind> BY_NAME;

    static {
        List<WarningKind> all = new ArrayList<>();
        Collections.addAll(all, ConfigWarning.Kind.values());
        Collections.addAll(all, LifecycleWarning.Kind.values());
        Collections.addAll(all, LootBoxWarning.Kind.values());
        Collections.addAll(all, VisualsWarning.Kind.values());
        Collections.addAll(all, AnimationWarning.Kind.values());
        Collections.addAll(all, HologramWarning.Kind.values());
        Collections.addAll(all, RollWarning.Kind.values());
        Collections.addAll(all, MenuWarning.Kind.values());

        Map<String, WarningKind> byName = new LinkedHashMap<>();
        for (WarningKind kind : all) {
            if (byName.put(kind.stableName(), kind) != null) {
                throw new IllegalStateException("Two warning kinds are named " + kind.stableName());
            }
        }
        ALL = List.copyOf(all);
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    private WarningKinds() {
    }

    /**
     * Every kind, in the order {@code warnings.yml} lists them.
     */
    public static List<WarningKind> all() {
        return ALL;
    }

    /**
     * The kind a stable name stands for, or empty for a name no module raises.
     */
    public static Optional<WarningKind> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /**
     * The kind of a warning enum constant.
     */
    public static WarningKind of(WarningKind constant) {
        Objects.requireNonNull(constant, "constant");
        WarningKind kind = BY_NAME.get(constant.stableName());
        if (kind == null) {
            throw new IllegalArgumentException("Unregistered warning kind " + constant);
        }
        return kind;
    }

    /**
     * The stable name of a warning enum constant.
     */
    public static String name(WarningKind constant) {
        return constant.stableName();
    }
}
