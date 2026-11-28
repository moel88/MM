package dev.sangwoo.celestialfall.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * One move as the combat engine runs it (2.157.0): how long it lasts and the hits it strikes, each at its tick with its
 * shape, harm, whether a guard takes it, and how it throws whom it strikes. The rules that hold whom it strikes (a
 * basic attack's stagger) come with the chain it is part of (Chain). A move is data: the server's runner strikes by
 * it, the client draws by it and shows its frame data, a boss reads its shape and timing to keep out of it.
 */
public final class Move {
    /** Whether a guard takes a hit. */
    public enum Guard{BLOCKABLE,UNBLOCKABLE}
    /**
     * One hit: at {@code tick} from the move's start, with {@code shape}, {@code damage} harm, taken by a guard or not,
     * and throwing whom it strikes {@code knock} as Minecraft's knockback does (0: no throw).
     */
    public record Hit(int tick,Hitbox shape,float damage,Guard guard,double knock){}

    public final String id;
    /** Its length (ticks): the move is under way from its start until then. */
    public final int length;
    public final List<Hit> hits;
    private Move(String id,int length,List<Hit> hits){this.id=id;this.length=length;this.hits=List.copyOf(hits);}

    /** The tick its first hit lands (its startup; its length if it has none). */
    public int startup(){return hits.isEmpty()?length:hits.get(0).tick();}
    /** Ticks from its first hit to its last, both counted (its active frames). */
    public int active(){return hits.isEmpty()?0:hits.get(hits.size()-1).tick()-hits.get(0).tick()+1;}
    /** Ticks after its last hit until it ends (its recovery). */
    public int recovery(){return hits.isEmpty()?0:length-hits.get(hits.size()-1).tick()-1;}
    /** Its first hit (null if none). */
    public Hit first(){return hits.isEmpty()?null:hits.get(0);}

    public static Builder of(String id){return new Builder(id);}
    public static final class Builder {
        private final String id;private int length;private final List<Hit> hits=new ArrayList<>();
        private Builder(String id){this.id=id;}
        public Builder length(int ticks){length=ticks;return this;}
        public Builder hit(int tick,Hitbox shape,float damage,Guard guard){return hit(tick,shape,damage,guard,0);}
        public Builder hit(int tick,Hitbox shape,float damage,Guard guard,double knock){hits.add(new Hit(tick,shape,damage,guard,knock));return this;}
        public Move build(){
            if(length<=0)throw new IllegalStateException(id+": no length");
            for(var h:hits)if(h.tick()<0||h.tick()>=length)throw new IllegalStateException(id+": a hit outside the move");
            return new Move(id,length,hits);
        }
    }
}
