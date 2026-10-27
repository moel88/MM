package dev.sangwoo.celestialfall.meteor;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The map's meteors: one per place (at most ten), with where it lands, where it fell from, when, and what is left of
 * it, plus when the places roll next. The meteors outlive their rocks on the ground (an unloaded chunk, a restart):
 * the rock entity is only shown where its chunk is loaded, and the times are world ticks.
 */
public final class MeteorState extends PersistentState {
    public record Meteor(int slot,long cycle,double x,double y,double z,double fromX,double fromY,double fromZ,long start,int seed,int fragments,int hits){
        public long landing(){return MeteorRules.landing(start);}
        public long expiry(){return MeteorRules.expiry(start);}
        public boolean landed(long now){return now>=landing();}
        public boolean expired(long now){return now>=expiry();}
        public MeteorRules.Rock rock(){return new MeteorRules.Rock(fragments,hits);}
        public Meteor with(MeteorRules.Rock r){return new Meteor(slot,cycle,x,y,z,fromX,fromY,fromZ,start,seed,r.fragments(),r.hits());}
        public UUID uuid(){return id(slot,cycle);}
        public Vec3d pos(){return new Vec3d(x,y,z);}
        public Vec3d from(){return new Vec3d(fromX,fromY,fromZ);}
    }
    private final Meteor[] slots=new Meteor[MeteorRules.SLOTS];
    private long nextRoll=-1,cycles;

    public static MeteorState get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(MeteorState::read,MeteorState::new,"botand_meteors");}
    /** A rock's fixed id: each fall gets its own, so a rock left from an earlier fall never passes for the next. */
    public static UUID id(int slot,long cycle){return UUID.nameUUIDFromBytes(("botand:meteor:"+slot+":"+cycle).getBytes(StandardCharsets.UTF_8));}

    public Meteor slot(int index){return index>=0&&index<slots.length?slots[index]:null;}
    public List<Meteor> all(){var out=new ArrayList<Meteor>();for(var m:slots)if(m!=null)out.add(m);return out;}
    public int firstEmpty(){for(int i=0;i<slots.length;i++)if(slots[i]==null)return i;return -1;}
    public long nextCycle(){cycles++;markDirty();return cycles;}
    public void put(Meteor m){if(m.slot()<0||m.slot()>=slots.length)throw new IllegalArgumentException("no such place");slots[m.slot()]=m;markDirty();}
    public void clear(int slot){if(slot>=0&&slot<slots.length&&slots[slot]!=null){slots[slot]=null;markDirty();}}
    public long nextRoll(){return nextRoll;}
    public void nextRoll(long tick){nextRoll=tick;markDirty();}

    public static MeteorState read(NbtCompound tag){
        var s=new MeteorState();s.nextRoll=tag.contains("NextRoll")?tag.getLong("NextRoll"):-1;s.cycles=tag.getLong("Cycles");
        var list=tag.getList("Meteors",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);int slot=n.getInt("Slot");if(slot<0||slot>=s.slots.length)continue;
            int fragments=Math.max(0,Math.min(MeteorRules.FRAGMENTS,n.getInt("Fragments"))),hits=Math.max(0,Math.min(MeteorRules.HITS_PER_FRAGMENT-1,n.getInt("Hits")));
            if(fragments==0)continue;
            s.slots[slot]=new Meteor(slot,n.getLong("Cycle"),n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z"),n.getDouble("FromX"),n.getDouble("FromY"),n.getDouble("FromZ"),
                n.getLong("Start"),n.getInt("Seed"),fragments,hits);
        }
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound tag){
        var list=new NbtList();
        for(var m:slots){
            if(m==null)continue;var n=new NbtCompound();
            n.putInt("Slot",m.slot());n.putLong("Cycle",m.cycle());n.putDouble("X",m.x());n.putDouble("Y",m.y());n.putDouble("Z",m.z());
            n.putDouble("FromX",m.fromX());n.putDouble("FromY",m.fromY());n.putDouble("FromZ",m.fromZ());n.putLong("Start",m.start());
            n.putInt("Seed",m.seed());n.putInt("Fragments",m.fragments());n.putInt("Hits",m.hits());list.add(n);
        }
        tag.putInt("Version",1);tag.putLong("NextRoll",nextRoll);tag.putLong("Cycles",cycles);tag.put("Meteors",list);return tag;
    }
}
