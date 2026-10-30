package me.rakkoon69.rakunloots.animation;

import java.util.Map;

/**
 * One kind of animation, such as {@code SPHERE}. A new type is one class that implements this and one
 * {@link AnimationRegistry#register} call; the lifecycle and the animation service never change.
 */
public interface AnimationType {

    /**
     * Starts one play of the animation. The type reads its settings from the {@code variables} of its
     * {@code animations.yml} entry, and draws through the context.
     *
     * @param variables the entry's {@code variables} section; empty when it has none
     * @return the running animation, which the service stops when its moment ends
     * @throws IllegalArgumentException when a variable cannot be used; nothing started
     */
    RunningAnimation start(Map<String, Object> variables, AnimationContext context);
}
