package dev.sangwoo.celestialfall.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every move the combat engine runs, by name and by number (2.157.0). The numbers go over the network (EngineNet) and
 * into the debug view; a move's place in this list is its number, so a new one is added at the end.
 * <p>
 * Bare hands' four strikes are the first chain the engine runs ({@link #BARE}): the same numbers as before it
 * (2.141.0's: startups 3·4·5·6, lengths 10·11·13·19, harm 3·3·4·6, a ray of 3.15 blocks from the eye meeting boxes
 * grown by 0.14, the fourth throwing as Minecraft's knockback of 0.95). The characters' moves come in as they are moved
 * onto the engine.
 */
public final class Moves {
    private Moves(){}
    /** Bare hands' reach: a ray this long from the eye, meeting boxes grown by this much. */
    public static final Hitbox.Ray BARE_RAY=new Hitbox.Ray(3.15,.14);
    /** Bare hands' four strikes: a press within the last 6 ticks of one is kept, the combo resets 14 ticks after one ends, presses at most every 3 ticks. */
    public static final Chain BARE=new Chain("bare",new Move[]{
        Move.of("bare.jab").length(10).hit(3,BARE_RAY,3,Move.Guard.BLOCKABLE).build(),
        Move.of("bare.cross").length(11).hit(4,BARE_RAY,3,Move.Guard.BLOCKABLE).build(),
        Move.of("bare.hook").length(13).hit(5,BARE_RAY,4,Move.Guard.BLOCKABLE).build(),
        Move.of("bare.finisher").length(19).hit(6,BARE_RAY,6,Move.Guard.BLOCKABLE,.95).build(),
    },6,14,3);

    private static final List<Move> ALL=new ArrayList<>();
    private static final Map<String,Integer> NUMBERS=new HashMap<>();
    static{
        for(int i=1;i<=BARE.size();i++)add(BARE.move(i));
    }
    private static void add(Move m){if(NUMBERS.putIfAbsent(m.id,ALL.size())!=null)throw new IllegalStateException("two moves named "+m.id);ALL.add(m);}
    /** A move's number (-1 if the engine runs no such move). */
    public static int number(Move m){return m==null?-1:NUMBERS.getOrDefault(m.id,-1);}
    /** The move of a number (null if none). */
    public static Move byNumber(int n){return n<0||n>=ALL.size()?null:ALL.get(n);}
    /** How many moves the engine runs. */
    public static int count(){return ALL.size();}
}
