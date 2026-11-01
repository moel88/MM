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
 * <p>R (2.81.0): R asks the server for the giant sword's sweep (isosang_skill_v1, R), the camera's crosshair turned to
 * first; the server's word of it is a swing of kind IsosangRules.SWEEP drawn by GiantSweep, with where its sweep stands.
 * (2.81.1) Let go within IsosangRules.R_HOLD_TICKS it is R, asked as the key comes up (the sweep right in front);
 * held that long it is R held (R_HELD), asked then with the key still down (the sword sent to the foe in the crosshair).
 * <p>H (2.82.0): H asks the server for the sword formation (isosang_skill_v1, H); the server's word of it is a swing of kind
 * IsosangRules.FORMATION drawn by SwordFormation, with the formation's middle (2.82.4: ahead of its user, who stands
 * on its outer ring). Its user's own client lifts them up onto the pommel of the sword struck where they stood and down
 * again (drive), and every client draws the body as high as the formation's plan has it (lift: the leap, the dive onto
 * the pommel, whatever the position heard from the server lags behind).
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
    /** (2.81.0) R's sweeps, by their swing's id: where each stands (its feet), the way it faces, and the foe it was aimed at (an entity id, -1 for none). */
    public record Sweep(Vec3d at,float yaw,int target){}
    public static final Map<UUID,Sweep> SWEEPS=new HashMap<>();
    /** (2.81.0) The tick each player's R is ready again (the server's). */
    private static final Map<UUID,Long> R_READY=new HashMap<>();
    /** (2.82.0) H's formations, by their swing's id: the formation's middle (on its ground) and the way its user faced. */
    public record Formation(Vec3d centre,float yaw){}
    public static final Map<UUID,Formation> FORMATIONS=new HashMap<>();
    /** (2.82.0) When a formation ended early (seconds into its swing, the server's word): its swords go then. */
    private static final Map<UUID,Float> FORMATION_ENDS=new HashMap<>();
    /** (2.82.0) The tick each player's H is ready again (the server's), and the formations whose end has been heard. */
    private static final Map<UUID,Long> H_READY=new HashMap<>();
    private static final Set<UUID> ENDED=new HashSet<>();
    /** (2.82.0) Whom each formation struck, by the tick of its clock it struck on (the server's word): entity ids. */
    private static final Map<UUID,java.util.TreeMap<Long,int[]>> FORMATION_STRUCK=new HashMap<>();
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
    /** Whether R's press was taken (2.81.0): its release is taken too. */
    /** R down (2.81.1: when, by the client's ticks, and whether R or R held has been asked for yet). */
    private static boolean heldR,askedR;private static long rDown;
    private static Object world;
    private IsosangClient(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(IsosangAttack.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();var dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int combo=b.readUnsignedByte(),blow=b.readByte();
            long start=b.readLong(),serverNow=b.readLong();Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>512||type>IsosangAttack.SPENT||combo>IsosangRules.FORMATION)return;
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
            // (2.81.0) when R is ready again, and where R's sweep stands (none from a 2.80 server)
            long rReady=b.isReadable()?b.readLong():0;Sweep sweep=null;
            if(b.isReadable()&&b.readBoolean()){Vec3d o=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float sy=b.readFloat();sweep=new Sweep(o,sy,b.readVarInt());}
            Sweep stands=sweep;
            // (2.82.0) when H is ready again, and H's formation's middle and way (none from a 2.81 server)
            long hReady=b.isReadable()?b.readLong():0;Formation form=null;
            if(b.isReadable()&&b.readBoolean()){Vec3d c=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());form=new Formation(c,b.readFloat());}
            Formation laid=form;
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck,empowered,charged,eReady,thrown,rReady,stands,hReady,laid));
        });
        ClientTickEvents.START_CLIENT_TICK.register(IsosangClient::tick);
        // (2.80.0) the swords of light: the empowered first's thrown swords and the empowered fourth's giant sword
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(IsosangEmpower::models);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->clear(mc)));
    }
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck,
                               boolean empowered,boolean charged,long eReady,Sword sword,long rReady,Sweep sweep,long hReady,Formation form){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world)clear(mc);
        READY.put(caster,ready);CHARGED.put(caster,charged);E_READY.put(caster,eReady);R_READY.put(caster,rReady);H_READY.put(caster,hReady);
        if(type==IsosangAttack.HIT&&IsosangRules.drawn(combo)){
            // (2.81.0) R's sweep: where it stands with it (it may stand out toward the foe in the crosshair)
            if(combo==IsosangRules.SWEEP){if(sweep==null)return;SWEEPS.put(id,sweep);}
            // (2.82.0) H's formation: where its middle is
            if(combo==IsosangRules.FORMATION){if(form==null)return;FORMATIONS.put(id,form);}
            var cast=new StandClient.Cast(id,caster,dim,IsosangRules.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)/20f>=life(combo))return;
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
        }else if(type==IsosangAttack.LANDED&&combo==IsosangRules.FORMATION){
            // (2.82.0) whom H's formation struck as its swords came down, and on every strike after (by its clock's tick)
            if(SWINGS.containsKey(id))FORMATION_STRUCK.computeIfAbsent(id,k->new java.util.TreeMap<>()).put(serverNow-start,struck.stream().mapToInt(StandClient.Target::entity).toArray());
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
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.SWEEP){
            // (2.81.0) R stopped short before its sword turned: its sword is gone; after, its user lets go of it and it
            // sweeps on (the server's word of when: (serverNow - start) ticks in)
            var c=SWINGS.get(id);
            if(c!=null){
                if((serverNow-start)/20.0<IsosangRules.SWEEP_START){SWINGS.remove(id);var v=VOICES.get(id);if(v!=null)v.fade();}
                else STOPPED.add(id);
            }
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.FORMATION){
            // (2.82.0) H's formation ended early (its user taken away): its swords go now, its user drops
            var c=SWINGS.get(id);if(c!=null){FORMATION_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);}
        }else if(type==IsosangAttack.STOPPED){
            // the swing in flight lets go of the body (a hit stopped before its first blow leaves nothing to see)
            // (2.82.0: not H's formation, told of by its own STOPPED, above)
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.contains(c.id())&&animating(c)&&c.combo()!=IsosangRules.FORMATION)STOPPED.add(c.id());
            // (2.81.0: R's sweep is told of by its own STOPPED, above)
            SWINGS.values().removeIf(c->STOPPED.contains(c.id())&&c.combo()!=IsosangRules.SWEEP&&c.age(0)<IsosangRules.blowAt(c.combo(),0));
            for(var e:VOICES.entrySet()){var c=SWINGS.get(e.getKey());if(c==null||STOPPED.contains(e.getKey())&&c.combo()!=IsosangRules.SWEEP&&c.combo()!=IsosangRules.FORMATION)e.getValue().fade();}
        }
    }
    /** Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short; E's flash has none). */
    static boolean animating(StandClient.Cast c){return IsosangRules.drawn(c.combo())&&c.age(0)<KagenoSkills.of(IsosangRules.SKILL).clip(c.combo()).length()*20+1;}
    /**
     * How long a swing of this kind is drawn for (seconds, 2.82.0): H's formation its whole length and the ink and mist
     * after it; every other its skill's (IsosangSlash.lifetime).
     */
    public static float life(int combo){return combo==IsosangRules.FORMATION?IsosangRules.H_END+SwordFormation.AFTER:KagenoSkills.of(IsosangRules.SKILL).lifetime();}
    /** How long this swing is drawn for (seconds): a formation that ended early only until its swords have gone (2.82.0). */
    public static float lifeOf(StandClient.Cast c){
        float life=life(c.combo());
        if(c.combo()==IsosangRules.FORMATION){Float end=FORMATION_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+SwordFormation.AFTER);}
        return life;
    }
    /** Whether this swing stopped short (its user struck or held: its body clip is over). */
    public static boolean stopped(UUID swing){return STOPPED.contains(swing);}
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
            // (2.81.0) R: the sword glows as it is raised to call the giant sword, until the sweep is through
            else if(c.combo()==IsosangRules.SWEEP&&!STOPPED.contains(c.id()))g=Math.max(g,dev.sangwoo.celestialfall.client.kageno.Ease.window(sec,0,.78f,.08f,.25f));
            // (2.82.0) H: the sword glows as she leaps and calls the swords down, until she stands on the middle one
            else if(c.combo()==IsosangRules.FORMATION&&!STOPPED.contains(c.id()))g=Math.max(g,dev.sangwoo.celestialfall.client.kageno.Ease.window(sec,0,1.6f,.06f,.3f));
        }
        return g;
    }
    /** The tick this player's E is ready again, as the server told it (0 if it is ready, 2.80.0). */
    public static long eReady(UUID id){return E_READY.getOrDefault(id,0L);}
    /** The tick this player's R is ready again, as the server told it (0 if it is ready, 2.81.0). */
    public static long rReady(UUID id){return R_READY.getOrDefault(id,0L);}
    /** The tick this player's H is ready again, as the server told it (0 if it is ready, 2.82.0). */
    public static long hReady(UUID id){return H_READY.getOrDefault(id,0L);}
    /** H's formation of this swing: its middle and its user's way (null for none, 2.82.0). */
    public static Formation formation(UUID swing){return FORMATIONS.get(swing);}
    /** Whom this swing's formation struck, by the tick of its clock (the server's word; empty for none yet, 2.82.0). */
    static java.util.SortedMap<Long,int[]> formationStruck(UUID swing){var m=FORMATION_STRUCK.get(swing);return m==null?java.util.Collections.emptySortedMap():m;}
    /** When this swing's formation ended early (seconds into it, the server's word), or NaN if it has not (2.82.0). */
    public static float formationEnd(UUID swing){var e=FORMATION_ENDS.get(swing);return e==null?Float.NaN:e;}
    /**
     * How much higher than where it is this player's body is drawn now (blocks, 2.82.0): during H, as high over the
     * formation's ground as its plan has them (IsosangRules.planned: the leap, the hang, the dive onto the pommel of the
     * sword struck under them, standing on it, the drop) less how high they are (their own client lifts them onto the
     * pommel and down, drive; others see that a little late), so the body is where the plan has it whatever the position
     * lags. 0 when they have no formation under way.
     */
    public static double lift(UUID caster,float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return 0;
        StandClient.Cast form=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(caster)&&!STOPPED.contains(c.id()))form=c;
        if(form==null)return 0;
        var f=FORMATIONS.get(form.id());var p=mc.world.getPlayerByUuid(caster);if(f==null||p==null)return 0;
        double t=form.age(delta)/20.0;if(t<0||t>=IsosangRules.H_DOWN+.3)return 0;
        return lift(IsosangRules.planned(t),p.getLerpedPos(delta).y-f.centre().y);
    }
    /** The lift that draws a body {@code real} blocks over the formation's ground {@code planned} blocks over it. */
    static double lift(double planned,double real){return Math.max(0,planned)-real;}
    /**
     * Where H's formation {@code f} holds its user, level on its ground (2.82.4, as the server does): where they stood as
     * it began, IsosangRules.FORMATION_STAND_BACK blocks behind its middle (the sword they stand on, IsosangRules.standAt).
     */
    public static Vec3d spot(Formation f){
        double[] st=IsosangRules.standAt();Vec3d fw=Vec3d.fromPolar(0,f.yaw()),r=new Vec3d(-fw.z,0,fw.x);
        return f.centre().add(fw.multiply(st[0])).add(r.multiply(st[1]));
    }
    /** Where R's sweep of this swing stands (null for none, 2.81.0). */
    public static Sweep sweep(UUID swing){return SWEEPS.get(swing);}
    /** R's sword's pommel, which it turns round, where this swing's sweep stands (world); null for a swing not R's (2.81.1). */
    public static Vec3d sweepPivot(UUID swing){var s=SWEEPS.get(swing);return s==null?null:s.at().add(Vec3d.fromPolar(0,s.yaw()).multiply(IsosangRules.SWEEP_PIVOT_FW)).add(0,IsosangRules.SWEEP_PIVOT_UP,0);}
    /** Where a swing is heard from by a listener: its user, or R's sword's pommel when that is nearer (2.81.1). */
    static Vec3d heardFrom(Vec3d listener,Vec3d user,Vec3d pivot){return pivot!=null&&pivot.squaredDistanceTo(listener)<user.squaredDistanceTo(listener)?pivot:user;}
    /**
     * Whether a swing is near enough the camera to be drawn: its user within {@code far} blocks, or R's sword standing
     * out toward the foe in the crosshair within that of it (2.81.1: up to some 32 blocks out from its user).
     */
    public static boolean shown(StandClient.Cast s,Vec3d user,Vec3d camera,double far){
        if(user.squaredDistanceTo(camera)<=far*far)return true;
        var pivot=sweepPivot(s.id());return pivot!=null&&pivot.squaredDistanceTo(camera)<=far*far;
    }
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
    public static void forget(UUID id){READY.remove(id);E_READY.remove(id);CHARGED.remove(id);R_READY.remove(id);H_READY.remove(id);}
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
        var mc=MinecraftClient.getInstance();if(window!=mc.getWindow().getHandle())return false;
        // (2.81.0) R's release is taken whenever its press was, whatever is open by then; (2.81.1) let go before R held
        // was asked for, it is R
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_R&&heldR&&action!=org.lwjgl.glfw.GLFW.GLFW_PRESS){
            if(action==org.lwjgl.glfw.GLFW.GLFW_RELEASE){heldR=false;if(!askedR)askR(mc,IsosangAttack.R);}
            return true;
        }
        if(mc.currentScreen!=null||!MapClient.enabled()||!StandManager.isosang(mc.player))return false;
        // (2.81.0) R: the giant sword's sweep; (2.81.1, user request: "그냥 r은 원래처럼 앞만 베고 R홀드를 해야지 보고 있는
        // 적에게 검이 순간이동 되게 만들자") right in front as the key comes up, or, held down R_HOLD_TICKS, at the foe in
        // the crosshair (tick). R is always this for Lee So-sang's player on the map, as it is the heavy punch's for Star
        // Platinum's.
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_R&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS){heldR=true;askedR=false;rDown=ticks;return true;}
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_E){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.E);ClientPlayNetworking.send(IsosangAttack.SKILL,b);
            }
            return true;
        }
        // (2.82.0) H: the sword formation (H is always this for Lee So-sang's player on the map, as it is the time stop's for
        // Star Platinum's)
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_H){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.H);ClientPlayNetworking.send(IsosangAttack.SKILL,b);
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
     * The empowered fourth's leap back (2.80.0; 5 blocks since 2.80.1), moved by its user's own client (a player's movement
     * is its own, as T held's dash, RushDash): at the start of each client tick of it the local player is given the
     * velocity that takes them to where the server's plan has them at the tick's end (IsosangRules.backstep, straight back
     * from where the hit began the way it faced, as far as the blocks behind them let it: IsosangAttack.room); at its end
     * they stop. Call at the start of every client tick, before the player moves.
     */
    public static void drive(MinecraftClient mc){
        var p=mc.player;if(p==null||!MapClient.enabled())return;
        for(var c:SWINGS.values()){
            // (2.82.0) H: where they stood (2.82.4: on the formation's outer ring, its middle ahead), up onto the pommel of
            // the sword struck there as they leap, held there and down again (spot and IsosangRules.raised: every tick to
            // where they have them at the tick's end; the leap over it and the dive onto the pommel are drawn, lift). Let go
            // of at once if it ended early: they fall as anyone would.
            if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(p.getUuid())&&!STOPPED.contains(c.id())){
                var f=FORMATIONS.get(c.id());double next=(Math.floor(c.age(0))+1)/20.0;
                if(f==null||next>=IsosangRules.H_DOWN+.1)continue;
                Vec3d spot=spot(f),v=spot.subtract(p.getPos()).multiply(1,0,1);
                double vy=next<IsosangRules.H_LEAP?Math.min(0,p.getVelocity().y):spot.y+IsosangRules.raised(next)-p.getY();
                // (a step down of a hair, the rounding of the sum, is taken by the game for landing on something: it would
                // tell the server they stand on the ground up there)
                if(Math.abs(vy)<1e-6)vy=0;
                p.setVelocity(Math.abs(v.x)<1e-6?0:v.x,vy,Math.abs(v.z)<1e-6?0:v.z);
                continue;
            }
            if(c.combo()!=IsosangRules.THRUST||!c.caster().equals(p.getUuid())||STOPPED.contains(c.id()))continue;
            int k=(int)Math.floor(c.age(0));
            if(k<IsosangRules.BACKSTEP_FROM-1||k>IsosangRules.BACKSTEP_TO)continue;
            Vec3d back=Vec3d.fromPolar(0,c.yaw()).multiply(-1);
            if(k==IsosangRules.BACKSTEP_TO){p.setVelocity(0,Math.min(0,p.getVelocity().y),0);continue;}
            Vec3d to=c.origin().add(back.multiply(IsosangRules.backstep(k+1,IsosangEmpower.room(c)))),v=to.subtract(p.getPos()).multiply(1,0,1);
            p.setVelocity(v.x,Math.min(0,p.getVelocity().y),v.z);
        }
    }
    /**
     * Asks the server for R or R held (2.81.1), once a press: the camera's crosshair turned to first, so the server's
     * sight is its (BotandCamera.prepareInteraction): R's way, and R held's foe.
     */
    private static void askR(MinecraftClient mc,int which){
        askedR=true;
        if(mc.player==null||mc.currentScreen!=null||!MapClient.enabled()||!StandManager.isosang(mc.player))return;
        if(CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
            BotandCamera.prepareInteraction();
            var b=PacketByteBufs.create();b.writeByte(which);ClientPlayNetworking.send(IsosangAttack.SKILL,b);
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
        var id=mc.world.getRegistryKey().getValue();
        // (2.80.0: E's flash is over in under a second; gone then, so it leaves no afterimages behind them as they walk on;
        // 2.82.0: H's formation lasts its whole length, lifeOf)
        SWINGS.values().removeIf(c->!c.dimension().equals(id)||c.age(0)>=(c.combo()==0?20:lifeOf(c)*20)||c.age(0)<0||gone(mc,c));
        STRUCK.keySet().retainAll(SWINGS.keySet());STOPPED.retainAll(SWINGS.keySet());EMPOWERED.retainAll(SWINGS.keySet());SWORDS.keySet().retainAll(SWINGS.keySet());SWEEPS.keySet().retainAll(SWINGS.keySet());
        FORMATIONS.keySet().retainAll(SWINGS.keySet());FORMATION_ENDS.keySet().retainAll(SWINGS.keySet());ENDED.retainAll(SWINGS.keySet());FORMATION_STRUCK.keySet().retainAll(SWINGS.keySet());
        // (2.82.0) H's formation ends (its whole length run, or the server's word that it ended early): its end's sound once
        for(var c:SWINGS.values()){
            if(c.combo()!=IsosangRules.FORMATION||ENDED.contains(c.id()))continue;
            Float early=FORMATION_ENDS.get(c.id());float at=early!=null?early:IsosangRules.H_END,sec=c.age(0)/20f;
            if(sec<at)continue;
            ENDED.add(c.id());
            var v=VOICES.get(c.id());if(v!=null)v.fade();
            if(sec<at+.3f)ending(mc,c);
        }
        VOICES.entrySet().removeIf(e->e.getValue().isDone());
        // (2.81.1) R held down R_HOLD_TICKS: R held, asked with the key still down
        if(heldR&&!askedR&&ticks-rDown>=IsosangRules.R_HOLD_TICKS)askR(mc,IsosangAttack.R_HELD);
        // held down, the attack key keeps asking (the server keeps a press made a little early for the next hit)
        if(mc.player!=null&&mc.currentScreen==null&&mc.options.attackKey.isPressed()&&attacks(mc.player))punch();
    }
    /**
     * A swing whose caster is gone, dead, or Lee So-sang no more (2.82.0: H's formation lingers as its swords go when its
     * user dies, the server's word of its end, FORMATION_ENDS; it goes at once only if they are Lee So-sang no more, or a
     * second after they died with no word of its end).
     */
    private static boolean gone(MinecraftClient mc,StandClient.Cast c){
        var p=mc.world.getPlayerByUuid(c.caster());if(p==null)return false;
        if(c.combo()==IsosangRules.FORMATION)return !StandManager.isosang(p)||!p.isAlive()&&!FORMATION_ENDS.containsKey(c.id())&&p.deathTime>20;
        return !p.isAlive()||!StandManager.isosang(p);
    }
    public static void clear(MinecraftClient mc){
        for(var v:VOICES.values())mc.getSoundManager().stop(v);
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();VOICES.clear();EMPOWERED.clear();CHARGED.clear();E_READY.clear();SWORDS.clear();SWEEPS.clear();R_READY.clear();
        FORMATIONS.clear();FORMATION_ENDS.clear();H_READY.clear();ENDED.clear();FORMATION_STRUCK.clear();world=null;heldR=false;
    }

    // ───────────────────────────── sounds ─────────────────────────────

    private static float volume(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;double far=from.distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*Math.max(0,1-far/CARRY));
    }
    /** The hit's sound, its whooshes on its blows: loud for its user and those near, fading out to CARRY blocks. */
    private static void voice(MinecraftClient mc,UUID id,UUID caster,int combo){
        var user=mc.world.getPlayerByUuid(caster);if(user==null||!IsosangRules.drawn(combo)||mc.player==null)return;
        // (2.81.1) R's sword may stand out some 24 blocks toward the foe in the crosshair: heard as from the nearer of its
        // user and the sword's pommel, so the foe it sweeps past hears it as loud as its user's neighbours do
        Vec3d from=heardFrom(mc.player.getPos(),user.getPos(),combo==IsosangRules.SWEEP?sweepPivot(id):null);
        float v=(user==mc.player?1:volume(mc,from))*VOLUME;if(v<=.01f)return;
        // (2.81.0) R's own: its recording, from the user's video of it; (2.82.0) H's too
        var s=new Sound(CelestialFall.id(combo==IsosangRules.SWEEP?"isosang_r":combo==IsosangRules.FORMATION?"isosang_h":"isosang_m1_"+combo),v);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /**
     * The sound of H's formation ending (2.82.0: from the user's video, the swords flaring and going to ink), heard from its
     * middle: loud for its user and those near, fading out to CARRY blocks.
     */
    private static void ending(MinecraftClient mc,StandClient.Cast c){
        var f=FORMATIONS.get(c.id());if(f==null||mc.player==null)return;
        float v=(c.caster().equals(mc.player.getUuid())?1:volume(mc,f.centre()))*VOLUME;if(v<=.01f)return;
        mc.getSoundManager().play(new Sound(CelestialFall.id("isosang_h_end"),v));
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
