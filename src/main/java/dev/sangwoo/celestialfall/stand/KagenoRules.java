package dev.sangwoo.celestialfall.stand;

/** Shared, tick-based timings sampled from the supplied review. No client-supplied damage/radius. */
public final class KagenoRules {
    public static final int M1=0,E=1,R=2,T=3,RUSH=4,Y=5,Q=6,H=7,G=8;
    public static final int T_DOWN=3,T_UP=4,CANCEL=9,PULSE=10,HOLD=8;
    /** T and T+: blast 0.65 s after the cast; T+ dash 1.09 s after the blast; final cut 0.8 s into the dash. */
    public static final int T_STRIKE=13,RUSH_IGNITE=13,RUSH_START=35,RUSH_FINISH=51;
    /** T fire ticks after the blast (0.267 s apart). */
    static final int[] T_TICKS={5,11,16,21,27,32,37,43,48,53,59,64};
    /** T+ dash: blocks moved on each tick from {@link #RUSH_START} (≈32 blocks, explosive start, eased out). */
    static final double[] RUSH_STEPS={8.5,6.5,4.5,4.5,3,2.5,1.5,1.0};
    public static final double STOP_RADIUS=3,HIGHEST_RADIUS=5;
    public static final int[] DURATION={24,64,74,124,74,92,600,116,468};
    /** M1 hit 4 holds its target 0.42 s after the thrust, then launches it. */
    public static final int M1_LAUNCH=14;
    public static final int[] COOLDOWN={0,140,240,260,260,180,1800,600,0};
    public static final String[] NAMES={"검술의 대가","신속한 맹공","시간을 걷는 자","어둠 속의 불꽃","어둠을 가로지르다","어둠 속으로 빠지다","오버드라이브","최강","I am Atomic"};
    private KagenoRules(){}
    public static boolean inside(double distanceSquared,double radius){return Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=radius*radius;}
    public static boolean thrustLane(double forward,double side,double height,double targetHalfWidth){return forward>=-.15&&forward<=3.4+targetHalfWidth&&Math.abs(side)<=.65+targetHalfWidth&&Math.abs(height)<=2.2;}
    public static boolean frozen(int skill,float age){return skill==R&&age>=8&&age<36||skill==H&&age>=0&&age<DURATION[H];}
    /** Targets caught in T's fire stand frozen inside the dark orb; T+ holds them until the cut. */
    public static boolean trapped(int skill,float age){return skill==T&&age>=T_STRIKE&&age<T_STRIKE+71||skill==RUSH&&age>=T_STRIKE+7&&age<RUSH_FINISH;}
    public static int inputEnd(int skill){return skill==T?15:skill==M1?8:skill==R?52:skill==Y?38:DURATION[skill];}
    public static float damage(int skill,int age,int combo){
        return switch(skill){
            case M1 -> age==6?(combo==4?10:6):0;
            case E -> age>=10&&age<=52&&(age-10)%6==0?(age==52?5:3):0;
            case R -> age>=36&&age<=44&&(age-36)%2==0?(age==44?7:3.5f):0;
            case T -> age==T_STRIKE?15:java.util.Arrays.binarySearch(T_TICKS,age-T_STRIKE)>=0?3:0;
            case RUSH -> age==T_STRIKE+7?4:age==T_STRIKE+13?5:age==T_STRIKE+18?5:age==RUSH_START?13:age==RUSH_START+1?5:age==RUSH_FINISH?18:0;
            case Y -> age==16?2:age==24?14:0;
            case H -> age>=80&&age<=102&&(age-80)%2==0?(age==102?10:2):0;
            default -> 0;
        };
    }
    public static double multiplier(boolean night,float overdrive,boolean marked){return (night?1.35:1)*(1+overdrive)*(marked?1.15:1);}
    public static double rushSpeed(int age){int i=age-RUSH_START;return i<0||i>=RUSH_STEPS.length?0:RUSH_STEPS[i];}
    public static boolean ignoresForeignTimeStop(String source){return !"sakuya".equals(source)&&!"return_to_zero".equals(source)&&!"kageno".equals(source);}
}
