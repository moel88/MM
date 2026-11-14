package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.vfx.FxLayer;
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

/**
 * X Chara's delete slash as this client knows it once it has gone (2.133.0; the server's XCharaErase, EraseRules). The
 * server tells of it as it goes (from where, the way, how far it goes), of everyone it deletes, and if it is cut short
 * (xchara_erase_event_v1). Each is a cast of its own skill ({@link #CASTS}, EraseRules.SKILL: EraseZone draws it through
 * KagenoRenderer), its clock the slash's own (from when it went), its origin where its user stood.
 * <p>
 * Whom it deletes is gone from sight for EraseRules.VANISH ticks from when this client hears of it ({@link #hides}):
 * glitching out (their body a flat silhouette for a frame or two, white, then purple, then a slipped copy), gone, and
 * glitching back in ({@link #silhouette}, drawn again through their own renderer, FlatBody); the effects round them are
 * EraseFx's. Its sounds there are ones the game has: X Chara's pull's burst as they are deleted (xchara_pull), the
 * double's coming as they are back (xchara_clone, part of it); loud for them and for its user, from where they are for
 * the rest (40 blocks).
 */
public final class EraseClient {
    /** Every slash this client knows of, by its id: a cast of its own skill (EraseRules.SKILL), its origin where its user stood, its caster its user. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One slash as this client knows it. */
    public static final class Wave {
        public final UUID id,owner;public final Vec3d from,f,right;public final float yaw;public final double range;public final StandClient.Cast cast;public final int seed;
        /** When it was cut short (ticks after it went, on its clock; Float.MAX_VALUE: it was not). */
        public float cut=Float.MAX_VALUE;
        /** Whom it deleted, by entity id, as this client heard of it. */
        final Map<Integer,Gone> deleted=new LinkedHashMap<>();
        /** Its ground as this client found it (EraseFx), once it is drawn. */
        EraseFx.Field field;
        Wave(UUID id,UUID owner,Vec3d from,float yaw,double range,StandClient.Cast cast){
            this.id=id;this.owner=owner;this.from=from;this.yaw=yaw;this.range=range;this.cast=cast;
            f=Vec3d.fromPolar(0,yaw);right=new Vec3d(-f.z,0,f.x);seed=(int)(id.getLeastSignificantBits()^id.getMostSignificantBits()>>>11);
        }
        /** Ticks since it went, on its clock. */
        public float age(float delta){return cast.age(delta);}
    }
    /** One deleted: whom (their entity id), by which slash, how old that slash was when this client heard of it, this client's tick then, what it dealt. */
    public record Gone(int entity,UUID wave,float at,int received,float dealt,int ticks){
        /** Seconds since this client heard of it. */
        public float since(float delta){return (StandClient.ticks()-received+delta)/20f;}
    }
    static final Map<UUID,Wave> WAVES=new LinkedHashMap<>();
    /** Everyone deleted now or a moment ago, by entity id (the latest). */
    private static final Map<Integer,Gone> GONE=new HashMap<>();
    /** How long one deleted is gone from sight (seconds): the server's. */
    static final float VANISH=EraseRules.VANISH/20f;
    /** How far its sounds there carry (blocks), and the double's coming's part. */
    static final double CARRY=40;static final float BACK_PART=.6f;
    static final String GONE_SOUND="xchara_pull",BACK_SOUND="xchara_clone";
    private static Object world;
    private EraseClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaErase.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID id=b.readUuid();
            if(type==XCharaErase.WAVE){
                UUID owner=b.readUuid();Identifier dim=b.readIdentifier();long went=b.readLong(),now=b.readLong();
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat(),range=b.readFloat();
                if(!finite(from)||!Float.isFinite(yaw)||!Float.isFinite(range)||range<0||range>EraseRules.RANGE+1||now<went||now-went>EraseRules.reached(EraseRules.RANGE)+20)return;
                mc.execute(()->{
                    if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    if(world!=mc.world){clear();world=mc.world;}
                    if(WAVES.containsKey(id))return;
                    var cast=new StandClient.Cast(id,owner,dim,EraseRules.SKILL,0,went,now,StandClient.ticks(),from,yaw,List.of());
                    WAVES.put(id,new Wave(id,owner,from,yaw,range,cast));CASTS.put(id,cast);
                });
            }else if(type==XCharaErase.DELETED){
                int entity=b.readVarInt();float dealt=b.readFloat();int ticks=b.readVarInt();
                if(!Float.isFinite(dealt)||ticks<0||ticks>200)return;
                mc.execute(()->{
                    var w=WAVES.get(id);if(w==null||w.deleted.containsKey(entity))return;
                    var g=new Gone(entity,id,w.age(0),StandClient.ticks(),dealt,ticks);
                    w.deleted.put(entity,g);GONE.put(entity,g);
                    sounds(mc,w,g);
                });
            }else if(type==XCharaErase.CUT){
                b.readLong();
                mc.execute(()->{var w=WAVES.get(id);if(w!=null&&w.cut==Float.MAX_VALUE)w.cut=Math.max(0,w.age(0));});
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(EraseClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(EraseClient::clear));
        WorldRenderEvents.AFTER_ENTITIES.register(EraseClient::render);
    }
    private static boolean finite(Vec3d v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    static void clear(){WAVES.clear();CASTS.clear();GONE.clear();world=null;}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!WAVES.isEmpty()||!GONE.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        var dim=mc.world.getRegistryKey().getValue();
        WAVES.values().removeIf(w->{
            float age=w.age(0);
            boolean gone=!w.cast.dimension().equals(dim)||age>EraseZone.LIFE*20+4;
            if(gone)CASTS.remove(w.id);
            return gone;
        });
        GONE.values().removeIf(g->g.since(0)>VANISH+2);
    }
    /** The slash {@code cast} is the cast of (EraseZone), or null. */
    static Wave of(StandClient.Cast cast){var w=WAVES.get(cast.id());return w!=null&&w.cast==cast?w:null;}

    // ───────────────────────────── whom it deleted ─────────────────────────────

    /** The deletion of this one, if it is still going on (gone from sight, or glitching back in). */
    static Gone gone(Entity e){
        if(e==null||GONE.isEmpty())return null;
        var g=GONE.get(e.getId());if(g==null)return null;
        var w=WAVES.get(g.wave());return w==null?null:g;
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
            if(WAVES.get(g.wave())==null)continue;
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

    /** How loud something at {@code at} is for this client: loud for whom it deleted and for its user, fading out to CARRY blocks for the rest. */
    private static float loudness(MinecraftClient mc,Wave w,Gone g){
        if(mc.player==null||mc.world==null)return 0;
        if(mc.player.getUuid().equals(w.owner)||mc.player.getId()==g.entity())return (float)CelestialFall.CONFIG.effectVolume;
        Vec3d at=mc.world.getEntityById(g.entity()) instanceof LivingEntity e?e.getPos():w.from;
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(at)/CARRY));
    }
    /** As one is deleted: the pull's burst now; the double's coming, part of it, as they are back (a tick before VANISH). */
    private static void sounds(MinecraftClient mc,Wave w,Gone g){
        if(mc.player==null)return;
        var burst=new XCharaClient.Voice(CelestialFall.id(GONE_SOUND),()->loudness(mc,w,g));
        if(burst.full()>.005f){mc.getSoundManager().play(burst);KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, deleted %d at %.2f",GONE_SOUND,g.entity(),burst.full()));}
        var back=XCharaClient.Voice.quieter(CelestialFall.id(BACK_SOUND),()->loudness(mc,w,g),BACK_PART);
        if(back.full()>.005f){mc.getSoundManager().play(back,Math.max(0,EraseRules.VANISH-1));KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s erase, back %d, played %d later at %.2f",BACK_SOUND,g.entity(),EraseRules.VANISH-1,back.full()));}
    }

    // ───────────────────────────── for the camera and the tools ─────────────────────────────

    /** Where the front of slash {@code castId} is now (its crown, a little over the ground), or null if this is not one of them: the camera shakes from there (KagenoCamera). */
    public static Vec3d front(UUID castId,float delta){
        var w=WAVES.get(castId);if(w==null)return null;
        double at=EraseRules.front(Math.max(0,Math.min(w.age(delta),w.cut)),w.range);
        return w.from.add(w.f.multiply(at+EraseFx.BOW*.5)).add(0,2,0);
    }
    /** For the capture tools: this player's latest slash here as "age,range,cut,deleted,columns,drawn pixels" ("-": none). */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        Wave best=null;for(var w:WAVES.values())if(w.owner.equals(player.getUuid()))best=w;
        if(best==null)return "-";
        return String.format(java.util.Locale.ROOT,"%.2f,%.1f,%s,%d,%d,%d",best.age(delta),best.range,best.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",best.cut),
            best.deleted.size(),best.field==null?-1:best.field.size(),EraseFx.pixelsLast());
    }
    /** For the checks: every slash here as "owner's first 8, age, range, from, deleted" lines, and whom it hides now. */
    public static List<String> all(float delta){
        var out=new ArrayList<String>();
        for(var w:WAVES.values())out.add(String.format(java.util.Locale.ROOT,"%s age %.1f range %.1f from (%.2f, %.2f, %.2f) yaw %.1f cut %s deleted %s",w.owner.toString().substring(0,8),w.age(delta),w.range,w.from.x,w.from.y,w.from.z,w.yaw,
            w.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",w.cut),w.deleted.keySet()));
        return out;
    }
    /** For the checks: whether this client hides this entity now (deleted), and its silhouette's colour (-1 none). */
    public static String seen(Entity e,float delta){
        var g=gone(e);if(g==null)return "-";
        float s=g.since(delta);return String.format(java.util.Locale.ROOT,"%.2f,%d,%d",s,s>=0&&s<VANISH?1:0,silhouette(s));
    }
}
