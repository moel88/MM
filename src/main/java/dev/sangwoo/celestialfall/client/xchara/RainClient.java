package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.xchara.RainRules;
import dev.sangwoo.celestialfall.xchara.XCharaRain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * X Chara's rain of knives as this client knows it as it rains (2.132.5; the server's XCharaRain, RainRules). The
 * server tells of a rain as it is called and again once a second (one who comes near later learns of it too), of every
 * knife that met a body, and as it is over or cut short (xchara_rain_event_v1). Each rain is a cast of its own skill
 * ({@link #CASTS}, RainRules.SKILL: RainZone draws it through KagenoRenderer as long as it rains) whose clock is its
 * call's; every knife of it is worked out from its seed as the server's are (RainFx).
 * <p>
 * Its sound, one the game has (the user's "있는 소리 재사용", 2.132.0): the knives shot's strike (xchara_shot_hit) at a
 * part of its loudness as a knife strikes a foe, from where they are (40 blocks).
 */
public final class RainClient {
    /** Every rain this client knows of, by its id: a cast of its own skill (RainRules.SKILL), its origin its middle, its caster its user. */
    public static final Map<UUID,StandClient.Cast> CASTS=new LinkedHashMap<>();
    /** One rain as this client knows it. */
    public static final class Rain {
        public final UUID id,owner;public final Vec3d middle;public final int seed;public final StandClient.Cast cast;
        /** When it was cut short (ticks after its call, on its clock; Float.MAX_VALUE: it was not), whether the server said it is over, and the last tick (this client's) it was heard of. */
        public float cut=Float.MAX_VALUE;boolean ended;int heard;
        /** The knives the server said met a body: by knife, {the entity's id, how high over their feet, the harm it did}. */
        final Map<Integer,float[]> told=new HashMap<>();
        /** Its knives as this client has worked them out (RainFx). */
        final RainFx.Knives knives=new RainFx.Knives();
        Rain(UUID id,UUID owner,Vec3d middle,int seed,StandClient.Cast cast){this.id=id;this.owner=owner;this.middle=middle;this.seed=seed;this.cast=cast;}
        public float age(float delta){return cast.age(delta);}
    }
    static final Map<UUID,Rain> RAINS=new LinkedHashMap<>();
    /** A rain not heard of again for this many ticks is gone here (out of its news' reach, or over). */
    static final int FORGET=50;
    /** A knife's strike on a foe sounds at this part of the knives shot's, and carries this far (blocks). */
    static final float STRIKE_PART=.35f;static final double CARRY=40;
    static final String STRIKE_SOUND="xchara_shot_hit";
    private static int ticks;private static Object world;
    private RainClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaRain.EVENT,(mc,handler,b,response)->{
            int type=b.readUnsignedByte();UUID id=b.readUuid();
            if(type==XCharaRain.START||type==XCharaRain.STATE){
                UUID owner=b.readUuid();Identifier dim=b.readIdentifier();long start=b.readLong(),now=b.readLong();
                Vec3d middle=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());int seed=b.readInt();
                if(!Double.isFinite(middle.x)||!Double.isFinite(middle.y)||!Double.isFinite(middle.z)||now<start||now-start>RainRules.LAST+40)return;
                mc.execute(()->{
                    if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
                    if(world!=mc.world){clear();world=mc.world;}
                    var r=RAINS.get(id);
                    if(r==null){
                        var cast=new StandClient.Cast(id,owner,dim,RainRules.SKILL,0,start,now,StandClient.ticks(),middle,0,List.of());
                        r=new Rain(id,owner,middle,seed,cast);
                        RAINS.put(id,r);CASTS.put(id,cast);
                    }
                    r.heard=ticks;
                });
            }else if(type==XCharaRain.STRUCK){
                int k=b.readVarInt(),entity=b.readVarInt();float height=b.readFloat(),dealt=b.readFloat();
                if(k<0||k>=RainRules.BLADES||!Float.isFinite(height)||!Float.isFinite(dealt))return;
                mc.execute(()->{
                    var r=RAINS.get(id);if(r==null||r.told.containsKey(k))return;
                    r.told.put(k,new float[]{entity,height,dealt});
                    if(dealt>0)strike(mc,r,entity);
                });
            }else if(type==XCharaRain.ENDED||type==XCharaRain.CUT){
                b.readLong();
                mc.execute(()->{
                    var r=RAINS.get(id);if(r==null)return;
                    if(type==XCharaRain.CUT&&r.cut==Float.MAX_VALUE)r.cut=Math.max(0,r.age(0));
                    r.ended=true;
                });
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(RainClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(RainClient::clear));
    }
    static void clear(){RAINS.clear();CASTS.clear();world=null;}
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!RAINS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        var dim=mc.world.getRegistryKey().getValue();
        RAINS.values().removeIf(r->{
            float age=r.age(0);
            // over (what its last knives leave gone), cut short a while ago, out of its news' reach, or elsewhere
            boolean gone=!r.cast.dimension().equals(dim)||age>RainRules.LAST+20||age>r.cut+40||!r.ended&&ticks-r.heard>FORGET;
            if(gone)CASTS.remove(r.id);
            return gone;
        });
    }
    /** The rain {@code cast} is the cast of (RainZone), or null. */
    static Rain of(StandClient.Cast cast){var r=RAINS.get(cast.id());return r!=null&&r.cast==cast?r:null;}
    /** A knife struck a foe: the knives shot's strike at a part of its loudness, from where they are (40 blocks). */
    private static void strike(MinecraftClient mc,Rain r,int entity){
        if(mc.world==null||mc.player==null)return;
        Vec3d at=mc.world.getEntityById(entity) instanceof LivingEntity e?e.getPos():r.middle;
        java.util.function.Supplier<Float> loud=()->(float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-(mc.player==null?CARRY:mc.player.getPos().distanceTo(at))/CARRY));
        var v=XCharaClient.Voice.quieter(CelestialFall.id(STRIKE_SOUND),loud,STRIKE_PART);
        if(v.full()<=.005f)return;
        mc.getSoundManager().play(v);
        KnifeClient.heard(String.format(java.util.Locale.ROOT,"%s rain, struck %d at %.2f",STRIKE_SOUND,entity,v.full()));
    }
    /** For the capture tools: this player's latest rain here as "age,cut,knives falling or standing,standing in bodies,of them told,told in all,drawn the last frame,microseconds the last frame's knives took" ("-": none). */
    public static String describe(net.minecraft.entity.player.PlayerEntity player,float delta){
        Rain best=null;for(var r:RAINS.values())if(r.owner.equals(player.getUuid()))best=r;
        if(best==null)return "-";
        float age=best.age(delta);int[] st=RainFx.standing(best,age);
        return String.format(java.util.Locale.ROOT,"%.2f,%d,%d,%d,%d,%d,%d,%d",age,best.cut==Float.MAX_VALUE?-1:Math.round(best.cut),RainFx.shown(best,age),st[0],st[1],best.told.size(),RainFx.drawnLast(),RainFx.microsLast());
    }
    /** For the checks: every rain here as "owner's first 8,age,middle,told" lines. */
    public static List<String> all(float delta){
        var out=new ArrayList<String>();
        for(var r:RAINS.values())out.add(String.format(java.util.Locale.ROOT,"%s age %.1f middle (%.2f, %.2f, %.2f) cut %s told %d",r.owner.toString().substring(0,8),r.age(delta),r.middle.x,r.middle.y,r.middle.z,r.cut==Float.MAX_VALUE?"-":String.format(java.util.Locale.ROOT,"%.1f",r.cut),r.told.size()));
        return out;
    }
}
