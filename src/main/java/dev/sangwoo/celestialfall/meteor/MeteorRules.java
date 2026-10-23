package dev.sangwoo.celestialfall.meteor;

/**
 * Meteors (2.49.0, user request): up to ten lie on the map at once. Every minute each empty one of the ten places rolls
 * 5% for a meteor to fall ("빈 자리 10개를 각각 굴리되 5% 낮추기"), so about one falls every two minutes while the map is
 * empty of them and two or so lie about on average. One lies five minutes after it lands and then crumbles. Its
 * pickaxe breaks its three crystals off one after another, five hits each, and every crystal broken off is a fragment;
 * but after the first and after the second the rock may shatter there and then, one time in two, and whatever is left
 * in it is lost (2.49.1, user request: "첫번째때 캐고 바로 부서지면 1개밖에 못 얻는거고 안 부서지면 2개 얻을 수 있고 또
 * 안 부서지면 3개"). So a meteor gives one fragment one time in two, two one time in four and three one time in four.
 * Three fragments and a stick make a stand arrow at the crafter. Pure rules (no game state), so they are testable.
 */
public final class MeteorRules {
    public static final int SLOTS=10,ROLL_TICKS=1200,CHANCE=5,LIFE_TICKS=6000,FALL_TICKS=72,FRAGMENTS=3,HITS_PER_FRAGMENT=5;
    /** The shortest gap between two hits of one player (ticks), and the reach of a hit (blocks from the eyes to the rock's box). */
    public static final int HIT_GAP=6;
    public static final double REACH=4.5;
    /** A stand arrow at the crafter: this many fragments and sticks. */
    public static final int ARROW_FRAGMENTS=3,ARROW_STICKS=1;
    /** Where one may land: this far from the spawn, from every merchant and supply spot, and from every other meteor. */
    public static final double CLEAR_SPAWN=20,CLEAR_SPOT=8,CLEAR_METEOR=28;
    /** The fall: from this high over its landing place, coming down at this angle from the horizontal (degrees). */
    public static final double FALL_HEIGHT=150,FALL_ANGLE_MIN=42,FALL_ANGLE_MAX=58;

    /** Whether an empty place gets a meteor this minute, from a draw in 0..99. */
    public static boolean falls(int roll){range(roll,100);return roll<CHANCE;}

    /** The chance (percent) that the rock holds together after its first and after its second crystal breaks off. */
    public static final int[] HOLD={50,50};

    /** What is left of a rock: crystals still in it and hits into the next. */
    public record Rock(int fragments,int hits){
        public Rock{if(fragments<0||fragments>FRAGMENTS||hits<0||hits>=HITS_PER_FRAGMENT)throw new IllegalArgumentException("rock out of range");}
        public static Rock whole(){return new Rock(FRAGMENTS,0);}
        public boolean depleted(){return fragments==0;}
        /** One hit: the fifth into a fragment breaks it off. */
        public Rock hit(){
            if(depleted())throw new IllegalStateException("nothing left to mine");
            return hits+1>=HITS_PER_FRAGMENT?new Rock(fragments-1,0):new Rock(fragments,hits+1);
        }
        /** Whether this hit (from {@code before}) broke a crystal off. */
        public boolean brokeFrom(Rock before){return fragments<before.fragments;}
        /** Which crystal broke off to leave this rock: 1 for the first, up to {@link MeteorRules#FRAGMENTS} for the last. */
        public int crystalsOff(){return FRAGMENTS-fragments;}
        /** Share of the whole rock mined so far, 0..1. */
        public double mined(){return ((FRAGMENTS-fragments)*HITS_PER_FRAGMENT+hits)/(double)(FRAGMENTS*HITS_PER_FRAGMENT);}
    }

    /**
     * Whether the rock holds together after the crystal that broke off to leave {@code after}, from a draw in 0..99;
     * otherwise it shatters at once. A rock with no crystal left is mined out and never holds.
     */
    public static boolean holds(Rock after,int roll){
        range(roll,100);int crystal=after.crystalsOff();
        if(crystal<1)throw new IllegalArgumentException("no crystal has broken off");
        return !after.depleted()&&roll<HOLD[crystal-1];
    }

    /** When it lands, and when it crumbles away (world ticks), for a fall that starts at {@code start}. */
    public static long landing(long start){return start+FALL_TICKS;}
    public static long expiry(long start){return landing(start)+LIFE_TICKS;}

    /** Whether the crafter can make an arrow from what the player carries. */
    public static boolean canCraft(int fragments,int sticks){return fragments>=ARROW_FRAGMENTS&&sticks>=ARROW_STICKS;}

    /**
     * The fall's start, from its landing place, the direction it comes from (radians, 0 = from +z, turning to -x like
     * Minecraft's yaw) and its angle from the horizontal (degrees).
     */
    public static double[] start(double x,double y,double z,double from,double angle){
        double run=FALL_HEIGHT/Math.tan(Math.toRadians(angle));
        return new double[]{x-Math.sin(from)*run,y+FALL_HEIGHT,z+Math.cos(from)*run};
    }
    private static void range(int n,int bound){if(n<0||n>=bound)throw new IllegalArgumentException("random draw out of range");}
    private MeteorRules(){}
}
