package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.config.LaserType;
import me.rakkoon69.rakunloots.config.VisualsSettings;
import me.rakkoon69.rakunloots.server.BlockPosition;
import me.rakkoon69.rakunloots.visuals.LaserPlacement.Hitbox;
import me.rakkoon69.rakunloots.visuals.LaserPlacement.Placed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where each laser puts its entities, as the client picks them. The start offset counts blocks up from the bottom of the
 * box's block, and the beam runs to the world's build limit.
 */
class LaserPlacementTest {

    private static final BlockPosition BOX = new BlockPosition(10, 64, -20);
    private static final int MAX_HEIGHT = 320;
    /**
     * A player's reach to an entity, in blocks.
     */
    private static final double ENTITY_REACH = 3;

    /**
     * Whether any hitbox of the laser touches the box's block grown by the entity reach on every side: the space a
     * player clicking the box, or the block under it, can aim through.
     */
    private static boolean hitboxNearBox(LaserType type, int startOffset) {
        Placed placed = LaserPlacement.of(type, BOX, startOffset, MAX_HEIGHT);
        for (Hitbox hitbox : LaserPlacement.hitboxes(type, placed)) {
            if (hitbox.intersects(BOX.x() - ENTITY_REACH, BOX.y() - ENTITY_REACH, BOX.z() - ENTITY_REACH,
                    BOX.x() + 1 + ENTITY_REACH, BOX.y() + 1 + ENTITY_REACH, BOX.z() + 1 + ENTITY_REACH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Acceptance: at the default offset of 0 the guardian starts at the bottom of the box's block and the beam ends at
     * the build limit. A player cannot pick GuardianBeam's guardian or squid, as the phase 8 smoke test showed, so the
     * guardian laser has no hitbox at any offset.
     */
    @Test
    void atOffsetZeroTheGuardianStartsAtTheBottomOfTheBoxAndHasNoHitbox() {
        Placed placed = LaserPlacement.of(LaserType.GUARDIAN, BOX, VisualsSettings.DEFAULT_LASER_START_OFFSET,
                MAX_HEIGHT);

        assertEquals(0, VisualsSettings.DEFAULT_LASER_START_OFFSET);
        assertEquals(new LaserPlacement.Point(10.5, 64 + 0.5, -19.5), placed.start());
        assertEquals(MAX_HEIGHT, placed.end().y());
        assertEquals(List.of(), LaserPlacement.hitboxes(LaserType.GUARDIAN, placed));
    }

    /**
     * Acceptance: the crystal sits on the start offset and its beam goes up to the build limit. At 4, three blocks
     * above the box's top face, its 2 by 2 hitbox is out of a player's reach of the box.
     */
    @Test
    void theCrystalSitsOnTheStartOffsetAndAtFourIsClearOfTheBox() {
        Placed placed = LaserPlacement.of(LaserType.CRYSTAL, BOX, 4, MAX_HEIGHT);

        assertFalse(hitboxNearBox(LaserType.CRYSTAL, 4));
        assertEquals(new LaserPlacement.Point(10.5, 64 + 4, -19.5), placed.start());
        assertEquals(new LaserPlacement.Point(10.5, MAX_HEIGHT, -19.5), placed.end());
    }

    /**
     * Acceptance: the start offset is the blocks above the bottom of the box's block where each laser's entity starts,
     * and the beam's top end does not move.
     */
    @Test
    void theStartOffsetIsTheBlocksAboveTheBottomOfTheBoxWhereEachLaserStartsItsEntity() {
        Placed crystal = LaserPlacement.of(LaserType.CRYSTAL, BOX, 10, MAX_HEIGHT);
        Placed guardian = LaserPlacement.of(LaserType.GUARDIAN, BOX, 10, MAX_HEIGHT);

        assertEquals(new LaserPlacement.Point(10.5, 64 + 10, -19.5), crystal.start());
        assertEquals(new LaserPlacement.Point(10.5, 64 + 10 + 0.5, -19.5), guardian.start());
        assertEquals(MAX_HEIGHT, crystal.end().y());
        assertEquals(MAX_HEIGHT, guardian.end().y());
        assertEquals(64 + 10, LaserPlacement.hitboxes(LaserType.CRYSTAL, crystal).get(0).minY());
    }

    /**
     * The crystal's hitbox is wider than a block, so at offset 0 it covers the box and takes clicks meant for it.
     */
    @Test
    void aCrystalAtOffsetZeroCoversTheBox() {
        Placed placed = LaserPlacement.of(LaserType.CRYSTAL, BOX, 0, MAX_HEIGHT);

        assertTrue(LaserPlacement.hitboxes(LaserType.CRYSTAL, placed).stream().anyMatch(hitbox -> hitbox.intersects(
                BOX.x(), BOX.y(), BOX.z(), BOX.x() + 1, BOX.y() + 1, BOX.z() + 1)));
    }
}
