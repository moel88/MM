package dev.sangwoo.celestialfall.progression;

import java.util.Map;

/** Stand-owned base health; level/rebirth multipliers are deliberately not invented. */
public final class StandStats {
    public static final double DEFAULT_HEALTH=200;
    /** {@code korean}: the name the Korean screens write (the menu, the shop); "없음" for no stand. */
    public record Definition(String id,String translation,double health,String korean) {}
    private static final Map<String,Definition> DEFINITIONS=Map.of(
        "none",new Definition("none","stand.botand.none",DEFAULT_HEALTH,"없음"),
        "kageno",new Definition("kageno","stand.botand.kageno",DEFAULT_HEALTH,"카게노"),
        // 2.51.0: a stand that comes out (Q) and floats beside its user; no skills yet
        "star_platinum",new Definition("star_platinum","stand.botand.star_platinum",DEFAULT_HEALTH,"스타플래티나"),
        // 2.76.0: Lee So-sang (Jade Knight · Moon Scar), a character who fights in person, as Kageno does; given by the
        // admin command only for now, their skills to come one at a time as the user gives them (none yet)
        "isosang",new Definition("isosang","stand.botand.isosang",DEFAULT_HEALTH,"이소상"));
    private StandStats() {}
    public static Definition get(String id){return DEFINITIONS.getOrDefault(id,DEFINITIONS.get("none"));}
    public static boolean known(String id){return id!=null&&DEFINITIONS.containsKey(id);}
    /** Every stand id with "none" first, for the commands. */
    public static String[] ids(){return new String[]{"none","kageno","star_platinum","isosang"};}
}
