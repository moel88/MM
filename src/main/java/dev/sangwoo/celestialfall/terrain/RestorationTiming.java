package dev.sangwoo.celestialfall.terrain;

/** World ticks, independent of time-of-day and client frame rate. */
public final class RestorationTiming {
    public static final int WAIT = 200, FADE = 20;
    private RestorationTiming() {}
    public static double alpha(double now, long solidAt) {
        double t = Math.max(0, Math.min(1, (now - (solidAt - FADE)) / FADE));
        return t * t * (3 - 2 * t);
    }
    public static long solidAt(long destroyedAt, long heldUntil) {
        return Math.max(destroyedAt, heldUntil) + WAIT + FADE;
    }
    public static int depth(double distanceSquared, double radius, int maxDepth) {
        return Math.max(1, (int)Math.ceil(maxDepth * Math.max(0, 1 - distanceSquared / (radius * radius))));
    }
}
