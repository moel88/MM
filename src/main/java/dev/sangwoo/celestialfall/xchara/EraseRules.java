package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's H with their hack off, the delete slash (삭제 베기, 2.133.0; user request: "1번으로 하되 조금만 바꾸자 5칸
 * 정도 뛰어올라서 거대한 참격을 바라보는 방향으로 날리고 참격을 맞은 블록은 픽셀로 산산이 부서지고 거기에 있던 플레이어는
 * 0.5초 동안 클리치로 사라졌다 나타나게"; they chose its damage and cooldown "피해 100 쿨타임 60초", its way "보는 쪽으로
 * 땅을 따라", its size "폭 16블록 · 40블록까지", and whom it deletes for the half second "못 움직이고 맞지도 않음"). It
 * took H's place from the rain of knives (2.132.0 to 2.132.5, XCharaRain: gone). A skill of the knife's pipeline
 * (XCharaCombo.ERASE, XCharaAttack.erase):
 * <ol>
 * <li>the knife lights up as the hack's does ({@link #LIT}); they crouch, and at {@link #LEAVE} leap straight up
 * {@link #JUMP} blocks (less under a roof: the server's word), slowing to the top by {@link #RISEN}, the knife raised over
 * the head;</li>
 * <li>at {@link #SLASH} (its one "blow") they bring it down: a giant slash goes along the ground the way they faced as it
 * began (XCharaErase), from {@link #NEAR} ahead of where they stood on to {@link #RANGE} blocks (less where a wall stands
 * across its middle), {@link #SPEED} blocks a tick, {@link #HALF} blocks to either side; everyone it comes over is struck
 * once for {@link #DAMAGE} (no guard takes it, no knockback) and deleted for {@link #VANISH} ticks: gone from sight,
 * nothing harms them and they can do nothing; then they are back where they were;</li>
 * <li>they fall back to where they stood, landing at {@link #LAND}; they are free at {@link #HOLD}, and at {@link #LENGTH}
 * (its slash has gone as far as it goes by then) it is over: its cooldown ({@link #COOLDOWN}, the user's 60 s) runs from
 * then.</li>
 * </ol>
 * The leap is drawn only ({@link #risen}: every client draws the body up there, over where it really stands): in the air
 * nothing harms them (what bypasses invulnerability aside). Before they leave the ground, struck or held, it stops short
 * and its cooldown runs from then; once the slash has gone it goes on whatever its user does, unless they are gone (dead,
 * left, elsewhere, X Chara's no more). The blocks it comes over break into pixels on the clients only (the map is not
 * changed). Ticks of 1/20 s from the press.
 */
public final class EraseRules {
    private EraseRules(){}
    /** Its slash's place among the effects' skills on the clients (KagenoSkills): a cast of its own for each (EraseClient). */
    public static final int SKILL=16;
    /** When the knife lights, when they leave the ground, when they are as high as they go, the slash, when they begin to fall, when they land, when they are free, and when it is over. */
    public static final int LIT=1,LEAVE=4,RISEN=12,SLASH=16,FALL=17,LAND=25,HOLD=29,LENGTH=36;
    /** How high they leap over where they stood (blocks; less under a roof): the user's "5칸 정도". */
    public static final double JUMP=5;
    /**
     * The slash: from how far ahead of where they stood it goes (blocks), how far it goes at most (the user's 40), how
     * fast (blocks a tick: 40 a second), how far to either side of its way it reaches (the user's 16 across), and from how
     * far under to how far over their feet.
     */
    public static final double NEAR=2,RANGE=40,SPEED=2,HALF=8,LOW=-2,HIGH=6;
    /** What it deals each one it comes over (the user's 100), how long each is deleted for (ticks: the user's 0.5 s), and its cooldown from its end (ticks: the user's 60 s). */
    public static final float DAMAGE=100;
    public static final int VANISH=10,COOLDOWN=1200;
    /**
     * A wall across the slash's middle stops it there: a solid block this high and this high over its user's feet (a low
     * step does not), solid {@link #WALL_WIDE} to either side of its middle too (one pillar or a tree's trunk is not a
     * wall: it goes on past it), there or as much as {@link #WALL_DEEP} deeper (a wall standing slantwise to it). It looks
     * past a thing that is not a wall {@link #WALL_STEP} on, at most {@link #WALL_LOOKS} times.
     */
    public static final double WALL_LOW=1.1,WALL_HIGH=2.1,WALL_WIDE=2,WALL_STEP=.8;
    public static final double[] WALL_DEEP={.3,1.3,2.3};
    public static final int WALL_LOOKS=16;

    private static double clamp01(double x){return x<0?0:x>1?1:x;}
    /**
     * How much of the leap they have at {@code age} ticks (0 on the ground, 1 as high as they go): off the ground at LEAVE
     * at speed, slowing to the top by RISEN; there (a little drift up) until FALL; then down, gathering speed as a fall
     * does, to the ground at LAND.
     */
    public static double risen(double age){
        if(age<=LEAVE||age>=LAND)return 0;
        if(age<=RISEN){double u=(age-LEAVE)/(RISEN-LEAVE);return 1-Math.pow(1-u,2.2);}
        if(age<=FALL)return 1;
        double u=(age-FALL)/(LAND-FALL);return 1-u*u;
    }
    /** Whether they are in the air of it {@code age} ticks in (drawn up there, standing where they began): nothing harms them. */
    public static boolean airborne(double age){return age>=LEAVE&&age<LAND;}
    /** How far ahead of where they stood the slash's front is {@code t} ticks after it went (blocks; NaN before), going as far as {@code range}. */
    public static double front(double t,double range){
        if(t<0)return Double.NaN;
        return Math.min(range,NEAR+SPEED*t);
    }
    /** The tick after it went that the slash's front is as far as it goes ({@code range}). */
    public static int reached(double range){return (int)Math.ceil(Math.max(0,range-NEAR)/SPEED);}
    /** The stretch ahead (blocks: {from, to}) the slash's front comes over in tick {@code t} after it went: the first, from its user's feet on. */
    public static double[] sweep(int t,double range){
        if(t<0)return null;
        double to=front(t,range),from=t==0?0:front(t-1,range);
        return to<=from&&t>0?null:new double[]{from,to};
    }
    /**
     * Whether a body {@code halfWidth} across whose feet are {@code forward} ahead of where its user stood, {@code side}
     * to the right and {@code height} up is in the slash's way as its front comes over {@code from} to {@code to}.
     */
    public static boolean struck(double forward,double side,double height,double halfWidth,double from,double to){
        return forward+halfWidth>=from&&forward-halfWidth<=to&&Math.abs(side)<=HALF+halfWidth&&height>=LOW&&height<=HIGH;
    }
    /** The tick it is ready again after one begun at {@code start}: its cooldown from its end. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
}
