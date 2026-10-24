package dev.sangwoo.celestialfall.stand;

/**
 * Star Platinum's basic attack, four punches (2.54.0, user request: "이제 평타 만들자 4타에 날아가야해 평타 소리는 내가
 * 보낸 파일이랑 싱크 맞게 해줘"): a left jab, a right cross, a left hook and a lunging right that sends whoever it strikes
 * flying. 2.56.0 (user request: "평타 속도를 더 빠르게 하자 그대신 1 2타는 오라 소리를 없애고 3 4타부터 생기게"): the
 * jab and the cross are quick and silent, three tenths of a second apart; the hook and the finisher shout the last two
 * "ORA!"s of the user's recording, each landing on the "RA" of its own (tools/stand/cut_ora.py: a clip starts one tick
 * after its punch and has its RA (CONTACT - 1) / 20 s in). Those two are long shouts, "Ooo-RAAA", their "O" 0.4 s, so
 * the hook and the finisher still take 0.45 and 0.5 s to land; the finisher comes 0.65 s after the hook (in the
 * recording the two are 1.2 s apart), and its shout takes over from the hook's (StandAttack fades the one before out).
 * The four land 0.20, 0.50, 1.05 and 1.75 s into a mashed combo (2.54.0: 0.30, 1.10, 2.00, 3.20). Pure timing and
 * reach, shared by the server and the client; ticks of 1/20 s.
 */
public final class StarPlatinumRules {
    /** Each punch's contact, ticks after it starts (by the punch, 1 to 4): the jab and the cross snap in, the hook and the finisher take the "O" of their shouts (0.4 s). */
    public static final int[] CONTACT={0,4,4,9,10};
    /** Ticks from a punch's start before the next may start (2.56.0: three tenths of a second, the hook's 0.65 s). */
    public static final int[] STEP={0,6,6,13,0};
    /** How long each punch runs (ticks) when no next one cuts it short: the strike, the follow-through and the guard again. */
    public static final int[] LENGTH={0,10,10,17,23};
    public static final float[] DAMAGE={0,5,5,6,10};
    /** A press this many ticks or fewer before the next punch may start is kept, and starts it then. */
    public static final int BUFFER=8;
    /** The next punch must start within this many ticks of when it may, or the combo breaks. */
    public static final int GRACE=20;
    /** The rest once the fourth punch is over, or once a late punch broke the combo (ticks), as Kageno's attack. */
    public static final int REST=24;
    /** The fourth punch sends whoever it strikes flying along it (blocks a tick, out and up). */
    public static final double FLY=1.75,FLY_UP=.5;
    /** Where a punch reaches from its user's feet (blocks): ahead (the fourth punch lunges further), to either side, up and down. */
    public static final double NEAR=.2,REACH=3.6,REACH_BIG=4.2,HALF_WIDTH=1.15,LOW=-1.2,HIGH=2.8;
    /** Ticks a punch's contact holds whoever it strikes for, past the next punch's contact if that comes as soon as it may. */
    public static final int HOLD_PAST=3;
    private StarPlatinumRules(){}

    /** Whether the punch {@code combo} shouts (2.56.0: the hook and the finisher; the jab and the cross have none). */
    public static boolean shouts(int combo){return combo==3||combo==4;}

    /** The punch after {@code combo}: 1 again after the fourth (or none yet). */
    public static int next(int combo){return combo>=1&&combo<4?combo+1:1;}
    /** When the punch after {@code combo} (begun at {@code start}) may start. */
    public static long nextAt(int combo,long start){return start+STEP[combo];}
    /**
     * The tick the attack is ready again after the combo whose punch {@code combo} began at {@code start}, if it broke by
     * {@code now} (the next punch did not start in time), or -1 while it holds (or has nothing to break: none yet, or the
     * fourth punch ended it).
     */
    public static long broken(int combo,long start,long now){
        if(combo<1||combo>=4)return -1;
        long late=nextAt(combo,start)+GRACE;
        return now>late?late+1+REST:-1;
    }
    /** When the attack is ready again after the fourth punch, begun at {@code start}: its rest runs once it is over (2.43.1, 2.44.0). */
    public static long rested(long start){return start+LENGTH[4]+REST;}
    /**
     * How long the punch {@code combo} holds whoever it strikes (ticks from its contact): until the next punch's contact
     * if it starts as soon as it may, and a little past. The fourth punch holds no one (it throws them).
     */
    public static int hold(int combo){return combo>=1&&combo<4?STEP[combo]-CONTACT[combo]+CONTACT[combo+1]+HOLD_PAST:0;}
    /**
     * Whether a point (ahead along the punch, to its right and up from its user's feet, blocks) is in the punch's way,
     * for a body {@code halfWidth} across.
     */
    public static boolean lane(int combo,double forward,double side,double height,double halfWidth){
        return forward>=NEAR-halfWidth&&forward<=(combo==4?REACH_BIG:REACH)+halfWidth&&Math.abs(side)<=HALF_WIDTH+halfWidth&&height>=LOW&&height<=HIGH;
    }
}
