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
 * KagenoClips). A swing stopped short keeps what its blows left but lets go of the body. Each hit's sound (the user's
 * recording of it, the sword and the voice, 2.77.2) starts with it on every client near enough, cut so that what it
 * strikes lands on the blows.
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
    /** (2.80.0) The swings of the empowered combo (their aura brighter), by id. */
    private static final Set<UUID> EMPOWERED=new HashSet<>();
    /** (2.80.0) Whose next combo is empowered (E used, its combo not begun or not over), and the tick each one's E is ready again. */
    private static final Map<UUID,Boolean> CHARGED=new HashMap<>();
    private static final Map<UUID,Long> E_READY=new HashMap<>();
    /**
     * (2.80.0) The empowered first's swords, by its swing's id: where each was thrown from, which way, how far it flew
     * (its range or the block in its way, else where it struck: told as it lands) and how many ticks into its swing
     * it was thrown (the server's).
     */
    public record Sword(Vec3d from,Vec3d dir,double end,float thrown,boolean landed){}
    public static final Map<UUID,Sword[]> SWORDS=new HashMap<>();
    /**
     * A hit's sound starts as this client hears of the hit, when its clock starts too (its whooshes are cut to land on
     * the blows from there); one heard later than this many ticks into the hit is not played.
     */
    static final int LATE=2;
    /** How loud the sounds are, and how far they carry (blocks, fading out linearly). */
    static final float VOLUME=.9f;
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
            int n=b.readVarInt();if(n<0||n>512||type>IsosangAttack.SPENT||combo>IsosangRules.THRUST)return;
            var struck=new ArrayList<StandClient.Target>();
            for(int i=0;i<n;i++)struck.add(new StandClient.Target(b.readUuid(),b.readInt(),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble())));
            // (2.80.0) whether the hit is of the empowered combo, whether the user holds an empowerment, when E is ready
            // again, and a thrown sword's way
            boolean empowered=b.readBoolean(),charged=b.readBoolean();long eReady=b.readLong();
            Sword sword=null;
            if(b.readBoolean()){
                Vec3d from=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()),dir=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());double end=b.readDouble();
                if(dir.lengthSquared()>.5&&dir.lengthSquared()<2&&end>=0&&end<=IsosangRules.SWORD_RANGE+1)sword=new Sword(from,dir.normalize(),end,serverNow-start,type==IsosangAttack.LANDED);
            }
            Sword thrown=sword;
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck,empowered,charged,eReady,thrown));
        });
        ClientTickEvents.START_CLIENT_TICK.register(IsosangClient::tick);
        // (2.80.0) the swords of light: the empowered first's thrown swords and the empowered fourth's giant sword
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(IsosangEmpower::models);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->clear(mc)));
    }
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck,
                               boolean empowered,boolean charged,long eReady,Sword sword){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world)clear(mc);
        READY.put(caster,ready);CHARGED.put(caster,charged);E_READY.put(caster,eReady);
        if(type==IsosangAttack.HIT&&IsosangRules.valid(combo)){
            var cast=new StandClient.Cast(id,caster,dim,IsosangRules.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)/20f>=KagenoSkills.of(IsosangRules.SKILL).lifetime())return;
            KagenoClips.begin(cast);SWINGS.put(id,cast);STRUCK.put(id,new int[IsosangRules.blows(combo)][]);
            if(empowered)EMPOWERED.add(id);
            if(serverNow-start<=LATE)voice(mc,id,caster,combo);
        }else if(type==IsosangAttack.EMPOWERED){
            // E (2.80.0): a flash of moonlight round them, drawn as a swing of its own with no body motion (combo 0)
            var cast=new StandClient.Cast(UUID.randomUUID(),caster,dim,IsosangRules.SKILL,0,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)<20)SWINGS.put(cast.id(),cast);
        }else if(type==IsosangAttack.THROWN){
            // a sword of the empowered first thrown (2.80.0): its way, for every viewer to draw it flying
            var swords=SWORDS.computeIfAbsent(id,k->new Sword[IsosangRules.SWORD_FROM.length]);
            if(sword!=null&&blow>=0&&blow<swords.length&&(swords[blow]==null||!swords[blow].landed()))swords[blow]=sword;
        }else if(type==IsosangAttack.LANDED){
            // (2.80.0) a thrown sword stops where it reached them
            if(combo==IsosangRules.THROWS&&sword!=null){
                var swords=SWORDS.computeIfAbsent(id,k->new Sword[IsosangRules.SWORD_FROM.length]);
                if(blow>=0&&blow<swords.length){var was=swords[blow];swords[blow]=new Sword(sword.from(),sword.dir(),sword.end(),was==null?sword.thrown():was.thrown(),true);}
            }
            var cast=SWINGS.get(id);var told=STRUCK.get(id);
            if(cast!=null&&told!=null&&blow>=0&&blow<told.length){
                // (the finisher's thrown slash tells its blow tick by tick as it strikes: whom it took is added up)
                int[] was=told[blow]==null?new int[0]:told[blow];
                told[blow]=java.util.stream.IntStream.concat(java.util.Arrays.stream(was),struck.stream().mapToInt(StandClient.Target::entity)).distinct().toArray();
                if(!struck.isEmpty()){
                    var all=new ArrayList<>(cast.targets());for(var t:struck)if(all.stream().noneMatch(o->o.entity()==t.entity()))all.add(t);
                    SWINGS.put(id,new StandClient.Cast(cast.id(),cast.caster(),cast.dimension(),cast.skill(),cast.combo(),cast.start(),cast.serverNow(),cast.received(),cast.origin(),cast.yaw(),List.copyOf(all)));
                }
            }
        }else if(type==IsosangAttack.STOPPED){
            // the swing in flight lets go of the body (a hit stopped before its first blow leaves nothing to see)
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.contains(c.id())&&animating(c))STOPPED.add(c.id());
            SWINGS.values().removeIf(c->STOPPED.contains(c.id())&&c.age(0)<IsosangRules.blowAt(c.combo(),0));
            for(var e:VOICES.entrySet()){var c=SWINGS.get(e.getKey());if(c==null||STOPPED.contains(e.getKey()))e.getValue().fade();}
        }
    }
    /** Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short; E's flash has none). */
    static boolean animating(StandClient.Cast c){return IsosangRules.valid(c.combo())&&c.age(0)<KagenoSkills.of(IsosangRules.SKILL).clip(c.combo()).length()*20+1;}
    /** Whether this swing is of the empowered combo (2.80.0: its blade's aura brighter). */
    public static boolean empowered(UUID swing){return EMPOWERED.contains(swing);}
    /** Whether this player's next combo is empowered (E used, its combo not begun or not over, 2.80.0): their sword glows. */
    public static boolean charged(UUID id){return CHARGED.getOrDefault(id,false);}
    /**
     * How brightly this player's sword glows now (0 to 1, 2.80.0): while their next combo is empowered (E used) and
     * through its hits, coming in over the E's flash and going out as the empowered fourth's thrust is over.
     */
    public static float glow(UUID id,float delta){
        float g=charged(id)?1:0;
        for(var c:SWINGS.values()){
            if(!c.caster().equals(id))continue;float sec=c.age(delta)/20f;
            if(c.combo()==0)g=Math.max(g,Math.min(1,sec/.12f));
            else if(EMPOWERED.contains(c.id())&&!STOPPED.contains(c.id()))g=Math.max(g,c.combo()==IsosangRules.THRUST?1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(sec,.35f,.8f):1);
        }
        return g;
    }
    /** The tick this player's E is ready again, as the server told it (0 if it is ready, 2.80.0). */
    public static long eReady(UUID id){return E_READY.getOrDefault(id,0L);}
    /** The empowered first's swords of this swing (null for none yet, 2.80.0). */
    public static Sword[] swords(UUID swing){return SWORDS.get(swing);}
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
    /** Forgets this player's rest (their character is not Lee So-sang now: the server forgot it too, StandManager); and E's (2.80.0). */
    public static void forget(UUID id){READY.remove(id);E_READY.remove(id);CHARGED.remove(id);}
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
    /**
     * E for Lee So-sang's player on the map (2.80.0; whatever the hand holds: their inventory is on I then, as Kageno's
     * and Star Platinum's): asks the server to empower the next combo. Returns whether it took the key.
     */
    public static boolean key(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle()||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.isosang(mc.player))return false;
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_E){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.E);ClientPlayNetworking.send(IsosangAttack.SKILL,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_I&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS){
            if(!CombatClient.actionLocked())mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player));
            return true;
        }
        return false;
    }
    /**
     * The empowered fourth's step back (2.80.0), moved by its user's own client (a player's movement is its own, as T
     * held's dash, RushDash): at the start of each client tick of it the local player is given the velocity that takes
     * them to where the server's plan has them at the tick's end (IsosangRules.backstep, straight back from where the hit
     * began the way it faced); at its end they stop. Call at the start of every client tick, before the player moves.
     */
    public static void drive(MinecraftClient mc){
        var p=mc.player;if(p==null||!MapClient.enabled())return;
        for(var c:SWINGS.values()){
            if(c.combo()!=IsosangRules.THRUST||!c.caster().equals(p.getUuid())||STOPPED.contains(c.id()))continue;
            int k=(int)Math.floor(c.age(0));
            if(k<IsosangRules.BACKSTEP_FROM-1||k>IsosangRules.BACKSTEP_TO)continue;
            Vec3d back=Vec3d.fromPolar(0,c.yaw()).multiply(-1);
            if(k==IsosangRules.BACKSTEP_TO){p.setVelocity(0,Math.min(0,p.getVelocity().y),0);continue;}
            Vec3d to=c.origin().add(back.multiply(IsosangRules.backstep(k+1))),v=to.subtract(p.getPos()).multiply(1,0,1);
            p.setVelocity(v.x,Math.min(0,p.getVelocity().y),v.z);
        }
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
        // (2.80.0: E's flash is over in under a second; gone then, so it leaves no afterimages behind them as they walk on)
        SWINGS.values().removeIf(c->!c.dimension().equals(id)||c.age(0)>=(c.combo()==0?20:life)||c.age(0)<0||gone(mc,c));
        STRUCK.keySet().retainAll(SWINGS.keySet());STOPPED.retainAll(SWINGS.keySet());EMPOWERED.retainAll(SWINGS.keySet());SWORDS.keySet().retainAll(SWINGS.keySet());
        VOICES.entrySet().removeIf(e->e.getValue().isDone());
        // held down, the attack key keeps asking (the server keeps a press made a little early for the next hit)
        if(mc.player!=null&&mc.currentScreen==null&&mc.options.attackKey.isPressed()&&attacks(mc.player))punch();
    }
    /** A swing whose caster is gone, dead, or Lee So-sang no more. */
    private static boolean gone(MinecraftClient mc,StandClient.Cast c){var p=mc.world.getPlayerByUuid(c.caster());return p!=null&&(!p.isAlive()||!StandManager.isosang(p));}
    public static void clear(MinecraftClient mc){
        for(var v:VOICES.values())mc.getSoundManager().stop(v);
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();VOICES.clear();EMPOWERED.clear();CHARGED.clear();E_READY.clear();SWORDS.clear();world=null;
    }

    // ───────────────────────────── sounds ─────────────────────────────

    private static float volume(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;double far=from.distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/CARRY));
    }
    /** The hit's sound, its whooshes on its blows: loud for its user and those near, fading out to CARRY blocks. */
    private static void voice(MinecraftClient mc,UUID id,UUID caster,int combo){
        var user=mc.world.getPlayerByUuid(caster);if(user==null||!IsosangRules.valid(combo))return;
        float v=(user==mc.player?1:volume(mc,user.getPos()))*VOLUME;if(v<=.01f)return;
        var s=new Sound(CelestialFall.id("isosang_m1_"+combo),v);VOICES.put(id,s);mc.getSoundManager().play(s);
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
