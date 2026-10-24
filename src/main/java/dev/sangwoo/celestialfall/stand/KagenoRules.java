package dev.sangwoo.celestialfall.stand;

/** Shared, tick-based timings sampled from the supplied review. No client-supplied damage/radius. */
public final class KagenoRules {
    public static final int M1=0,E=1,R=2,T=3,RUSH=4,Y=5,Q=6,H=7,G=8,P=9;
    public static final int T_DOWN=3,T_UP=4,CANCEL=9,PULSE=10,HOLD=8,POSE=11,POSE_CANCEL=12;
    /** T: the blast 0.65 s after the cast. */
    public static final int T_STRIKE=13;
    /**
     * T+ (T held, remade in 2.38.0): three fire slashes leave the blade at {@link #RUSH_SLASHES} (ticks) and fly up the
     * cast's facing at {@link #RUSH_SLASH_SPEED} blocks a tick out to {@link #RUSH_RANGE}; each ends on the first one it
     * reaches and strikes them (it does not cut through). The first one struck is where the dash goes; with no one struck it runs to the end of the range. The
     * dash runs {@link #RUSH_DASH} ticks from {@link #RUSH_START}, aimed again every tick, so a slash striking someone
     * while it runs turns it to them; the caster's own client moves him along it ({@link #rushDestination}). It strikes as it arrives ({@link #RUSH_ARRIVE}), the fire it brings bursts on
     * them ({@link #RUSH_BURST}), and the big spin cut lands at {@link #RUSH_FINISH}.
     */
    public static final int RUSH_START=18,RUSH_DASH=6,RUSH_ARRIVE=RUSH_START+RUSH_DASH,RUSH_BURST=RUSH_ARRIVE+2,RUSH_FINISH=31;
    public static final int[] RUSH_SLASHES={8,12,16};
    static final float[] RUSH_SLASH_DAMAGE={4,5,5};
    public static final double RUSH_SLASH_SPEED=5,RUSH_RANGE=30,RUSH_SLASH_HALF_WIDTH=2.4,RUSH_SPIN_RADIUS=5.5;
    /** How far short of the one struck the dash stops (blocks). */
    public static final double RUSH_STOP=1.6;
    /** T fire ticks after the blast (0.267 s apart). */
    static final int[] T_TICKS={5,11,16,21,27,32,37,43,48,53,59,64};
    /** T+ dash: the share of its way covered on each of its ticks (an explosive start, eased out). */
    static final double[] RUSH_STEPS={.24,.2,.17,.15,.13,.11};
    public static final double STOP_RADIUS=7,HIGHEST_RADIUS=5;
    /** P: the pose lets go of the body at 18.68 s; the cast runs on to 19.45 s so the fading effects and a crescent in flight finish. */
    public static final int P_RELEASE=374;
    /**
     * How long each skill's cast runs (ticks), its effects included; its cooldown runs from the end (2.44.0). T's ends as its
     * last wisps die out (user request, 2.44.0: it ran on to 6.2 s with nothing left to see, so its cooldown began late).
     */
    public static final int[] DURATION={24,78,80,98,58,92,600,116,468,389};
    /** E: barrage ticks 0.262 s apart from 0.8 s, finisher (guard break) with the crescent at 2.9 s. */
    static final int[] E_TICKS={16,21,26,32,37,42,47};
    public static final int E_FINISH=58;
    /**
     * E's caster walks and turns with his aim through the thrusts (user request, 2.38.2: he could neither move nor
     * turn); from here, the last thrust struck, the finisher is planted: he stands and it goes the way he faced then.
     */
    public static final int E_PLANT=48;
    /** Whether E's caster still walks and aims at {@code age} (ticks). */
    public static boolean eWalking(float age){return age>=0&&age<E_PLANT;}
    /** How much slower E's caster walks through the thrusts (user request, 2.57.0: "카게노 E는 95% 느려지게": at a twentieth of his speed). */
    public static final double E_SLOW=.95;
    /** E's barrage ticks (a copy). */
    public static int[] eTicks(){return E_TICKS.clone();}
    /** R: the world is gray from 0.5 s; the four stored cuts land together as colour returns, then the big final cut (the release). */
    public static final int R_GRAY=10,R_COLOR=35,R_STORED=36,R_RELEASE=38,R_CUTS=4;
    /** Y: the stomp (hit 1) at 0.5 s, the flash (hit 2, guard break) at 1.05 s, the launch 0.5 s later. */
    public static final int Y_STOMP=10,Y_FLASH=21,Y_LAUNCH=31;
    /** Y launch: straight up ≈2.1 character heights in 0.32 s, down in 0.37 s (twice vanilla gravity). */
    public static final double Y_LIFT=1.03,Y_GRAVITY=.16;
    /** H cutscene: ten zigzag dashes, the slash burst and the beam; the camera cuts back at 4.97 s. */
    static final int[] H_ZIG={43,45,47,48,51,53,54,56,58,60};
    public static final int H_BURST=75,H_BEAM=92,H_CUT=99;
    /**
     * M1 hit 4: the thrust sends whoever it strikes flying along it (blocks per tick, forward and up): ≈6.9 blocks out
     * and 1.4 blocks up, landing ≈0.6 s later.
     */
    public static final double M1_FLY=1.4,M1_FLY_UP=.45;
    /**
     * M1's combo (user request, 2.43.0): each hit must start within {@link #M1_WINDOW} ticks of the one before, or the combo
     * breaks. A finished combo rests {@link #M1_REST} ticks once its fourth hit is over (user request, 2.43.1: the rest used
     * to run from the fourth hit's start, so it showed while the blow was still coming), a broken one as long from the
     * break, and either starts again from the first hit; within a combo the hits are {@link #M1_STEP} ticks apart at the
     * least.
     */
    public static final int M1_WINDOW=36,M1_REST=24,M1_STEP=8;
    /**
     * When a skill is ready again, its cast having begun at {@code start} and ended at {@code end} (its whole length, unless
     * it was stopped short). Every cooldown runs once its skill is over, not from when it was used (user request, 2.44.0;
     * M1's rest after the fourth hit since 2.43.1); M1's hits only keep {@link #M1_STEP} ticks between their starts.
     */
    public static long readyAt(int skill,int combo,long start,long end){return skill==M1?combo>=4?end+M1_REST:start+M1_STEP:end+COOLDOWN[skill];}
    /** When a skill is ready again if its cast runs its whole length. */
    public static long readyAfter(int skill,int combo,long start){return readyAt(skill,combo,start,start+DURATION[skill]);}
    /** How long the cooldown shown for a slot runs, the end of the wait for it: M1's rest after a combo, else the skill's cooldown. */
    public static int cooling(int slot){return slot==M1?M1_REST:COOLDOWN[slot];}
    /**
     * When the attack is ready again after a combo whose hit {@code combo} started at {@code last} broke, or -1 while it
     * still holds at {@code now} (or has none to break: no hit yet, or the fourth hit already ended it).
     */
    public static long m1Broken(int combo,long last,long now){return combo>0&&combo<4&&now-last>M1_WINDOW?last+M1_WINDOW+1+M1_REST:-1;}
    public static final int[] COOLDOWN={0,140,180,260,260,180,1800,600,0,20};
    public static final String[] NAMES={"검술의 대가","신속한 맹공","시간을 걷는 자","어둠 속의 불꽃","어둠을 가로지르다","어둠 속으로 빠지다","오버드라이브","최강","I am Atomic","포즈"};
    private KagenoRules(){}
    public static boolean inside(double distanceSquared,double radius){return Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=radius*radius;}
    public static boolean thrustLane(double forward,double side,double height,double targetHalfWidth){return forward>=-.15&&forward<=3.4+targetHalfWidth&&Math.abs(side)<=.65+targetHalfWidth&&Math.abs(height)<=2.2;}
    public static boolean frozen(int skill,float age){return skill==R&&age>=R_GRAY&&age<R_COLOR||skill==H&&age>=0&&age<DURATION[H];}
    /** Targets caught in T's fire stand frozen inside the dark orb; T+ holds them until the cut. */
    public static boolean trapped(int skill,float age){return skill==T&&age>=T_STRIKE&&age<T_STRIKE+71||skill==RUSH&&age>=RUSH_SLASHES[0]&&age<RUSH_FINISH||skill==Y&&age>=Y_FLASH&&age<Y_LAUNCH;}
    /**
     * R's caster walks at half speed and cannot run only while R holds his actions (user request, 2.38.2: he was still
     * slow for 1.6 s after R looked finished, as the slowdown lasted the whole cast with its effects).
     */
    public static boolean rWalking(float age){return age>=0&&age<inputEnd(R);}
    public static int inputEnd(int skill){return skill==T?15:skill==M1?8:skill==E?67:skill==R?48:skill==Y?33:skill==P?P_RELEASE:DURATION[skill];}
    /** Vertical speed (blocks per tick) of a Y target {@code ticks} after the launch. */
    public static double yLift(int ticks){return Y_LIFT-Y_GRAVITY*ticks;}
    /** Hits one damage tick stands for (R's four stored cuts register together; with the final cut R lands 5 hits). */
    public static int hits(int skill,int age){return skill==R&&age==R_STORED?R_CUTS:1;}
    /**
     * R's big final cut explodes in a band across the front (forward, the caster's right and height from the cast spot,
     * in blocks): it also strikes whoever stands in it outside the time stop.
     */
    public static boolean rBand(double forward,double side,double height){return forward>=0&&forward<=7&&Math.abs(side)<=10&&height>=-2&&height<=5;}
    public static float damage(int skill,int age,int combo){
        return switch(skill){
            case M1 -> age==6?(combo==4?10:6):0;
            case E -> age==E_FINISH?5:java.util.Arrays.binarySearch(E_TICKS,age)>=0?3:0;
            case R -> age==R_STORED?24:age==R_RELEASE?14:0;
            case T -> age==T_STRIKE?15:java.util.Arrays.binarySearch(T_TICKS,age-T_STRIKE)>=0?3:0;
            case RUSH -> age==RUSH_ARRIVE?13:age==RUSH_BURST?5:age==RUSH_FINISH?18:0;
            case Y -> age==Y_STOMP?2:age==Y_FLASH?14:0;
            case H -> java.util.Arrays.binarySearch(H_ZIG,age)>=0?2:age==H_BURST?4:age==H_BEAM?8:0;
            default -> 0;
        };
    }
    public static double multiplier(boolean night,float overdrive,boolean marked){return (night?1.35:1)*(1+overdrive)*(marked?1.15:1);}
    /** Share of the dash covered by the end of dash tick {@code k} (0 before the dash, 1 once it is over). */
    public static double rushDashed(int k){double d=0;for(int i=0;i<Math.min(k+1,RUSH_STEPS.length);i++)d+=RUSH_STEPS[i];return k<0?0:Math.min(1,d);}
    /** Share of what is left of the dash covered on its tick {@code k} (the whole of it on the last). */
    public static double rushStep(int k){double before=rushDashed(k-1);return before>=1?1:(rushDashed(k)-before)/(1-before);}
    /**
     * Where the dash heads from {@code at}: {@link #RUSH_STOP} short of the one struck ({@code mark}, null with no one
     * struck), or the end of the range up {@code facing} from the cast spot. The caster's own client steers the dash
     * along this (its movement is its own) and the server plans the same path for the arrival strike.
     */
    public static net.minecraft.util.math.Vec3d rushDestination(net.minecraft.util.math.Vec3d origin,net.minecraft.util.math.Vec3d facing,net.minecraft.util.math.Vec3d at,net.minecraft.util.math.Vec3d mark){
        if(mark==null)return origin.add(facing.multiply(RUSH_RANGE));
        var back=at.subtract(mark).multiply(1,0,1);
        return back.lengthSquared()>RUSH_STOP*RUSH_STOP?mark.add(back.normalize().multiply(RUSH_STOP)):at;
    }
    /** How far up the line fire slash {@code i} has flown at {@code age} (ticks), or -1 before it leaves. */
    public static double rushSlash(int i,float age){float t=age-RUSH_SLASHES[i];return t<0?-1:Math.min(RUSH_RANGE,1+RUSH_SLASH_SPEED*t);}
    /** Damage of fire slash {@code i}. */
    public static float rushSlashDamage(int i){return RUSH_SLASH_DAMAGE[i];}
    /** A point (forward, the caster's right and height from the cast spot, blocks) inside a slash's path from {@code from} to {@code to} blocks up the line. */
    public static boolean rushPath(double forward,double side,double height,double from,double to){return forward>=from&&forward<=to&&Math.abs(side)<=RUSH_SLASH_HALF_WIDTH&&height>=-1.5&&height<=3.5;}
    public static boolean ignoresForeignTimeStop(String source){return !"sakuya".equals(source)&&!"return_to_zero".equals(source)&&!"kageno".equals(source);}
}
