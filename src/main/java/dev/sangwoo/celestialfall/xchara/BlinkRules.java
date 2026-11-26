package dev.sangwoo.celestialfall.xchara;

/**
 * X Chara's Z, the teleport (2.138.0, user request: "Z 스킬 만들자 q활성화 비활성화 공통 스킬이고 효과음은 영상에 있고 기존
 * 효과음이랑 똑같을거야 기존에 있던거 쓰고 스킬은 영상처럼 z스킬을 누르면 몸에 보라색 아우라가 생기고 앞으로 짧게
 * 순간이동하는 스킬이야", with a screen recording, 2.5 s, whose own label for it reads "Z - Teleportation"): its timing
 * and its numbers, on the server's ticks from the press. The same whether the hack is on or off.
 * <p>
 * What I read in the video and showed the user on its frames ("맞음, 이대로"): from the press their body (the knife
 * with it) flickers between itself and a flat purple silhouette for about a fifth of a second where they stand; then
 * they are gone from there and stand further on the way they look (the sound of it is the pull's burst, xchara_pull:
 * the two plays in the recording match that file at 0.93 and 0.88); purple bars are left where they stood, gone in half
 * a second, and their body flickers on some two fifths of a second where they came. The user's answers to what I
 * asked: how far, 5 blocks ("5블록": the video's is some 3.8 bodies, about 7); its cooldown 15 seconds ("15초"); through
 * the fifth of a second before it they may walk and a blow does not call it off ("걸을 수 있고 안 끊김").
 * <p>
 * The press begins it; at {@link #GO} its user is put up to {@link #REACH} blocks on, level, the way they looked as
 * they pressed (nearer if a block is in the way). Until then they walk as ever, though nothing else of theirs begins.
 * Its cooldown ({@link #COOLDOWN}) runs from the move, or from when it was called off (only something holding them
 * where they are calls it off: a cutscene, stopped time, another's hold).
 */
public final class BlinkRules {
    private BlinkRules(){}
    /** They are moved at this tick from the press (the video: a fifth of a second of the flicker before it). */
    public static final int GO=4;
    /** How far on they are put at most (blocks: the user's answer). */
    public static final double REACH=5;
    /** Its cooldown (the user's answer: 15 seconds): from the move, or from when it was called off. */
    public static final int COOLDOWN=300;
    /** For how long after the move their body flickers on, and the bars left where they stood show, on every client (ticks: the video's half second of each, the body's purple gone in the first fifth of it). */
    public static final int AFTER=10,LEFT=10;
    /** A step up they are put onto and a drop they are put down (blocks); how much nearer each try is when there is no place for them; how near a block in the way they come. */
    public static final double UP=.6,DOWN=4,NEARER=.5,SHORT=.4;
    /** How finely the way is looked along for something in it (blocks: far under a body's width, so nothing is stepped over). */
    public static final double LOOK=.05;

    /** Whether it is on its way at {@code age} ticks: pressed, not yet moved (nothing else of theirs begins meanwhile). */
    public static boolean casting(long age){return age>=0&&age<GO;}
    /** When it is ready again after one that moved them (or was called off) at {@code end}. */
    public static long ready(long end){return end+COOLDOWN;}
    /** The way {@code yaw} looks (degrees), level, as a unit {x, z}. */
    public static double[] way(float yaw){double r=Math.toRadians(yaw);return new double[]{-Math.sin(r),Math.cos(r)};}
    /**
     * How far along their way a body goes with nothing in it (2.138.1, user request: "벽통과는 못하게"): the furthest
     * of 0 to {@code most} blocks on that it can be at, and at every place before it ({@code free}: whether the whole
     * body, as wide as it is, has room that far on). Looked at every {@link #LOOK} from where they stand, so nothing a
     * body could not pass is passed; where it first has no room, the last free place is found to a hair. With no room
     * where they stand, 0.
     */
    public static double clearAlong(java.util.function.DoublePredicate free,double most){
        if(most<=0||!free.test(0))return 0;
        double at=0;
        while(at<most){
            double next=Math.min(most,at+LOOK);
            if(!free.test(next)){
                double lo=at,hi=next;
                for(int i=0;i<6;i++){double mid=(lo+hi)/2;if(free.test(mid))lo=mid;else hi=mid;}
                return lo;
            }
            at=next;
        }
        return most;
    }
    /** How far on they go when the way is clear for {@code clear} blocks (to the nearest block in the way): REACH at most, short of the block, never back. */
    public static double reach(double clear){return Math.max(0,Math.min(REACH,clear-SHORT));}
}
