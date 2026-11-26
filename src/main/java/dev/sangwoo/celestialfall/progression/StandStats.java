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
    /** (2.143.0) The test samurai's (mine: Kageno's, the other one who fights with a katana). */
    public static final double SAMURAI_HEALTH=325;
    /** (2.146.0) Gold Experience's (the user's answer when asked: 375). */
    public static final double GOLD_EXPERIENCE_HEALTH=375;
    /** {@code korean}: the name the Korean screens write (the menu, the shop); "없음" for no stand. */
    public record Definition(String id,String translation,double health,String korean) {}
    private static final Map<String,Definition> DEFINITIONS=Map.ofEntries(
        Map.entry("none",new Definition("none","stand.botand.none",NONE_HEALTH,"없음")),
        Map.entry("kageno",new Definition("kageno","stand.botand.kageno",KAGENO_HEALTH,"카게노")),
        // 2.51.0: a stand that comes out (Q) and floats beside its user; no skills yet
        Map.entry("star_platinum",new Definition("star_platinum","stand.botand.star_platinum",STAR_PLATINUM_HEALTH,"스타플래티나")),
        // 2.76.0: Lee So-sang (Jade Knight · Moon Scar), a character who fights in person, as Kageno does; given by the
        // admin command only for now, their skills to come one at a time as the user gives them (none yet)
        Map.entry("isosang",new Definition("isosang","stand.botand.isosang",ISOSANG_HEALTH,"이소상")),
        // 2.111.0: X Chara, a character who fights in person with their knife, as Kageno and Lee So-sang do; given by the
        // admin command only for now, their skills to come one at a time as the user gives them (none yet)
        Map.entry("x_chara",new Definition("x_chara","stand.botand.x_chara",X_CHARA_HEALTH,"엑스 차라")),
        // 2.143.0: the samurai, a test character (user request: "그냥 테스트용 사무라이 캐릭터 G스킬이고"): given by the admin
        // command only; their one skill is G (samurai/SamuraiUltimate)
        Map.entry("samurai",new Definition("samurai","stand.botand.samurai",SAMURAI_HEALTH,"사무라이")),
        // 2.146.0: Gold Experience, a stand that comes out (Q) and floats beside its user in a golden aura, as Star
        // Platinum does (user request: "이제 이걸 플레이어 q스킬로 이식하고 q누르면 나오게 바꾸고 골익과 플레이어에게
        // 황금색 아우라가 생기게"); no skills yet
        Map.entry("gold_experience",new Definition("gold_experience","stand.botand.gold_experience",GOLD_EXPERIENCE_HEALTH,"골드 익스피리언스")));
    private StandStats() {}
    public static Definition get(String id){return DEFINITIONS.getOrDefault(id,DEFINITIONS.get("none"));}
    public static boolean known(String id){return id!=null&&DEFINITIONS.containsKey(id);}
    /** Every stand id with "none" first, for the commands. */
    public static String[] ids(){return new String[]{"none","kageno","star_platinum","isosang","x_chara","samurai","gold_experience"};}
}
