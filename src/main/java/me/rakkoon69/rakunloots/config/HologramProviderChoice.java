package me.rakkoon69.rakunloots.config;

/**
 * The {@code hologram_provider} value in {@code config.yml}: which hologram provider draws, or {@code auto} to pick
 * the first that can run on this server, or {@code none} for no holograms.
 */
public enum HologramProviderChoice {
    AUTO,
    PACKETS,
    DECENTHOLOGRAMS,
    HOLOGRAPHICDISPLAYS,
    FANCYHOLOGRAMS,
    NONE
}
