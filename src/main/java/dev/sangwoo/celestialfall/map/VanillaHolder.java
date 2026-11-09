package dev.sangwoo.celestialfall.map;

/**
 * A player's vanilla mode as every client sees it (2.110.0, VanillaMode): a bit of the stand's tracked byte
 * (PlayerStandMixin), so an older client, which knows only the stand's bits, is not thrown by it.
 */
public interface VanillaHolder {
    /** Whether this player plays as plain Minecraft now (VanillaMode): set by the server, read by every client. */
    boolean botand$isVanilla();
    void botand$setVanilla(boolean on);
}
