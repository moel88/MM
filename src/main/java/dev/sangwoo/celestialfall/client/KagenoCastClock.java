package dev.sangwoo.celestialfall.client;

/** A packet establishes the origin once. Later snapshots cannot rewind a running animation. */
public record KagenoCastClock(long serverAge,long localTick) {
    public float age(long tick,float fraction){return Math.max(0,serverAge+tick-localTick)+Math.max(0,Math.min(1,fraction));}
}
