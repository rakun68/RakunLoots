package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.config.HologramProviderChoice;
import me.rakkoon69.rakunloots.warning.Severity;
import me.rakkoon69.rakunloots.warning.Warning;
import me.rakkoon69.rakunloots.warning.WarningKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A problem the hologram provider choice found. The choice does not write to the log; it hands these to its warning
 * sink. Severities are the config severities, so the warning level filters them the same way.
 */
public final class HologramWarning implements Warning {

    public enum Kind implements WarningKind {
        /**
         * The provider {@code hologram_provider} names cannot run on this server, so the choice fell back to
         * {@code auto}. Carries the provider and the reason. Always high.
         */
        PROVIDER_CANNOT_RUN(Severity.HIGH),
        /**
         * {@code auto} could not use the plugin's own packet holograms, most often because no packet adapter matches
         * this server's Minecraft version, and moved on to a hologram plugin. Carries the reason, which names the
         * version. Always high.
         */
        PACKETS_CANNOT_RUN(Severity.HIGH),
        /**
         * No provider can run on this server, so no loot box shows a hologram. Carries each provider's reason, in
         * {@code auto}'s order. Always high.
         */
        NO_PROVIDER(Severity.HIGH);

        private final Severity defaultSeverity;

        Kind(Severity defaultSeverity) {
            this.defaultSeverity = defaultSeverity;
        }

        @Override
        public Severity defaultSeverity() {
            return defaultSeverity;
        }
    }

    private final Kind kind;
    private final Optional<HologramProviderChoice> provider;
    private final Map<HologramProviderChoice, String> reasons;

    private HologramWarning(Kind kind, Optional<HologramProviderChoice> provider,
                            Map<HologramProviderChoice, String> reasons) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.reasons = Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
    }

    static HologramWarning providerCannotRun(HologramProviderChoice provider, String reason) {
        return new HologramWarning(Kind.PROVIDER_CANNOT_RUN, Optional.of(provider), Map.of(provider, reason));
    }

    static HologramWarning packetsCannotRun(String reason) {
        return new HologramWarning(Kind.PACKETS_CANNOT_RUN, Optional.of(HologramProviderChoice.PACKETS),
                Map.of(HologramProviderChoice.PACKETS, reason));
    }

    static HologramWarning noProvider(Map<HologramProviderChoice, String> reasons) {
        return new HologramWarning(Kind.NO_PROVIDER, Optional.empty(), reasons);
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public Kind kind() {
        return kind;
    }

    /**
     * The provider {@code hologram_provider} named, for {@link Kind#PROVIDER_CANNOT_RUN}, or packets, for
     * {@link Kind#PACKETS_CANNOT_RUN}.
     */
    public Optional<HologramProviderChoice> provider() {
        return provider;
    }

    /**
     * Why each provider this warning is about cannot run, in the order they were asked.
     */
    public Map<HologramProviderChoice, String> reasons() {
        return reasons;
    }

    @Override
    public Optional<String> subject() {
        return provider.map(Object::toString);
    }

    @Override
    public String fields() {
        return Warning.join(
                Warning.field("provider", provider),
                reasons.isEmpty() ? "" : "reasons=" + reasons
        );
    }

    @Override
    public Optional<Throwable> cause() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return kind + " " + provider.map(Enum::name).orElse("-") + " " + reasons;
    }
}
