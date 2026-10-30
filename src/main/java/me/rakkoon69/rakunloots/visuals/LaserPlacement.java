package me.rakkoon69.rakunloots.visuals;

import me.rakkoon69.rakunloots.server.BlockPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a laser puts its entities. GuardianBeam draws a beam with client side entities. The entity starts the loot's
 * start offset above the bottom of the box's block, and the beam runs from it up to the world's build limit. The client
 * picks the crystal with its mouse ray like a real entity, so a crystal near the box takes a player's right click and
 * break away from the block; it is wider than a block and needs 4, three blocks above the box's top face, to be out of
 * a player's reach. A player cannot pick the guardian or its squid, so a guardian laser can start anywhere.
 */
final class LaserPlacement {

    /**
     * The lowest start offset that keeps a crystal's hitbox out of a player's reach of the box: three blocks above the
     * box's top face.
     */
    static final int CRYSTAL_CLEAR_OFFSET = 4;
    private static final double BLOCK_CENTRE = 0.5;
    /**
     * GuardianBeam spawns a guardian half a block below the start it is given.
     */
    private static final double GUARDIAN_SPAWN_DROP = 0.5;
    private static final double CRYSTAL_SIZE = 2.0;

    private LaserPlacement() {
    }

    /**
     * A point in a world.
     */
    record Point(double x, double y, double z) {
    }

    /**
     * The start and the end a laser is given.
     */
    record Placed(Point start, Point end) {
    }

    /**
     * The box a client picks an entity by, from its lowest corner and its width and height.
     */
    record Hitbox(double minX, double minY, double minZ, double width, double height) {

        boolean intersects(double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY,
                           double otherMaxZ) {
            return minX < otherMaxX && minX + width > otherMinX
                    && minY < otherMaxY && minY + height > otherMinY
                    && minZ < otherMaxZ && minZ + width > otherMinZ;
        }
    }

    /**
     * The start and end for a laser over a box.
     *
     * @param startOffset    blocks above the bottom of the box's block where the entity sits
     * @param worldMaxHeight the world's build limit
     */
    static Placed of(LaserType type, BlockPosition box, int startOffset, int worldMaxHeight) {
        double x = box.x() + BLOCK_CENTRE;
        double z = box.z() + BLOCK_CENTRE;
        double top = worldMaxHeight;
        if (type == LaserType.GUARDIAN) {
            double guardianStart = box.y() + startOffset + GUARDIAN_SPAWN_DROP;
            return new Placed(new Point(x, guardianStart, z), new Point(x, top, z));
        }
        // The crystal stays close enough for the client to draw, and its beam goes up to the top end.
        double crystalStart = box.y() + startOffset;
        return new Placed(new Point(x, crystalStart, z), new Point(x, top, z));
    }

    /**
     * The hitboxes of every entity the laser puts in the world that the client picks: the crystal's, and none for a
     * guardian laser.
     */
    static List<Hitbox> hitboxes(LaserType type, Placed placed) {
        List<Hitbox> boxes = new ArrayList<>();
        if (type == LaserType.CRYSTAL) {
            boxes.add(centred(placed.start().x(), placed.start().y(), placed.start().z(), CRYSTAL_SIZE));
        }
        return boxes;
    }

    private static Hitbox centred(double x, double feetY, double z, double size) {
        return new Hitbox(x - size / 2, feetY, z - size / 2, size, size);
    }
}
