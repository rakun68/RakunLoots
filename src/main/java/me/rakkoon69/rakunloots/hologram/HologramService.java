package me.rakkoon69.rakunloots.hologram;

import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.lootbox.BoxEvent;
import me.rakkoon69.rakunloots.lootbox.BoxObserver;
import me.rakkoon69.rakunloots.text.MessageText;
import me.rakkoon69.rakunloots.text.TimeText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps one hologram above each shown loot box whose loot has its hologram enabled, and hands it to the hologram
 * provider. It follows the lifecycle as its {@link BoxObserver}: shown creates the hologram, each tick renders its
 * lines again and updates the provider only when they changed, and out of view and ended remove it. Stop removes
 * every hologram.
 *
 * <p>A line's {@code {name}} is the loot's display name. {@code {owner}} is the owner's name, or the
 * {@code hologram_no_owner} message. {@code {lock}} is the remaining lock as time, or {@code hologram_unlocked} at 0.
 * {@code {life}} is the remaining life as time, or {@code hologram_unending}. Time reads {@code m:ss}, or
 * {@code h:mm:ss} from an hour. The lines and the three messages are resolved once per loot for each config, and each
 * owner's name is looked up once, so a tick only fills in numbers.
 *
 * <p>A hologram sits {@code height} blocks above the bottom of its block, centred on it, and that is where its lowest
 * line is. After a reload, the next tick removes a hologram whose loot is gone or has its hologram turned off, and
 * moves one whose height or view range changed. A box shown while its loot is gone or has its hologram turned off gets
 * none.
 *
 * <p>Every call is safe on any thread and renders on the thread it arrives on.
 */
public final class HologramService implements BoxObserver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(name|owner|lock|life)}");
    private static final String KEY_UNLOCKED = "hologram_unlocked";
    private static final String KEY_NO_OWNER = "hologram_no_owner";
    private static final String KEY_UNENDING = "hologram_unending";
    private static final double BLOCK_CENTRE = 0.5;

    private final Function<ConfigSnapshot, HologramProvider> picker;
    private final OwnerNames ownerNames;

    private final Object lock = new Object();
    private ConfigSnapshot config;
    private HologramProvider provider;
    private HologramProviderChoice choice;
    /**
     * Each loot's lines and messages for the current config.
     */
    private final Map<String, Template> templates = new HashMap<>();
    private final Map<UUID, String> knownOwners = new HashMap<>();
    /**
     * Every hologram the provider draws now, by box id.
     */
    private final Map<UUID, Drawn> drawn = new HashMap<>();
    private boolean stopped;

    /**
     * A service that keeps {@code provider} for its whole life.
     */
    public HologramService(ConfigSnapshot config, HologramProvider provider, OwnerNames ownerNames) {
        this(config, provider, ownerNames, snapshot -> provider);
    }

    /**
     * @param provider the provider picked for {@code config}'s {@code hologram_provider}
     * @param picker   picks the provider for a reloaded config whose {@code hologram_provider} changed
     */
    public HologramService(ConfigSnapshot config, HologramProvider provider, OwnerNames ownerNames,
                           Function<ConfigSnapshot, HologramProvider> picker) {
        this.config = Objects.requireNonNull(config, "config");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.ownerNames = Objects.requireNonNull(ownerNames, "ownerNames");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.choice = config.hologramProvider();
    }

    /**
     * Takes a reloaded config. Each hologram follows it from the next tick. When {@code hologram_provider} changed,
     * the old provider removes every hologram, and the picked provider draws each hologram that was shown, with the
     * lines it had.
     */
    public void reload(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (lock) {
            config = snapshot;
            templates.clear();
            if (snapshot.hologramProvider() != choice && !stopped) {
                switchProvider(snapshot);
            }
        }
    }

    private void switchProvider(ConfigSnapshot snapshot) {
        provider.removeAll();
        choice = snapshot.hologramProvider();
        provider = Objects.requireNonNull(picker.apply(snapshot), "provider");
        drawn.forEach((boxId, shown) -> provider.create(boxId, shown.position(), shown.lines(), shown.viewRange()));
    }

    /**
     * Removes every hologram through the provider's remove all. Later events draw nothing.
     */
    public void stop() {
        synchronized (lock) {
            stopped = true;
            drawn.clear();
            provider.removeAll();
        }
    }

    @Override
    public void shown(BoxEvent box) {
        synchronized (lock) {
            if (stopped || drawn.containsKey(box.boxId())) {
                return;
            }
            enabledSettings(box.lootId()).ifPresent(settings -> create(box, settings));
        }
    }

    @Override
    public void ticked(BoxEvent box) {
        synchronized (lock) {
            Drawn current = drawn.get(box.boxId());
            if (current == null) {
                return;
            }
            Optional<HologramSettings> settings = enabledSettings(box.lootId());
            if (settings.isEmpty()) {
                remove(box.boxId());
                return;
            }
            HologramPosition position = position(box, settings.get());
            if (!position.equals(current.position()) || settings.get().viewRange() != current.viewRange()) {
                remove(box.boxId());
                create(box, settings.get());
                return;
            }
            List<String> lines = render(box);
            if (!lines.equals(current.lines())) {
                provider.update(box.boxId(), lines);
                drawn.put(box.boxId(), new Drawn(position, current.viewRange(), lines));
            }
        }
    }

    @Override
    public void outOfView(BoxEvent box) {
        synchronized (lock) {
            remove(box.boxId());
        }
    }

    @Override
    public void ended(BoxEvent box) {
        synchronized (lock) {
            remove(box.boxId());
        }
    }

    private Optional<HologramSettings> enabledSettings(String lootId) {
        return config.hologram(lootId).filter(HologramSettings::enabled);
    }

    private void create(BoxEvent box, HologramSettings settings) {
        HologramPosition position = position(box, settings);
        List<String> lines = render(box);
        provider.create(box.boxId(), position, lines, settings.viewRange());
        drawn.put(box.boxId(), new Drawn(position, settings.viewRange(), lines));
    }

    private void remove(UUID boxId) {
        if (drawn.remove(boxId) != null) {
            provider.remove(boxId);
        }
    }

    private static HologramPosition position(BoxEvent box, HologramSettings settings) {
        return new HologramPosition(box.worldKey(), box.position().x() + BLOCK_CENTRE,
                box.position().y() + settings.height(), box.position().z() + BLOCK_CENTRE);
    }

    private List<String> render(BoxEvent box) {
        Template template = templates.computeIfAbsent(box.lootId(), this::template);
        String owner = box.owner().map(this::ownerName).orElse(template.noOwner());
        String lock = box.remainingLockSeconds() > 0 ? time(box.remainingLockSeconds()) : template.unlocked();
        String life = box.remainingLifeSeconds().isPresent()
                ? time(box.remainingLifeSeconds().getAsInt())
                : template.unending();
        List<String> lines = new ArrayList<>(template.lines().size());
        for (String line : template.lines()) {
            Matcher matcher = PLACEHOLDER.matcher(line);
            StringBuilder filled = new StringBuilder();
            while (matcher.find()) {
                String value = switch (matcher.group(1)) {
                    case "name" -> template.name();
                    case "owner" -> owner;
                    case "lock" -> lock;
                    default -> life;
                };
                matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(filled);
            lines.add(MessageText.color(filled.toString()));
        }
        return List.copyOf(lines);
    }

    private Template template(String lootId) {
        return new Template(
                config.hologramLines(lootId),
                config.loot(lootId).map(loot -> loot.displayName()).orElse(lootId),
                config.message(lootId, KEY_UNLOCKED),
                config.message(lootId, KEY_NO_OWNER),
                config.message(lootId, KEY_UNENDING)
        );
    }

    /**
     * The owner's name, looked up once. A name the server does not know shows the owner's id, and that is kept too, so
     * the server is not asked again.
     */
    private String ownerName(UUID owner) {
        return knownOwners.computeIfAbsent(owner, id -> ownerNames.name(id).orElse(id.toString()));
    }

    /**
     * {@code m:ss}, or {@code h:mm:ss} from an hour.
     */
    static String time(int seconds) {
        return TimeText.time(seconds);
    }

    private record Template(List<String> lines, String name, String unlocked, String noOwner, String unending) {
    }

    private record Drawn(HologramPosition position, int viewRange, List<String> lines) {
    }
}
