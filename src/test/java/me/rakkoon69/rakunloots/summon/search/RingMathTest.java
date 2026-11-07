package me.rakkoon69.rakunloots.summon.search;

import me.rakkoon69.rakunloots.config.summon.SummonRing;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RingMathTest {

    /**
     * Ring 0 to 100 holds 201^2 = 40401 columns, so 20000 samples put 20000 / 40401 = 0.495 on each column, and a
     * 41 by 41 box gets 832, with a standard deviation of 29. The bound of 125 is 4.3 of them. A uniform angle put a
     * diagonal box at about 0.64 of that and a box on an axis at about 1.27, so both fall outside it.
     */
    @Test
    void aSpawnRingSampleIsSpreadEvenlyOverTheSquareRing() {
        SummonRing ring = new SummonRing(0, 100);
        Random random = new Random(3);
        int corner = 0;
        int edge = 0;
        for (int i = 0; i < 20000; i++) {
            RingMath.SampledColumn column = RingMath.sampleColumn(0, 0, ring, random);
            assertTrue(RingMath.inRing(0, 0, column.x(), column.z(), ring), column.toString());
            if (column.x() >= 60 && column.z() >= 60) {
                corner++;
            }
            if (column.x() >= 60 && column.z() >= -20 && column.z() <= 20) {
                edge++;
            }
        }

        assertTrue(Math.abs(corner - 832) <= 125, "corner=" + corner);
        assertTrue(Math.abs(edge - 832) <= 125, "edge=" + edge);
    }

    @Test
    void aSpawnRingSampleNeverFallsInsideTheMinimum() {
        SummonRing ring = new SummonRing(30, 40);
        Random random = new Random(5);
        for (int i = 0; i < 5000; i++) {
            RingMath.SampledColumn column = RingMath.sampleColumn(7, -3, ring, random);
            assertTrue(RingMath.inRing(7, -3, column.x(), column.z(), ring), column.toString());
        }
    }
}
