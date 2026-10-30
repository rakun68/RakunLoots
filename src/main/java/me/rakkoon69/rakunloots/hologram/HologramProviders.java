package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.hologram.HologramProvider.CanRun;
import me.rakkoon69.rakunloots.warning.Warnings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Picks the hologram provider for {@code hologram_provider} in {@code config.yml}.
 */
public final class HologramProviders {

    /**
     * The order {@code auto} tries: the plugin's own packets first, then the hologram plugins.
     */
    public static final List<HologramProviderChoice> AUTO_ORDER = List.of(
            HologramProviderChoice.PACKETS,
            HologramProviderChoice.DECENTHOLOGRAMS,
            HologramProviderChoice.FANCYHOLOGRAMS,
            HologramProviderChoice.HOLOGRAPHICDISPLAYS
    );

    private static final String NOT_AVAILABLE = "it is not available in this build of the plugin";

    private HologramProviders() {
    }

    /**
     * Picks a provider. {@code none} picks {@link HologramProvider#NONE} and asks no provider. A named provider is
     * picked when it can run; when it cannot, the choice warns high with its name and reason and goes on as
     * {@code auto}. {@code auto} picks the first in {@link #AUTO_ORDER} that can run. When that is a hologram plugin
     * because packets cannot run, the choice warns high with the packets reason, which names the Minecraft version,
     * unless {@code hologram_provider} named packets and that was warned already. When none can, the choice warns
     * high once and picks {@link HologramProvider#NONE}. Each provider is asked {@link HologramProvider#canRun} at most
     * once, and a provider whose check throws cannot run, so no provider stops the plugin.
     *
     * @param providers the providers built for this server, by name; one that is absent cannot run
     * @param warnings  receives each warning
     */
    public static HologramProvider choose(
            HologramProviderChoice choice,
            Map<HologramProviderChoice, HologramProvider> providers,
            Warnings warnings
    ) {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(warnings, "warnings");
        for (HologramProviderChoice named : providers.keySet()) {
            if (!AUTO_ORDER.contains(named)) {
                throw new IllegalArgumentException("not a provider: " + named);
            }
        }
        if (choice == HologramProviderChoice.NONE) {
            return HologramProvider.NONE;
        }
        Map<HologramProviderChoice, String> refused = new LinkedHashMap<>();
        if (choice != HologramProviderChoice.AUTO) {
            CanRun named = canRun(providers.get(choice));
            if (named.yes()) {
                return providers.get(choice);
            }
            String reason = named.refusal().orElseThrow();
            warnings.warn(HologramWarning.providerCannotRun(choice, reason));
            refused.put(choice, reason);
        }
        Map<HologramProviderChoice, String> reasons = new LinkedHashMap<>();
        for (HologramProviderChoice candidate : AUTO_ORDER) {
            CanRun canRun = refused.containsKey(candidate)
                    ? CanRun.no(refused.get(candidate))
                    : canRun(providers.get(candidate));
            if (canRun.yes()) {
                String packets = reasons.get(HologramProviderChoice.PACKETS);
                if (packets != null && !refused.containsKey(HologramProviderChoice.PACKETS)) {
                    warnings.warn(HologramWarning.packetsCannotRun(packets));
                }
                return providers.get(candidate);
            }
            reasons.put(candidate, canRun.refusal().orElseThrow());
        }
        warnings.warn(HologramWarning.noProvider(reasons));
        return HologramProvider.NONE;
    }

    private static CanRun canRun(HologramProvider provider) {
        if (provider == null) {
            return CanRun.no(NOT_AVAILABLE);
        }
        try {
            return Objects.requireNonNull(provider.canRun(), "canRun");
        } catch (RuntimeException | LinkageError failure) {
            return CanRun.no("its check failed: " + failure);
        }
    }
}
