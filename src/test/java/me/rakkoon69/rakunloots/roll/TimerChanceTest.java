package me.rakkoon69.rakunloots.roll;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerChanceTest {

    @Test
    void weightsAndLoadedLootsMatchingWorldTimers() {
        Map<String, Integer> weights = Map.of(
                "airdrops", 100,
                "treasure_chest", 30,
                "buried_stash", 30,
                "head_trophy", 20,
                "deep_vault", 10,
                "missing_unloaded", 50,
                "zero_weight", 0,
                "negative_weight", -5
        );
        Set<String> loaded = Set.of(
                "airdrops",
                "treasure_chest",
                "buried_stash",
                "head_trophy",
                "deep_vault",
                "zero_weight",
                "negative_weight"
        );

        Map<String, Double> chances = TimerChance.calculate(weights, loaded);

        // Eligible loots sum to 100 + 30 + 30 + 20 + 10 = 190
        assertEquals(5, chances.size());
        assertEquals(100.0 / 190.0, chances.get("airdrops"), 1e-12);
        assertEquals(30.0 / 190.0, chances.get("treasure_chest"), 1e-12);
        assertEquals(30.0 / 190.0, chances.get("buried_stash"), 1e-12);
        assertEquals(20.0 / 190.0, chances.get("head_trophy"), 1e-12);
        assertEquals(10.0 / 190.0, chances.get("deep_vault"), 1e-12);

        // Missing, zero, and negative are absent and excluded from total
        assertFalse(chances.containsKey("missing_unloaded"));
        assertFalse(chances.containsKey("zero_weight"));
        assertFalse(chances.containsKey("negative_weight"));
    }

    @Test
    void emptyEligibleWeightsYieldsEmptyMap() {
        // No loaded loots
        assertTrue(TimerChance.calculate(Map.of("airdrop", 10), Set.of()).isEmpty());

        // Zero and negative weights only
        assertTrue(TimerChance.calculate(Map.of("zero", 0, "neg", -10), Set.of("zero", "neg")).isEmpty());

        // Empty map
        assertTrue(TimerChance.calculate(Map.of(), Set.of("airdrop")).isEmpty());
    }
}
