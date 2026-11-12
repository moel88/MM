package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's R with the hack on, the double (2.125.0, user request: "Q활성화 R스킬을 새로 만들자 효과음은 영상을 참고해 2번
 * 재생할거야 R스킬은 영상을 보면G스킬처럼 누르면 위에 !!!가 뜨고 뒤에 벽을 소환하고 옆에 적이랑 똑같은 분신인데 얼굴에
 * X자가 쳐져있는 분신이 적을 날리는 모션을 취하고 적이 벽으로 날아가 박히는 스킬이야", and the user's video): its timing
 * and its numbers, on the server's ticks from the press (the video's moments from its press, at 20 ticks a second). The
 * user's answers to what I read of the video: all of it right but the wall, which is made of the block under it ("다
 * 맞는데 벽은 아래 있는 블록에 따라 바뀜"); a guard during the "!!!" blocks it as G's ("G처럼 가드로 막힘"); 20 damage and
 * 20 seconds ("피해 20 · 쿨타임 20초"); held in the wall 1.5 seconds ("1.5초").
 * <p>
 * The press marks the one looked at (as G's, XCharaRules.AIM_RANGE) and the "!!!" shows over them from {@link #MARK};
 * a double of them, an X on its face, comes beside its user at {@link #CLONE} and winds up. Any tick before
 * {@link #THROW} that the one marked holds a guard up toward its user, it is blocked. At THROW the double throws (it
 * touches no one): the one marked is seized where they stand, lifted and turned over in the air ({@link #lift},
 * {@link #tumble}), and the double breaks up. At {@link #FLING} they are flung the way its user faced them into a wall
 * raised behind them out of the ground there ({@link #WALL} blocks behind at most), and at {@link #IMPACT} they are in
 * it: {@link #DAMAGE}, and held in it {@link #STUCK} ticks, until {@link #FREE}, when the wall lets them go and it is
 * over. Its user is held where they stand until {@link #RELEASE}.
 */
public final class CloneRules {
    private CloneRules(){}
    /** Its casts' place among the effects' skills (KagenoSkills: Kageno's 0 to 9, the knife's 13, G's 14, Lee So-sang's 15). */
    public static final int SKILL=12;
    /** The "!!!" over the one marked from this tick (the video's 0.05 s); the double beside its user from this one (its first sound: 0.15 s). */
    public static final int MARK=1,CLONE=3;
    /** The double's throw (the video's 0.87 s, its second sound): the "!!!" gone, the one marked seized and lifted. Until then a guard blocks it. */
    public static final int THROW=17;
    /** Its user is held where they stand until this tick (the press of the panel, and watching the double throw). */
    public static final int RELEASE=24;
    /** The double gone by this tick (the video: broken up into pixels 1.1 to 1.5 s). */
    public static final int CLONE_GONE=30;
    /** The one seized flung at the wall, and in it (the video's two uses: flung 1.85 and 2.2 s, in the wall a tenth of a second later). */
    public static final int FLING=37,IMPACT=40;
    /** How long they are held in the wall (the user's answer: "1.5초"), and so when it lets them go: it is over. */
    public static final int STUCK=30,FREE=IMPACT+STUCK,END=FREE;
    /** What the wall deals (the user's answer). */
    public static final float DAMAGE=20;
    /** Its cooldown (the user's answer: 20 seconds): from its end, or from when it was blocked or called off. */
    public static final int COOLDOWN=400;
    /** How far behind the one seized the wall is raised at most (blocks; nearer if something stands in the way). */
    public static final double WALL=8;
    /** How high the one seized is lifted (their feet off where they stood, blocks), and how long the lift takes (ticks). */
    public static final double LIFT=1.5;
    public static final float LIFT_TICKS=7;
    /** In the wall: their feet this far off the ground, their body this far into it past where they stand after (blocks), leaning back this much (degrees). */
    public static final double STUCK_UP=.55,SINK=.45;
    public static final float LEAN=18;
    /** Let go, they drop to their feet over this long (ticks). */
    public static final float DROP=5;

    private static float smooth(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** Whether a guard can still block it at {@code age} ticks (the "!!!" before the throw). */
    public static boolean warning(long age){return age>=0&&age<THROW;}
    /** Whether its user is held at {@code age} ticks. */
    public static boolean userHeld(long age){return age>=0&&age<RELEASE;}
    /** Whether the one seized is held by it at {@code age} ticks: from the throw until the wall lets them go. */
    public static boolean held(long age){return age>=THROW&&age<FREE;}
    /** Whether the one seized is in the air at {@code age} ticks (nothing else reaches them there): from the throw until the wall. */
    public static boolean aloft(long age){return age>=THROW&&age<IMPACT;}
    /** When it is ready again after one that ended (or was blocked or called off) at {@code end}. */
    public static long ready(long end){return end+COOLDOWN;}

    /**
     * How high the one seized is off where they stood at {@code t} ticks, while they hang there (THROW to FLING): jerked
     * up fast, a little past LIFT and back, then bobbing a little.
     */
    public static double lift(float t){
        if(t<=THROW)return 0;
        float u=Math.min(1,(t-THROW)/LIFT_TICKS),back=1.6f;
        // (out fast, a little over and back)
        double out=1+(back+1)*Math.pow(u-1,3)+back*Math.pow(u-1,2);
        double bob=.07*Math.sin((t-THROW-LIFT_TICKS)*.42)*smooth((t-THROW-LIFT_TICKS)/6f);
        return LIFT*out+(u>=1?bob:0);
    }
    /** How far along the way from where they hang to the wall they are at {@code t} ticks (0 to 1: FLING to IMPACT, quickening). */
    public static double flown(float t){
        if(t<=FLING)return 0;if(t>=IMPACT)return 1;
        return Math.pow((t-FLING)/(float)(IMPACT-FLING),1.5);
    }
    /** How far they have dropped out of the wall at {@code t} ticks (0 in it, 1 on their feet: FREE to FREE+DROP). */
    public static float dropped(float t){return smooth((t-FREE)/DROP);}
    /**
     * How far over backwards the one seized is turned at {@code t} ticks (degrees, about their middle): once round as
     * they hang and are flung, into the wall leaning back LEAN; upright again as they drop out of it.
     */
    public static float tumble(float t){
        if(t<=THROW)return 0;
        if(t<IMPACT)return (360+LEAN)*(float)Math.pow((t-THROW)/(float)(IMPACT-THROW),1.25);
        return LEAN*(1-dropped(t));
    }
    /**
     * Where the one seized is drawn at {@code t} ticks, as {right-of-way 0, up, along the way} from where they stood
     * when seized, the wall's foot {@code far} blocks along the way and {@code rise} blocks higher than where they
     * stood: hanging, flung in a straight line, in the wall, dropping to its foot. Null before the throw and once they
     * are down.
     */
    public static double[] drawn(float t,double far,double rise){
        if(t<THROW||t>=FREE+DROP)return null;
        double hung=lift(Math.min(t,FLING));
        if(t<FLING)return new double[]{0,hung,0};
        double u=flown(t),down=dropped(t);
        double up=rise+STUCK_UP*(1-down),along=far+SINK*(1-down);
        return new double[]{0,hung+(up-hung)*u,along*u};
    }
}
