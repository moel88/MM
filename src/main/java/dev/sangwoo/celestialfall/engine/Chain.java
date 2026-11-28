package dev.sangwoo.celestialfall.engine;

/**
 * A combo the combat engine runs (2.157.0): its moves in turn, and when a press is kept, when the combo resets, and how
 * often a press is taken. A press within the last {@code queue} ticks of a move (not the last) is kept and its next
 * move starts as it ends; the next move started more than {@code reset} ticks after one ended starts the combo over;
 * a press sooner than {@code every} ticks after the last is dropped. After the last move the combo starts over.
 */
public final class Chain {
    public final String id;
    private final Move[] moves;
    public final int queue,reset,every;
    public Chain(String id,Move[] moves,int queue,int reset,int every){
        if(moves.length==0)throw new IllegalArgumentException(id+": no moves");
        this.id=id;this.moves=moves.clone();this.queue=queue;this.reset=reset;this.every=every;
    }
    /** How many moves it has. */
    public int size(){return moves.length;}
    /** Its move {@code index} (from 1). */
    public Move move(int index){return moves[index-1];}
    /** The move after {@code combo} if it starts at {@code now}, the combo going on until {@code comboUntil}: the first again after the last, or once it has run out. */
    public int next(int combo,long now,long comboUntil){return now>comboUntil||combo>=moves.length?1:combo+1;}
    /** Until when the combo goes on after move {@code index} started at {@code start}. */
    public long until(int index,long start){return start+move(index).length+reset;}
    /** Whether a press at {@code now}, move {@code index} under way since {@code start}, is kept for the next. */
    public boolean keeps(int index,long start,long now){return index<moves.length&&now>=start+move(index).length-queue;}

    /** Each move's startup, by index from 1 (0 at 0): the arrays the combat state has always had (CombatState.WINDUP). */
    public int[] windups(){int[] a=new int[moves.length+1];for(int i=1;i<=moves.length;i++)a[i]=move(i).startup();return a;}
    /** Each move's length, by index from 1 (CombatState.DURATION). */
    public int[] durations(){int[] a=new int[moves.length+1];for(int i=1;i<=moves.length;i++)a[i]=move(i).length;return a;}
    /** Each move's first hit's harm, by index from 1 (CombatState.DAMAGE). */
    public float[] damages(){float[] a=new float[moves.length+1];for(int i=1;i<=moves.length;i++){var h=move(i).first();a[i]=h==null?0:h.damage();}return a;}
}
