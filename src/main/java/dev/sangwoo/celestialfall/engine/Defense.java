package dev.sangwoo.celestialfall.engine;

/**
 * The two rules the combat engine adds for everyone (2.157.0, user request: "회피와 퍼펙트가드만 넣고"): the perfect guard
 * and the evasive. Pure: ticks in, answers out; the combat state keeps the ticks (CombatState), the combat manager acts
 * on the answers (CombatManager).
 * <p>
 * The perfect guard: a blow a guard can take that reaches a guard raised no longer than {@link #PERFECT_WINDOW} ticks
 * ago is taken perfectly: it does nothing (no harm, no pressure on the guard, some taken off it), and whoever struck
 * from no farther than {@link #PARRY_REACH} is turned back, stunned {@link #PARRY_TICKS} ticks. A window opens at most
 * once every {@link #PERFECT_EVERY} ticks, so tapping the guard does not keep one open. A blow no guard can take is no
 * more taken by a perfect one.
 * <p>
 * The evasive: the dash key while a combo or a blow holds them (a basic attack's stagger, a barrage's hold, an
 * incapacitation, a broken guard) gets them out: what held them ends, and they dash {@link #EVADE_TICKS} ticks the way
 * they pressed (back if none), untouchable for {@link #EVADE_SAFE} ticks from its start. It is ready again
 * {@link #EVADE_COOLDOWN} ticks after it ends. What holds them by a cutscene or a grab (CombatManager.evadeValid) is not
 * left this way.
 */
public final class Defense {
    private Defense(){}
    /** Ticks from the guard going up in which a blow is taken perfectly (a quarter of a second). */
    public static final int PERFECT_WINDOW=5;
    /** A perfect window opens at most this often (ticks): raising the guard again sooner opens none. */
    public static final int PERFECT_EVERY=20;
    /** How long whoever a perfect guard turns back is stunned (ticks). */
    public static final int PARRY_TICKS=14;
    /** From how far (blocks, the striker's feet to the guard's) a perfect guard turns its striker back: one throwing from afar is not. */
    public static final double PARRY_REACH=6;
    /** How much pressure a perfect guard takes off the guard (of 100). */
    public static final float PERFECT_EASE=20;

    /** The evasive dash's length (ticks). */
    public static final int EVADE_TICKS=6;
    /** Untouchable from the evasive's start this long (ticks): no blow, hold or stagger reaches them. */
    public static final int EVADE_SAFE=10;
    /** Ticks after the evasive ends before it is ready again (15 s). */
    public static final int EVADE_COOLDOWN=300;
    private static final double[] EVADE_SPEED={1.12,1.16,1.02,.82,.52,.26};

    /** The evasive's speed (blocks a tick) {@code age} ticks into it (0 outside it). */
    public static double evadeSpeed(int age){return age<0||age>=EVADE_TICKS?0:EVADE_SPEED[age];}
    /** How far a whole evasive carries them (blocks, on flat ground). */
    public static double evadeDistance(){double d=0;for(double v:EVADE_SPEED)d+=v;return d;}

    /** Whether raising the guard at {@code now} opens a perfect window, the last having opened at {@code lastOpen}. */
    public static boolean opens(long lastOpen,long now){return now-lastOpen>=PERFECT_EVERY;}
    /**
     * Whether a guard {@code guarding} now, raised at {@code upAt}, whose last perfect window opened at {@code openAt},
     * takes a blow at {@code now} perfectly: the window opened as this guard went up and is still open.
     */
    public static boolean perfect(boolean guarding,long upAt,long openAt,long now){
        return guarding&&openAt==upAt&&now>=upAt&&now<upAt+PERFECT_WINDOW;
    }
    /**
     * Whether a perfect guard turns back a striker whose feet are {@code distance} blocks from the guard's: one near
     * enough to have struck from close. (Most skills strike as their user even from afar, so who struck is no test.)
     */
    public static boolean parries(double distance){return distance<=PARRY_REACH;}
}
