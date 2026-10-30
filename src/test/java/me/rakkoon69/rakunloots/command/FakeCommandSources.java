package me.rakkoon69.rakunloots.command;

import java.util.Collection;
import java.util.List;

/**
 * Fixed names. Steve and Steven are known, and only Steve is online.
 */
final class FakeCommandSources implements CommandSources {

    @Override
    public Collection<String> lootIds() {
        return List.of("airdrops", "airdrop", "airdrop1", "airdrop2", "airdrop3", "vote_crate");
    }

    @Override
    public Collection<String> worldNames() {
        return List.of("world", "world_nether", "world_the_end", "creative");
    }

    /**
     * The world the lifecycle tests build their boxes in.
     */
    static final java.util.UUID WORLD_KEY = java.util.UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    /**
     * A second loaded world that holds no box. The other names are not loaded.
     */
    static final java.util.UUID CREATIVE_KEY = java.util.UUID.fromString("00000000-0000-0000-0000-0000000c0c0c");

    @Override
    public java.util.Optional<java.util.UUID> worldKey(String worldName) {
        return switch (worldName) {
            case "world" -> java.util.Optional.of(WORLD_KEY);
            case "creative" -> java.util.Optional.of(CREATIVE_KEY);
            default -> java.util.Optional.empty();
        };
    }

    static final java.util.UUID STEVE_ID = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final java.util.UUID STEVEN_ID = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final java.util.UUID ALEX_ID = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    @Override
    public java.util.Optional<java.util.UUID> playerId(String playerName) {
        return switch (playerName) {
            case "Steve" -> java.util.Optional.of(STEVE_ID);
            case "Steven" -> java.util.Optional.of(STEVEN_ID);
            case "Alex" -> java.util.Optional.of(ALEX_ID);
            default -> java.util.Optional.empty();
        };
    }

    @Override
    public Collection<String> playerNames() {
        return List.of("Steve", "Steven", "Alex");
    }

    @Override
    public Collection<String> onlinePlayerNames() {
        return List.of("Steve", "Alex");
    }
}
