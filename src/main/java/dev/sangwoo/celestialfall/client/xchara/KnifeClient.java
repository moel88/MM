package dev.sangwoo.celestialfall.client.xchara;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.xchara.XCharaAttack;
import dev.sangwoo.celestialfall.xchara.XCharaCombo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * X Chara's basic attack as this client knows it (2.114.0; the server's XCharaAttack, XCharaCombo for its timing). The
 * attack key on the map is X Chara's player's knife: it asks the server for a hit ({@link #punch}; held down it keeps
 * asking, the server keeps a press made a little early for the next hit). The server tells when a hit starts, whom each
 * of its blows struck, when the attack rests and when a hit stops short; each hit is a swing ({@link #SWINGS}, a cast of
 * its own skill drawn with the effects: KnifeCombo through KagenoRenderer) whose clock (the server's age as it was told,
 * this client's ticks since and the frame's part of a tick) drives the body's motion ({@link #animated}, KagenoClips) and
 * everything drawn. A swing stopped short lets go of the body at once and draws nothing of the blows it never came to
 * ({@link #until}).
 * <p>
 * Its sounds are made for it (tools/xchara/make_m1_sounds.py: a knife's whooshes with a hacker's noises in them, no
 * recording): each hit's own (xchara_m1_1 to 4) is played a tick after the hit starts, so what is a tick short of a
 * blow in its file is heard as the blow lands; a blow that lands on someone is heard at once (xchara_m1_hit, a little
 * higher with every blow of the combo; the finisher's last its own, xchara_m1_hit_last). Loud for their user and whom
 * a blow struck, fading out to CARRY blocks for everyone else; a hit stopped short fades out over a few ticks.
 */
public final class KnifeClient {
    /** Every hit this client knows of, by its id: a cast of the knife's skill (XCharaCombo.SKILL), its combo the hit's number. */
    public static final Map<UUID,StandClient.Cast> SWINGS=new LinkedHashMap<>();
    /** Whom each blow of each swing struck, by the server's word (the entities' ids; null until it is told). */
    private static final Map<UUID,int[][]> STRUCK=new HashMap<>();
    /** The swings that stopped short, and how far into each (ticks) it did. */
    private static final Map<UUID,Float> STOPPED=new HashMap<>();
    /** When each player's attack is ready again after its rest (the server's world time), as told. */
    private static final Map<UUID,Long> READY=new HashMap<>();
    /** A swing told of later than this many ticks after it began is not drawn (it is nearly over). */
    static final int LATE=12;
    /** Each swing's sound as it plays (to fade it out if the swing stops short). */
    private static final Map<UUID,XCharaClient.Voice> VOICES=new HashMap<>();
    /** A swing's sound starts this many ticks into it (its file starts there: make_m1_sounds.py's LEAD); how far its sounds carry (blocks). */
    static final int SOUND_LEAD=1;static final double CARRY=40;
    /** For the capture tools: the sounds this client started, newest last (each "name hit/blow, ticks into its swing"). */
    public static final List<String> HEARD=new ArrayList<>();
    private static void heard(String line){HEARD.add(line);while(HEARD.size()>64)HEARD.remove(0);}
    /** The pitch of a blow's sound as it lands, by hit and blow: a little higher with every blow of the combo; the finisher's first cut low. */
    static final float[][] HIT_PITCH={{},{1f},{1.06f,1.12f},{1.2f,1.27f,1.35f},{.84f,1f}};
    private static int ticks,lastAsk=-100;
    private static Object world;
    private KnifeClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(XCharaAttack.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();Identifier dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int combo=b.readUnsignedByte(),blow=b.readByte();
            long start=b.readLong(),serverNow=b.readLong();Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>512||type>XCharaAttack.STOPPED||combo>XCharaCombo.DELETE||!Float.isFinite(yaw))return;
            var struck=new ArrayList<StandClient.Target>();
            for(int i=0;i<n;i++)struck.add(new StandClient.Target(b.readUuid(),b.readInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble())));
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck));
        });
        ClientTickEvents.START_CLIENT_TICK.register(KnifeClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(KnifeClient::clear));
    }
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world){clear();world=mc.world;}
        READY.put(caster,ready);
        if(type==XCharaAttack.HIT&&XCharaCombo.valid(combo)){
            if(serverNow-start>LATE)return;
            var cast=new StandClient.Cast(id,caster,dim,XCharaCombo.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            KagenoClips.begin(cast);
            SWINGS.put(id,cast);STRUCK.put(id,new int[XCharaCombo.blows(combo)][]);
            voice(mc,cast);
        }else if(type==XCharaAttack.LANDED){
            var cast=SWINGS.get(id);var told=STRUCK.get(id);
            if(cast==null||told==null||blow<0||blow>=told.length)return;
            told[blow]=struck.stream().mapToInt(StandClient.Target::entity).toArray();
            if(!struck.isEmpty()){
                landed(mc,cast,blow,struck);
                var all=new ArrayList<>(cast.targets());for(var t:struck)if(all.stream().noneMatch(o->o.entity()==t.entity()))all.add(t);
                SWINGS.put(id,new StandClient.Cast(cast.id(),cast.caster(),cast.dimension(),cast.skill(),cast.combo(),cast.start(),cast.serverNow(),cast.received(),cast.origin(),cast.yaw(),List.copyOf(all)));
            }
        }else if(type==XCharaAttack.STOPPED){
            // the swing in flight lets go of the body, and none of the blows it never came to is drawn; one stopped before
            // anything of it was to be seen is gone
            float age=Math.max(0,serverNow-start);
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.containsKey(c.id())&&animating(c)){
                STOPPED.put(c.id(),c.id().equals(id)?age:c.age(0));
                var v=VOICES.get(c.id());if(v!=null)v.fade();
            }
            SWINGS.values().removeIf(c->STOPPED.containsKey(c.id())&&STOPPED.get(c.id())<2);
        }
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(world!=null||!SWINGS.isEmpty())clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        var dim=mc.world.getRegistryKey().getValue();
        SWINGS.values().removeIf(c->!c.dimension().equals(dim)||c.age(0)>=KnifeCombo.LIFE*20||c.age(0)<0||gone(mc,c));
        STRUCK.keySet().retainAll(SWINGS.keySet());STOPPED.keySet().retainAll(SWINGS.keySet());VOICES.keySet().retainAll(SWINGS.keySet());
        // held down, the attack key keeps asking (the server keeps a press made a little early for the next hit)
        if(mc.player!=null&&mc.currentScreen==null&&mc.options.attackKey.isPressed()&&attacks(mc.player))punch();
    }
    /** A swing whose caster is dead or X Chara no more. */
    private static boolean gone(MinecraftClient mc,StandClient.Cast c){
        var p=mc.world.getPlayerByUuid(c.caster());return p!=null&&(!p.isAlive()||!StandManager.xChara(p));
    }
    public static void clear(){
        for(var v:VOICES.values())v.fade();
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();VOICES.clear();KnifeCombo.clear();world=null;
    }

    // ───────────────────────────── sounds ─────────────────────────────

    /** How loud something at {@code from} is for this client now (before the game's volume settings): fading out to CARRY blocks. */
    static float loudness(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;
        return (float)(dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume*Math.max(0,1-mc.player.getPos().distanceTo(from)/CARRY));
    }
    /** The hit's own sound: a tick into it (its file starts there), loud for its user, for everyone else from where its user is. */
    private static void voice(MinecraftClient mc,StandClient.Cast cast){
        if(mc.world==null||mc.player==null)return;
        UUID caster=cast.caster();Vec3d origin=cast.origin();
        java.util.function.Supplier<Float> loud=caster.equals(mc.player.getUuid())?()->(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume:()->{
            var m=MinecraftClient.getInstance();var user=m.world==null?null:m.world.getPlayerByUuid(caster);
            return loudness(m,user==null?origin:user.getPos());
        };
        var v=new XCharaClient.Voice(dev.sangwoo.celestialfall.CelestialFall.id("xchara_m1_"+cast.combo()),loud);
        if(v.full()<=.01f)return;
        // told of a tick or more into it: at once (a tick late at most is not heard as late; later, LATE has dropped it)
        int wait=SOUND_LEAD-(int)Math.floor(cast.age(0));
        if(wait>0)mc.getSoundManager().play(v,wait);else mc.getSoundManager().play(v);
        VOICES.put(cast.id(),v);
        heard(String.format(java.util.Locale.ROOT,"xchara_m1_%d, told %.0f ticks in, played %d later at %.2f",cast.combo(),cast.age(0),Math.max(0,wait),v.full()));
    }
    /** A blow landing on someone: heard at once, from the nearer of its user and whom it struck (loud for both). */
    private static void landed(MinecraftClient mc,StandClient.Cast cast,int blow,List<StandClient.Target> struck){
        if(mc.world==null||mc.player==null||!XCharaCombo.valid(cast.combo()))return;
        boolean mine=cast.caster().equals(mc.player.getUuid());Vec3d from=struck.get(0).position();
        for(var t:struck){if(t.entity()==mc.player.getId())mine=true;if(t.position().squaredDistanceTo(mc.player.getPos())<from.squaredDistanceTo(mc.player.getPos()))from=t.position();}
        var user=mc.world.getPlayerByUuid(cast.caster());
        if(user!=null&&user.getPos().squaredDistanceTo(mc.player.getPos())<from.squaredDistanceTo(mc.player.getPos()))from=user.getPos();
        float loud=mine?(float)dev.sangwoo.celestialfall.CelestialFall.CONFIG.effectVolume:loudness(mc,from);if(loud<=.01f)return;
        boolean last=XCharaCombo.launches(cast.combo(),blow);
        heard(String.format(java.util.Locale.ROOT,"%s hit %d blow %d, %.0f ticks in, at %.2f",last?"xchara_m1_hit_last":"xchara_m1_hit",cast.combo(),blow,cast.age(0),loud));
        mc.getSoundManager().play(new XCharaClient.Voice(dev.sangwoo.celestialfall.CelestialFall.id(last?"xchara_m1_hit_last":"xchara_m1_hit"),()->loud,last?1:HIT_PITCH[cast.combo()][blow]));
    }

    /** Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short). */
    static boolean animating(StandClient.Cast c){return c.age(0)<KnifeCombo.clipOf(c.combo()).length()*20+1;}
    /** The latest swing of this player whose body clip still plays and did not stop short (KagenoClips.animated), or null. */
    public static StandClient.Cast animated(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&!STOPPED.containsKey(c.id())&&animating(c))best=c;
        return best;
    }
    /** How far into swing {@code id} (seconds) its blows are drawn: all of it, or until it stopped short. */
    public static float until(UUID id){var s=STOPPED.get(id);return s==null?Float.MAX_VALUE:s/20f;}
    /** Whether swing {@code id} came to its blow {@code i}: it did not stop short before it. */
    static boolean cameTo(StandClient.Cast c,int i){return XCharaCombo.blowAt(c.combo(),i)/20f<=until(c.id())+1e-4f;}
    /** Whom blow {@code i} of swing {@code id} struck, by the server's word (null until it is told). */
    static int[] struck(UUID id,int i){var t=STRUCK.get(id);return t==null||i<0||i>=t.length?null:t[i];}
    /**
     * Whom blow {@code i} of swing {@code c} struck, for the effects: the server's word once it is told; until then
     * (a tick or two) whoever stands in the blow's way on this client, as the server finds them (XCharaCombo.lane; one
     * with a guard up is struck by none of the first three hits).
     */
    static List<LivingEntity> struckBy(MinecraftClient mc,StandClient.Cast c,int i){
        var out=new ArrayList<LivingEntity>();if(mc.world==null)return out;
        int[] told=struck(c.id(),i);
        if(told!=null){for(int id:told)if(mc.world.getEntityById(id) instanceof LivingEntity e)out.add(e);return out;}
        var user=mc.world.getPlayerByUuid(c.caster());Vec3d at=user==null?c.origin():user.getPos();
        Vec3d f=Vec3d.fromPolar(0,c.yaw()),right=new Vec3d(-f.z,0,f.x);
        for(var e:mc.world.getEntitiesByClass(LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(XCharaCombo.MAX_REACH+3),
            e->e!=user&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);
            // (one holding a guard up takes the first three hits on it: struck by none of them)
            if(!XCharaCombo.unblockable(c.combo())&&guarding(e,mc.world.getTime()))continue;
            if(XCharaCombo.lane(c.combo(),i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5))out.add(e);
        }
        return out;
    }
    /** Whether this one holds a guard up now, as this client knows it (a player's combat state, a training dummy's). */
    private static boolean guarding(net.minecraft.entity.Entity e,long now){
        if(e instanceof dev.sangwoo.celestialfall.training.TrainingDummyEntity d)return d.guarding();
        if(e instanceof PlayerEntity p){var state=CombatClient.snapshot(p.getUuid());return state.guard()&&!state.broken(now);}
        return false;
    }
    /** The tick this player's attack is ready again after its rest, as the server told it (0 if none). */
    public static long ready(UUID id){return READY.getOrDefault(id,0L);}
    /** Forgets this player's rest (their character is not X Chara now: the server forgot it too, StandManager). */
    public static void forget(UUID id){READY.remove(id);}
    /**
     * Whether a hit holds the local player where they stand now (XCharaCombo.HOLD): their keys do not walk them. The
     * server holds them as long (XCharaAttack.busy).
     */
    public static boolean movementLocked(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;
        for(var c:SWINGS.values())if(c.caster().equals(mc.player.getUuid())&&!STOPPED.containsKey(c.id())&&c.age(0)<XCharaCombo.HOLD[c.combo()])return true;
        return false;
    }
    /** Whether this player's attack key swings X Chara's knife now: theirs, on the map, holding neither the meteor pickaxe nor a fruit. */
    public static boolean attacks(PlayerEntity p){
        return p!=null&&MapClient.enabled()&&StandManager.xChara(p)&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand()
            &&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand();
    }
    /** The attack key: asks the server for a hit (at most every other tick). Returns whether the knife takes the key (nothing else happens). */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();if(!attacks(mc.player))return false;
        if(CombatClient.combatInput()&&ticks-lastAsk>=2&&ClientPlayNetworking.canSend(XCharaAttack.INPUT)){
            BotandCamera.prepareInteraction();
            ClientPlayNetworking.send(XCharaAttack.INPUT,PacketByteBufs.create());lastAsk=ticks;
        }
        return true;
    }
    /** For the capture tools: this player's latest swing as "hit,age,stopped", or null. */
    public static String describe(PlayerEntity p,float delta){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(p.getUuid()))best=c;
        return best==null?null:String.format(java.util.Locale.ROOT,"%d,%.2f,%d",best.combo(),best.age(delta),STOPPED.containsKey(best.id())?1:0);
    }
}
