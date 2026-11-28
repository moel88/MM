package dev.sangwoo.celestialfall.stand;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stand skins (2.156.0, user request: "보탠드에 스탠드 스킨 기능을 추가하자 저 고퀄리티 골익을 스킨으로 하고 우리가
 * 만들었던 원본 골익이 원본으로 스킨 기능은 현재까지는 명령어로만 적용가능하게"; asked and answered: an operator applies
 * them, the skin is called "고퀄리티" (hq), and a stand lost takes its skin with it). A stand is drawn in its own look,
 * the original, unless its user has one of its skins: so far only Gold Experience has one, the model the user sent (its
 * body from 2.155.0 to 2.155.1). The server keeps each player's skin (ProgressState: it goes when their stand changes)
 * and every client in sight learns its number with their stand (the stand's tracked byte, StandHolder). A skin is only how
 * the stand is drawn: its rules, reach and timing are the stand's.
 */
public final class StandSkins {
    private StandSkins(){}
    /** Every stand's own look. */
    public static final String ORIGINAL="original";
    /** Each stand's skins after its own look, in the order of their numbers (1 on): the number its users' clients learn. */
    private static final Map<String,List<String>> SKINS=Map.of("gold_experience",List.of("hq"));
    /** Gold Experience's skin "고퀄리티": the model the user sent. */
    public static final String GOLD_HQ="hq";
    /** The most skins a stand can have after its own look (the tracked byte holds the number in three bits). */
    public static final int MOST=7;

    /** The skins a stand can be drawn in: its own look, then its skins. */
    public static List<String> of(String stand){
        var all=new ArrayList<String>();all.add(ORIGINAL);all.addAll(SKINS.getOrDefault(stand,List.of()));return all;
    }
    /** A skin's number for a stand: 0 its own look, a skin's place after it, -1 if the stand has no such skin. */
    public static int number(String stand,String skin){
        if(ORIGINAL.equals(skin))return 0;
        int i=SKINS.getOrDefault(stand,List.of()).indexOf(skin);
        return i<0?-1:i+1;
    }
    /** The skin numbered {@code number} of a stand: its own look for 0, or for a number it has no skin for. */
    public static String skin(String stand,int number){
        var list=SKINS.getOrDefault(stand,List.of());
        return number<1||number>list.size()?ORIGINAL:list.get(number-1);
    }
    /** Every skin of any stand and the original, for the command (in a steady order). */
    public static Set<String> all(){
        var all=new LinkedHashSet<String>();all.add(ORIGINAL);
        SKINS.keySet().stream().sorted().forEach(stand->all.addAll(SKINS.get(stand)));
        return all;
    }
    /** The language key of a skin's name (a stand's skin is named for its stand; the original is every stand's). */
    public static String translation(String stand,String skin){
        return ORIGINAL.equals(skin)?"skin.botand.original":"skin.botand."+stand+"."+skin;
    }
}
