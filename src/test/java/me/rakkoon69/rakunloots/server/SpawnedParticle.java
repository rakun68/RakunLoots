package me.rakkoon69.rakunloots.server;

import org.bukkit.Particle;

/**
 * One particle spawned on a fake world: the particle, where, how many, and for dust its colour and size as
 * {@code red:green:blue:size}, or empty text for no data.
 */
record SpawnedParticle(Particle particle, double x, double y, double z, int count, String data) {

    static SpawnedParticle of(Particle particle, double x, double y, double z, int count, Object data) {
        String text = data instanceof Particle.DustOptions dust
                ? dust.getColor().getRed() + ":" + dust.getColor().getGreen() + ":" + dust.getColor().getBlue() + ":"
                + dust.getSize()
                : data == null ? "" : data.toString();
        return new SpawnedParticle(particle, x, y, z, count, text);
    }
}
