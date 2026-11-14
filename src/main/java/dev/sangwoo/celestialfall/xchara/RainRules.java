package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's H with their hack off, the rain of knives (2.132.0, user request, no video: "q비활성화 h를 만들자 이건
 * 창작해야하는데 h를 쓰면 달려가다가 앞에 적이 있으면 적을 찌르고 수많은 q활성화 검들이 사방에서 날아와서 적을 맞추는
 * 스킬로 만들자", then "검들이 쏟아지는 수준으로 많이 날아와야해 그리고 달려간다음에 검이 다 쏟아질때까지 적을 찌르고
 * 있는거야"; they chose "합 70 · 60초", "가드 불가", the one stabbed "그 자리에 묶임" and its sounds "있는 소리 재사용").
 * Two skills of the knife's pipeline, one after the other:
 * <ul>
 * <li>{@link XCharaCombo#RUSH}, the run: from {@link #RUN_FROM} they run the way they faced, as far as {@link #RUN}
 * blocks (less where a block or a drop stands in the way), {@link #ran} blocks by each tick. Drawn, not walked (as the
 * three cuts' going forward: the server keeps them where they began and takes them to where it ends). Every tick of it
 * the server looks ahead of where they are drawn: with someone's near side within {@link #CATCH} blocks ahead (in a
 * lane {@link #PATH} to either side, in sight) the run is over and the stab begins. No one by {@link #RUN_TO}: they
 * come to a stop there and that is all of it (its cooldown runs).</li>
 * <li>{@link XCharaCombo#RAIN}, the stab and the rain: from where the run had them they go on to {@link #KEEP} short of
 * that one's near side (at most {@link #LUNGE} more; there by {@link #ARRIVE}), and at {@link #STAB} the knife is
 * driven into the nearest one ahead ({@link #REACH}): {@link #STAB_DAMAGE}, no guard takes it, no knockback; they are
 * bound where they stand ({@link #BIND}: they can do nothing, their legs stopped). The knife stays in them: from
 * {@link #FIRST} a volley of knives lit as the hack lights them lands on them every tick, {@link #VOLLEYS} of them,
 * {@link #VOLLEY_DAMAGE} each (the clients draw {@link #PER} knives a volley come from every side: {@link #BLADES} in
 * all). At {@link #WITHDRAW} the knife is drawn out; they are free at {@link #HOLD} and it is over at {@link #LENGTH}.
 * A stab that meets no one ends {@link #MISS} ticks on; the one stabbed dead or gone, the rain ends there.</li>
 * </ul>
 * Its cooldown ({@link #COOLDOWN}) runs from its end; cut short before the stab (struck in the run, or before the knife
 * is in), from then. With someone right ahead as H is pressed there is no run: the stab begins at once. Everything
 * here but the user's numbers (70 in all, 60 s, no guard, bound) is mine: the name, the times, the run's length and
 * speed, how near is caught, the knives' count and how they are split.
 * Pure timing and geometry, shared by the server and the clients; ticks of 1/20 s.
 */
public final class RainRules {
    private RainRules(){}
    // ───────────────────────────── the run ─────────────────────────────
    /** The tick into the run they set off, how fast they run once up to speed (blocks a tick: 16 a second), over how many ticks they come up to it, and how far at most (blocks). */
    public static final int RUN_FROM=1;
    public static final double SPEED=.8,SPRING=3,RUN=8;
    /** Over the last of the way (blocks) they slow to a stop where it ends. */
    public static final double BRAKE=1.2;
    /** The tick into the run it is over with no one met (they are where it ends), how long it holds them and how long it runs (ticks). */
    public static final int RUN_TO=13,RUSH_HOLD=17,RUSH_LENGTH=21;
    /** How far to either side of their way someone is in it (blocks, beyond half their own width), and how near ahead of where they are drawn someone's near side is caught (blocks). */
    public static final double PATH=1.1,CATCH=4.8;
    /** They stop this far short of the near side of the one caught (blocks: the knife's length is past it), going on at most LUNGE more from where the run had them. */
    public static final double KEEP=2.4,LUNGE=CATCH-KEEP;
    /** How far down from where they run the floor they come to may be, and how far up (blocks: a low step). */
    public static final double DOWN=3,UP=.6;

    // ───────────────────────────── the stab and the rain ─────────────────────────────
    /** The tick into the stab they are where they stab from, and the tick the knife goes in. */
    public static final int ARRIVE=4,STAB=5;
    /** The tick the first volley lands, and how many (one a tick). */
    public static final int FIRST=13,VOLLEYS=30;
    /** The tick the last volley lands. */
    public static final int LAST=FIRST+VOLLEYS-1;
    /** The tick the knife is drawn out, how long it holds them, and when it is over. */
    public static final int WITHDRAW=LAST+3,HOLD=LAST+7,LENGTH=LAST+14;
    /** The stab's damage and each volley's: 10 + 30 × 2 = 70 (the user's). */
    public static final float STAB_DAMAGE=10,VOLLEY_DAMAGE=2;
    /** Its cooldown from its end (ticks: 60 s, the user's). */
    public static final int COOLDOWN=1200;
    /** How long the one stabbed is bound from the stab (ticks: until the last volley and a little), their legs stopped wholly. */
    public static final int BIND=LAST-STAB+4;
    public static final double BIND_SLOW=1;
    /** A stab that met no one ends this many ticks after it. */
    public static final int MISS=6;
    /** Where the stab reaches: from how near ahead of their feet, how far, and how far to either side (blocks). */
    public static final double[] REACH={0,3.6,1.2};
    /** The one stabbed further than this from them (blocks), the rain ends (someone took them away). */
    public static final double LEASH=10;
    /** How many knives the clients draw a volley, and in all. */
    public static final int PER=3,BLADES=PER*VOLLEYS;

    /** How far along their way they have run {@code age} ticks into the run, with {@code far} blocks of it open (blocks): springing off, then at speed, slowing in over the last of it. */
    public static double ran(double age,double far){
        double u=Math.max(0,age-RUN_FROM),d=SPEED*(u<SPRING?u*u/(2*SPRING):u-SPRING/2);
        if(far<=0)return 0;
        double brake=Math.min(BRAKE,far);
        if(d<=far-brake)return d;
        return far-brake*Math.exp(-(d-(far-brake))/brake);
    }
    /** How much of the stab's last way they have gone {@code age} ticks into it (0 to 1): quick, slowing in; there by ARRIVE. */
    public static double lunged(double age){
        double u=Math.max(0,Math.min(1,age/ARRIVE));
        return 1-(1-u)*(1-u);
    }
    /** How much further they go as the stab begins, with the near side of the one caught {@code ahead} blocks ahead and {@code room} blocks of the way open (blocks). */
    public static double advance(double ahead,double room){return Math.max(0,Math.min(Math.min(LUNGE,room),ahead-KEEP));}
    /** The tick into the stab volley {@code v} lands on. */
    public static int volleyAt(int v){return FIRST+v;}
    /** The volley knife {@code k} is of, and the tick into the stab it is seen to land (the volley's three a third of a tick apart about its tick). */
    public static int volleyOf(int k){return k/PER;}
    public static double landsAt(int k){return volleyAt(volleyOf(k))+(k%PER-1)/3.0;}
    /** When it is ready again: its cooldown after a run begun at {@code start} that met no one, or after a stab begun at {@code start}, is over. */
    public static long readyAfterRun(long start){return start+RUSH_LENGTH+COOLDOWN;}
    public static long ready(long start){return start+LENGTH+COOLDOWN;}
    /** All of it together. */
    public static float total(){return STAB_DAMAGE+VOLLEYS*VOLLEY_DAMAGE;}
}
