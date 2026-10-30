package me.rakkoon69.rakunloots.wiki;

import me.rakkoon69.rakunloots.WarningKinds;

import me.rakkoon69.rakunloots.config.ConfigLoader;
import me.rakkoon69.rakunloots.config.ConfigSnapshot;
import me.rakkoon69.rakunloots.config.ConfigSources;
import me.rakkoon69.rakunloots.roll.LootChance;
import me.rakkoon69.rakunloots.server.TaskHandle;
import me.rakkoon69.rakunloots.server.TaskScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reload that lands after the old job's first generation check but before its math returns must still discard the old
 * result at publication.
 */
class Phase9ChanceCacheMidCalculationTest {

    @Test
    void anOldCalculationThatReturnsAfterTheNewerPublicationIsDiscarded() throws Exception {
        HeldScheduler scheduler = new HeldScheduler();
        CountDownLatch calculating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        LootChanceCache cache = new LootChanceCache(scheduler, roll -> {
            if (first.compareAndSet(true, false)) {
                calculating.countDown();
                await(release);
            }
            return LootChance.calculate(roll);
        });
        ConfigSnapshot old = config(1);
        ConfigSnapshot current = config(3);

        cache.load(old);
        Thread oldJob = new Thread(scheduler.jobs.removeFirst(), "old-chance-job");
        oldJob.start();
        assertTrue(calculating.await(5, TimeUnit.SECONDS), "the old job reached the expensive calculation");

        cache.load(current);
        scheduler.jobs.removeFirst().run();
        assertEquals(0.25, cache.chances(current, "demo").orElseThrow().get("a").probability(), 1e-9);

        release.countDown();
        oldJob.join(5000);
        assertFalse(oldJob.isAlive(), "the old calculation returned");
        assertEquals(0.25, cache.chances(current, "demo").orElseThrow().get("a").probability(), 1e-9,
                "an older result cannot replace the newer generation");
        assertTrue(cache.chances(old, "demo").isEmpty(), "the older snapshot never gets the newer result");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for the test interleaving");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static ConfigSnapshot config(int secondWeight) {
        ConfigSnapshot snapshot = ConfigLoader.load(ConfigSources.builder().globals("language: en\n")
                .worlds("{}\n").language("en", "text: test\n")
                .loot("demo", "open_mode: drop\nblock: barrel\nroll:\n  min: 1\n  max: 1\n  entries:\n"
                        + "    a:\n      item: {id: diamond}\n      weight: 1\n"
                        + "    b:\n      item: {id: emerald}\n      weight: " + secondWeight + "\n")
                .build(), id -> Optional.of(id), WarningKinds.all());
        assertTrue(snapshot.loot("demo").isPresent(), "fixture loot demo must load");
        return snapshot;
    }

    private static final class HeldScheduler implements TaskScheduler {
        final Deque<Runnable> jobs = new ArrayDeque<>();

        @Override
        public TaskHandle run(Runnable job) {
            jobs.addLast(job);
            return () -> {
            };
        }

        @Override
        public TaskHandle runLater(Runnable job, long ticks) {
            throw new AssertionError("unexpected delayed chance job");
        }

        @Override
        public TaskHandle repeat(Runnable job, long delay, long period) {
            throw new AssertionError("unexpected repeating chance job");
        }
    }
}
