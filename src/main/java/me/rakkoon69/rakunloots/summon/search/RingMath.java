package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.config.summon.SummonRing;
import me.rakkoon69.rakunloots.server.ChunkPosition;
import me.rakkoon69.rakunloots.server.PlayerPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Square-ring geometry and smart-summon sampling.
 */
final class RingMath {

    private RingMath() {
    }

    static int chebyshev(int centerX, int centerZ, int x, int z) {
        return Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
    }

    static boolean inRing(int centerX, int centerZ, int x, int z, SummonRing ring) {
        int distance = chebyshev(centerX, centerZ, x, z);
        return distance >= ring.min() && distance <= ring.max();
    }

    static SampledColumn sampleColumn(int centerX, int centerZ, SummonRing ring, double angle, Random random) {
        double u = random.nextDouble();
        double r = Math.sqrt(ring.min() * (double) ring.min() + u * (ring.max() * (double) ring.max()
                - ring.min() * (double) ring.min()));
        double vx = Math.cos(angle);
        double vz = Math.sin(angle);
        double denom = Math.max(Math.abs(vx), Math.abs(vz));
        double scale = r / denom;
        int x = centerX + (int) Math.floor(vx * scale + 0.5);
        int z = centerZ + (int) Math.floor(vz * scale + 0.5);
        return new SampledColumn(x, z);
    }

    static SampledColumn sampleColumn(int centerX, int centerZ, SummonRing ring, Random random) {
        double angle = random.nextDouble() * 2.0 * Math.PI;
        return sampleColumn(centerX, centerZ, ring, angle, random);
    }

    static double sampleSmartAngle(
            PlayerPosition player,
            double medianX,
            double medianZ,
            int medianPull,
            Random random
    ) {
        if (Math.max(Math.abs(player.x() - medianX), Math.abs(player.z() - medianZ)) <= 1.0) {
            return random.nextDouble() * 2.0 * Math.PI;
        }
        double baseAngle = Math.atan2(medianZ - player.z(), medianX - player.x());
        double pull = medianPull / 100.0;
        if (pull >= 1.0) {
            return baseAngle;
        }
        double u = random.nextDouble();
        double exponent = Math.log(0.5) / Math.log(pull);
        double offset = Math.PI * Math.pow(u, exponent);
        if (random.nextBoolean()) {
            offset = -offset;
        }
        return baseAngle + offset;
    }

    static double medianX(List<? extends PlayerPosition> players) {
        return median(players, true);
    }

    static double medianZ(List<? extends PlayerPosition> players) {
        return median(players, false);
    }

    private static double median(List<? extends PlayerPosition> players, boolean xAxis) {
        List<Double> values = new ArrayList<>(players.size());
        for (PlayerPosition player : players) {
            values.add(xAxis ? player.x() : player.z());
        }
        Collections.sort(values);
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2);
        }
        return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    static double distanceToMedian(PlayerPosition player, double medianX, double medianZ) {
        double dx = player.x() - medianX;
        double dz = player.z() - medianZ;
        return Math.hypot(dx, dz);
    }

    static PlayerPosition pickWeightedPlayer(
            List<PlayerPosition> players,
            double medianX,
            double medianZ,
            int pickBias,
            Random random
    ) {
        if (players.size() == 1) {
            return players.get(0);
        }
        double nearestWeight = 1.0 + pickBias;
        double[] distances = new double[players.size()];
        double minDistance = Double.MAX_VALUE;
        double maxDistance = -Double.MAX_VALUE;
        for (int i = 0; i < players.size(); i++) {
            distances[i] = distanceToMedian(players.get(i), medianX, medianZ);
            minDistance = Math.min(minDistance, distances[i]);
            maxDistance = Math.max(maxDistance, distances[i]);
        }
        double total = 0.0;
        double[] weights = new double[players.size()];
        for (int i = 0; i < players.size(); i++) {
            if (maxDistance == minDistance) {
                weights[i] = 1.0;
            } else {
                weights[i] = nearestWeight - (nearestWeight - 1.0) * (distances[i] - minDistance)
                        / (maxDistance - minDistance);
            }
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < players.size(); i++) {
            roll -= weights[i];
            if (roll <= 0.0) {
                return players.get(i);
            }
        }
        return players.get(players.size() - 1);
    }

    static boolean chunkOverlapsRing(int centerX, int centerZ, SummonRing ring, ChunkPosition chunk) {
        int baseX = chunk.chunkX() * 16;
        int baseZ = chunk.chunkZ() * 16;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                if (inRing(centerX, centerZ, baseX + localX, baseZ + localZ, ring)) {
                    return true;
                }
            }
        }
        return false;
    }

    record SampledColumn(int x, int z) {
    }
}
