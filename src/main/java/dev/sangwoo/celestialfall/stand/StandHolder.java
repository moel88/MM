package dev.sangwoo.celestialfall.stand;

/** A server-owned presentation value, replicated by vanilla entity tracking. */
public interface StandHolder {
    String botand$getStand();
    void botand$setStand(String stand);
    /** Whether the stand is out beside its user (Star Platinum's Q, 2.51.0); every player in sight sees it. */
    boolean botand$isStandOut();
    /** Whether its last coming out or going back was its user's own call (Q, or a boss's call; 2.72.0: it sounds then). */
    boolean botand$standCalled();
    /** Out or in, not by its user's call (the arrow's awakening, its user no longer able to have it out): no sound. */
    void botand$setStandOut(boolean out);
    /** Out or in at its user's call (Q): every client in sight hears it come out or go back (client/stand/StandCall). */
    void botand$callStand(boolean out);
}
