package me.rakkoon69.rakunloots.hologram;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What draws loot box holograms: this plugin's own packets, or a hologram plugin such as DecentHolograms. The hologram
 * service is its only caller, and {@link HologramProviders#choose} picks which one it gets.
 *
 * <p>Building a provider touches nothing of its plugin. {@link #canRun} is asked first, and the other calls come only
 * when it said yes.
 *
 * <p>A hologram's position is the bottom of the hologram: its lowest line sits there, centred on the block, and the
 * lines stack upward in the order given. Each provider places its lines so, whatever its plugin's own anchor, so a
 * loot's {@code height} means the same under every provider.
 *
 * <p>Lines are what players see, with colours already turned into legacy colour codes ({@code §}). A line may be
 * empty.
 *
 * <p>Calls come from any thread, such as a region thread on Folia or the global scheduler, while the caller holds its
 * own lock. A provider must return quickly and never wait for another thread; it hands work to whichever thread its
 * plugin needs. For one hologram the calls arrive in order: create, then any number of updates, then remove. Create
 * never repeats an id that was not removed. Update and remove of an unknown id do nothing. Nothing a provider draws is
 * saved to the world or to its plugin's files.
 */
public interface HologramProvider {

    /**
     * Draws nothing. Chosen for {@code none}, and when no provider can run.
     */
    HologramProvider NONE = new HologramProvider() {
        @Override
        public CanRun canRun() {
            return CanRun.YES;
        }

        @Override
        public void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange) {
        }

        @Override
        public void update(UUID hologramId, List<String> lines) {
        }

        @Override
        public void remove(UUID hologramId) {
        }

        @Override
        public void removeAll() {
        }
    };

    /**
     * Whether this provider can draw on this server: its plugin is installed and enabled, it runs on this server's
     * platform and Minecraft version, and on Folia its plugin declares {@code folia-supported: true}. Asked once, before
     * any other call.
     */
    CanRun canRun();

    /**
     * Draws a new hologram.
     *
     * @param hologramId the loot box's id; names the hologram in later calls
     * @param position   where its lowest line sits
     * @param lines      top line first; never empty
     * @param viewRange  blocks within which players see it
     */
    void create(UUID hologramId, HologramPosition position, List<String> lines, int viewRange);

    /**
     * Replaces the lines of a hologram, top line first; never empty. The position stays.
     */
    void update(UUID hologramId, List<String> lines);

    /**
     * Takes a hologram away.
     */
    void remove(UUID hologramId);

    /**
     * Takes away every hologram this provider still draws. Called when the plugin stops.
     */
    void removeAll();

    /**
     * The answer to {@link #canRun}: yes, or no with the reason, which the warning that skips this provider names.
     *
     * @param refusal why this provider cannot run; empty when it can
     */
    record CanRun(Optional<String> refusal) {

        public static final CanRun YES = new CanRun(Optional.empty());

        public CanRun {
            Objects.requireNonNull(refusal, "refusal");
        }

        public static CanRun no(String reason) {
            return new CanRun(Optional.of(Objects.requireNonNull(reason, "reason")));
        }

        public boolean yes() {
            return refusal.isEmpty();
        }
    }
}
