package dev.sangwoo.celestialfall.loot;

/** Exact cumulative respawn distribution. A slot draws once, never rerolls while unloaded. */
public final class LootRules {
    public static final int HOLD_TICKS=20,OPEN_TICKS=60,BOX_MONEY=10,BOX_XP=10,CHEST_MONEY=15,CHEST_XP=15;
    public static final int FRUIT=1,KEY=2;
    public static final double REACH=3.5;
    public static boolean treasure(int roll){range(roll,100);return roll<20;}
    public static int drops(boolean chest,int fruitRoll,int keyRoll){
        range(fruitRoll,100);range(keyRoll,100);
        return chest?FRUIT:(fruitRoll<50?FRUIT:0)|(keyRoll<10?KEY:0);
    }
    public static int respawnTicks(int percentile,int position){
        range(percentile,100);range(position,60);
        // First minute includes a five-second opening/readability grace period.
        int seconds=percentile<30?5+position*56/60:percentile<60?61+position:121+position;
        return seconds*20;
    }
    public static int money(boolean chest){return chest?CHEST_MONEY:BOX_MONEY;}
    public static int xp(boolean chest){return chest?CHEST_XP:BOX_XP;}
    private static void range(int n,int bound){if(n<0||n>=bound)throw new IllegalArgumentException("random draw out of range");}
    private LootRules(){}
}
