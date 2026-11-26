package dev.sangwoo.celestialfall.progression;

import java.util.Map;

/**
 * Stand-owned base health; level/rebirth multipliers are deliberately not invented.
 * <p>
 * (2.140.0, user request: "이번에는 캐릭터별로 HP다르게 하자 스탠드 없음은 hp 350 이소상은 300 엑스차라는 375 스타플래티나는 400
 * 카게노 325") Each character has its own: none 350, Kageno 325, Star Platinum 400, Lee So-sang 300, X Chara 375 (all were
 * 200 before). A player on the map has their character's (ProgressManager.applyHealth, each second and as it changes: the
 * part of their health they had is kept). The bosses have their own (BossKind.health), whatever their stand.
 */
public final class StandStats {
    /** The health of each character (the user's numbers). */
    public static final double NONE_HEALTH=350,KAGENO_HEALTH=325,STAR_PLATINUM_HEALTH=400,ISOSANG_HEALTH=300,X_CHARA_HEALTH=375;
    /** {@code korean}: the name the Korean screens write (the menu, the shop); "없음" for no stand. */
    public record Definition(String id,String translation,double health,String korean) {}
    private static final Map<String,Definition> DEFINITIONS=Map.of(
        "none",new Definition("none","stand.botand.none",NONE_HEALTH,"없음"),
        "kageno",new Definition("kageno","stand.botand.kageno",KAGENO_HEALTH,"카게노"),
        // 2.51.0: a stand that comes out (Q) and floats beside its user; no skills yet
        "star_platinum",new Definition("star_platinum","stand.botand.star_platinum",STAR_PLATINUM_HEALTH,"스타플래티나"),
        // 2.76.0: Lee So-sang (Jade Knight · Moon Scar), a character who fights in person, as Kageno does; given by the
        // admin command only for now, their skills to come one at a time as the user gives them (none yet)
        "isosang",new Definition("isosang","stand.botand.isosang",ISOSANG_HEALTH,"이소상"),
        // 2.111.0: X Chara, a character who fights in person with their knife, as Kageno and Lee So-sang do; given by the
        // admin command only for now, their skills to come one at a time as the user gives them (none yet)
        "x_chara",new Definition("x_chara","stand.botand.x_chara",X_CHARA_HEALTH,"엑스 차라"));
    private StandStats() {}
    public static Definition get(String id){return DEFINITIONS.getOrDefault(id,DEFINITIONS.get("none"));}
    public static boolean known(String id){return id!=null&&DEFINITIONS.containsKey(id);}
    /** Every stand id with "none" first, for the commands. */
    public static String[] ids(){return new String[]{"none","kageno","star_platinum","isosang","x_chara"};}
}
