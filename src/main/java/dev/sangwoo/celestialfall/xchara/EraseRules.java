package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's H with their hack off, the delete slash (삭제 베기, 2.133.0). The user asked first: "1번으로 하되 조금만
 * 바꾸자 5칸 정도 뛰어올라서 거대한 참격을 바라보는 방향으로 날리고 참격을 맞은 블록은 픽셀로 산산이 부서지고 거기에 있던
 * 플레이어는 0.5초 동안 클리치로 사라졌다 나타나게" (they chose "피해 100 쿨타임 60초" and "못 움직이고 맞지도 않음"). Its first
 * build (a slash going along the ground like a wave, block by block breaking into pixels) was not what they meant: "내가
 * 생각했던건 일단 공중에 떠올라서 참격을 세로로 날리면 바닥에 맞은 부분만 삭제되게 … 한 번 날리고 그냥 바닥에 박히고 삭제되게
 * 그리고 삭제된 부분은 한덩어리로 글리치 … 10 × 10으로 범위를 줄여", "참격이 맞은 땅은 덩어리째 떠오르다가 글리치 폭발되게
 * 그리고 폭발되고 10초 이따가 땅이 다시 돌아오게", "올라가는 덩어리는 10×10×10 정육면체로 하고 터질때까지 그 덩어리 위에
 * 있으면 다시 150데미지 들어오게 떠올라서 터지기까지 시간은 15초 정도 시간 안에 덩어리에서 떨어지면 데미지 없음 그래서
 * 참격까지 맞고 덩어리 폭발 데미지까지 총 250데미지야" (they chose where it lands "십자선이 가리키는 땅", the ground while it
 * is gone "보이기만", the slash's plane "내려찍는 면 그대로"). A skill of the knife's pipeline (XCharaCombo.ERASE,
 * XCharaAttack.erase):
 * <ol>
 * <li>the knife lights up as the hack's does ({@link #LIT}); they crouch, and at {@link #LEAVE} leap straight up
 * {@link #JUMP} blocks (less under a roof: the server's word), slowing to the top by {@link #RISEN}, the knife wound over
 * the head; meanwhile their client tells where the middle of its screen points (as the slam's aim);</li>
 * <li>at {@link #SLASH} (its one "blow") they bring it down: an upright slash (in the plane the knife comes down in) flies
 * from the knife to the ground the middle of their screen pointed at (no further than {@link #REACH} blocks out from
 * where they stood; the sky, as far out the way they looked; XCharaErase), {@link #flight} ticks, and stands in it;</li>
 * <li>where it stands in the ground, the {@link #SIDE} by {@link #SIDE} blocks round it ({@link #corner}: the block
 * corner nearest it its middle) is deleted: everyone on it is struck for {@link #DAMAGE} (no guard takes it, no
 * knockback) and deleted for {@link #VANISH} ticks (gone from sight, nothing harms them and they can do nothing);</li>
 * <li>the ground there, a cube {@link #SIDE} blocks each way, rises out of it ({@link #lift}: from {@link #RISE_FROM}
 * over {@link #RISE_TICKS} to {@link #LIFT} blocks up, standing on the air a block over the ground), carrying whoever is
 * on it, glitching as one; {@link #BURST} after it was struck it bursts in a glitch: whoever is still on it is struck
 * for {@link #BURST_DAMAGE} (all told {@link #DAMAGE} and that, the user's 250); off it before then, nothing. Falls off
 * it do no harm. Where it was is a void from then on (only a picture: the ground is there to walk on) until
 * {@link #BACK}, when the ground is drawn back;</li>
 * <li>they fall back to where they stood, landing at {@link #LAND}; they are free at {@link #HOLD}, and at {@link #LENGTH}
 * it is over: its cooldown ({@link #COOLDOWN}, the user's 60 s) runs from then.</li>
 * </ol>
 * The leap is drawn only ({@link #risen}: every client draws the body up there, over where it really stands): in the air
 * nothing harms them (what bypasses invulnerability aside). Before they leave the ground, struck or held, it stops short
 * and its cooldown runs from then; once the slash has gone it goes on whatever its user does, unless they are gone (dead,
 * left, elsewhere, X Chara's no more): then the slash and the cube are gone. The cube is no block: the server and the
 * clients stand bodies on it and keep them from walking into it (EraseCubes); the map is not changed. Ticks of 1/20 s.
 */
public final class EraseRules {
    private EraseRules(){}
    /** Its slash's place among the effects' skills on the clients (KagenoSkills): a cast of its own for each (EraseClient). */
    public static final int SKILL=16;
    /** When the knife lights, when they leave the ground, when they are as high as they go, the slash, when they begin to fall, when they land, when they are free, and when it is over. */
    public static final int LIT=1,LEAVE=4,RISEN=12,SLASH=16,FALL=17,LAND=25,HOLD=29,LENGTH=36;
    /** How high they leap over where they stood (blocks; less under a roof): the user's "5칸 정도". */
    public static final double JUMP=5;
    /** From when their client tells where the middle of its screen points (ticks from the press: as they leave the ground, a little before). */
    public static final int AIM_FROM=LEAVE-2;
    /** How far out from where they stood it lands at most (blocks, level), and how far under their feet the ground it lands on may be. */
    public static final double REACH=20,FLOOR_UNDER=16;
    /** Where the slash leaves the knife: this far before them, this high over their feet up in the air (blocks). */
    public static final double KNIFE_AHEAD=1.0,KNIFE_UP=1.4;
    /** How fast the slash flies (blocks a tick), and its flight's fewest and most ticks. */
    public static final double FLY_SPEED=3.5;
    public static final int FLY_MIN=2,FLY_MAX=7;
    /** The ground it deletes, and the cube that rises: this many blocks each way (the user's 10 × 10 × 10). */
    public static final int SIDE=10;
    /** What it deals everyone on that ground (the user's 100), how long each is deleted for (ticks: the user's 0.5 s), what the burst deals whoever is on the cube then (the user's 150), and its cooldown from its end (ticks: the user's 60 s). */
    public static final float DAMAGE=100,BURST_DAMAGE=150;
    public static final int VANISH=10,COOLDOWN=1200;
    /**
     * The cube, in ticks after the slash struck: it begins to rise as those it deleted are back ({@link #RISE_FROM}),
     * rises over {@link #RISE_TICKS} (6 s, gently in and out: at most 0.14 blocks a tick, so the clients' cube and the
     * server's stay together) to {@link #LIFT} blocks up (wholly out of the ground and a block over it), and bursts at
     * {@link #BURST} (the user's 15 s from when it rises); the ground is back at {@link #BACK} (the user's 10 s after).
     */
    public static final int RISE_FROM=VANISH,RISE_TICKS=120,BURST=RISE_FROM+300,BACK=BURST+200;
    public static final double LIFT=11;
    /** Whom the slash strikes: on its ground (their box over it), their feet from this far under it to this far over it (blocks). */
    public static final double STRUCK_UNDER=1.5,STRUCK_OVER=4;
    /** Who is on the cube: over it (their box over it), their feet from this far under its top to this far over it (a jump up from it). */
    public static final double ON_UNDER=.6,ON_OVER=3;
    /** Who stands on it, carried up as it rises: their feet within this of its top (blocks). */
    public static final double STANDING=.12;

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
    /** How many ticks the slash flies to land {@code distance} blocks from the knife. */
    public static int flight(double distance){
        int t=(int)Math.ceil(Math.max(0,distance)/FLY_SPEED);
        return Math.max(FLY_MIN,Math.min(FLY_MAX,t));
    }
    /** The point ({x, z}) {@code (x, z)} brought within REACH of where they stood {@code (fromX, fromZ)}, level (the way it lies from there kept). */
    public static double[] within(double fromX,double fromZ,double x,double z){
        double dx=x-fromX,dz=z-fromZ,d=Math.hypot(dx,dz);
        if(!(d>REACH))return new double[]{x,z};
        return new double[]{fromX+dx/d*REACH,fromZ+dz/d*REACH};
    }
    /** The west (or north) edge of the ground it deletes round {@code x} (or z): the block corner nearest it is its middle. */
    public static int corner(double x){return (int)Math.round(x)-SIDE/2;}
    /** Whether a box from {@code minX} to {@code maxX} and {@code minZ} to {@code maxZ} is over the ground whose corner is ({@code x0}, {@code z0}). */
    public static boolean over(int x0,int z0,double minX,double maxX,double minZ,double maxZ){
        return maxX>x0&&minX<x0+SIDE&&maxZ>z0&&minZ<z0+SIDE;
    }
    /** Whether feet {@code feet} high over the ground's top {@code top} (where it struck) are struck by it. */
    public static boolean struck(double feet,double top){return feet>=top-STRUCK_UNDER&&feet<=top+STRUCK_OVER;}
    /**
     * How far the cube is up {@code t} ticks after the slash struck (blocks): none until RISE_FROM, then gently rising
     * over RISE_TICKS to LIFT, held there until it bursts. NaN once it has burst (there is none).
     */
    public static double lift(double t){
        if(t>=BURST)return Double.NaN;
        double u=clamp01((t-RISE_FROM)/RISE_TICKS);
        return LIFT*u*u*(3-2*u);
    }
    /** Whether there is a cube {@code t} ticks after the slash struck (from then until it bursts). */
    public static boolean cube(double t){return t>=0&&t<BURST;}
    /** Whether feet {@code feet} high are on a cube whose top is {@code top} high (over it, a jump up from it at most). */
    public static boolean on(double feet,double top){return feet>=top-ON_UNDER&&feet<=top+ON_OVER;}
    /** Whether feet {@code feet} high stand on a cube whose top is {@code top} high (carried up as it rises). */
    public static boolean standing(double feet,double top){return Math.abs(feet-top)<=STANDING;}
    /** The tick it is ready again after one begun at {@code start}: its cooldown from its end. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
}
