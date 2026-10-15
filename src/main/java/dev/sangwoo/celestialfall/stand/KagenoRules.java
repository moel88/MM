package dev.sangwoo.celestialfall.stand;

/** Shared, tick-based timings sampled from the supplied review. No client-supplied damage/radius. */
public final class KagenoRules {
    public static final int M1=0,E=1,R=2,T=3,RUSH=4,Y=5,Q=6,H=7,G=8;
    public static final int T_DOWN=3,T_UP=4,CANCEL=9,PULSE=10,HOLD=8;
    public static final int T_STRIKE=6,RUSH_IGNITE=10,RUSH_START=30,RUSH_FINISH=50;
    public static final double STOP_RADIUS=3,HIGHEST_RADIUS=5;
    public static final int[] DURATION={14,64,74,112,70,92,600,116,468};
    public static final int[] COOLDOWN={0,140,240,260,260,180,1800,600,0};
    public static final String[] NAMES={"검술의 대가","신속한 맹공","시간을 걷는 자","어둠 속의 불꽃","어둠을 가로지르다","어둠 속으로 빠지다","오버드라이브","최강","I am Atomic"};
    private KagenoRules(){}
    public static boolean inside(double distanceSquared,double radius){return Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=radius*radius;}
    public static boolean thrustLane(double forward,double side,double height,double targetHalfWidth){return forward>=-.15&&forward<=3.4+targetHalfWidth&&Math.abs(side)<=.65+targetHalfWidth&&Math.abs(height)<=2.2;}
    public static boolean frozen(int skill,float age){return skill==R&&age>=8&&age<36||skill==H&&age>=0&&age<DURATION[H];}
    public static int inputEnd(int skill){return skill==T?28:skill==M1?13:skill==R?52:skill==Y?38:DURATION[skill];}
    public static float damage(int skill,int age,int combo){
        return switch(skill){
            case M1 -> age==6?(combo==4?10:6):0;
            case E -> age>=10&&age<=52&&(age-10)%6==0?(age==52?5:3):0;
            case R -> age>=36&&age<=44&&(age-36)%2==0?(age==44?7:3.5f):0;
            case T -> age==T_STRIKE?15:age>=T_STRIKE+6&&age<=T_STRIKE+72&&(age-T_STRIKE)%6==0?3:0;
            case RUSH -> age==RUSH_IGNITE?14:age>=RUSH_START+4&&age<=RUSH_FINISH&&(age-RUSH_START)%4==0?(age==RUSH_FINISH?8:7):0;
            case Y -> age==16?2:age==24?14:0;
            case H -> age>=80&&age<=102&&(age-80)%2==0?(age==102?10:2):0;
            default -> 0;
        };
    }
    public static double multiplier(boolean night,float overdrive,boolean marked){return (night?1.35:1)*(1+overdrive)*(marked?1.15:1);}
    public static double rushSpeed(int age){return age<RUSH_START||age>RUSH_FINISH?0:age<RUSH_START+6?.95:.16;}
    public static boolean ignoresForeignTimeStop(String source){return !"sakuya".equals(source)&&!"return_to_zero".equals(source)&&!"kageno".equals(source);}
}
