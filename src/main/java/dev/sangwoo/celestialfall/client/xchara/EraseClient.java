package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.xchara.EraseCubes;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import dev.sangwoo.celestialfall.xchara.XCharaErase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * X Chara's terrain delete as this client knows it once its grounds are marked (2.133.0; the server's XCharaErase,
 * EraseRules). The server tells of it as the grounds are marked (where its user stood, each ground's corner and top, what
 * it is: where they looked, under someone near, a random place), of everyone a burst strikes, and if it is cut short
 * (xchara_erase_event_v1). Each is a cast of its own skill ({@link #CASTS}, EraseRules.SKILL: EraseZone draws it through
 * KagenoRenderer), its clock ticks since the mark, its origin where its user stood.
 * <p>
 * Its cubes of ground are something to stand on here too ({@link #SIDE}, EraseCubes): this client's own player stands on
 * them and is carried up as they rise, on this client's clock (ticks since the mark, a whole number for the tick being
 * run).
 * <p>
 * Its sounds are ones the game has, each once for the four (heard from the ground nearest this client): as the grounds are
 * marked, the "!!!"'s warning (G's own, as their T's and Y's; loud for its user and for whoever stands on one); as they
 * rise, the hack coming on; as they burst, the slam's landing boom, the double's wall breaking and the pull's burst; as
 * the ground is back, the double's coming (part of it). Loud for its user as far out as its grounds may be, from the
 * nearest ground for the rest (40 blocks). The press's click is its user's cast's (ErasePress).
 */
public final class EraseClient {
    /** Every call this client knows of, by its id: a cast of its own skill (EraseRules.SKILL), its origin where its user stood, its caster its user. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One marked ground as this client knows it. */
    public static final class Ground {
        public final int x0,z0,kind,under,seed;public final double top;
        /** Whom its burst struck, by entity id, and how old the call was when this client heard of it. */
        final Map<Integer,Float> blasted=new LinkedHashMap<>();
        /** Its cube's faces as this client found them (EraseFx), once it is drawn. */
        EraseFx.Faces faces;
        /** Where its "!!!" shows (set as this client hears of it): over the head of whom it is under, where they stood; else over its middle (EraseFx.BANG_OVER, the giant one higher). */
        Vec3d bang;
        Ground(int x0,int z0,double top,int kind,int under,int seed){this.x0=x0;this.z0=z0;this.top=top;this.kind=kind;this.under=under;this.seed=seed;bang=EraseFx.bangOver(this,null);}
        /** The middle of its ground, at its top. */
        public Vec3d middle(){return new Vec3d(x0+EraseRules.SIDE*.5,top,z0+EraseRules.SIDE*.5);}
        /** Whether this is the one its user looked at (the giant "!!!"). */
        public boolean looked(){return kind==EraseRules.LOOK;}
    }
    /** One call as this client knows it. */
    public static final class Call {
        public final UUID id,owner;public final Vec3d from;public final StandClient.Cast cast;public final List<Ground> grounds;
        /** When it was cut short (ticks after the mark, on its clock; Float.MAX_VALUE: it was not). */
        public float cut=Float.MAX_VALUE;
        Call(UUID id,UUID owner,Vec3d from,StandClient.Cast cast,List<Ground> grounds){this.id=id;this.owner=owner;this.from=from;this.cast=cast;this.grounds=grounds;}
        /** Ticks since the mark, on its clock. */
        public float age(float delta){return cast.age(delta);}
        /** Ticks since the mark: a whole number for the tick being run. */
        public long tick(){return (long)Math.floor(cast.age(0)+1e-4);}
        /** Whether it has been cut short by {@code age}. */
        boolean cutBy(float age){return age>=cut;}
    }
    static final Map<UUID,Call> CALLS=new LinkedHashMap<>();
    /** How far its sounds carry (blocks), and the parts of some. */
    static final double CARRY=40;static final float RISE_PART=.8f,RETURN_PART=.7f;
    static final String WARN_SOUND="xchara_g_warn",GONE_SOUND="xchara_pull",BACK_SOUND="xchara_clone",BOOM_SOUND="xchara_slam_land",BREAK_SOUND="xchara_clone_wall",RISE_SOUND="xchara_q_on";
    private static Object world;
    /** This client's cubes, for EraseCubes (its own player stands on them). */
    static final EraseCubes.Side SIDE=new EraseCubes.Side(){
        @Override public boolean any(){return !CALLS.isEmpty();}
        @Override public void cubes(World w,List<EraseCubes.Cube> out){
            if(w!=world)return;
            for(var c:CALLS.values()){
                long t=c.tick();
                if(c.cutBy(c.age(0))||!EraseRules.cube(t))continue;
                for(var g:c.grounds)out.add(new EraseCubes.Cube(g.x0,g.z0,g.top,t));
            }
        }
    };
    private EraseClient(){}

    public static void initialize(){
        EraseCubes.client(SIDE);
        ClientPlayNetworking.registerGlobalReceiver(XCharaErase.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID id=b.readUuid();
            if(type==XCharaErase.MARKED){
                UUID owner=b.readUuid();Identifier dim=b.readIdentifier();long marked=b.readLong(),now=b.readLong();
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                int n=b.readVarInt();if(n<0||n>8)return;
                var grounds=new ArrayList<Ground>();
                int seed0=(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>11);
                for(int i=0;i<n;i++){
                    int x0=b.readInt(),z0=b.readInt();double top=b.readDouble();int kind=b.readUnsignedByte(),under=b.readVarInt();
                    if(!Double.isFinite(top))return;
                    grounds.add(new Ground(x0,z0,top,kind,under,seed0+i*7919));
                }
                if(!finite(from)||now<marked||now-marked>EraseRules.BACK+20)return;
                mc.execute(()->{
                    if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    if(world!=mc.world){clear();world=mc.world;}
                    if(CALLS.containsKey(id))return;
                    // the "!!!" over whom each is under, where they stand as this client hears of it
                    for(var g:grounds)if(g.kind==EraseRules.PLAYER&&mc.world.getEntityById(g.under) instanceof net.minecraft.entity.LivingEntity e)g.bang=EraseFx.bangOver(g,e);
                    var cast=new StandClient.Cast(id,owner,dim,EraseRules.SKILL,0,marked,now,StandClient.ticks(),from,0,List.of());
                    var c=new Call(id,owner,from,cast,List.copyOf(grounds));
                    CALLS.put(id,c);CASTS.put(id,cast);
                    cues(mc,c);
                });
            }else if(type==XCharaErase.BLASTED){
                int ground=b.readVarInt(),entity=b.readVarInt();float dealt=b.readFloat();
                if(!Float.isFinite(dealt))return;
                mc.execute(()->{
                    var c=CALLS.get(id);if(c==null||ground<0||ground>=c.grounds.size())return;
                    c.grounds.get(ground).blasted.putIfAbsent(entity,c.age(0));
                });
            }else if(type==XCharaErase.CUT){
                b.readLong();
                mc.execute(()->{var c=CALLS.get(id);if(c!=null&&c.cut==Float.MAX_VALUE)c.cut=Math.max(0,c.age(0));});
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(EraseClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(EraseClient::clear));
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    static void clear(){CALLS.clear();CASTS.clear();world=null;}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!CALLS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        var dim=mc.world.getRegistryKey().getValue();
        CALLS.values().removeIf(c->{
            float age=c.age(0);
            boolean gone=!c.cast.dimension().equals(dim)||age>EraseRules.BACK+EraseFx.AFTER*20||c.cutBy(age-EraseFx.CUT_LIFE*20);
            if(gone)CASTS.remove(c.id);
            return gone;
        });
    }
    /** The call {@code cast} is the cast of (EraseZone), or null. */
    static Call of(StandClient.Cast cast){var c=CALLS.get(cast.id());return c!=null&&c.cast==cast?c:null;}

    // ───────────────────────────── its sounds ─────────────────────────────

    /** Whether {@code p} stands on one of call {@code c}'s grounds (over it, near its top). */
    static boolean onOne(PlayerEntity p,Call c){
        var box=p.getBoundingBox();
        for(var g:c.grounds)if(EraseRules.over(g.x0,g.z0,box.minX,box.maxX,box.minZ,box.maxZ)&&Math.abs(p.getY()-g.top)<3)return true;
        return false;
    }
    /** The ground of call {@code c} nearest {@code at}, its middle {@code up} over its top. */
    static Vec3d nearest(Call c,Vec3d at,double up){
        Vec3d best=c.from;double d=Double.MAX_VALUE;
        for(var g:c.grounds){Vec3d m=g.middle().add(0,up,0);double q=m.squaredDistanceTo(at);if(q<d){d=q;best=m;}}
        return best;
    }
    /**
     * How loud one of its sounds at {@code at} is here: full for whoever stands on a marked ground if {@code marked} (the
     * "!!!"'s warning is theirs); for its user, full as far out as its grounds may be and a little (EraseRules.NEAR+8),
     * then fading over CARRY as for everyone else.
     */
    static float loudness(MinecraftClient mc,Call c,Vec3d at,boolean marked){
        if(mc.player==null||mc.world==null)return 0;
        float full=(float)CelestialFall.CONFIG.effectVolume;
        if(marked&&onOne(mc.player,c))return full;
        double d=mc.player.getPos().distanceTo(at);
        if(mc.player.getUuid().equals(c.owner))d=Math.max(0,d-(EraseRules.NEAR+8));
        return (float)(full*Math.max(0,1-d/CARRY));
    }
    private static void play(MinecraftClient mc,String sound,float part,int later,Call c,double up,boolean marked,String what){
        java.util.function.Supplier<Float> loud=()->c.cutBy(c.age(0))||mc.player==null?0f:loudness(mc,c,nearest(c,mc.player.getPos(),up),marked);
        var v=part>=1?new XCharaClient.Voice(CelestialFall.id(sound),loud):XCharaClient.Voice.quieter(CelestialFall.id(sound),loud,part);
        if(later>0)mc.getSoundManager().play(v,later);else if(v.full()>.005f)mc.getSoundManager().play(v);else return;
        KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, %s, played %d later at %.2f",sound,what,Math.max(0,later),v.full()));
    }
    /**
     * Its sounds by its clock, played as this client hears it is marked (each on the tick it is due, from then): the
     * "!!!"'s warning at once (heard late, not at all); the hack coming on as its cubes rise; as they burst, the boom, the
     * wall and the pull's burst; the double's coming as the ground is back. Cut short, those not yet begun are not heard.
     */
    private static void cues(MinecraftClient mc,Call c){
        int now=(int)Math.floor(c.age(0));
        if(now<=3)play(mc,WARN_SOUND,1f,0,c,0,true,"marked");
        Object[][] cues={{RISE_SOUND,EraseRules.RISE_FROM-1,RISE_PART,"rises",3.0},
            {BOOM_SOUND,EraseRules.BURST-1,1f,"bursts",EraseRules.LIFT},{BREAK_SOUND,EraseRules.BURST-1,1f,"bursts",EraseRules.LIFT},{GONE_SOUND,EraseRules.BURST,1f,"bursts",EraseRules.LIFT},
            {BACK_SOUND,EraseRules.BACK-2,RETURN_PART,"ground back",0.0}};
        for(var q:cues){
            int at=(int)q[1]-now;if(at<-3)continue;
            play(mc,(String)q[0],(float)q[2],Math.max(0,at),c,(double)q[4],false,(String)q[3]);
        }
    }

    // ───────────────────────────── for the camera and the tools ─────────────────────────────

    /** Where call {@code castId} shakes the camera from (KagenoCamera): its ground nearest this client's player, at its cube's top. Null if this is not one of them. */
    public static Vec3d front(UUID castId,float delta){
        var c=CALLS.get(castId);if(c==null)return null;
        var mc=MinecraftClient.getInstance();
        double lift=EraseRules.lift(c.age(delta));
        return nearest(c,mc.player==null?c.from:mc.player.getPos(),Double.isNaN(lift)?EraseRules.LIFT:lift);
    }
    /** For the capture tools: this player's latest call here as "age,grounds,cube top,cut,blasted,faces" ("-": none). */
    public static String describe(PlayerEntity player,float delta){
        Call best=null;for(var c:CALLS.values())if(c.owner.equals(player.getUuid()))best=c;
        if(best==null)return "-";
        long t=best.tick();double lift=EraseRules.lift(t);int blasted=0,faces=0;
        for(var g:best.grounds){blasted+=g.blasted.size();faces+=g.faces==null?0:g.faces.n;}
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%s,%s,%d,%d",best.age(delta),best.grounds.size(),EraseRules.cube(t)&&!Double.isNaN(lift)?String.format(java.util.Locale.ROOT,"+%.2f",lift):"-",
            best.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",best.cut),blasted,faces);
    }
    /** For the checks: every call here as "owner's first 8, age, its grounds (kind corner top under, where its "!!!" shows), cube lift, cut, blasted" lines. */
    public static List<String> all(float delta){
        var out=new ArrayList<String>();
        for(var c:CALLS.values()){
            long t=c.tick();double lift=EraseRules.lift(t);
            var gs=new StringBuilder();var hit=new ArrayList<Integer>();
            for(var g:c.grounds){gs.append(String.format(java.util.Locale.ROOT," [%s %d %d %.2f %d, !!! at %.2f %.2f %.2f]",g.kind==EraseRules.LOOK?"look":g.kind==EraseRules.PLAYER?"near":"random",g.x0,g.z0,g.top,g.under,g.bang.x,g.bang.y,g.bang.z));hit.addAll(g.blasted.keySet());}
            out.add(String.format(java.util.Locale.ROOT,"%s age %.1f grounds%s lift %s cut %s blasted %s",c.owner.toString().substring(0,8),c.age(delta),gs,
                EraseRules.cube(t)&&!Double.isNaN(lift)?String.format(java.util.Locale.ROOT,"%.2f",lift):"-",c.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",c.cut),hit));
        }
        return out;
    }
}
