package me.rakkoon69.rakunloots.summon;

/**
 * Minimal valid loot YAML for summon search tests. Loot parse requires open_mode, block, and roll.
 */
final class SummonSearchFixtures {

    private SummonSearchFixtures() {
    }

    static String topSurfaceLoot(int attempts, String extraInsideSummonSettings) {
        return loot(attempts, "60:80", extraInsideSummonSettings);
    }

    static String topSurfaceLootWideHeight(int attempts, String extraInsideSummonSettings) {
        return loot(attempts, "60:100", extraInsideSummonSettings);
    }

    private static String loot(int attempts, String yRange, String extraInsideSummonSettings) {
        StringBuilder extra = new StringBuilder();
        if (extraInsideSummonSettings != null) {
            for (String line : extraInsideSummonSettings.split("\n")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                // Match the indent of summon_settings children in the template below.
                extra.append("                  ").append(trimmed).append('\n');
            }
        }
        return """
                language: en
                open_mode: drop
                block: barrel
                roll:
                  entries:
                    one:
                      item:
                        id: diamond
                summon_settings:
                  summon_type: top_surface
                  attempts: %d
                  y_range: '%s'
                  replaceable_blocks:
                    - minecraft:air
                %s""".formatted(attempts, yRange, extra);
    }
}
