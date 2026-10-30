package me.rakkoon69.rakunloots.hologram.packet;

import me.rakkoon69.rakunloots.hologram.HologramPosition;

import java.util.List;
import java.util.UUID;

/**
 * Builds and sends the fake entity packets of a hologram for one server version. One implementation exists per NMS
 * revision, each in its own subproject, and {@link PacketAdapters} loads the one this server needs by class name.
 * Below 1.19.4 a hologram is one fake armor stand per line: invisible, marker, no gravity, small, custom name
 * visible, the lowest line at the position and each line above the one after it. From 1.19.4 it is one fake text
 * display with the lines joined by new lines and billboard set to center, its bottom at the position.
 *
 * <p>Packets are opaque: the viewer tracker keeps what a build returned and hands it back to {@link #send}, so no
 * server class reaches the core. A built packet may go to many players. Lines are legacy colour coded ({@code §},
 * hex as {@code §x§r§r§g§g§b§b}), top line first, never an empty list; the adapter turns each into its version's
 * text component. Every call is safe on any thread and returns quickly.
 *
 * <p>An implementation has a public constructor with no parameters.
 */
public interface PacketAdapter {

    /**
     * How many fake entities a hologram of that many lines is: the line count for armor stands, 1 for a text display.
     *
     * @param lineCount at least 1
     */
    int entityCount(int lineCount);

    /**
     * A fresh entity id from the server's own entity id counter, so it never clashes with a real entity.
     */
    int nextEntityId();

    /**
     * The packets that make a hologram appear to a player: each entity's spawn and its meta.
     *
     * @param entityIds {@link #entityCount} ids for these lines, lowest line's entity first
     * @param position  where the lowest line sits
     * @param lines     top line first
     * @throws IllegalArgumentException when the id count does not fit the lines
     */
    Object spawn(List<Integer> entityIds, HologramPosition position, List<String> lines);

    /**
     * The meta packets that show new lines on a hologram a player already sees, with as many lines as its entities
     * hold.
     *
     * @param entityIds the ids the hologram was spawned with
     * @param lines     top line first
     * @throws IllegalArgumentException when the id count does not fit the lines
     */
    Object meta(List<Integer> entityIds, List<String> lines);

    /**
     * The packet that takes a hologram's entities away from a player.
     *
     * @param entityIds the ids the hologram was spawned with
     */
    Object destroy(List<Integer> entityIds);

    /**
     * Sends a packet this adapter built to one player. Does nothing when the player is not online.
     *
     * @param playerId the player's UUID
     * @throws IllegalArgumentException when this adapter did not build the packet
     */
    void send(UUID playerId, Object packet);
}
