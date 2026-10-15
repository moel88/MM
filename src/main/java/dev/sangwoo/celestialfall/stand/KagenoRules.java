package dev.sangwoo.celestialfall.stand;

/** Shared, tick-based timings sampled from the supplied review. No client-supplied damage/radius. */
public final class KagenoRules {
    public static final int M1=0,E=1,R=2,T=3,RUSH=4,Y=5,Q=6,H=7,G=8,P=9;
    public static final int T_DOWN=3,T_UP=4,CANCEL=9,PULSE=10,HOLD=8,POSE=11;
    /** T and T+: blast 0.65 s after the cast; T+ dash 1.09 s after the blast; final cut 0.8 s into the dash. */
    public static final int T_STRIKE=13,RUSH_IGNITE=13,RUSH_START=35,RUSH_FINISH=52;
    /** T fire ticks after the blast (0.267 s apart). */
    static final int[] T_TICKS={5,11,16,21,27,32,37,43,48,53,59,64};
    /** T+ dash: blocks moved on each tick from {@link #RUSH_START} (≈32 blocks, explosive start, eased out). */
    static final double[] RUSH_STEPS={8.5,6.5,4.5,4.5,3,2.5,1.5,1.0};
    public static final double STOP_RADIUS=3,HIGHEST_RADIUS=5;
    /** P: the pose lets go of the body at 18.68 s; the cast runs on to 19.45 s so the fading effects and a crescent in flight finish. */
    public static final int P_RELEASE=374;
    /** H holds the caster and the struck until 5.8 s; the cast runs on to 6.45 s while the ground crescent fades. */
    public static final int H_HOLD=116;
    public static final int[] DURATION={24,78,80,124,74,92,600,129,468,389};
    /** E: barrage ticks 0.262 s apart from 0.8 s, finisher (guard break) with the crescent at 2.9 s. */
    static final int[] E_TICKS={16,21,26,32,37,42,47};
    public static final int E_FINISH=58;
    /** R: the world is gray from 0.5 s; the four stored cuts land together as colour returns, then the release. */
    public static final int R_GRAY=10,R_COLOR=35,R_STORED=36,R_RELEASE=38;
    /** Y: the stomp (hit 1) at 0.5 s, the flash (hit 2, guard break) at 1.05 s, the launch 0.5 s later. */
    public static final int Y_STOMP=10,Y_FLASH=21,Y_LAUNCH=31;
    /** Y launch: straight up ≈2.1 character heights in 0.32 s, down in 0.37 s (twice vanilla gravity). */
    public static final double Y_LIFT=1.03,Y_GRAVITY=.16;
    /** H cutscene: ten zigzag dashes, the slash burst and the beam; the camera cuts back at 4.97 s. */
    static final int[] H_ZIG={43,45,47,48,51,53,54,56,58,60};
    public static final int H_BURST=75,H_BEAM=92,H_CUT=99;
    /** M1 hit 4 holds its target 0.42 s after the thrust, then launches it. */
    public static final int M1_LAUNCH=14;
    public static final int[] COOLDOWN={0,140,240,260,260,180,1800,600,0,20};
    public static final String[] NAMES={"검술의 대가","신속한 맹공","시간을 걷는 자","어둠 속의 불꽃","어둠을 가로지르다","어둠 속으로 빠지다","오버드라이브","최강","I am Atomic","포즈"};
    private KagenoRules(){}
    public static boolean inside(double distanceSquared,double radius){return Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=radius*radius;}
    public static boolean thrustLane(double forward,double side,double height,double targetHalfWidth){return forward>=-.15&&forward<=3.4+targetHalfWidth&&Math.abs(side)<=.65+targetHalfWidth&&Math.abs(height)<=2.2;}
    public static boolean frozen(int skill,float age){return skill==R&&age>=R_GRAY&&age<R_COLOR||skill==H&&age>=0&&age<H_HOLD;}
    /** Targets caught in T's fire stand frozen inside the dark orb; T+ holds them until the cut. */
    public static boolean trapped(int skill,float age){return skill==T&&age>=T_STRIKE&&age<T_STRIKE+71||skill==RUSH&&age>=T_STRIKE+7&&age<RUSH_FINISH||skill==Y&&age>=Y_FLASH&&age<Y_LAUNCH;}
    public static int inputEnd(int skill){return skill==T?15:skill==M1?8:skill==E?67:skill==R?48:skill==Y?33:skill==P?P_RELEASE:skill==H?H_HOLD:DURATION[skill];}
    /** Vertical speed (blocks per tick) of a Y target {@code ticks} after the launch. */
    public static double yLift(int ticks){return Y_LIFT-Y_GRAVITY*ticks;}
    /** Hits one damage tick stands for (R's four stored cuts register together). */
    public static int hits(int skill,int age){return skill==R&&age==R_STORED?4:1;}
    public static float damage(int skill,int age,int combo){
        return switch(skill){
            case M1 -> age==6?(combo==4?10:6):0;
            case E -> age==E_FINISH?5:java.util.Arrays.binarySearch(E_TICKS,age)>=0?3:0;
            case R -> age==R_STORED?14:age==R_RELEASE?7:0;
            case T -> age==T_STRIKE?15:java.util.Arrays.binarySearch(T_TICKS,age-T_STRIKE)>=0?3:0;
            case RUSH -> age==T_STRIKE+7?4:age==T_STRIKE+13?5:age==T_STRIKE+18?5:age==RUSH_START?13:age==RUSH_START+1?5:age==RUSH_FINISH?18:0;
            case Y -> age==Y_STOMP?2:age==Y_FLASH?14:0;
            case H -> java.util.Arrays.binarySearch(H_ZIG,age)>=0?2:age==H_BURST?4:age==H_BEAM?8:0;
            default -> 0;
        };
    }
    public static double multiplier(boolean night,float overdrive,boolean marked){return (night?1.35:1)*(1+overdrive)*(marked?1.15:1);}
    public static double rushSpeed(int age){int i=age-RUSH_START;return i<0||i>=RUSH_STEPS.length?0:RUSH_STEPS[i];}
    public static boolean ignoresForeignTimeStop(String source){return !"sakuya".equals(source)&&!"return_to_zero".equals(source)&&!"kageno".equals(source);}
}
