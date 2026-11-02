package me.rakkoon69.rakunloots.animation.type;

import me.rakkoon69.rakunloots.animation.AnimationContext;
import me.rakkoon69.rakunloots.animation.AnimationVariables;
import me.rakkoon69.rakunloots.animation.RunningAnimation;
import me.rakkoon69.rakunloots.server.ParticleDraw;
import me.rakkoon69.rakunloots.server.ParticleSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The frame of the sphere animations: {@code count} rings stacked from the top of a sphere to its bottom, each ring
 * a circle of 20 points, around the context's centre. Rings and points are counted by whole numbers, so a frame never
 * gains a ring from rounding.
 */
final class SphereFrame {

    private static final int POINTS_PER_RING = 20;

    private SphereFrame() {
    }

    /**
     * Reads the sphere variables and starts the loop. Shared by every type that draws a sphere.
     *
     * @throws IllegalArgumentException when a variable cannot be used
     */
    static RunningAnimation start(Map<String, Object> variables, AnimationContext context) {
        AnimationVariables read = new AnimationVariables(variables);
        int delay = read.integer("delay", 0, 0);
        int interval = read.integer("interval", 10, 1);
        int loops = read.integer("loops", 26, 1);
        double radius = read.positive("radius", 1.2);
        int count = read.integer("count", 10, 1);
        ParticleSpec particle = read.particle("outer_line");
        return FrameLoop.start(context, delay, interval, loops, points(context, radius, count, particle));
    }

    private static List<ParticleDraw> points(AnimationContext context, double radius, int count, ParticleSpec particle) {
        List<ParticleDraw> points = new ArrayList<>();
        for (int ring = 0; ring < count; ring++) {
            double theta = Math.PI * ring / count;
            double ringY = context.y() + radius * Math.cos(theta);
            double ringRadius = radius * Math.sin(theta);
            for (int step = 0; step < POINTS_PER_RING; step++) {
                double angle = Math.PI * 2 * step / POINTS_PER_RING;
                points.add(new ParticleDraw(context.x() + ringRadius * Math.cos(angle), ringY,
                        context.z() + ringRadius * Math.sin(angle), particle));
            }
        }
        return List.copyOf(points);
    }
}
