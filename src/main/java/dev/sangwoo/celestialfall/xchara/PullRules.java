package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's Y with the hack on, the pull (2.127.0, user request: "이번에는 q활성화 Y스킬 효과음은 영상에 있고 영 상 보면
 * 상대에게 !!!가 뜨고 상대가 내 앞으로 순간이동 돼", with a screen recording, 2.27 s: the game's own label for it reads
 * "Y - ACT = [TELEPORT]"): its timing and its numbers, on the server's ticks from the press. What I read in the video
 * and showed the user on its frames ("맞음, 이대로"): the "!!!" over the one looked at from 0.17 s to 0.43 s of it, blinking
 * red and white; at 0.43 s they are gone from where they stood and stand right before its user, about a body and a
 * half away; for half a second after, their body flickers between a flat purple silhouette and purple bars; no harm is
 * done. The user's answers to what I asked: a guard during the "!!!" blocks it as G's, R's and T's ("G·R·T처럼 가드로
 * 막힘"); its cooldown 20 seconds ("20초"); nothing more comes to the one pulled ("아무것도 없음": they may move and guard
 * at once). The name, the pull, is mine.
 * <p>
 * The press marks the one looked at (as G's, XCharaRules.AIM_RANGE) and the "!!!" shows over them from {@link #MARK};
 * any tick before {@link #PULL} that they hold a guard up toward its user, it is blocked. At PULL they are put
 * {@link #AHEAD} blocks before its user, on the line from its user to where they stood (nearer if a block is in the
 * way), still facing as they faced. Its user is held where they stand until PULL. Its cooldown ({@link #COOLDOWN})
 * runs from then, or from when it was blocked or called off.
 */
public final class PullRules {
    private PullRules(){}
    /**
     * The "!!!" over the one marked from this tick; they are pulled at this one (until then a guard blocks it, and its
     * user is held). By the video's sound, which runs 0.24 s behind its picture (the pull's burst comes on sharply at
     * 0.665 s of it, as the one marked is put before its user at 0.43 s of the picture): the press, where the "!!!"
     * warning starts, is 0.10 s into the picture, the "!!!" shows 0.07 s after it and the pull is 0.33 s after it.
     */
    public static final int MARK=1,PULL=7;
    /** How far before its user's feet the one pulled is put (blocks: the video's body and a half). */
    public static final double AHEAD=1.5;
    /** Its cooldown (the user's answer: 20 seconds): from the pull, or from when it was blocked or called off. */
    public static final int COOLDOWN=400;
    /** For how long after the pull the one pulled flickers, on every client (ticks: the video's half second). */
    public static final int GLITCH=10;

    /** Whether a guard can still block it at {@code age} ticks (before the pull). */
    public static boolean warning(long age){return age>=0&&age<PULL;}
    /** Whether its user is held at {@code age} ticks: until the pull (they stand looking at the one marked). */
    public static boolean userHeld(long age){return age>=0&&age<PULL;}
    /** When it is ready again after one that pulled (or was blocked or called off) at {@code end}. */
    public static long ready(long end){return end+COOLDOWN;}
    /**
     * The way from its user at ({@code ux},{@code uz}) to the one marked at ({@code tx},{@code tz}), level, as a unit
     * {x, z}: "before me" is toward where they stood. If they stand on its user, the way its user faces ({@code yaw},
     * degrees).
     */
    public static double[] way(double ux,double uz,double tx,double tz,float yaw){
        double dx=tx-ux,dz=tz-uz,l=Math.sqrt(dx*dx+dz*dz);
        if(l<1e-3){double r=Math.toRadians(yaw);return new double[]{-Math.sin(r),Math.cos(r)};}
        return new double[]{dx/l,dz/l};
    }
    /** How far before its user the one pulled is put when the way is clear for {@code clear} blocks (the nearest block in the way, less their half width; AHEAD if nothing is): AHEAD at most, never behind its user. */
    public static double reach(double clear){return Math.max(0,Math.min(AHEAD,clear));}
}
