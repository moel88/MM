package dev.sangwoo.celestialfall.client.isosang;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.client.StandClient;
import dev.sangwoo.celestialfall.client.kageno.KagenoClips;
import dev.sangwoo.celestialfall.client.kageno.KagenoSkills;
import dev.sangwoo.celestialfall.isosang.IsosangAttack;
import dev.sangwoo.celestialfall.isosang.IsosangRules;
import dev.sangwoo.celestialfall.stand.StandManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Lee So-sang's basic attack on the client (2.77.0): the attack key asks the server for a hit (isosang_attack_v1), and
 * the server's word of every hit (isosang_attack_event_v1) becomes a swing: a cast of the skill IsosangRules.SKILL with
 * the server's clock, whose body clip, slashes and light IsosangSlash draws with the casts' passes (KagenoRenderer,
 * KagenoClips). A swing stopped short keeps what its blows left but lets go of the body. Each hit's sound starts with it
 * on every client near enough (its whooshes on the blows), and every blow that strikes someone is heard on them.
 */
public final class IsosangClient {
    /** Every swing seen, by its id: a cast of IsosangRules.SKILL on the cast clock (StandClient.Cast.age). */
    public static final Map<UUID,StandClient.Cast> SWINGS=new LinkedHashMap<>();
    /** Whom each swing's blows struck, by the server's word (entity ids, by blow; null for a blow not landed yet). */
    private static final Map<UUID,int[][]> STRUCK=new HashMap<>();
    /** Swings stopped short: their body clip is over. */
    private static final Set<UUID> STOPPED=new HashSet<>();
    /** The tick each player's attack is ready again after its rest (the server's). */
    private static final Map<UUID,Long> READY=new HashMap<>();
    private static final Map<UUID,Sound> VOICES=new HashMap<>();
    /**
     * A hit's sound starts as this client hears of the hit, when its clock starts too (its whooshes are cut to land on
     * the blows from there); one heard later than this many ticks into the hit is not played.
     */
    static final int LATE=2;
    /** How loud the sounds are, and how far they carry (blocks, fading out linearly). */
    static final float VOLUME=.9f,HIT_VOLUME=.8f;
    static final double CARRY=40;
    /** Ticks a sound takes to fade out once its swing stops short. */
    static final int FADE=3;
    private static int ticks,lastAsk=-100;
    private static Object world;
    private IsosangClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(IsosangAttack.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();var dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int combo=b.readUnsignedByte(),blow=b.readByte();
            long start=b.readLong(),serverNow=b.readLong();Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>512||type>IsosangAttack.STOPPED||combo>4)return;
            var struck=new ArrayList<StandClient.Target>();
            for(int i=0;i<n;i++)struck.add(new StandClient.Target(b.readUuid(),b.readInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble())));
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck));
        });
        ClientTickEvents.START_CLIENT_TICK.register(IsosangClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->clear(mc)));
    }
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world)clear(mc);
        READY.put(caster,ready);
        if(type==IsosangAttack.HIT&&combo>=1){
            var cast=new StandClient.Cast(id,caster,dim,IsosangRules.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)/20f>=KagenoSkills.of(IsosangRules.SKILL).lifetime())return;
            KagenoClips.begin(cast);SWINGS.put(id,cast);STRUCK.put(id,new int[IsosangRules.blows(combo)][]);
            if(serverNow-start<=LATE)voice(mc,id,caster,combo);
        }else if(type==IsosangAttack.LANDED){
            var cast=SWINGS.get(id);var told=STRUCK.get(id);
            if(cast!=null&&told!=null&&blow>=0&&blow<told.length){
                told[blow]=struck.stream().mapToInt(StandClient.Target::entity).toArray();
                if(!struck.isEmpty()){
                    var all=new ArrayList<>(cast.targets());for(var t:struck)if(all.stream().noneMatch(o->o.entity()==t.entity()))all.add(t);
                    SWINGS.put(id,new StandClient.Cast(cast.id(),cast.caster(),cast.dimension(),cast.skill(),cast.combo(),cast.start(),cast.serverNow(),cast.received(),cast.origin(),cast.yaw(),List.copyOf(all)));
                    impact(mc,struck);
                }
            }
        }else if(type==IsosangAttack.STOPPED){
            // the swing in flight lets go of the body (a hit stopped before its first blow leaves nothing to see)
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.contains(c.id())&&animating(c))STOPPED.add(c.id());
            SWINGS.values().removeIf(c->STOPPED.contains(c.id())&&c.age(0)<IsosangRules.blowAt(c.combo(),0));
            for(var e:VOICES.entrySet()){var c=SWINGS.get(e.getKey());if(c==null||STOPPED.contains(e.getKey()))e.getValue().fade();}
        }
    }
    /** Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short). */
    static boolean animating(StandClient.Cast c){return c.age(0)<KagenoSkills.of(IsosangRules.SKILL).clip(c.combo()).length()*20+1;}
    /** The latest swing of this player whose body clip still plays and did not stop short (KagenoClips.animated), or null. */
    public static StandClient.Cast animated(UUID id){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.caster().equals(id)&&!STOPPED.contains(c.id())&&animating(c))best=c;
        return best;
    }
    /** Whom blow {@code i} of swing {@code id} struck, by the server's word (null until it is told). */
    static int[] struck(UUID id,int i){var t=STRUCK.get(id);return t==null||i<0||i>=t.length?null:t[i];}
    /** The tick this player's attack is ready again after its rest, as the server told it (0 if none). */
    public static long ready(UUID id){return READY.getOrDefault(id,0L);}
    /** Forgets this player's rest (their character is not Lee So-sang now: the server forgot it too, StandManager). */
    public static void forget(UUID id){READY.remove(id);}
    /**
     * Whether a hit holds the local player where they stand now (IsosangRules.HOLD): their keys do not walk them. The
     * server holds them as long (IsosangAttack.busy).
     */
    public static boolean movementLocked(){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return false;
        for(var c:SWINGS.values())if(c.caster().equals(mc.player.getUuid())&&!STOPPED.contains(c.id())&&c.age(0)<IsosangRules.HOLD[c.combo()])return true;
        return false;
    }
    /**
     * How far the local player's camera is drawn back now by their own finisher (IsosangSlash.pull, blocks; 2.77.1), on
     * its clock to the end even if it stopped short, so the camera never jumps.
     */
    public static double cameraPull(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return 0;
        double pull=0;
        for(var c:SWINGS.values())if(c.caster().equals(mc.player.getUuid()))pull=Math.max(pull,IsosangSlash.pull(c.combo(),c.age(delta)/20f));
        return pull;
    }
    /** Whether this player's attack key swings Lee So-sang's sword now: theirs, on the map, holding neither the meteor pickaxe nor a fruit. */
    public static boolean attacks(PlayerEntity p){
        return p!=null&&MapClient.enabled()&&StandManager.isosang(p)&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand()
            &&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand();
    }
    /** The attack key: asks the server for a hit (at most every other tick). Returns whether the sword takes the key (nothing else happens). */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();if(!attacks(mc.player))return false;
        if(CombatClient.combatInput()&&ticks-lastAsk>=2&&ClientPlayNetworking.canSend(IsosangAttack.INPUT)){
            BotandCamera.prepareInteraction();ClientPlayNetworking.send(IsosangAttack.INPUT,PacketByteBufs.create());lastAsk=ticks;
        }
        return true;
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(world!=null||!SWINGS.isEmpty())clear(mc);return;}
        if(world!=mc.world){clear(mc);world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        var id=mc.world.getRegistryKey().getValue();float life=KagenoSkills.of(IsosangRules.SKILL).lifetime()*20;
        SWINGS.values().removeIf(c->!c.dimension().equals(id)||c.age(0)>=life||c.age(0)<0||gone(mc,c));
        STRUCK.keySet().retainAll(SWINGS.keySet());STOPPED.retainAll(SWINGS.keySet());
        VOICES.entrySet().removeIf(e->e.getValue().isDone());
        // held down, the attack key keeps asking (the server keeps a press made a little early for the next hit)
        if(mc.player!=null&&mc.currentScreen==null&&mc.options.attackKey.isPressed()&&attacks(mc.player))punch();
    }
    /** A swing whose caster is gone, dead, or Lee So-sang no more. */
    private static boolean gone(MinecraftClient mc,StandClient.Cast c){var p=mc.world.getPlayerByUuid(c.caster());return p!=null&&(!p.isAlive()||!StandManager.isosang(p));}
    public static void clear(MinecraftClient mc){
        for(var v:VOICES.values())mc.getSoundManager().stop(v);
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();VOICES.clear();world=null;
    }

    // ───────────────────────────── sounds ─────────────────────────────

    private static float volume(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;double far=from.distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/CARRY));
    }
    /** The hit's sound, its whooshes on its blows: loud for its user and those near, fading out to CARRY blocks. */
    private static void voice(MinecraftClient mc,UUID id,UUID caster,int combo){
        var user=mc.world.getPlayerByUuid(caster);if(user==null)return;
        float v=(user==mc.player?1:volume(mc,user.getPos()))*VOLUME;if(v<=.01f)return;
        var s=new Sound(CelestialFall.id("isosang_m1_"+combo),v);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /** A blow struck someone: the blade biting, heard on them. */
    private static void impact(MinecraftClient mc,List<StandClient.Target> struck){
        Vec3d at=struck.get(0).position();float v=volume(mc,at)*HIT_VOLUME;if(v<=.01f)return;
        mc.getSoundManager().play(new Sound(CelestialFall.id("isosang_m1_hit"),v));
    }
    /**
     * A sound played like any other until it is told to fade, then quieter tick by tick to nothing (the game sets a
     * playing sound's loudness once a tick, so a fade has no click where a stop would). Heard at the listener, its
     * loudness set by the distance.
     */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private final float full;private int fading=-1;private boolean done,begun;
        Sound(Identifier id,float volume){
            super(id,SoundCategory.PLAYERS,Random.create());
            this.volume=full=volume;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            if(fading<0)return;
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
}
