package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
import dev.sangwoo.celestialfall.xchara.EraseCubes;
import dev.sangwoo.celestialfall.xchara.EraseRules;
import dev.sangwoo.celestialfall.xchara.XCharaErase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * X Chara's delete slash as this client knows it once it is thrown (2.133.0; the server's XCharaErase, EraseRules). The
 * server tells of it as it is thrown (from the knife to the ground it lands on, the tick it strikes, its ground's corner),
 * of everyone it deletes, everyone its cube's burst strikes, and if it is cut short (xchara_erase_event_v1). Each is a cast
 * of its own skill ({@link #CASTS}, EraseRules.SKILL: EraseZone draws it through KagenoRenderer), its clock the slash's
 * own (from when it was thrown), its origin the ground it lands on.
 * <p>
 * Its cube of ground is something to stand on here too ({@link #SIDE}, EraseCubes): this client's own player stands on
 * it and is carried up as it rises, on this client's clock (ticks since it struck, a whole number for the tick being run).
 * <p>
 * Whom it deletes is gone from sight for EraseRules.VANISH ticks from when this client hears of it ({@link #hides}):
 * glitching out (their body a flat silhouette for a frame or two, white, then purple, then a slipped copy), gone, and
 * glitching back in ({@link #silhouette}, drawn again through their own renderer, FlatBody); the effects round them are
 * EraseFx's. Its sounds are ones the game has: where it lands, the slam's landing boom and the double's wall breaking;
 * as one is deleted, the pull's burst, and the double's coming as they are back; as its cube rises, the hack coming on;
 * as it bursts, the boom, the wall and the pull's burst; as the ground is back, the double's coming (part of it); loud
 * for its user and whom it strikes, from where it is for the rest (40 blocks).
 */
public final class EraseClient {
    /** Every slash this client knows of, by its id: a cast of its own skill (EraseRules.SKILL), its origin the ground it lands on, its caster its user. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One slash as this client knows it. */
    public static final class Strike {
        public final UUID id,owner;public final Vec3d from,to;public final int fly,x0,z0;public final double top;public final StandClient.Cast cast;public final int seed;
        /** Its way (unit, from the knife to the ground), the upright of the plane it stands in (unit, world up turned a little about its heading), the plane's side (unit), and its heading (unit, level: its cutting edge leads that way). */
        public final Vec3d way,upright,side,head;
        /** When it was cut short (ticks after it was thrown, on its clock; Float.MAX_VALUE: it was not). */
        public float cut=Float.MAX_VALUE;
        /** Whom it deleted, and whom its burst struck, by entity id, as this client heard of it. */
        final Map<Integer,Gone> deleted=new LinkedHashMap<>();final Map<Integer,Float> blasted=new LinkedHashMap<>();
        /** Its cube's faces as this client found them (EraseFx), once it is drawn. */
        EraseFx.Faces faces;
        Strike(UUID id,UUID owner,Vec3d from,Vec3d to,int fly,int x0,int z0,StandClient.Cast cast){
            this.id=id;this.owner=owner;this.from=from;this.to=to;this.fly=fly;this.x0=x0;this.z0=z0;top=to.y;this.cast=cast;
            seed=(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>11);
            Vec3d[] f=EraseFx.frame(from,to,cast.yaw());way=f[0];upright=f[1];side=f[2];head=f[3];
        }
        /** Ticks since it was thrown, on its clock. */
        public float age(float delta){return cast.age(delta);}
        /** Ticks since it struck (negative before), on its clock: a whole number for the tick being run. */
        public long struckTick(){return (long)Math.floor(cast.age(0)+1e-4)-fly;}
        /** Whether it has been cut short by {@code age}. */
        boolean cutBy(float age){return age>=cut;}
        /** The middle of its ground, at its top. */
        public Vec3d middle(){return new Vec3d(x0+EraseRules.SIDE*.5,top,z0+EraseRules.SIDE*.5);}
    }
    /** One deleted: whom (their entity id), by which slash, how old that slash was when this client heard of it, this client's tick then, what it dealt. */
    public record Gone(int entity,UUID wave,float at,int received,float dealt,int ticks){
        /** Seconds since this client heard of it. */
        public float since(float delta){return (StandClient.ticks()-received+delta)/20f;}
    }
    static final Map<UUID,Strike> STRIKES=new LinkedHashMap<>();
    /** Everyone deleted now or a moment ago, by entity id (the latest). */
    private static final Map<Integer,Gone> GONE=new HashMap<>();
    /** How long one deleted is gone from sight (seconds): the server's. */
    static final float VANISH=EraseRules.VANISH/20f;
    /** How far its sounds carry (blocks), and the parts of some. */
    static final double CARRY=40;static final float BACK_PART=.6f,BREAK_PART=.8f,RISE_PART=.8f,RETURN_PART=.7f;
    static final String GONE_SOUND="xchara_pull",BACK_SOUND="xchara_clone",BOOM_SOUND="xchara_slam_land",BREAK_SOUND="xchara_clone_wall",RISE_SOUND="xchara_q_on";
    private static Object world;
    /** This client's cubes, for EraseCubes (its own player stands on them). */
    static final EraseCubes.Side SIDE=new EraseCubes.Side(){
        @Override public boolean any(){return !STRIKES.isEmpty();}
        @Override public void cubes(World w,List<EraseCubes.Cube> out){
            if(w!=world)return;
            for(var s:STRIKES.values()){
                long t=s.struckTick();
                if(s.cutBy(s.age(0))||!EraseRules.cube(t))continue;
                out.add(new EraseCubes.Cube(s.x0,s.z0,s.top,t));
            }
        }
    };
    private EraseClient(){}

    public static void initialize(){
        EraseCubes.client(SIDE);
        ClientPlayNetworking.registerGlobalReceiver(XCharaErase.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID id=b.readUuid();
            if(type==XCharaErase.THROWN){
                UUID owner=b.readUuid();Identifier dim=b.readIdentifier();long went=b.readLong(),now=b.readLong();
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),to=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                long struck=b.readLong();int x0=b.readInt(),z0=b.readInt();
                int fly=(int)(struck-went);
                if(!finite(from)||!finite(to)||fly<0||fly>EraseRules.FLY_MAX+2||now<went||now-went>fly+EraseRules.BACK+20)return;
                mc.execute(()->{
                    if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    if(world!=mc.world){clear();world=mc.world;}
                    if(STRIKES.containsKey(id))return;
                    float yaw=(float)Math.toDegrees(Math.atan2(-(to.x-from.x),to.z-from.z));
                    var cast=new StandClient.Cast(id,owner,dim,EraseRules.SKILL,0,went,now,StandClient.ticks(),to,yaw,List.of());
                    var s=new Strike(id,owner,from,to,fly,x0,z0,cast);
                    STRIKES.put(id,s);CASTS.put(id,cast);
                    cues(mc,s);
                });
            }else if(type==XCharaErase.DELETED||type==XCharaErase.BLASTED){
                int entity=b.readVarInt();float dealt=b.readFloat();int ticks=b.readVarInt();
                if(!Float.isFinite(dealt)||ticks<0||ticks>200)return;
                mc.execute(()->{
                    var s=STRIKES.get(id);if(s==null)return;
                    if(type==XCharaErase.BLASTED){s.blasted.putIfAbsent(entity,s.age(0));return;}
                    if(s.deleted.containsKey(entity))return;
                    var g=new Gone(entity,id,s.age(0),StandClient.ticks(),dealt,ticks);
                    s.deleted.put(entity,g);GONE.put(entity,g);
                    deletedSounds(mc,s,g);
                });
            }else if(type==XCharaErase.CUT){
                b.readLong();
                mc.execute(()->{var s=STRIKES.get(id);if(s!=null&&s.cut==Float.MAX_VALUE)s.cut=Math.max(0,s.age(0));});
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(EraseClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(EraseClient::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(EraseClient::render);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    static void clear(){STRIKES.clear();CASTS.clear();GONE.clear();world=null;}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!STRIKES.isEmpty()||!GONE.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        var dim=mc.world.getRegistryKey().getValue();
        STRIKES.values().removeIf(s->{
            float age=s.age(0);
            boolean gone=!s.cast.dimension().equals(dim)||age>s.fly+EraseRules.BACK+EraseFx.AFTER*20||s.cutBy(age-EraseFx.CUT_LIFE*20);
            if(gone)CASTS.remove(s.id);
            return gone;
        });
        GONE.values().removeIf(g->g.since(0)>VANISH+2);
    }
    /** The slash {@code cast} is the cast of (EraseZone), or null. */
    static Strike of(StandClient.Cast cast){var s=STRIKES.get(cast.id());return s!=null&&s.cast==cast?s:null;}

    // ───────────────────────────── whom it deleted ─────────────────────────────

    /** The deletion of this one, if it is still going on (gone from sight, or glitching back in). */
    static Gone gone(Entity e){
        if(e==null||GONE.isEmpty())return null;
        var g=GONE.get(e.getId());if(g==null)return null;
        return STRIKES.get(g.wave())==null?null:g;
    }
    /** Whether this one is not to be drawn as they are now (EntityRenderer.shouldRender): deleted, for VANISH from when this client heard of it. */
    public static boolean hides(Entity e){
        if(GONE.isEmpty()||!MapClient.enabled())return false;
        var g=gone(e);if(g==null)return false;
        float s=g.since(MinecraftClient.getInstance().getTickDelta());
        return s>=0&&s<VANISH;
    }
    /** The silhouettes: white as it strikes, then X Chara's purple, a lilac as they are back. */
    static final int STRUCK=0xeedcff,FLAT=0x7c30ff,BACK=0xc890ff;
    /**
     * The colour this one's flat silhouette is {@code s} seconds after they were deleted (-1: none, they are gone), by
     * frames of a sixtieth: white for two as it strikes, gone, purple for two, gone, purple once slipped aside, gone for
     * a quarter of a second, then purple, gone, lilac, gone, and themselves again at VANISH.
     */
    static int silhouette(float s){
        if(s<0||s>=VANISH)return -1;
        int k=(int)Math.floor(s*60+1e-3);
        if(k<=1)return STRUCK;
        if(k==3||k==4||k==7||k==24||k==25)return FLAT;
        if(k==27||k==28)return BACK;
        return -1;
    }
    /** How far aside (blocks, along the camera's right) the silhouette slips {@code s} seconds after (one frame of it). */
    static double slip(float s){int k=(int)Math.floor(s*60+1e-3);return k==7?.32:0;}
    /** Draws the ones deleted as their flat silhouette, the frames they are it (not this client's own body in the first person; not where a shader pack draws the world). */
    private static void render(WorldRenderContext ctx){
        var mc=MinecraftClient.getInstance();
        if(GONE.isEmpty()||mc.world==null||!(ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate)||!FxLayer.available())return;
        float delta=ctx.tickDelta();Vec3d cam=ctx.camera().getPos();
        boolean drew=false;
        for(var g:GONE.values()){
            if(STRIKES.get(g.wave())==null)continue;
            int rgb=silhouette(g.since(delta));if(rgb<0)continue;
            if(!(mc.world.getEntityById(g.entity()) instanceof LivingEntity e)||!e.isAlive()||e.isInvisible())continue;
            if(e==mc.getCameraEntity()&&!ctx.camera().isThirdPerson())continue;
            Vec3d at=e.getLerpedPos(delta);
            if(at.squaredDistanceTo(cam)>96*96)continue;
            double side=slip(g.since(delta));
            if(side!=0){var f=GlitchFx.facing(cam,at.add(0,e.getHeight()*.5,0));at=at.add(f[0].multiply(side));}
            FlatBody.draw(mc,ctx.matrixStack(),immediate,cam,e,at,delta,rgb,1.035f);
            drew=true;
        }
        if(drew)immediate.draw(RenderLayer.getDebugQuads());
    }

    // ───────────────────────────── its sounds ─────────────────────────────

    /** How loud something of slash {@code s} at {@code at} is for this client: loud for its user and for {@code entity} (-1: no one), fading out to CARRY blocks for the rest. */
    private static float loudness(MinecraftClient mc,Strike s,int entity,Vec3d at){
        if(mc.player==null||mc.world==null)return 0;
        if(mc.player.getUuid().equals(s.owner)||mc.player.getId()==entity)return (float)CelestialFall.CONFIG.effectVolume;
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(at)/CARRY));
    }
    private static void play(MinecraftClient mc,String sound,float part,int later,Strike s,java.util.function.Supplier<Vec3d> at,String what){
        java.util.function.Supplier<Float> loud=()->s.cutBy(s.age(0))?0f:loudness(mc,s,-1,at.get());
        var v=part>=1?new XCharaClient.Voice(CelestialFall.id(sound),loud):XCharaClient.Voice.quieter(CelestialFall.id(sound),loud,part);
        if(later>0)mc.getSoundManager().play(v,later);else if(v.full()>.005f)mc.getSoundManager().play(v);else return;
        KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, %s, played %d later at %.2f",sound,what,Math.max(0,later),v.full()));
    }
    /**
     * Its sounds by its clock, played as this client hears it is thrown (each on the tick it is due, from then): where it
     * lands, a tick before it strikes (the onsets on it): the slam's boom and the double's wall breaking; the hack coming on
     * as its cube rises; as it bursts, the boom, the wall and the pull's burst; the double's coming as the ground is back.
     * Cut short, those not yet begun are not heard.
     */
    private static void cues(MinecraftClient mc,Strike s){
        int now=(int)Math.floor(s.age(0));
        Object[][] cues={{BOOM_SOUND,s.fly-1,1f,"lands"},{BREAK_SOUND,s.fly-1,BREAK_PART,"lands"},{RISE_SOUND,s.fly+EraseRules.RISE_FROM-1,RISE_PART,"rises"},
            {BOOM_SOUND,s.fly+EraseRules.BURST-1,1f,"bursts"},{BREAK_SOUND,s.fly+EraseRules.BURST-1,1f,"bursts"},{GONE_SOUND,s.fly+EraseRules.BURST,1f,"bursts"},
            {BACK_SOUND,s.fly+EraseRules.BACK-2,RETURN_PART,"ground back"}};
        for(var q:cues){
            int at=(int)q[1]-now;if(at<-3)continue;
            boolean high="rises".equals(q[3])||"bursts".equals(q[3]);
            play(mc,(String)q[0],(float)q[2],Math.max(0,at),s,()->high?s.middle().add(0,EraseRules.LIFT,0):s.middle(),(String)q[3]);
        }
    }
    /** As one is deleted: the pull's burst now; the double's coming, part of it, as they are back (a tick before VANISH). */
    private static void deletedSounds(MinecraftClient mc,Strike s,Gone g){
        if(mc.player==null)return;
        java.util.function.Supplier<Vec3d> at=()->mc.world.getEntityById(g.entity()) instanceof LivingEntity e?e.getPos():s.middle();
        var burst=new XCharaClient.Voice(CelestialFall.id(GONE_SOUND),()->loudness(mc,s,g.entity(),at.get()));
        if(burst.full()>.005f){mc.getSoundManager().play(burst);KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, deleted %d at %.2f",GONE_SOUND,g.entity(),burst.full()));}
        var back=XCharaClient.Voice.quieter(CelestialFall.id(BACK_SOUND),()->loudness(mc,s,g.entity(),at.get()),BACK_PART);
        if(back.full()>.005f){mc.getSoundManager().play(back,Math.max(0,EraseRules.VANISH-1));KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, back %d, played %d later at %.2f",BACK_SOUND,g.entity(),EraseRules.VANISH-1,back.full()));}
    }

    // ───────────────────────────── for the camera and the tools ─────────────────────────────

    /** Where slash {@code castId} shakes the camera from (KagenoCamera): the slash as it flies, then its ground (its cube, up where it is). Null if this is not one of them. */
    public static Vec3d front(UUID castId,float delta){
        var s=STRIKES.get(castId);if(s==null)return null;
        float age=s.age(delta);
        if(age<s.fly)return EraseFx.tip(s,age);
        double lift=EraseRules.lift(age-s.fly);
        return s.middle().add(0,Double.isNaN(lift)?EraseRules.LIFT:lift,0);
    }
    /** For the capture tools: this player's latest slash here as "age,fly,struck tick,cube top,cut,deleted,blasted,faces" ("-": none). */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        Strike best=null;for(var s:STRIKES.values())if(s.owner.equals(player.getUuid()))best=s;
        if(best==null)return "-";
        long t=best.struckTick();double lift=EraseRules.lift(t);
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%s,%s,%d,%d,%d",best.age(delta),best.fly,t,EraseRules.cube(t)&&!Double.isNaN(lift)?String.format(java.util.Locale.ROOT,"%.2f",best.top+lift):"-",
            best.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",best.cut),best.deleted.size(),best.blasted.size(),best.faces==null?-1:best.faces.n);
    }
    /** For the checks: every slash here as "owner's first 8, age, to, corner, struck tick, cube top, deleted, blasted" lines. */
    public static List<String> all(float delta){
        var out=new ArrayList<String>();
        for(var s:STRIKES.values()){
            long t=s.struckTick();double lift=EraseRules.lift(t);
            out.add(String.format(java.util.Locale.ROOT,"%s age %.1f to (%.2f, %.2f, %.2f) corner %d %d struck %d cube %s cut %s deleted %s blasted %s",s.owner.toString().substring(0,8),s.age(delta),s.to.x,s.to.y,s.to.z,s.x0,s.z0,t,
                EraseRules.cube(t)&&!Double.isNaN(lift)?String.format(java.util.Locale.ROOT,"%.2f",s.top+lift):"-",s.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",s.cut),s.deleted.keySet(),s.blasted.keySet()));
        }
        return out;
    }
    /** For the checks: whether this client hides this entity now (deleted), and its silhouette's colour (-1 none). */
    public static String seen(Entity e,float delta){
        var g=gone(e);if(g==null)return "-";
        float s=g.since(delta);return String.format(java.util.Locale.ROOT,"%.2f,%d,%d",s,s>=0&&s<VANISH?1:0,silhouette(s));
    }
}
