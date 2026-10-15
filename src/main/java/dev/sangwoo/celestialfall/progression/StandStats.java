package dev.sangwoo.celestialfall.progression;

import java.util.Map;

/** Stand-owned base health; level/rebirth multipliers are deliberately not invented. */
public final class StandStats {
    public static final double DEFAULT_HEALTH=200;
    public record Definition(String id,String translation,double health) {}
    private static final Map<String,Definition> DEFINITIONS=Map.of(
        "none",new Definition("none","stand.botand.none",DEFAULT_HEALTH),
        "kageno",new Definition("kageno","stand.botand.kageno",DEFAULT_HEALTH));
    private StandStats() {}
    public static Definition get(String id){return DEFINITIONS.getOrDefault(id,DEFINITIONS.get("none"));}
    public static boolean known(String id){return id!=null&&DEFINITIONS.containsKey(id);}
}
