package dev.sangwoo.celestialfall.xchara;

import java.util.List;

/**
 * X Chara's H with their hack on, the terrain delete (지형 삭제, 2.133.0). It began as the delete slash, H with the hack
 * off (a leap, an upright slash thrown down to the ground they aimed at, that ground deleted and lifted as a cube: the
 * user's "일단 공중에 떠올라서 참격을 세로로 날리면 바닥에 맞은 부분만 삭제되게 … 10 × 10으로 범위를 줄여", "참격이 맞은 땅은
 * 덩어리째 떠오르다가 글리치 폭발되게 그리고 폭발되고 10초 이따가 땅이 다시 돌아오게", "올라가는 덩어리는 10×10×10 정육면체로
 * 하고 터질때까지 그 덩어리 위에 있으면 다시 150데미지 들어오게 떠올라서 터지기까지 시간은 15초 정도 시간 안에 덩어리에서
 * 떨어지면 데미지 없음"); before it was given out the user moved it to the hack's H, in the cleave's place (gone), and made
 * it a press of the panel lifting four cubes: "지금 H를 q활성화 H를 바꾸고 기존 H는 삭제해 그리고 띄우는 걸 4개로 하자 참격
 * 모션 전부 삭제하고 패널을 건들이고 하나는 바라보는 방향에 있는 10 × 10 × 10 블록 위에 거대한 !!!가 뜨게하고 나머지는 근처
 * 플레이어 위치에 !!!가 각각 3개 뜨게하고 공중으로 뜨게 근처 플레이어가 없으면 그냥 랜덤한 곳에 !!! 뜨고 올라가게" (they chose:
 * the cleave gone and H with the hack off left empty, no hit as it begins, only the burst's 150, "!!!" a second, nearby
 * within 20 blocks). A skill of the knife's pipeline (XCharaCombo.ERASE, XCharaAttack.erase):
 * <ol>
 * <li>they press the panel; at {@link #MARK} (its one "blow") four grounds {@link #SIDE} by {@link #SIDE} are marked
 * (XCharaErase): one where the middle of their screen points ({@link #LOOK}: no further than {@link #REACH} out, the
 * ground there), a giant "!!!" over it; the others under the nearest ones within {@link #NEAR} ({@link #PLAYER}: players
 * they may harm, and training dummies), as many as there are of them up to three, a "!!!" over each; any left at random
 * places within {@link #NEAR} ({@link #RANDOM}), a "!!!" over each. No two overlap ({@link #around}, {@link #free}); a
 * random one never has its user on it;</li>
 * <li>{@link #WARN} later (the user's second: off it by then, not lifted) each ground rises out of the earth as a cube
 * {@link #SIDE} blocks each way ({@link #lift}: from {@link #RISE_FROM} over {@link #RISE_TICKS} to {@link #LIFT} blocks
 * up, standing on the air a block over the ground), carrying whoever is on it, glitching as one;</li>
 * <li>{@link #BURST} after the mark (the user's 15 s from when they rise) they burst in a glitch: whoever is still on one
 * is struck for {@link #BURST_DAMAGE} (no guard takes it, no knockback); off them before then, nothing. Falls off them do
 * no harm. Where each was is a void from then on (only a picture: the ground is there to walk on) until {@link #BACK},
 * when the ground is drawn back.</li>
 * </ol>
 * Its user is held where they stand for the press until {@link #HOLD}; at {@link #LENGTH} it is over and its cooldown
 * ({@link #COOLDOWN}, the user's 60 s) runs from then. Before the mark, struck or held (or the hack turned off), it stops
 * short and its cooldown runs from then; once marked the grounds go on whatever its user does, unless they are gone
 * (dead, left, elsewhere, X Chara's no more): then the cubes are gone. The cubes are no blocks: the server and the
 * clients stand bodies on them and keep them from walking into them (EraseCubes); the map is not changed. Ticks of
 * 1/20 s.
 */
public final class EraseRules {
    private EraseRules(){}
    /** Its call's place among the effects' skills on the clients (KagenoSkills): a cast of its own for each (EraseClient). */
    public static final int SKILL=16;
    /** When the panel is pressed and the grounds are marked (its one "blow"), when its user is free, and when it is over (ticks from the press of H). */
    public static final int MARK=2,HOLD=11,LENGTH=14;
    /** How long the "!!!" shows before the grounds rise (ticks: the user's 1 s). */
    public static final int WARN=20;
    /** How many cubes rise (the user's four), and how many of them may be under someone near (the user's three). */
    public static final int CUBES=4,NEAR_ONES=CUBES-1;
    /** What each marked ground is: where they look (the giant "!!!"), under someone near, at a random place. */
    public static final int LOOK=0,PLAYER=1,RANDOM=2;
    /** How far out (level, from where they stand) the one they look at may be, and who is near (the user's 20 blocks: and where the random ones may be). */
    public static final double REACH=20,NEAR=20;
    /** How far under their feet the ground of the one they look at may be, and how far over it (a slope or a hill up ahead). */
    public static final double FLOOR_UNDER=16,FLOOR_OVER=8;
    /** How far under one near the ground under them is looked for (blocks; deeper, they are not lifted). */
    public static final double UNDER_THEM=16;
    /** How far over and under their feet a random place's ground is looked for (blocks). */
    public static final double RANDOM_OVER=8,RANDOM_UNDER=16;
    /** How many random places are tried for each random cube before it is given up. */
    public static final int RANDOM_TRIES=48;
    /** The ground marked, and the cube that rises: this many blocks each way (the user's 10 × 10 × 10). */
    public static final int SIDE=10;
    /** What the burst deals whoever is on a cube then (the user's 150), and its cooldown from its end (ticks: the user's 60 s). */
    public static final float BURST_DAMAGE=150;
    public static final int COOLDOWN=1200;
    /**
     * The cubes, in ticks after the mark: they begin to rise as the "!!!" goes ({@link #RISE_FROM}), rise over
     * {@link #RISE_TICKS} (6 s, gently in and out: at most 0.14 blocks a tick, so the clients' cubes and the server's stay
     * together) to {@link #LIFT} blocks up (wholly out of the ground and a block over it), and burst at {@link #BURST}
     * (the user's 15 s from when they rise); the ground is back at {@link #BACK} (the user's 10 s after).
     */
    public static final int RISE_FROM=WARN,RISE_TICKS=120,BURST=RISE_FROM+300,BACK=BURST+200;
    public static final double LIFT=11;
    /** Who is on a cube: over it (their box over it), their feet from this far under its top to this far over it (a jump up from it). */
    public static final double ON_UNDER=.6,ON_OVER=3;
    /** Who stands on it, carried up as it rises: their feet within this of its top (blocks). */
    public static final double STANDING=.12;

    private static double clamp01(double x){return x<0?0:x>1?1:x;}
    /** The point ({x, z}) {@code (x, z)} brought within REACH of where they stood {@code (fromX, fromZ)}, level (the way it lies from there kept). */
    public static double[] within(double fromX,double fromZ,double x,double z){
        double dx=x-fromX,dz=z-fromZ,d=Math.hypot(dx,dz);
        if(!(d>REACH))return new double[]{x,z};
        return new double[]{fromX+dx/d*REACH,fromZ+dz/d*REACH};
    }
    /** The west (or north) edge of the ground round {@code x} (or z): the block corner nearest it is its middle. */
    public static int corner(double x){return (int)Math.round(x)-SIDE/2;}
    /** Whether a box from {@code minX} to {@code maxX} and {@code minZ} to {@code maxZ} is over the ground whose corner is ({@code x0}, {@code z0}). */
    public static boolean over(int x0,int z0,double minX,double maxX,double minZ,double maxZ){
        return maxX>x0&&minX<x0+SIDE&&maxZ>z0&&minZ<z0+SIDE;
    }
    /** Whether the point ({@code x}, {@code z}) is on the ground whose corner is ({@code x0}, {@code z0}). */
    public static boolean holds(int x0,int z0,double x,double z){return x>=x0&&x<x0+SIDE&&z>=z0&&z<z0+SIDE;}
    /** Whether the grounds whose corners are ({@code ax}, {@code az}) and ({@code bx}, {@code bz}) overlap. */
    public static boolean overlap(int ax,int az,int bx,int bz){return Math.abs(ax-bx)<SIDE&&Math.abs(az-bz)<SIDE;}
    /** Whether the ground whose corner is ({@code x0}, {@code z0}) overlaps none of {@code taken} (each {x0, z0}). */
    public static boolean free(int x0,int z0,List<int[]> taken){
        for(var t:taken)if(overlap(x0,z0,t[0],t[1]))return false;
        return true;
    }
    /**
     * The ground ({x0, z0}) for one standing at ({@code x}, {@code z}), none of {@code taken} overlapped: the one round them
     * (their block corner its middle), else the nearest shift of it that still has them on it; null if none does, or if
     * they are on one of {@code taken} already (it lifts them).
     */
    public static int[] around(double x,double z,List<int[]> taken){
        for(var t:taken)if(holds(t[0],t[1],x,z))return null;
        int cx=corner(x),cz=corner(z);
        if(free(cx,cz,taken))return new int[]{cx,cz};
        int lx=(int)Math.floor(x)-SIDE+1,lz=(int)Math.floor(z)-SIDE+1;
        int[] best=null;int bestD=Integer.MAX_VALUE;
        for(int x0=lx;x0<lx+SIDE;x0++)for(int z0=lz;z0<lz+SIDE;z0++){
            int d=(x0-cx)*(x0-cx)+(z0-cz)*(z0-cz);
            if(d<bestD&&free(x0,z0,taken)){best=new int[]{x0,z0};bestD=d;}
        }
        return best;
    }
    /** A random place ({x, z}) within NEAR of ({@code x}, {@code z}) for two numbers {@code u}, {@code v} from 0 to 1: even over the round. */
    public static double[] random(double x,double z,double u,double v){
        double r=NEAR*Math.sqrt(clamp01(u)),a=2*Math.PI*v;
        return new double[]{x+r*Math.cos(a),z+r*Math.sin(a)};
    }
    /**
     * How far a cube is up {@code t} ticks after the mark (blocks): none until RISE_FROM, then gently rising over
     * RISE_TICKS to LIFT, held there until it bursts. NaN once it has burst (there is none).
     */
    public static double lift(double t){
        if(t>=BURST)return Double.NaN;
        double u=clamp01((t-RISE_FROM)/RISE_TICKS);
        return LIFT*u*u*(3-2*u);
    }
    /** Whether there is a cube {@code t} ticks after the mark (from then until it bursts). */
    public static boolean cube(double t){return t>=0&&t<BURST;}
    /** Whether feet {@code feet} high are on a cube whose top is {@code top} high (over it, a jump up from it at most). */
    public static boolean on(double feet,double top){return feet>=top-ON_UNDER&&feet<=top+ON_OVER;}
    /** Whether feet {@code feet} high stand on a cube whose top is {@code top} high (carried up as it rises). */
    public static boolean standing(double feet,double top){return Math.abs(feet-top)<=STANDING;}
    /** The tick it is ready again after one begun at {@code start}: its cooldown from its end. */
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
}
