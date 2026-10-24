package dev.sangwoo.gachashots;

import java.util.Random;

/**
 * Visual QA (2.40.0): the rolls the server makes while filming. Each entry reseeds them from its name, so an entry pulls
 * the same grades whichever runs film it and in whatever order (the slowed film run and the real-time sound run).
 */
public final class QaRolls {
    private QaRolls(){}
    private static Random random=new Random(7);
    public static synchronized void reseed(String entry){random=new Random(Long.getLong("botand.gacha.seed",7)*31+entry.hashCode());}
    public static synchronized int next(int bound){return random.nextInt(bound);}
}
