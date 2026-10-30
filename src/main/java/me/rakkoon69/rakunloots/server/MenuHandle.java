package me.rakkoon69.rakunloots.server;

import java.util.UUID;

/**
 * One menu inventory made by a server adapter. The handle is the identity the menu service uses to tell a close
 * event for an old menu from one for its replacement.
 */
public interface MenuHandle {

    /**
     * The player the menu was opened for.
     */
    UUID playerId();
}
