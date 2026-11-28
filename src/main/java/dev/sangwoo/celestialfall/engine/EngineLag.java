package dev.sangwoo.celestialfall.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simulated round trip on a player's connection (2.157.0, {@code /botand engine lag}, EngineLagMixin): what the
 * server sends them and what it reads from them each wait half of it. Netty's thread reads it as packets go by, so it
 * is kept in a concurrent map. A tool only: nothing in the game puts it on.
 */
public final class EngineLag {
    private EngineLag(){}
    private static final Map<UUID,Integer> ROUND_TRIP=new ConcurrentHashMap<>();
    /** Set while a held-back packet goes on its way, so it is not held back again. */
    private static final ThreadLocal<Boolean> PASSING=ThreadLocal.withInitial(()->false);
    public static void set(UUID player,int ms){if(ms<=0)ROUND_TRIP.remove(player);else ROUND_TRIP.put(player,ms);}
    /** How long a packet to or from {@code player} waits each way (ms; 0 none). */
    public static int each(UUID player){Integer ms=ROUND_TRIP.get(player);return ms==null?0:ms/2;}
    public static boolean passing(){return PASSING.get();}
    public static void pass(Runnable send){PASSING.set(true);try{send.run();}finally{PASSING.set(false);}}
    public static boolean any(){return !ROUND_TRIP.isEmpty();}
    static void clear(){ROUND_TRIP.clear();}
    static Map<UUID,Integer> all(){return Map.copyOf(ROUND_TRIP);}
    static String describe(){return ROUND_TRIP.toString();}
}
