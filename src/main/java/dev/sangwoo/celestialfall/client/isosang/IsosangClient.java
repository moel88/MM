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
    /**
     * (2.83.0) G's casts, by their swing's id: which recording (and face) it plays and where its sword strikes the ground
     * (the server's word); when one was called off (seconds into it: its sword goes then); and the tick each player's G is
     * ready again (the server's).
     */
    public record Ultimate(int variant,Vec3d strike){}
    public static final Map<UUID,Ultimate> ULTIMATES=new HashMap<>();
    private static final Map<UUID,Float> ULTIMATE_ENDS=new HashMap<>();
    private static final Map<UUID,Long> G_READY=new HashMap<>();
    /** (2.82.0) Whom each formation struck, by the tick of its clock it struck on (the server's word): entity ids. */
    private static final Map<UUID,java.util.TreeMap<Long,int[]>> FORMATION_STRUCK=new HashMap<>();
    /**
     * (2.93.0) Z: where each formation's user stands, by its swing's id (the server's word): the sword they stand on (its
     * place in IsosangRules.formationSwords), the one they flew from (-1 for none) and the server's tick they left it.
     */
    public record Perch(int on,int from,long moved){}
    static final Map<UUID,Perch> PERCHES=new HashMap<>();
    /** (2.93.0) Each formation's flights so far (for their light: the bubble left behind, the orb, the burst on landing), by its swing's id. */
    public record Flight(int from,int to,long moved,Vec3d start){
        public Flight(int from,int to,long moved){this(from,to,moved,null);}
    }
    static final Map<UUID,List<Flight>> FLIGHTS=new HashMap<>();
    /** (2.93.0) Each Lee So-sang player's Z (its window, its moves, when it is ready again: the server's), by their id. */
    private static final Map<UUID,IsosangRules.ZState> Z_STATES=new HashMap<>();
    /** (2.93.0) Each formation's swords' places (forward and right of its middle), by its swing's id. */
    private static final Map<UUID,double[][]> PLACES=new HashMap<>();
    /**
     * (2.95.0) H's basic attack's volleys (회풍만검), by their swing's id: whom its swords come down on (an entity id, -1
     * for none) and where (the foe's chest as it began, else where the crosshair met: the server's word); and the middle
     * of the formation it was begun on (2.95.2: its swords follow the foe no further than VOLLEY_LEASH beyond its reach,
     * as the server's; null for none heard of).
     */
    public record VolleyAim(int target,Vec3d point,Vec3d centre){}
    private static final Map<UUID,VolleyAim> VOLLEYS=new HashMap<>();
    /**
     * (2.96.0) T's run, by its swing's id: the sword she stood on, the one she flies on to and where she lands beyond the
     * foe (the server's word; its foe and where it comes down are VOLLEYS', as H's basic attack's).
     */
    public record LungeRun(int from,int to,Vec3d past){}
    private static final Map<UUID,LungeRun> LUNGES=new HashMap<>();
    /** (2.96.0) T's way through each formation, by the formation's swing id: T's start (the server's tick), its run and T's own swing. */
    record Rush(long start,LungeRun run,UUID swing){}
    static final Map<UUID,Rush> RUSHES=new HashMap<>();
    /** (2.96.0) When a T was called off (seconds into it, the server's word), and when each Lee So-sang player's T is ready again. */
    private static final Map<UUID,Float> LUNGE_ENDS=new HashMap<>();
    /** (2.96.6) Where the first T of each T's row began (her sword then): the side her camera stays on, the way its slashes reach. */
    private static final Map<UUID,Vec3d> CHAIN_FROM=new HashMap<>();
    private static final Map<UUID,Long> T_READY=new HashMap<>();
    /** (2.95.0) When a volley was called off (seconds into its swing, the server's word: its user gone): its swords go then. */
    private static final Map<UUID,Float> VOLLEY_ENDS=new HashMap<>();
    /** (2.95.0) The tick each player's H's basic attack is ready again (the server's). */
    private static final Map<UUID,Long> V_READY=new HashMap<>();
    /**
     * (2.97.0) Y's rings, by their swing's id: whether the server has set it and where its middle stands on the ground (the
     * server's word; until it is set the swords follow the foe as drawn, SwordCage.ring).
     */
    public record CageRing(boolean planted,Vec3d at){}
    private static final Map<UUID,CageRing> CAGES=new HashMap<>();
    /** (2.97.0) Y's leap off each formation's sword, by the formation's swing id: Y's start (the server's tick) and Y's own swing. */
    record Leap(long start,UUID swing){}
    static final Map<UUID,Leap> LEAPS=new HashMap<>();
    /** (2.97.0) When a Y was called off (seconds into it, the server's word), and when each Lee So-sang player's Y is ready again. */
    private static final Map<UUID,Float> CAGE_ENDS=new HashMap<>();
    private static final Map<UUID,Long> Y_READY=new HashMap<>();
    /**
     * (2.101.0) H again, by its swing's id (the server's word): the formation it ends (its swing's id and its start: where
     * its swords lie follows from the id), that formation's middle and its user's way, the sword she stood on, which
     * recording it plays, and where she stands (on the formation's ground, where she comes down).
     */
    public record FinaleRun(UUID form,long formStart,Vec3d centre,float formYaw,int on,int variant,Vec3d spot){}
    private static final Map<UUID,FinaleRun> FINALES=new HashMap<>();
    /** (2.101.0) The formations H again has taken, by the formation's swing id: the finale's swing id. */
    private static final Map<UUID,UUID> TAKEN=new HashMap<>();
    /** (2.101.0) When an H again was called off (seconds into it, the server's word). */
    private static final Map<UUID,Float> FINALE_ENDS=new HashMap<>();
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
        // (the user's 2.87.0 preview) G's arena: a cutscene shared with everyone in it
        GArenaClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(IsosangAttack.EVENT,(mc,handler,b,response)->{
            UUID caster=b.readUuid();var dim=b.readIdentifier();int type=b.readUnsignedByte();UUID id=b.readUuid();int combo=b.readUnsignedByte(),blow=b.readByte();
            long start=b.readLong(),serverNow=b.readLong();Vec3d at=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>512||type>IsosangAttack.CROSSED||combo>IsosangRules.FINALE)return;
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
            // (2.83.0) when G is ready again, and G's recording and where its sword strikes (none from a 2.82 server)
            long gReady=b.isReadable()?b.readLong():0;Ultimate ult=null;
            if(b.isReadable()&&b.readBoolean()){int variant=b.readUnsignedByte();ult=new Ultimate(Math.min(variant,IsosangRules.G_VARIANTS-1),new Vec3d(b.readDouble(),b.readDouble(),b.readDouble()));}
            Ultimate cast=ult;
            // (2.93.0) Z: when it is ready again, its window and its moves; and H's formation's perch (none from a 2.92 server)
            IsosangRules.ZState z=null;Perch perch=null;
            if(b.isReadable()){
                long zReady=b.readLong(),zFirst=b.readLong();int zUses=b.readUnsignedByte();z=new IsosangRules.ZState(zFirst,Math.min(zUses,IsosangRules.Z_USES),zReady);
                if(b.readBoolean()){int on=b.readUnsignedByte(),from=b.readUnsignedByte();long moved=b.readLong();
                    if(on<IsosangRules.FORMATION_SWORDS&&(from==255||from<IsosangRules.FORMATION_SWORDS))perch=new Perch(on,from==255?-1:from,moved);}
            }
            IsosangRules.ZState zs=z;Perch perched=perch;
            // (2.95.0) when H's basic attack is ready again, and for it whom its swords come down on and where (none from a
            // 2.94 server)
            long vReady=0;VolleyAim volley=null;
            if(b.isReadable()){
                vReady=b.readLong();
                if(b.readBoolean()){int target=b.readVarInt();Vec3d p=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                    if(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z))volley=new VolleyAim(target,p,null);}
            }
            long vr=vReady;VolleyAim shower=volley;
            // (2.96.0) when T is ready again, and for T the sword she stood on, the one she flies on to and where she lands
            // (none from a 2.95 server)
            long tReady=0;LungeRun lunge=null;
            if(b.isReadable()){
                tReady=b.readLong();
                if(b.readBoolean()){int from=b.readUnsignedByte(),to=b.readUnsignedByte();Vec3d past=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                    if(from<IsosangRules.FORMATION_SWORDS&&to<IsosangRules.FORMATION_SWORDS&&Double.isFinite(past.x)&&Double.isFinite(past.y)&&Double.isFinite(past.z))lunge=new LungeRun(from,to,past);}
            }
            long tr=tReady;LungeRun run=lunge;
            // (2.97.0) when Y is ready again, and for Y whether its ring is set and where its middle stands (none from a
            // 2.96 server)
            long yReady=0;CageRing cage=null;
            if(b.isReadable()){
                yReady=b.readLong();
                if(b.readBoolean()){boolean planted=b.readBoolean();Vec3d c=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                    if(Double.isFinite(c.x)&&Double.isFinite(c.y)&&Double.isFinite(c.z))cage=new CageRing(planted,c);}
            }
            long yr=yReady;CageRing ring=cage;
            // (2.101.0) H again: the formation it ends, its middle and way, the sword she stood on, its recording, where she
            // stands (none from a 2.100 server)
            FinaleRun finale=null;
            if(b.isReadable()&&b.readBoolean()){
                UUID taken=b.readUuid();long formStart=b.readLong();Vec3d mid=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());float fy=b.readFloat();
                int on=b.readUnsignedByte(),variant=b.readUnsignedByte();Vec3d spot=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
                if(on<IsosangRules.FORMATION_SWORDS&&Double.isFinite(mid.x)&&Double.isFinite(mid.y)&&Double.isFinite(mid.z)&&Double.isFinite(spot.x)&&Double.isFinite(spot.y)&&Double.isFinite(spot.z)&&Float.isFinite(fy))
                    finale=new FinaleRun(taken,formStart,mid,fy,on,Math.min(variant,IsosangRules.FINALE_VARIANTS-1),spot);
            }
            FinaleRun ends=finale;
            mc.execute(()->accept(mc,caster,dim,type,id,combo,blow,start,serverNow,at,yaw,ready,struck,empowered,charged,eReady,thrown,rReady,stands,hReady,laid,gReady,cast,zs,perched,vr,shower,tr,run,yr,ring,ends));
        });
        ClientTickEvents.START_CLIENT_TICK.register(IsosangClient::tick);
        // (2.80.0) the swords of light: the empowered first's thrown swords and the empowered fourth's giant sword
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(IsosangEmpower::models);
        // (2.83.0) G's own space for its user's cutscene: its ground under the sword coming down, and the sword (solid, with the entities)
        GStage.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(GStage::ground);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(GStage::sword);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(GStage::worldClouds);
        // (2.90.0) the observers' storm before the effect layer (Kageno's renderer, in the default phase)
        net.minecraft.util.Identifier farClouds=dev.sangwoo.celestialfall.CelestialFall.id("isosang_g_far_clouds");
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.addPhaseOrdering(farClouds,net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(farClouds,GStage::farClouds);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->clear(mc)));
    }
    private static void accept(MinecraftClient mc,UUID caster,Identifier dim,int type,UUID id,int combo,int blow,long start,long serverNow,Vec3d at,float yaw,long ready,List<StandClient.Target> struck,
                               boolean empowered,boolean charged,long eReady,Sword sword,long rReady,Sweep sweep,long hReady,Formation form,long gReady,Ultimate ult,
                               IsosangRules.ZState z,Perch perch,long vReady,VolleyAim volley,long tReady,LungeRun lunge,long yReady,CageRing cage,FinaleRun finale){
        if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dim))return;
        if(world!=mc.world)clear(mc);
        READY.put(caster,ready);CHARGED.put(caster,charged);E_READY.put(caster,eReady);R_READY.put(caster,rReady);H_READY.put(caster,hReady);G_READY.put(caster,gReady);
        V_READY.put(caster,vReady);T_READY.put(caster,tReady);Y_READY.put(caster,yReady);
        // (2.97.0) Y's ring: where the server has it now (set a tick before its first swords strike)
        if(combo==IsosangRules.CAGE&&cage!=null&&type!=IsosangAttack.HIT&&SWINGS.containsKey(id))CAGES.put(id,cage);
        if(z!=null)Z_STATES.put(caster,z);
        // (2.93.0) where H's formation's user stands: every formation's news says
        if(perch!=null&&combo==IsosangRules.FORMATION)PERCHES.put(id,perch);
        if(type==IsosangAttack.CROSSED){
            // Z (2.93.0): its user flies to another sword of their formation: its light, and its sound as it begins
            if(perch!=null&&perch.from()>=0&&combo==IsosangRules.FORMATION){
                var all=FLIGHTS.computeIfAbsent(id,k->new ArrayList<>());
                if(all.stream().noneMatch(f->f.moved()==perch.moved()))all.add(new Flight(perch.from(),perch.on(),perch.moved()));
                while(all.size()>6)all.remove(0);
                if(serverNow-perch.moved()<=LATE)stepVoice(mc,id,caster);
            }
            return;
        }
        if(type==IsosangAttack.HIT&&IsosangRules.drawn(combo)){
            // (2.81.0) R's sweep: where it stands with it (it may stand out toward the foe in the crosshair)
            if(combo==IsosangRules.SWEEP){if(sweep==null)return;SWEEPS.put(id,sweep);}
            // (2.82.0) H's formation: where its middle is
            if(combo==IsosangRules.FORMATION){if(form==null)return;FORMATIONS.put(id,form);}
            // (2.83.0) G: which recording it plays and where its sword strikes
            if(combo==IsosangRules.ULTIMATE){if(ult==null)return;ULTIMATES.put(id,ult);}
            // (2.95.0) H's basic attack: whom its swords come down on, or where
            if(combo==IsosangRules.VOLLEY){if(volley==null)return;VOLLEYS.put(id,new VolleyAim(volley.target(),volley.point(),form==null?null:form.centre()));}
            // (2.96.0) T: whom its blows come down on and where, as the basic attack's; its run through the formation (the
            // formation holds her along it: spot, raised; her flight on is Z's, SwordStep)
            if(combo==IsosangRules.LUNGE){
                if(volley==null||lunge==null)return;
                VOLLEYS.put(id,new VolleyAim(volley.target(),volley.point(),form==null?null:form.centre()));LUNGES.put(id,lunge);
                var on=formationOf(caster);
                // (2.96.6) where the first T of her row began: the side her camera stays on through them (cameraHold), the
                // way its slashes reach (SwordLunge.toward); the one before it in the row still drawn as this one begins
                StandClient.Cast before=null;
                for(var c:SWINGS.values())if(c.combo()==IsosangRules.LUNGE&&c.caster().equals(caster)&&!STOPPED.contains(c.id())
                    &&start>c.start()&&start-c.start()<IsosangRules.LUNGE_LENGTH&&(before==null||c.start()>before.start()))before=c;
                Vec3d began=before==null?null:CHAIN_FROM.get(before.id());
                if(began==null&&on!=null){var f=formation(on.id());if(f!=null)began=place(on.id(),f,lunge.from());}
                if(began!=null)CHAIN_FROM.put(id,began);
                if(on!=null){
                    RUSHES.put(on.id(),new Rush(start,lunge,id));PERCHES.put(on.id(),new Perch(lunge.to(),lunge.from(),start+IsosangRules.LUNGE_HOP));
                    var all=FLIGHTS.computeIfAbsent(on.id(),k->new ArrayList<>());long hop=start+IsosangRules.LUNGE_HOP;
                    if(all.stream().noneMatch(f->f.moved()==hop))all.add(new Flight(-1,lunge.to(),hop,lunge.past()));
                    while(all.size()>6)all.remove(0);
                }
            }
            // (2.97.0) Y: whom its swords come down on and where, as the basic attack's; its ring; and her leap off the sword
            // she stands on (the formation lifts her: raised)
            if(combo==IsosangRules.CAGE){
                if(volley==null||cage==null)return;
                VOLLEYS.put(id,new VolleyAim(volley.target(),volley.point(),form==null?null:form.centre()));CAGES.put(id,cage);
                var on=formationOf(caster);if(on!=null)LEAPS.put(on.id(),new Leap(start,id));
            }
            // (2.101.0) H again: the formation it takes (its swords pulled out of the ground are the finale's; its sound
            // fades now, no sound of its end)
            if(combo==IsosangRules.FINALE){
                if(finale==null)return;
                FINALES.put(id,finale);TAKEN.put(finale.form(),id);ENDED.add(finale.form());
                var v=VOICES.get(finale.form());if(v!=null)v.fade();
            }
            var cast=new StandClient.Cast(id,caster,dim,IsosangRules.SKILL,combo,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)/20f>=life(combo))return;
            // (2.95.0: H's basic attack moves no body, standing on in the formation's)
            if(combo!=IsosangRules.VOLLEY)KagenoClips.begin(cast);
            SWINGS.put(id,cast);STRUCK.put(id,new int[IsosangRules.blows(combo)][]);
            // (2.87.0) G: the fire round her after its cutscene
            if(combo==IsosangRules.ULTIMATE)FallingSword.begin(cast);
            if(empowered)EMPOWERED.add(id);
            if(serverNow-start<=LATE)voice(mc,id,caster,combo);
        }else if(type==IsosangAttack.EMPOWERED){
            // E (2.80.0): a flash of moonlight round them, drawn as a swing of its own with no body motion (combo 0)
            var cast=new StandClient.Cast(UUID.randomUUID(),caster,dim,IsosangRules.SKILL,0,start,serverNow,StandClient.ticks(),at,yaw,List.of());
            if(cast.age(0)<20)SWINGS.put(cast.id(),cast);
        }else if(type==IsosangAttack.THROWN&&combo==IsosangRules.CAGE){
            // (2.97.0) Y's ring set (its middle taken above, CAGES)
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
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.ULTIMATE){
            // (2.83.0) G called off before its sword struck (its user gone): its sword goes now, its sound fades
            var c=SWINGS.get(id);if(c!=null){ULTIMATE_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);var v=VOICES.get(id);if(v!=null)v.fade();FallingSword.calledOff(id);}
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.FORMATION){
            // (2.82.0) H's formation ended early (its user taken away): its swords go now, its user drops
            var c=SWINGS.get(id);if(c!=null){FORMATION_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);}
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.LUNGE){
            // (2.96.0) T called off (its user gone, their formation over): its swords and slashes not due yet go now, its
            // sound fades, and the formation (if it stands on) holds her on the sword she was flying to
            var c=SWINGS.get(id);if(c!=null){LUNGE_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);var v=VOICES.get(id);if(v!=null)v.fade();}
            var on=formationOf(caster);var run=LUNGES.get(id);
            if(on!=null&&run!=null){RUSHES.remove(on.id());PERCHES.put(on.id(),new Perch(run.to(),-1,serverNow));}
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.CAGE){
            // (2.97.0) Y called off (its user gone, their formation over): its swords not down yet go now, its sound fades;
            // she comes down off her leap
            var c=SWINGS.get(id);if(c!=null){CAGE_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);var v=VOICES.get(id);if(v!=null)v.fade();}
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.FINALE){
            // (2.101.0) H again called off (her gone): its swords not down yet go now, its sound fades
            var c=SWINGS.get(id);if(c!=null){FINALE_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);var v=VOICES.get(id);if(v!=null)v.fade();}
        }else if(type==IsosangAttack.STOPPED&&combo==IsosangRules.VOLLEY){
            // (2.95.0) H's basic attack called off (its user gone): the swords not struck yet go now, its sound fades
            var c=SWINGS.get(id);if(c!=null){VOLLEY_ENDS.put(id,Math.max(0,(serverNow-start)/20f));STOPPED.add(id);var v=VOICES.get(id);if(v!=null)v.fade();}
        }else if(type==IsosangAttack.STOPPED){
            // the swing in flight lets go of the body (a hit stopped before its first blow leaves nothing to see)
            // (2.82.0: not H's formation, told of by its own STOPPED, above)
            for(var c:SWINGS.values())if(c.caster().equals(caster)&&!STOPPED.contains(c.id())&&animating(c)&&c.combo()!=IsosangRules.FORMATION&&c.combo()!=IsosangRules.ULTIMATE&&c.combo()!=IsosangRules.LUNGE&&c.combo()!=IsosangRules.CAGE&&c.combo()!=IsosangRules.FINALE)STOPPED.add(c.id());
            // (2.81.0: R's sweep is told of by its own STOPPED, above)
            // (2.95.0: nor H's basic attack, by its own STOPPED, above; its swords fade as they go)
            SWINGS.values().removeIf(c->STOPPED.contains(c.id())&&c.combo()!=IsosangRules.SWEEP&&c.combo()!=IsosangRules.VOLLEY&&c.combo()!=IsosangRules.LUNGE&&c.combo()!=IsosangRules.CAGE&&c.combo()!=IsosangRules.FINALE&&c.age(0)<IsosangRules.blowAt(c.combo(),0));
            for(var e:VOICES.entrySet()){var c=SWINGS.get(e.getKey());if(c==null||STOPPED.contains(e.getKey())&&c.combo()!=IsosangRules.SWEEP&&c.combo()!=IsosangRules.FORMATION&&c.combo()!=IsosangRules.ULTIMATE&&c.combo()!=IsosangRules.FINALE)e.getValue().fade();}
        }
    }
    /**
     * Whether this swing's body clip still plays (it is the body's motion until then, unless it stopped short; E's flash
     * has none, nor (2.95.0) H's basic attack: its user stands on in the formation's stance, the formation's clip).
     */
    static boolean animating(StandClient.Cast c){return IsosangRules.drawn(c.combo())&&c.combo()!=IsosangRules.VOLLEY&&c.age(0)<KagenoSkills.of(IsosangRules.SKILL).clip(c.combo()).length()*20+1;}
    /**
     * How long a swing of this kind is drawn for (seconds, 2.82.0): H's formation its whole length and the ink and mist
     * after it; (2.95.0) H's basic attack until its last sword's light is out; every other its skill's (IsosangSlash.lifetime).
     */
    public static float life(int combo){
        return combo==IsosangRules.FORMATION?IsosangRules.H_END+SwordFormation.AFTER:combo==IsosangRules.ULTIMATE?FallingSword.LIFE
            :combo==IsosangRules.VOLLEY?SwordVolley.LIFE:combo==IsosangRules.LUNGE?SwordLunge.LIFE:combo==IsosangRules.CAGE?SwordCage.LIFE:combo==IsosangRules.FINALE?SwordFinale.LIFE
            :KagenoSkills.of(IsosangRules.SKILL).lifetime();
    }
    /** How long this swing is drawn for (seconds): a formation that ended early only until its swords have gone (2.82.0). */
    /** (2.99.1) How much of H's night (FormationSky) this client shows for the formation of swing {@code swing} as it last drew it (0 to 1), for the checks. */
    public static float nightShown(UUID swing){return FormationSky.presenceNow(swing);}
    public static float lifeOf(StandClient.Cast c){
        float life=life(c.combo());
        if(c.combo()==IsosangRules.FORMATION){Float end=FORMATION_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+SwordFormation.AFTER);}
        if(c.combo()==IsosangRules.ULTIMATE){Float end=ULTIMATE_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+FallingSword.GONE);}
        // (2.95.0) H's basic attack called off: until its swords have faded (SwordVolley.kept)
        if(c.combo()==IsosangRules.VOLLEY){Float end=VOLLEY_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+.15f);}
        // (2.96.0) T called off: until what it left has faded
        if(c.combo()==IsosangRules.LUNGE){Float end=LUNGE_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+.15f);}
        if(c.combo()==IsosangRules.CAGE){Float end=CAGE_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+.4f);}
        // (2.101.0) H again called off: until its swords have faded
        if(c.combo()==IsosangRules.FINALE){Float end=FINALE_ENDS.get(c.id());if(end!=null)life=Math.min(life,end+.4f);}
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
    /** The tick this player's G is ready again, as the server told it (0 if it is ready, 2.83.0). */
    public static long gReady(UUID id){return G_READY.getOrDefault(id,0L);}
    /** The tick this player's H's basic attack is ready again, as the server told it (0 if it is ready, 2.95.0). */
    public static long vReady(UUID id){return V_READY.getOrDefault(id,0L);}
    /** H's basic attack of this swing: whom its swords come down on and where (null for none, 2.95.0). */
    public static VolleyAim volley(UUID swing){return VOLLEYS.get(swing);}
    /** (2.97.0) The tick this player's Y is ready again, as the server told it (0 if it is ready). */
    public static long yReady(UUID id){return Y_READY.getOrDefault(id,0L);}
    /** (2.97.0) Y's ring of this swing as the server last told it (null for none). */
    public static CageRing cage(UUID swing){return CAGES.get(swing);}
    /** (2.97.0) When this Y was called off (seconds into it, the server's word), or NaN if it was not. */
    public static float cageEnd(UUID swing){var e=CAGE_ENDS.get(swing);return e==null?Float.NaN:e;}
    // (2.96.5) the local player's camera held through their T: where it stands (null for none), the latest T it holds for,
    // how much it was held the last time, when that T is over (ticks into it), where it came down, the way the camera
    // looked as it began to come back, and whether its last turn has been given
    private static Vec3d heldAt,heldFoe;private static StandClient.Cast heldBy;private static double heldW,heldEnd;
    private static float turnFrom=Float.NaN;private static boolean turnDone;
    private static void letGo(){heldAt=heldFoe=null;heldBy=null;heldW=0;turnFrom=Float.NaN;turnDone=false;}
    /**
     * Where the local player's camera stands through their T (2.96.5, user request: "이게 카메라가 너무 어지러운데 T쓸때 카메라
     * 무빙을 영상 처럼 바꿔줘", the reading confirmed: "맞음, 이대로"): where it stood as their first T began, all through it and
     * every T after it in a row (it follows neither her dash nor her flight on to the far sword: the video's camera stood
     * still through four T's while she moved about in it; 2.96.1 to 2.96.4 went with her and turned round onto the foe,
     * half a turn a T), then back to her over IsosangRules.LUNGE_CAMERA_BACK once the last is over (user choice "T가
     * 끝나면"); {@code follow}, where it would stand with her, otherwise. Its way stays the mouse's all the while (user choice
     * "방향만 돌림"). A T begun as it comes back holds it where it is then, so it never jumps.
     */
    public static Vec3d cameraHold(Vec3d follow,float delta){
        var mc=MinecraftClient.getInstance();var p=mc.player;
        if(p==null||mc.world==null||!MapClient.enabled()){letGo();return follow;}
        StandClient.Cast latest=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.LUNGE&&c.caster().equals(p.getUuid())&&(latest==null||c.start()>latest.start()))latest=c;
        if(latest!=null&&(heldBy==null||latest.start()>heldBy.start())){
            heldAt=heldBy==null?follow:follow.lerp(heldAt,heldW);
            heldBy=latest;heldEnd=IsosangRules.LUNGE_LENGTH;turnFrom=Float.NaN;turnDone=false;
        }
        if(heldBy==null)return follow;
        Float stop=LUNGE_ENDS.get(heldBy.id());if(stop!=null)heldEnd=Math.min(IsosangRules.LUNGE_LENGTH,stop*20);
        Vec3d at=SWINGS.containsKey(heldBy.id())?SwordVolley.target(heldBy,delta):null;if(at!=null)heldFoe=at;
        double age=heldBy.age(delta),w=IsosangRules.lungeCameraHeld(age,heldEnd);
        if(w<=0){if(age>heldEnd+IsosangRules.LUNGE_CAMERA_BACK+5)letGo();heldW=0;return follow;}
        heldW=w;
        return follow.lerp(heldAt,w);
    }
    /**
     * The way the local player's camera looks as it comes back to them after their T (2.96.5): round onto where their last
     * T came down (its foe as drawn, or the ground it went for) from where they are, on the same ease as it comes back
     * (IsosangRules.lungeCameraTurn), from the way it looked ({@code current}) as it began to; NaN while it is held (its way
     * the mouse's) and after (BotandCamera sets it). With her across the foe from where it stood, the camera come back
     * behind her the way it looked would have the foe behind it.
     */
    public static float cameraTurn(float current,float delta){
        var p=MinecraftClient.getInstance().player;
        if(p==null||heldBy==null||heldFoe==null||turnDone)return Float.NaN;
        double age=heldBy.age(delta);if(age<=heldEnd){turnFrom=Float.NaN;return Float.NaN;}
        Vec3d d=heldFoe.subtract(p.getLerpedPos(delta));if(d.horizontalLengthSquared()<1)return Float.NaN;
        if(Float.isNaN(turnFrom))turnFrom=current;
        if(age>=heldEnd+IsosangRules.LUNGE_CAMERA_BACK)turnDone=true;
        return IsosangRules.lungeCameraTurn(age,heldEnd,turnFrom,(float)Math.toDegrees(Math.atan2(-d.x,d.z)));
    }
    /** (2.96.5) Whether the local player's camera is held through their T now (for the film's log). */
    public static double cameraHeld(){return heldBy==null?0:heldW;}
    // (2.97.0) the local player's camera through their Y: the Y whose shot it was last, and the way the game's camera
    // looked as it began (turned round from it onto the foe while the shot holds the screen, cageTurn)
    private static UUID cageBy;private static float cageFrom=Float.NaN;private static double cageW;
    private static void cageLetGo(){cageBy=null;cageFrom=Float.NaN;cageW=0;}
    /** The local player's latest Y while its shot may still hold their camera (null for none). */
    static StandClient.Cast ownCage(float delta){
        var p=MinecraftClient.getInstance().player;if(p==null)return null;
        StandClient.Cast latest=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.CAGE&&c.caster().equals(p.getUuid())&&(latest==null||c.start()>latest.start()))latest=c;
        return latest==null||latest.age(delta)/20f>=IsosangRules.CAGE_BACK1?null:latest;
    }
    /**
     * The local player's camera through their Y (2.97.0, the user's videos of it, the reading confirmed: "맞음, 이대로"):
     * swung round to her side so she and the foe are seen together, then low behind her as she comes down onto her sword,
     * then back to her as Y lets go of her (SwordCage.shot, its weight SwordCage.held); {@code game}, the game's camera,
     * otherwise and blended with it. Only in the third person.
     */
    public static dev.sangwoo.celestialfall.client.CinematicDirector.Pose cageCamera(dev.sangwoo.celestialfall.client.CinematicDirector.Pose game,float delta){
        var mc=MinecraftClient.getInstance();if(game==null||mc.world==null||!MapClient.enabled()){cageW=0;return game;}
        var c=ownCage(delta);if(c==null){cageW=0;return game;}
        float t=c.age(delta)/20f;double w=SwordCage.held(c,t);cageW=w;
        if(w<=1e-4)return game;
        var shot=SwordCage.shot(c,t,delta);if(shot==null)return game;
        float yaw=game.yaw()+net.minecraft.util.math.MathHelper.wrapDegrees(shot.yaw()-game.yaw())*(float)w;
        float pitch=game.pitch()+(shot.pitch()-game.pitch())*(float)w;
        return new dev.sangwoo.celestialfall.client.CinematicDirector.Pose(game.position().lerp(shot.position(),w),yaw,pitch,0);
    }
    /**
     * The way the game's camera looks through the local player's Y (2.97.0): while its shot holds the screen it turns
     * round from the way it looked as Y began ({@code current} then) onto the foe (Y's way, the server's), so as the shot
     * lets go the camera is behind her looking at the cage; NaN otherwise (the mouse's then).
     */
    public static float cageTurn(float current,float delta){
        var c=ownCage(delta);
        if(c==null){cageBy=null;cageFrom=Float.NaN;return Float.NaN;}
        float t=c.age(delta)/20f;
        if(t<0||t>=IsosangRules.CAGE_BACK0){return Float.NaN;}
        if(!c.id().equals(cageBy)){cageBy=c.id();cageFrom=current;}
        float w=dev.sangwoo.celestialfall.client.kageno.Ease.smooth(t,IsosangRules.CAGE_SIDE_IN,IsosangRules.CAGE_BEHIND1);
        return cageFrom+net.minecraft.util.math.MathHelper.wrapDegrees(c.yaw()-cageFrom)*w;
    }
    /** (2.97.0) Where this Y's ring stands as drawn now (the multiplayer check reads it; null for none). */
    public static Vec3d cageRingNow(UUID swing){var c=SWINGS.get(swing);return c==null?null:SwordCage.ring(c,0);}
    /** (2.97.0) How many of this Y's swords are drawn now (the multiplayer check reads it). */
    public static int cageSwordsDrawn(UUID swing,float delta){
        var c=SWINGS.get(swing);if(c==null)return 0;var r=SwordCage.ring(c,delta);if(r==null)return 0;
        int n=0;for(var b:SwordCage.blades(c,r,c.age(delta)/20f))if(b!=null)n++;return n;
    }
    /** (2.97.0) How much the local player's camera is held by their Y's shot now (for the film's log). */
    public static double cageHeld(){return cageW;}
    /** (2.97.0) Where this Y's ring stands now (its middle on the ground; null for none heard of): where its sound is heard from. */
    static Vec3d cageAt(UUID swing){var c=SWINGS.get(swing);if(c!=null){var r=SwordCage.ring(c,0);if(r!=null)return r;}var t=CAGES.get(swing);return t==null?null:t.at();}
    /**
     * Whether this player is carried by their formation now (2.96.0): Z's flight or the dodge's, T's dash and its flight
     * on, a tick either side. BotandCamera takes no step of theirs then for a teleport (a long flight's first tick goes
     * more than 8 blocks).
     */
    public static boolean carried(UUID caster,float delta){
        for(var c:SWINGS.values()){
            if(c.combo()!=IsosangRules.FORMATION||!c.caster().equals(caster)||STOPPED.contains(c.id()))continue;
            double age=c.age(delta);
            for(int d=-1;d<=1;d++){
                if(flying(c.id(),age+d)>=0)return true;
                double a=rushAge(c.id(),age+d);if(!Double.isNaN(a)&&a>=IsosangRules.LUNGE_GO-1)return true;
            }
        }
        return false;
    }
    /**
     * The way T's body faces {@code delta} into its clock (2.96.0): the way she dashed (the swing's way, the server's)
     * until she flies on, then turning back over the rest of it to her formation's way (the formation's own swing faces
     * that way: no turn as T's motion ends and the formation's goes on).
     */
    public static float lungeFacing(StandClient.Cast cast,float delta){return lungeFacingAt(cast,cast.age(delta));}
    /** The same {@code age} ticks into T. */
    static float lungeFacingAt(StandClient.Cast cast,double age){
        var form=formationOf(cast.caster());if(form==null)return cast.yaw();
        float u=dev.sangwoo.celestialfall.client.kageno.Ease.smooth((float)age,IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL,IsosangRules.LUNGE_LENGTH-2);
        float own=cast.yaw()+net.minecraft.util.math.MathHelper.wrapDegrees(form.yaw()-cast.yaw())*u;
        // (2.96.4) used again as she stands on the far sword: from the way the one before had her facing then, round to
        // this one's as she coils (before she sets off), never snapping
        var before=chainedAfter(cast);if(before==null)return own;
        float from=lungeFacingAt(before,cast.start()-before.start()),w=dev.sangwoo.celestialfall.client.kageno.Ease.smooth((float)age,0,IsosangRules.LUNGE_GO);
        return from+net.minecraft.util.math.MathHelper.wrapDegrees(own-from)*w;
    }
    /**
     * The T this one follows on from (2.96.4: she may use it again as she stands on the far sword, IsosangRules.LUNGE_FREE):
     * the latest T of the same player begun before it whose body clip still played as it began, or null.
     */
    public static StandClient.Cast chainedAfter(StandClient.Cast cast){
        if(cast.combo()!=IsosangRules.LUNGE)return null;
        StandClient.Cast best=null;
        for(var c:SWINGS.values()){
            if(c==cast||c.combo()!=IsosangRules.LUNGE||!c.caster().equals(cast.caster())||STOPPED.contains(c.id()))continue;
            long gap=cast.start()-c.start();
            if(gap>0&&gap<IsosangRules.LUNGE_LENGTH&&(best==null||c.start()>best.start()))best=c;
        }
        return best;
    }
    /** (2.96.0) T's run of this swing (null for a swing not T's). */
    public static LungeRun lunge(UUID swing){return LUNGES.get(swing);}
    /** (2.96.6) Where the first T of this T's row began (her sword then), or null if not heard. */
    public static Vec3d chainFrom(UUID swing){return CHAIN_FROM.get(swing);}
    /** (2.96.0) The tick this player's T is ready again (0 if it is: the server's word). */
    public static long tReady(UUID id){return T_READY.getOrDefault(id,0L);}
    /** The formation under way of this player (2.96.0: T's run goes through it; the multiplayer check reads it), or null. */
    public static StandClient.Cast formationOf(UUID caster){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(caster)&&!STOPPED.contains(c.id())&&FORMATIONS.containsKey(c.id()))best=c;
        return best;
    }
    /** T's run through formation swing {@code id} {@code age} ticks into the formation: ticks into T, or NaN when none runs then (2.96.0). */
    static double rushAge(UUID id,double age){
        var r=RUSHES.get(id);var c=SWINGS.get(id);if(r==null||c==null)return Double.NaN;
        double a=age-rushLag(id)-(r.start()-c.start());return a>=0&&a<IsosangRules.LUNGE_HOP+IsosangRules.Z_TRAVEL?a:Double.NaN;
    }
    /**
     * How far ahead of T's own clock the clock of the formation of swing {@code id} runs (ticks, 2.96.0; 0 with no T, or
     * once T's swing is gone). The formation was heard long before, and on a slow server its client's ticks drift from
     * the server's (the multiplayer check: 3 ticks after some 20 s, on the watcher's); T's was heard as it began. T's way
     * through the formation (the body's dash and flight on, its orb, IsosangClient.spot, SwordStep) runs on T's clock, so
     * the body goes with T's light (the streak, the swords, the slashes: T's swing).
     */
    static double rushLag(UUID id){
        var r=RUSHES.get(id);var f=SWINGS.get(id);var t=r==null?null:SWINGS.get(r.swing());
        return r==null||f==null||t==null?0:(f.age(0)-t.age(0))-(r.start()-f.start());
    }
    /** The same for a perch or a flight of the formation of swing {@code id} that left at {@code moved}: T's flight on runs on T's clock. */
    static double moveLag(UUID id,long moved){var r=RUSHES.get(id);return r!=null&&moved==r.start()+IsosangRules.LUNGE_HOP?rushLag(id):0;}
    /** When this swing's volley was called off (seconds into it, the server's word), or NaN if it was not (2.95.0). */
    public static float volleyEnd(UUID swing){var e=VOLLEY_ENDS.get(swing);if(e==null)e=LUNGE_ENDS.get(swing);return e==null?Float.NaN:e;}
    /**
     * (2.95.0, the multiplayer check reads it) Where the swords of H's basic attack of this swing come down as this client
     * draws them now (its foe's chest as drawn, else where it was last drawn or the server said), and how many of its
     * swords are drawn now (2.96.0: T's too); null and 0 for no such swing.
     */
    public static Vec3d volleyAt(UUID swing,float delta){var c=SWINGS.get(swing);return c==null||c.combo()!=IsosangRules.VOLLEY?null:SwordVolley.target(c,delta);}
    public static int volleyDrawn(UUID swing,float delta){
        var c=SWINGS.get(swing);if(c==null||c.combo()!=IsosangRules.VOLLEY&&c.combo()!=IsosangRules.LUNGE)return 0;Vec3d at=SwordVolley.target(c,delta);if(at==null)return 0;
        int n=0;float t=c.age(delta)/20f;for(int i=0;i<IsosangRules.VOLLEY_SWORDS;i++)if(SwordVolley.pose(c.combo(),c.origin(),c.yaw(),at,t,i)!=null)n++;return n;
    }
    /** (2.95.0) Whom and where the local player's crosshair picks for H's basic attack now (SwordVolley.aim: null off their formation's sword; the film's log reads it). */
    public static VolleyAim aimNow(MinecraftClient mc){var a=SwordVolley.aim(mc);return a==null?null:new VolleyAim(a.target(),a.point(),null);}
    /** (2.96.0) The same for T (its own window, IsosangRules.lungeTime; the film's log reads it). */
    public static VolleyAim lungeAimNow(MinecraftClient mc){var a=SwordVolley.aim(mc,true);return a==null?null:new VolleyAim(a.target(),a.point(),null);}
    /** (2.97.0) The same for Y (for the film's log). */
    public static VolleyAim cageAimNow(MinecraftClient mc){var a=SwordVolley.aim(mc,IsosangRules.CAGE);return a==null?null:new VolleyAim(a.target(),a.point(),null);}
    /** Where H's basic attack of this swing comes down as the server told it (null for a swing not one, 2.95.0): its sound is heard from the nearer of it and its user. */
    public static Vec3d volleyPoint(UUID swing){var v=VOLLEYS.get(swing);return v==null?null:v.point();}
    /** G of this swing: its recording and where its sword strikes (null for none, 2.83.0). */
    public static Ultimate ultimate(UUID swing){return ULTIMATES.get(swing);}
    /** When this swing's G was called off (seconds into it, the server's word), or NaN if it was not (2.83.0). */
    public static float ultimateEnd(UUID swing){var e=ULTIMATE_ENDS.get(swing);return e==null?Float.NaN:e;}
    /** The local player's own G whose cutscene is on now (2.83.0), or null. */
    public static StandClient.Cast ownUltimate(){
        var mc=MinecraftClient.getInstance();if(mc.player==null)return null;
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.ULTIMATE&&c.caster().equals(mc.player.getUuid())&&!STOPPED.contains(c.id()))best=c;
        return best;
    }
    /** The local player's own formation under way now (2.93.0, for Z: the swords they may look at), or null. */
    public static StandClient.Cast ownFormation(){
        var mc=MinecraftClient.getInstance();if(mc.player==null)return null;
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(mc.player.getUuid())&&!STOPPED.contains(c.id())&&FORMATIONS.containsKey(c.id()))best=c;
        return best;
    }
    /** H's formation of this swing: its middle and its user's way (null for none, 2.82.0). */
    public static Formation formation(UUID swing){return FORMATIONS.get(swing);}
    /** (2.101.0) H again of this swing: the formation it ends, where she stands, its recording (null for none). */
    public static FinaleRun finale(UUID swing){return FINALES.get(swing);}
    /** (2.101.0) When this H again was called off (seconds into it, the server's word), or NaN if it was not. */
    public static float finaleEnd(UUID swing){var e=FINALE_ENDS.get(swing);return e==null?Float.NaN:e;}
    /** (2.101.0) The H again that took the formation of swing {@code formation} (null for none): its swords are the finale's from as each is pulled out. */
    public static StandClient.Cast takenBy(UUID formation){var id=TAKEN.get(formation);return id==null?null:SWINGS.get(id);}
    /** (2.101.0) This player's H again under way (not called off), or null. */
    public static StandClient.Cast finaleOf(UUID caster){
        StandClient.Cast best=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FINALE&&c.caster().equals(caster)&&!STOPPED.contains(c.id())&&FINALES.containsKey(c.id()))best=c;
        return best;
    }
    /** (2.101.0) The local player's own H again under way, or null. */
    public static StandClient.Cast ownFinale(){var mc=MinecraftClient.getInstance();return mc.player==null?null:finaleOf(mc.player.getUuid());}
    /** (2.101.0) How high over its ground the pommel she stood on as H again began stands (blocks: that sword's, SwordFormation.layout). */
    public static double finalePommel(UUID swing){return SwordFinale.pommel(FINALES.get(swing));}
    /** (2.101.0, the MP check) How many of formation {@code form}'s swords are still drawn standing in it now. */
    public static int finaleFormationStanding(UUID form){
        var c=SWINGS.get(form);if(c==null)return 0;
        var layout=SwordFormation.layout(form);float t=c.age(MinecraftClient.getInstance().getTickDelta())/20f;int n=0;
        for(var b:layout)if(SwordFormation.shown(c,b,t)>.01f)n++;
        return n;
    }
    /** (2.101.0, the MP check) How many of H again {@code swing}'s swords are drawn now. */
    public static int finaleSwordsDrawn(UUID swing){
        var c=SWINGS.get(swing);if(c==null)return 0;int n=0;
        for(var b:SwordFinale.blades(c,c.age(MinecraftClient.getInstance().getTickDelta())/20f))if(b!=null)n++;
        return n;
    }
    /**
     * (2.101.0) The way H again's body faces {@code delta} into it: from the way her formation had her facing round to the
     * way she looked as she pressed it (its band's), as she dips on the pommel, never snapping; (2.101.1) then the way she
     * rides round her lap (IsosangRules.finaleHeading: once round to the right), back to the band's as she stops in the air.
     */
    public static float finaleFacing(StandClient.Cast cast,float delta){
        var r=FINALES.get(cast.id());if(r==null)return cast.yaw();
        return SwordFinale.faces(SwordFinale.frame(cast,r),cast.age(delta)/20f);
    }
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
        // (2.101.0) H again: her leap off the pommel and down onto the ground where she stood (IsosangRules.finaleDrawn)
        var fin=finaleOf(caster);
        if(fin!=null){
            var r=FINALES.get(fin.id());var who=mc.world.getPlayerByUuid(caster);double t=fin.age(delta)/20.0;
            if(r!=null&&who!=null&&t>=0&&t<IsosangRules.FINALE_LAND+.3)return lift(IsosangRules.finaleDrawn(t,finalePommel(fin.id())),who.getLerpedPos(delta).y-r.spot().y);
        }
        StandClient.Cast form=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(caster)&&!STOPPED.contains(c.id()))form=c;
        if(form==null)return 0;
        var f=FORMATIONS.get(form.id());var p=mc.world.getPlayerByUuid(caster);if(f==null||p==null)return 0;
        double t=form.age(delta)/20.0;if(t<0||t>=IsosangRules.H_DOWN+.3)return 0;
        return lift(planned(form.id(),t),p.getLerpedPos(delta).y-f.centre().y);
    }
    /**
     * How far level from where it is this player's body is drawn now (2.93.0, Z): from Z's flight to a moment after it,
     * where their formation has them (IsosangClient.spot: over the sword they fly to) less where they are, so whoever sees
     * them a little late (another client, 3 ticks behind) sees them stand on that sword as the orb lands, not walk the
     * last of the way there (2.93.0's check: up to 8.5 blocks behind for 7 ticks on a local server; held long enough for a
     * slower one); easing out over SHIFT_OUT ticks once their position has caught up. Zero otherwise.
     */
    public static Vec3d shift(UUID caster,float delta){
        var mc=MinecraftClient.getInstance();if(mc.world==null)return Vec3d.ZERO;
        // (2.101.1) H again: round her lap of the sky and back over where she stands (IsosangRules.finaleLap; where she
        // really is the server keeps)
        var fin=finaleOf(caster);
        if(fin!=null){
            var r=FINALES.get(fin.id());var who=mc.world.getPlayerByUuid(caster);double t=fin.age(delta)/20.0;
            if(r!=null&&who!=null&&t>=0&&t<IsosangRules.FINALE_LAND+.3){
                Vec3d to=finaleOver(fin,t),at=who.getLerpedPos(delta);
                return new Vec3d(to.x-at.x,0,to.z-at.z);
            }
        }
        StandClient.Cast form=null;
        for(var c:SWINGS.values())if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(caster)&&!STOPPED.contains(c.id()))form=c;
        if(form==null)return Vec3d.ZERO;
        var p=PERCHES.get(form.id());var f=FORMATIONS.get(form.id());var who=mc.world.getPlayerByUuid(caster);
        if(p==null||p.from()<0||f==null||who==null)return Vec3d.ZERO;
        double age=form.age(delta),since=age-moveLag(form.id(),p.moved())-(p.moved()-form.start());
        // (2.96.0) T: from its start (the dash) through its flight on, held as after Z's
        var r=RUSHES.get(form.id());
        if(r!=null&&age-rushLag(form.id())>=r.start()-form.start()&&p.moved()==r.start()+IsosangRules.LUNGE_HOP)since=Math.max(0,since);
        float w=1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth((float)since,IsosangRules.Z_TRAVEL+SHIFT_HOLD,IsosangRules.Z_TRAVEL+SHIFT_HOLD+SHIFT_OUT);
        if(since<0||w<=0)return Vec3d.ZERO;
        Vec3d to=spot(form.id(),f,age),at=who.getLerpedPos(delta);
        return new Vec3d((to.x-at.x)*w,0,(to.z-at.z)*w);
    }
    /**
     * (2.101.1) Where H again {@code fin} has her body {@code t} seconds in, level, on the ground where she stands: round her
     * lap of the sky (IsosangRules.finaleLap, the band's way and its right), else where she stands.
     */
    public static Vec3d finaleOver(StandClient.Cast fin,double t){
        var r=FINALES.get(fin.id());if(r==null)return fin.origin();
        double[] lap=IsosangRules.finaleLap(t);Vec3d f=Vec3d.fromPolar(0,fin.yaw()),right=new Vec3d(-f.z,0,f.x);
        return r.spot().add(f.multiply(lap[0])).add(right.multiply(lap[1]));
    }
    /** How long after Z's flight the body is still drawn where the formation has it (ticks), and how long it eases out over. */
    static final float SHIFT_HOLD=12,SHIFT_OUT=6;
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
    /** Where sword {@code k} of the formation of swing {@code id} strikes its ground (level, world; 2.93.0, as the server's Formation.place). */
    static Vec3d place(UUID id,Formation f,int k){
        double[] w=PLACES.computeIfAbsent(id,i->IsosangRules.formationSwords(IsosangRules.formationSeed(i)))[k];
        Vec3d fw=Vec3d.fromPolar(0,f.yaw()),r=new Vec3d(-fw.z,0,fw.x);
        return f.centre().add(fw.multiply(w[0])).add(r.multiply(w[1]));
    }
    /** The sword the user of the formation of swing {@code id} stands on (2.93.0: Z moves them; the first one until then). */
    public static int perch(UUID id){var p=PERCHES.get(id);return p==null?IsosangRules.standSword():p.on();}
    /** Their flight in the formation of swing {@code id}, {@code age} ticks into it: how far through it (0 to 1), or -1 when not flying (2.93.0). */
    static double flying(UUID id,double age){
        var p=PERCHES.get(id);var c=SWINGS.get(id);if(p==null||p.from()<0||c==null||!Double.isNaN(rushAge(id,age)))return -1;
        double since=age-moveLag(id,p.moved())-(p.moved()-c.start());return since<0||since>=IsosangRules.Z_TRAVEL?-1:since/IsosangRules.Z_TRAVEL;
    }
    /**
     * Where the formation of swing {@code id} holds its user {@code age} ticks into it, level on its ground (2.93.0, as the
     * server's Formation.spotAt): under the sword they stand on, on the way between two in Z's flight.
     */
    public static Vec3d spot(UUID id,Formation f,double age){
        // (2.96.0) T: along its dash from the sword she stood on to where she lands, there, and on over Z's flight
        double a=rushAge(id,age);
        if(!Double.isNaN(a)){
            var run=RUSHES.get(id).run();Vec3d s0=place(id,f,run.from()),s1=place(id,f,run.to());
            double[] xz=IsosangRules.lungeSpot(a,s0.x,s0.z,run.past().x,run.past().z,s1.x,s1.z);
            return new Vec3d(xz[0],f.centre().y,xz[1]);
        }
        var p=PERCHES.get(id);var c=SWINGS.get(id);
        if(p==null||p.from()<0||c==null)return p==null||p.on()==IsosangRules.standSword()?spot(f):place(id,f,p.on());
        double since=age-moveLag(id,p.moved())-(p.moved()-c.start());
        if(since>=IsosangRules.Z_TRAVEL)return place(id,f,p.on());
        return place(id,f,p.from()).lerp(place(id,f,p.on()),IsosangRules.zAlong(Math.max(0,since)/IsosangRules.Z_TRAVEL));
    }
    /** How high the pommel of sword {@code k} of the formation of swing {@code id} stands over its ground (blocks; SwordFormation's layout). */
    static double pommel(UUID id,int k){var l=SwordFormation.layout(id);return k>=0&&k<l.length?l[k].height():IsosangRules.FORMATION_STAND;}
    /**
     * How high the formation of swing {@code id} has its user over its ground {@code t} seconds into it (blocks, 2.93.0):
     * IsosangRules.raised, standing on the pommel of the sword they stand on (each sword's own height), in Z's flight from
     * one pommel to the other over a low arc, and dropping from the last.
     */
    public static double raised(UUID id,double t){
        double base=IsosangRules.raised(t);if(t<IsosangRules.H_PEAK||t>=IsosangRules.H_DOWN)return base;
        // (2.96.0) T: down along the dash from the pommel she stood on, on the ground, up over Z's arc onto the far pommel
        double ra=rushAge(id,t*20);
        if(!Double.isNaN(ra)){var run=RUSHES.get(id).run();return base/IsosangRules.FORMATION_STAND*IsosangRules.lungeHeight(ra,pommel(id,run.from()),pommel(id,run.to()));}
        // (2.97.0) Y: up off the pommel she stands on and down on it again (IsosangRules.cageLeap)
        double leap=leapHeight(id,t*20);
        var p=PERCHES.get(id);if(p==null)return base+leap;
        double h=pommel(id,p.on()),u=flying(id,t*20);
        if(u>=0){double a=IsosangRules.zAlong(u);h=pommel(id,p.from())*(1-a)+h*a+IsosangRules.zArc(u);}
        return base/IsosangRules.FORMATION_STAND*h+leap;
    }
    /**
     * How high Y's leap has the user of the formation of swing {@code id} over the pommel they stand on {@code age} ticks
     * into the formation (2.97.0, IsosangRules.cageLeap): on Y's own clock (its swing heard as it began; the formation's
     * long before, its client's ticks drifting from the server's on a slow server, as T's rushLag), 0 when none runs or it
     * was called off.
     */
    static double leapHeight(UUID id,double age){
        var l=LEAPS.get(id);var f=SWINGS.get(id);if(l==null||f==null||STOPPED.contains(l.swing()))return 0;
        var y=SWINGS.get(l.swing());double lag=y==null?0:(f.age(0)-y.age(0))-(l.start()-f.start());
        double a=age-lag-(l.start()-f.start());
        return a>0&&a<IsosangRules.CAGE_LEAP_LAND?IsosangRules.cageLeap(a):0;
    }
    /** How high H's plan draws its user {@code t} seconds into the formation of swing {@code id} (IsosangRules.planned, on the pommel as raised has it from the landing). */
    static double planned(UUID id,double t){return t<IsosangRules.H_LANDED?IsosangRules.planned(t):raised(id,t);}
    /** (2.93.0) The flights of this swing's formation so far (Z: from which sword to which, when), oldest first (the multiplayer check reads them). */
    public static List<Flight> flights(UUID swing){return FLIGHTS.getOrDefault(swing,List.of());}
    /** This player's Z (its window, its moves, when it is ready again: the server's; 2.93.0). */
    public static IsosangRules.ZState zState(UUID id){return Z_STATES.getOrDefault(id,IsosangRules.ZState.FRESH);}
    /** The tick this player's Z is ready again (0 if it is ready: the server's word, 2.93.0). */
    public static long zReady(UUID id){return zState(id).ready();}
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
    public static void forget(UUID id){READY.remove(id);E_READY.remove(id);CHARGED.remove(id);R_READY.remove(id);H_READY.remove(id);G_READY.remove(id);Z_STATES.remove(id);V_READY.remove(id);Y_READY.remove(id);}
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
    /**
     * How far right of the local player their camera stands now (blocks, 2.93.0): through their own formation, from as Z
     * may first be used until its swords flare (or it ends early), over their right shoulder (SHOULDER, the shift lock's
     * way), so the middle of the screen, which picks the sword Z flies them to (SwordStep.aimed: "화면 가운데를 따라 바뀜"),
     * is not on their own body and the sword is seen beside it (the user's video). Eased in and out on the formation's clock.
     */
    public static double cameraShoulder(float delta){
        var mc=MinecraftClient.getInstance();if(mc.player==null||!MapClient.enabled())return 0;
        double s=0;
        for(var c:SWINGS.values()){
            if(c.combo()!=IsosangRules.FORMATION||!c.caster().equals(mc.player.getUuid()))continue;
            float t=c.age(delta)/20f,end=formationEnd(c.id()),off=Float.isNaN(end)?IsosangRules.H_END:Math.min(end,IsosangRules.H_END);
            s=Math.max(s,SHOULDER*dev.sangwoo.celestialfall.client.kageno.Ease.smooth(t,SHOULDER_IN,IsosangRules.Z_FROM)*(1-dev.sangwoo.celestialfall.client.kageno.Ease.smooth(t,off,off+SHOULDER_OUT)));
        }
        return s;
    }
    /** How far right the formation's camera stands (blocks), and when it eases in (seconds into H, to Z_FROM) and how long it eases out. */
    static final double SHOULDER=1.0;static final float SHOULDER_IN=1.0f,SHOULDER_OUT=.4f;
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
        // (2.83.0) G: the ultimate (G is always this for Lee So-sang's player on the map, as it is Atomic's for Kageno's and
        // the beatdown's for Star Platinum's)
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_G){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.G);ClientPlayNetworking.send(IsosangAttack.SKILL,b);
            }
            return true;
        }
        // (2.93.0) Z: onto the sword of their formation they look at (the camera's middle, as R held's foe: SwordStep.aimed),
        // the sword's place sent with it; nothing if they look at none
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Z){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                int k=SwordStep.aimedNow(mc);
                if(k>=0){var b=PacketByteBufs.create();b.writeByte(IsosangAttack.Z);b.writeByte(k);ClientPlayNetworking.send(IsosangAttack.SKILL,b);}
            }
            return true;
        }
        // (2.96.0) T: on their formation's sword, through the foe the crosshair picks (whom and where sent with it, as the
        // basic attack's press). T is always this for Lee So-sang's player on the map, as it is the Star Finger's for Star
        // Platinum's: the chat opens with Enter
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_T){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.T);var aim=SwordVolley.aim(mc,true);
                if(aim!=null){b.writeVarInt(aim.target());b.writeDouble(aim.point().x);b.writeDouble(aim.point().y);b.writeDouble(aim.point().z);}
                ClientPlayNetworking.send(IsosangAttack.SKILL,b);
            }
            return true;
        }
        // (2.97.0) Y: on their formation's sword, the cage of swords on the foe the crosshair picks (whom and where sent with
        // it, as T's). Y is always this for Lee So-sang's player on the map, as it is the inhale's for Star Platinum's
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_Y){
            if(action==org.lwjgl.glfw.GLFW.GLFW_PRESS&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(IsosangAttack.SKILL)){
                BotandCamera.prepareInteraction();
                var b=PacketByteBufs.create();b.writeByte(IsosangAttack.Y);var aim=SwordVolley.aim(mc,IsosangRules.CAGE);
                if(aim!=null){b.writeVarInt(aim.target());b.writeDouble(aim.point().x);b.writeDouble(aim.point().y);b.writeDouble(aim.point().z);}
                ClientPlayNetworking.send(IsosangAttack.SKILL,b);
            }
            return true;
        }
        if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER&&action==org.lwjgl.glfw.GLFW.GLFW_PRESS){mc.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));return true;}
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
            // (2.101.0) H again: held where she stood, up on the pommel until her drawn body comes down past it, then down
            // with it onto the ground (IsosangRules.finaleReal: every tick to where it has her at the tick's end)
            if(c.combo()==IsosangRules.FINALE&&c.caster().equals(p.getUuid())&&!STOPPED.contains(c.id())){
                var r=FINALES.get(c.id());double next=(Math.floor(c.age(0))+1)/20.0;
                if(r==null||next>=IsosangRules.FINALE_LAND+.15)continue;
                Vec3d v=r.spot().subtract(p.getPos()).multiply(1,0,1);
                double vy=r.spot().y+IsosangRules.finaleReal(next,finalePommel(c.id()))-p.getY();
                if(Math.abs(vy)<1e-6)vy=0;
                p.setVelocity(Math.abs(v.x)<1e-6?0:v.x,vy,Math.abs(v.z)<1e-6?0:v.z);
                continue;
            }
            // (2.82.0) H: where they stood (2.82.4: on the formation's outer ring, its middle ahead), up onto the pommel of
            // the sword struck there as they leap, held there and down again (spot and IsosangRules.raised: every tick to
            // where they have them at the tick's end; the leap over it and the dive onto the pommel are drawn, lift). Let go
            // of at once if it ended early: they fall as anyone would.
            if(c.combo()==IsosangRules.FORMATION&&c.caster().equals(p.getUuid())&&!STOPPED.contains(c.id())){
                var f=FORMATIONS.get(c.id());double next=(Math.floor(c.age(0))+1)/20.0;
                if(f==null||next>=IsosangRules.H_DOWN+.1)continue;
                // (2.93.0) Z: under the sword they stand on now, flying between two
                Vec3d spot=spot(c.id(),f,next*20),v=spot.subtract(p.getPos()).multiply(1,0,1);
                double vy=next<IsosangRules.H_LEAP?Math.min(0,p.getVelocity().y):spot.y+raised(c.id(),next)-p.getY();
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
    /**
     * The attack key: asks the server for a hit (at most every other tick). Returns whether the sword takes the key (nothing
     * else happens). (2.95.0) On their formation's sword it is H's basic attack, 회풍만검: the press carries whom the
     * crosshair looks at and where it meets (SwordVolley.aim), for its swords to come down on; every other press is empty.
     */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();if(!attacks(mc.player))return false;
        if(CombatClient.combatInput()&&ticks-lastAsk>=2&&ClientPlayNetworking.canSend(IsosangAttack.INPUT)){
            BotandCamera.prepareInteraction();
            var b=PacketByteBufs.create();var aim=SwordVolley.aim(mc);
            if(aim!=null){b.writeVarInt(aim.target());b.writeDouble(aim.point().x);b.writeDouble(aim.point().y);b.writeDouble(aim.point().z);}
            ClientPlayNetworking.send(IsosangAttack.INPUT,b);lastAsk=ticks;
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
        PERCHES.keySet().retainAll(SWINGS.keySet());FLIGHTS.keySet().retainAll(SWINGS.keySet());PLACES.keySet().retainAll(SWINGS.keySet());
        ULTIMATES.keySet().retainAll(SWINGS.keySet());ULTIMATE_ENDS.keySet().retainAll(SWINGS.keySet());
        VOLLEYS.keySet().retainAll(SWINGS.keySet());VOLLEY_ENDS.keySet().retainAll(SWINGS.keySet());
        LUNGES.keySet().retainAll(SWINGS.keySet());RUSHES.keySet().retainAll(SWINGS.keySet());LUNGE_ENDS.keySet().retainAll(SWINGS.keySet());CHAIN_FROM.keySet().retainAll(SWINGS.keySet());
        CAGES.keySet().retainAll(SWINGS.keySet());LEAPS.keySet().retainAll(SWINGS.keySet());CAGE_ENDS.keySet().retainAll(SWINGS.keySet());
        FINALES.keySet().retainAll(SWINGS.keySet());FINALE_ENDS.keySet().retainAll(SWINGS.keySet());TAKEN.values().retainAll(SWINGS.keySet());
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
        // (the user's 2.87.0 preview) G plays to its end whatever becomes of her (the server calls it off)
        if(c.combo()==IsosangRules.ULTIMATE)return false;
        // (2.101.11) H again past its end: its swords stand on and go in their own time (FINALE_GONE0 to FINALE_GONE1, now
        // after its end) whatever becomes of her; the server's finale is over, nothing calls it off now
        if(c.combo()==IsosangRules.FINALE&&c.age(0)>=IsosangRules.FINALE_LENGTH)return false;
        var p=mc.world.getPlayerByUuid(c.caster());if(p==null)return false;
        if(c.combo()==IsosangRules.FORMATION)return !StandManager.isosang(p)||!p.isAlive()&&!FORMATION_ENDS.containsKey(c.id())&&p.deathTime>20;
        // (2.95.0) H's basic attack: its swords fade as the server calls it off (its user dead, VOLLEY_ENDS), or a little
        // after they died with no word of it
        if(c.combo()==IsosangRules.VOLLEY)return !StandManager.isosang(p)||!p.isAlive()&&!VOLLEY_ENDS.containsKey(c.id())&&p.deathTime>4;
        // (2.96.0) T the same
        if(c.combo()==IsosangRules.LUNGE)return !StandManager.isosang(p)||!p.isAlive()&&!LUNGE_ENDS.containsKey(c.id())&&p.deathTime>4;
        // (2.97.0) and Y
        if(c.combo()==IsosangRules.CAGE)return !StandManager.isosang(p)||!p.isAlive()&&!CAGE_ENDS.containsKey(c.id())&&p.deathTime>4;
        // (2.101.0) and H again
        if(c.combo()==IsosangRules.FINALE)return !StandManager.isosang(p)||!p.isAlive()&&!FINALE_ENDS.containsKey(c.id())&&p.deathTime>4;
        return !p.isAlive()||!StandManager.isosang(p);
    }
    public static void clear(MinecraftClient mc){
        for(var v:VOICES.values())mc.getSoundManager().stop(v);
        SWINGS.clear();STRUCK.clear();STOPPED.clear();READY.clear();VOICES.clear();EMPOWERED.clear();CHARGED.clear();E_READY.clear();SWORDS.clear();SWEEPS.clear();R_READY.clear();
        FORMATIONS.clear();FORMATION_ENDS.clear();H_READY.clear();ENDED.clear();FORMATION_STRUCK.clear();ULTIMATES.clear();ULTIMATE_ENDS.clear();G_READY.clear();world=null;heldR=false;
        PERCHES.clear();FLIGHTS.clear();Z_STATES.clear();PLACES.clear();
        VOLLEYS.clear();VOLLEY_ENDS.clear();V_READY.clear();SwordVolley.clear();
        LUNGES.clear();RUSHES.clear();LUNGE_ENDS.clear();T_READY.clear();CHAIN_FROM.clear();letGo();
        CAGES.clear();LEAPS.clear();CAGE_ENDS.clear();Y_READY.clear();SwordCage.clear();cageLetGo();
        FINALES.clear();TAKEN.clear();FINALE_ENDS.clear();SwordFinale.clear();
    }

    // ───────────────────────────── sounds ─────────────────────────────

    private static float volume(MinecraftClient mc,Vec3d from){
        if(mc.player==null)return 0;double far=from.distanceTo(mc.player.getPos());
        return (float)(CelestialFall.CONFIG.effectVolume*carried(far));
    }
    /** How loud a sound is (0 to 1) {@code beyond} blocks out of where it is as loud as at its source: fading out to CARRY blocks. */
    static double carried(double beyond){return Math.max(0,1-beyond/CARRY);}
    /** The hit's sound, its whooshes on its blows: loud for its user and those near, fading out to CARRY blocks. */
    private static void voice(MinecraftClient mc,UUID id,UUID caster,int combo){
        var user=mc.world.getPlayerByUuid(caster);if(user==null||!IsosangRules.drawn(combo)||mc.player==null)return;
        if(combo==IsosangRules.FORMATION){formationVoice(mc,id,caster);return;}
        if(combo==IsosangRules.ULTIMATE){ultimateVoice(mc,id,caster);return;}
        if(combo==IsosangRules.FINALE){finaleVoice(mc,id,caster);return;}
        // (2.81.1) R's sword may stand out some 24 blocks toward the foe in the crosshair: heard as from the nearer of its
        // user and the sword's pommel, so the foe it sweeps past hears it as loud as its user's neighbours do; (2.95.0) so
        // may H's basic attack's swords come down some 30 blocks off: heard from the nearer of its user and where they do
        // (2.97.0) Y's swords come down round the foe some 15 blocks off: heard from the nearer of its user and its ring
        Vec3d from=heardFrom(mc.player.getPos(),user.getPos(),combo==IsosangRules.SWEEP?sweepPivot(id):combo==IsosangRules.VOLLEY||combo==IsosangRules.LUNGE?volleyPoint(id)
            :combo==IsosangRules.CAGE?cageAt(id):null);
        float v=(user==mc.player?1:volume(mc,from))*VOLUME;if(v<=.01f)return;
        // (2.81.0) R's own: its recording, from the user's video of it; (2.95.0) H's basic attack's, from the user's video of it
        // (2.96.0) T's, from the user's video of it
        // (2.97.0) Y's, from the user's second video of it
        String name=combo==IsosangRules.SWEEP?"isosang_r":combo==IsosangRules.VOLLEY?"isosang_volley":combo==IsosangRules.LUNGE?"isosang_t":combo==IsosangRules.CAGE?"isosang_y":"isosang_m1_"+combo;
        var s=new Sound(CelestialFall.id(name),v);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /**
     * H's sound (2.82.18, user request with their video of H: "처음부터 끝날때까지 소리가 나야함 영상처럼"): its
     * recording from H's start right through to the formation's end (40.32 s; until 2.82.17 only its first 4.2 s), the
     * end's sound going on from where it stops (ending). Loud for its user; for everyone else heard from the nearer of its
     * user and the formation's middle, fading out to CARRY blocks, measured again every tick (over its 40 s a listener
     * may walk in or away), so it plays even when it starts too far off to be heard.
     */
    private static void formationVoice(MinecraftClient mc,UUID id,UUID caster){
        java.util.function.DoubleSupplier loud=caster.equals(mc.player.getUuid())?()->VOLUME:()->formationLoudness(MinecraftClient.getInstance(),id,caster);
        var s=new Sound(CelestialFall.id("isosang_h"),(float)loud.getAsDouble(),loud);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /**
     * G's sound (2.83.0, user request: "효과음과 대사는 내가 영상에 보냈고 저거 2개중에 랜덤으로 하나 나오게 하면 돼"): the
     * recording of the user's video the server chose (its variant), from the screen going dark to its end, as it is. Loud
     * for its user. (2.92.0, user request: "그대신 시전하는 소리는 처음부터 끝까지 들리게 하고 ... 그래야 다른 플레이어들이
     * 소리 듣고 조금이라도 도망 갈 수 있을 거 같아") As loud for everyone in its blast's ground (GArenaClient.ground: the
     * arena it was cast in, or 62 × 47 blocks round where its sword strikes), from its start to its end, and fading out to
     * CARRY blocks beyond the ground's edge ({@link #carried}; before 2.92.0, from the nearer of its user and where its
     * sword strikes, so the ground's far corners barely heard it), measured again every tick: one running out of it hears
     * it fade as they go.
     */
    private static void ultimateVoice(MinecraftClient mc,UUID id,UUID caster){
        var u=ULTIMATES.get(id);if(u==null)return;
        java.util.function.DoubleSupplier loud=caster.equals(mc.player.getUuid())?()->VOLUME:()->ultimateLoudness(MinecraftClient.getInstance(),id,u.strike());
        var s=new Sound(CelestialFall.id("isosang_g_"+(u.variant()+1)),(float)loud.getAsDouble(),loud);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /**
     * How loud another's G {@code id}, its sword striking at {@code strike}, sounds for this client's player now (2.92.0,
     * ultimateVoice): as loud as for its user in its blast's ground, fading out to CARRY blocks beyond its edge.
     */
    public static double ultimateLoudness(MinecraftClient mc,UUID id,Vec3d strike){
        if(mc.player==null||mc.world==null)return 0;
        return CelestialFall.CONFIG.effectVolume*carried(GArenaClient.ground(id,strike).outside(mc.player.getPos()))*VOLUME;
    }
    /**
     * H again's sound (2.101.0, user request: "음성은 저렇게 3개고 랜덤으로 하나 나오면 돼"): the recording of the user's video the
     * server chose (its variant), from its flash on, as it is. Loud for its user; for everyone else heard from the nearer
     * of its user and the middle of the band its swords come down in, fading out to CARRY blocks, measured again every tick.
     */
    private static void finaleVoice(MinecraftClient mc,UUID id,UUID caster){
        var r=FINALES.get(id);if(r==null)return;
        java.util.function.DoubleSupplier loud=caster.equals(mc.player.getUuid())?()->VOLUME:()->finaleLoudness(MinecraftClient.getInstance(),id,caster);
        var s=new Sound(CelestialFall.id("isosang_hh_"+(r.variant()+1)),(float)loud.getAsDouble(),loud);VOICES.put(id,s);mc.getSoundManager().play(s);
    }
    /** How loud H again sounds where this client listens (2.101.0): from the nearer of its user and its band's middle. */
    private static double finaleLoudness(MinecraftClient mc,UUID id,UUID caster){
        if(mc.player==null||mc.world==null)return 0;
        var c=SWINGS.get(id);var user=mc.world.getPlayerByUuid(caster);Vec3d band=c==null?null:SwordFinale.bandMiddle(c);
        Vec3d from=user!=null?heardFrom(mc.player.getPos(),user.getPos(),band):band;
        return from==null?0:volume(mc,from)*VOLUME;
    }
    /**
     * Z's sound (2.93.0, user's choice: the move's own sound from their video of it, as it is): as its user flies off a
     * sword, loud for its user, for everyone else from its user fading out to CARRY blocks.
     */
    private static void stepVoice(MinecraftClient mc,UUID id,UUID caster){
        if(mc.player==null||mc.world==null)return;
        var user=mc.world.getPlayerByUuid(caster);if(user==null)return;
        float v=caster.equals(mc.player.getUuid())?VOLUME:volume(mc,user.getPos())*VOLUME;if(v<=.01f)return;
        mc.getSoundManager().play(new Sound(CelestialFall.id("isosang_z"),v));
    }
    /** How loud H's sounds are where this client listens (2.82.18): from the nearer of its user and the formation's middle. */
    private static double formationLoudness(MinecraftClient mc,UUID id,UUID caster){
        if(mc.player==null||mc.world==null)return 0;
        var f=FORMATIONS.get(id);var user=mc.world.getPlayerByUuid(caster);
        Vec3d from=user!=null?heardFrom(mc.player.getPos(),user.getPos(),f==null?null:f.centre()):f==null?null:f.centre();
        return from==null?0:volume(mc,from)*VOLUME;
    }
    /**
     * The sound of H's formation ending (2.82.0: from the user's video, the swords flaring and going to ink; 2.82.18: the
     * recording going on from where H's own sound stops): loud for its user, for everyone else heard from the nearer of
     * its user and its middle (as H's own sound is, so the two meet at one loudness), fading out to CARRY blocks.
     */
    private static void ending(MinecraftClient mc,StandClient.Cast c){
        if(!FORMATIONS.containsKey(c.id())||mc.player==null)return;
        float v=c.caster().equals(mc.player.getUuid())?VOLUME:(float)formationLoudness(mc,c.id(),c.caster());if(v<=.01f)return;
        mc.getSoundManager().play(new Sound(CelestialFall.id("isosang_h_end"),v));
    }
    /**
     * A sound played like any other until it is told to fade, then quieter tick by tick to nothing (the game sets a
     * playing sound's loudness once a tick, so a fade has no click where a stop would). Heard at the listener, its
     * loudness set by the distance (as it starts, or (2.82.18) every tick by its loud: one that does plays even when it
     * starts too far off to be heard).
     */
    static final class Sound extends AbstractSoundInstance implements TickableSoundInstance {
        private float full;private int fading=-1;private boolean done,begun;
        private final java.util.function.DoubleSupplier loud;
        Sound(Identifier id,float volume){this(id,volume,null);}
        Sound(Identifier id,float volume,java.util.function.DoubleSupplier loud){
            super(id,SoundCategory.PLAYERS,Random.create());
            this.loud=loud;
            this.volume=full=volume;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        void fade(){if(fading<0)fading=0;}
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        @Override public boolean isDone(){return done;}
        @Override public boolean shouldAlwaysPlay(){return loud!=null;}
        @Override public void tick(){
            begun=true;
            if(loud!=null)full=(float)loud.getAsDouble();
            if(fading<0){volume=full;return;}
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
}
