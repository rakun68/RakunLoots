package me.rakkoon69.rakunloots.server;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.profile.PlayerProfileMock;
import org.bukkit.World;
import org.bukkit.inventory.ItemFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A MockBukkit server whose scheduler records the thread each task ran on. Its main thread is the thread that
 * started it, so a test ticks the scheduler to run main-thread tasks. It also records each world lookup by key, and
 * makes player profiles with working textures and player head meta that holds one.
 */
final class RecordingServerMock extends ServerMock {

    /**
     * One world lookup: whether it ran on the main thread, and the recorded task it ran inside, if any.
     */
    static final class WorldLookup {

        private final boolean mainThread;
        private final OptionalInt task;

        private WorldLookup(boolean mainThread, OptionalInt task) {
            this.mainThread = mainThread;
            this.task = task;
        }

        boolean mainThread() {
            return mainThread;
        }

        OptionalInt task() {
            return task;
        }

        @Override
        public String toString() {
            return "getWorld" + (mainThread ? " on main" : " off main") + " in task " + task;
        }
    }

    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final List<WorldLookup> worldLookups = new CopyOnWriteArrayList<>();
    private final ItemFactory itemFactory = FakeSkullMeta.itemFactory();
    private volatile boolean failProfiles;

    private RecordingServerMock() {
    }

    /**
     * Starts the mock server in place of {@code MockBukkit.mock()}. Stop it with {@code MockBukkit.unmock()}.
     */
    static RecordingServerMock mock() {
        return MockBukkit.mock(new RecordingServerMock());
    }

    @Override
    public RecordingScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public World getWorld(UUID uid) {
        worldLookups.add(new WorldLookup(isPrimaryThread(), RecordingScheduler.runningTask()));
        return super.getWorld(uid);
    }

    /**
     * A profile with working textures, which MockBukkit's own profile lacks.
     */
    @Override
    public @NotNull PlayerProfileMock createPlayerProfile(@Nullable UUID uniqueId, @Nullable String name) {
        if (failProfiles) {
            throw new IllegalStateException("the server cannot make a profile");
        }
        return new FakePlayerProfile(uniqueId, name);
    }

    /**
     * From now on every profile the server is asked for fails, so a head's skin cannot be applied.
     */
    void failPlayerProfiles() {
        failProfiles = true;
    }

    /**
     * An item factory whose player head meta holds an owner profile, which MockBukkit's own lacks.
     */
    @Override
    public @NotNull ItemFactory getItemFactory() {
        return itemFactory;
    }

    List<WorldLookup> worldLookups() {
        return List.copyOf(worldLookups);
    }
}
