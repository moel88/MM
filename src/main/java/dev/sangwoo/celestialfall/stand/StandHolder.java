package dev.sangwoo.celestialfall.stand;

/** A server-owned presentation value, replicated by vanilla entity tracking. */
public interface StandHolder {
    String botand$getStand();
    void botand$setStand(String stand);
    /** Whether the stand is out beside its user (Star Platinum's Q, 2.51.0); every player in sight sees it. */
    boolean botand$isStandOut();
    void botand$setStandOut(boolean out);
}
