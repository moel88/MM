package dev.sangwoo.celestialfall.stand;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * The stand arrow's rules (2.70.0, user request: "일단 스탠드 화살 시스템 만들자 화살을 몸에 꽂는 이펙트와 모션 만들어주고
 * 20% 확률로 사망 할 확률이 있어 80%확률로 스타플래티나 추후에 스탠드가 추가되면 스타플래티나의 확률은 조정 할거야"). One
 * with no stand drives the arrow into their own chest; it kills one in five, and awakens a stand in the rest (Star
 * Platinum for now). The odds are a table out of ODDS: death first, then each stand the arrow can awaken, so a stand
 * added later takes its share from Star Platinum's here.
 * <p>
 * The server rolls as the arrow is raised and tells nobody: the arrow's choice shows, and is made (the arrow spent,
 * the stand given or the death begun), at REVEAL, once it has gone in and been taken into the body.
 */
public final class StandArrowRules {
    private StandArrowRules(){}
    /** A stand the arrow can awaken, and its share of ODDS. */
    public record Awakening(String stand,int weight){}
    /** The table's whole, and the share of it that dies. */
    public static final int ODDS=100,DEATH=20;
    /** The stands the arrow awakens and their shares (with DEATH they make up ODDS; adjusted as stands are added). */
    public static final List<Awakening> STANDS=List.of(new Awakening("star_platinum",80));
    /** The death's word in the events and the captures' rig (a stand's word is its id). */
    public static final String DIE="death";

    /**
     * Ticks from the use (2.71.0: about ten seconds, user request "화살 꽂는 모션이 10초는 됐으면 좋겠어 지금 너무 짧고
     * 감동이 없어"; 2.70.0 had the choice at 2.2 s): the arrow is looked at, raised to the sky, doubted, breathed for,
     * raised high over the face and held there trembling, then driven into the chest at HIT; they fall to their knees
     * and it is taken into the body until REVEAL, when its choice shows; one it rejects falls and dies at DEATH_AT; one
     * it chose gets up and is free to move again at FREE, its stand out beside them.
     */
    public static final int HIT=108,REVEAL=180,DEATH_AT=206,FREE=220;

    /** Whether one with {@code stand} equipped may use the arrow: only one with none. */
    public static boolean canUse(String stand){return "none".equals(stand);}
    /** What the arrow does for {@code roll} in [0, ODDS): {@link #DIE}, or the stand it awakens. */
    public static String outcome(int roll){
        if(roll<0||roll>=ODDS)throw new IllegalArgumentException("roll "+roll);
        if(roll<DEATH)return DIE;
        int at=DEATH;
        for(var a:STANDS){at+=a.weight();if(roll<at)return a.stand();}
        throw new IllegalStateException("the arrow's table does not make up "+ODDS);
    }
    /** A roll of {@code dice} (each in [0, ODDS)) made into what the arrow does. */
    public static String roll(IntSupplier dice){return outcome(dice.getAsInt());}
    /** Whether {@code outcome} is a death. */
    public static boolean dies(String outcome){return DIE.equals(outcome);}
    /** The share of ODDS that ends in {@code outcome} (death or a stand), as the table says. */
    public static int share(String outcome){
        if(dies(outcome))return DEATH;
        for(var a:STANDS)if(a.stand().equals(outcome))return a.weight();
        return 0;
    }
    /** Whether the table makes up ODDS exactly (no share below 1). */
    public static boolean whole(){
        int sum=DEATH;for(var a:STANDS){if(a.weight()<1)return false;sum+=a.weight();}
        return DEATH>=0&&sum==ODDS;
    }
}
