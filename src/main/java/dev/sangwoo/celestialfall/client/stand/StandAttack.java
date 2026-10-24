package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Star Platinum's punches as this client sees them (2.54.0). The attack key of a user whose stand is out asks the server
 * for a punch (held, it keeps asking); the server says when each punch of every user nearby starts, whom it struck and
 * when the attack rests or stops (StarPlatinumAttack). Each punch runs on its own clock from when the client heard of
 * it, the way Kageno's casts do (KagenoCastClock): the server's age when it was sent, plus this client's ticks since,
 * plus the frame's part of a tick. Its shout from the user's recording starts LEAD ticks into it, so that its "RA"
 * lands on the punch's contact (tools/stand/cut_ora.py). StandPresence draws the stand through the punches
 * (StandPunch) and StandPunchFx the light of them.
 */
public final class StandAttack {
    /** Ticks into a punch its shout starts: a sound held back a tick starts on a client tick, the grid this clock counts on. */
    public static final int LEAD=1;
    /** The shouts are a little louder than the game's other attack sounds (about -9 LUFS against -11 to -15). */
    static final float VOICE=.8f;
    /** A punch as heard: the server's age of it when it was sent and this client's tick then, whom it struck (entity ids and where they stood) once it landed. */
    public static final class Seen {
        public final long start;public final StandPunch.Punch punch;final long serverAge,received;
        public int[] struck=new int[0];public Vec3d[] struckAt=new Vec3d[0];public boolean landed;
        SoundInstance voice;
        Seen(long start,StandPunch.Punch punch,long serverAge,long received){this.start=start;this.punch=punch;this.serverAge=serverAge;this.received=received;}
        /** Seconds on the punch's clock this frame. */
        public float age(float delta){return (ticksIn()+Math.max(0,Math.min(1,delta)))/20f;}
        /** Whole ticks on the punch's clock since it started (the frames after this tick show this much and the frame's part of a tick). */
        public long ticksIn(){return Math.max(0,serverAge+ticks-received);}
        /** Its shout, once asked for (null if it has none here: too far, or heard too late). */
        public SoundInstance voice(){return voice;}
    }
    static final class User {final ArrayDeque<Seen> punches=new ArrayDeque<>();long ready;Seen stopped;}
    private static final Map<UUID,User> USERS=new HashMap<>();
    /** This client's ticks while the game runs (counted as each tick begins). */
    static long ticks;
    private static Object world;
    private static long lastAsk=-100;
    private StandAttack(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(StarPlatinumAttack.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dimension=b.readIdentifier();int type=b.readUnsignedByte(),combo=b.readUnsignedByte();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat(),aim=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>256)return;
            int[] ids=new int[n];Vec3d[] at=new Vec3d[n];
            for(int i=0;i<n;i++){ids[i]=b.readInt();at[i]=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
            if(!Float.isFinite(yaw)||combo>4)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){USERS.clear();world=mc.world;}
                event(mc,user,type,combo,start,serverNow,yaw,aim,ready,ids,at);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandAttack::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandAttack::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();
        for(var u:USERS.values())for(var s:u.punches)if(s.voice!=null)mc.getSoundManager().stop(s.voice);
        USERS.clear();world=null;
    }
    private static void event(MinecraftClient mc,UUID id,int type,int combo,long start,long serverNow,float yaw,float aim,long ready,int[] ids,Vec3d[] at){
        var user=USERS.computeIfAbsent(id,k->new User());user.ready=ready;
        switch(type){
            case StarPlatinumAttack.PUNCH->{
                if(combo<1)return;
                var seen=new Seen(start,new StandPunch.Punch(combo,yaw,Float.isFinite(aim)?aim:Float.NaN,Float.NaN),Math.max(0,serverNow-start),ticks);
                user.punches.addLast(seen);while(user.punches.size()>3)user.punches.removeFirst();
                shout(mc,id,seen);
            }
            case StarPlatinumAttack.LANDED->{
                var seen=find(user,start);if(seen==null)return;
                seen.landed=true;seen.struck=ids;seen.struckAt=at;
            }
            case StarPlatinumAttack.STOPPED->{
                var seen=find(user,start);if(seen==null||!Float.isNaN(seen.punch.stopped()))return;
                float age=seen.age(0);
                var stopped=new Seen(seen.start,seen.punch.stop(age),seen.serverAge,seen.received);
                stopped.struck=seen.struck;stopped.struckAt=seen.struckAt;stopped.landed=seen.landed;stopped.voice=seen.voice;
                replace(user,seen,stopped);
                // a punch stopped before it landed takes its shout with it
                if(!seen.landed&&seen.voice!=null)mc.getSoundManager().stop(seen.voice);
            }
            default->{}
        }
    }
    private static Seen find(User u,long start){for(var s:u.punches)if(s.start==start)return s;return null;}
    private static void replace(User u,Seen old,Seen now){var list=new ArrayList<>(u.punches);list.set(list.indexOf(old),now);u.punches.clear();u.punches.addAll(list);}
    /** The punch's shout, LEAD ticks into it: loud for its user and those near, fading out to 40 blocks. */
    private static void shout(MinecraftClient mc,UUID id,Seen seen){
        if(mc.player==null||seen.serverAge>LEAD)return;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        float volume=(float)(CelestialFall.CONFIG.effectVolume*VOICE*Math.max(0,1-far/40));if(volume<=.01f)return;
        var sound=new PositionedSoundInstance(CelestialFall.id("star_platinum_ora_"+seen.punch.combo()),SoundCategory.PLAYERS,volume,1,net.minecraft.util.math.random.Random.create(),false,0,SoundInstance.AttenuationType.NONE,0,0,0,true);
        seen.voice=sound;
        int delay=(int)(LEAD-seen.serverAge);
        if(delay>0)mc.getSoundManager().play(sound,delay);else mc.getSoundManager().play(sound);
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!USERS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        // a punch long over is let go (its user gone from sight, or simply done)
        USERS.entrySet().removeIf(e->{
            e.getValue().punches.removeIf(s->s.age(0)>StandPunch.length(s.punch)+1);
            return e.getValue().punches.isEmpty()&&e.getValue().ready<=mc.world.getTime()&&mc.world.getPlayerByUuid(e.getKey())==null;
        });
        // held, the attack keeps asking: the server keeps a press made a little before the next punch may start
        if(mc.player!=null&&mc.options.attackKey.isPressed()&&ticks-lastAsk>=2&&mine(mc)&&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand()&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand())ask(mc);
    }
    /** Whether the local player's attack is their stand's punches now. */
    static boolean mine(MinecraftClient mc){return mc.player!=null&&StandPresence.out(mc.player);}
    /** The local player's attack is their stand's punches: the attack key breaks no blocks (2.54.0). */
    public static boolean punching(){return mine(MinecraftClient.getInstance());}
    /** The attack key, pressed: Star Platinum punches if it is out (whatever the hand holds). Returns whether it took the press. */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();
        if(!mine(mc))return false;
        ask(mc);
        return true;
    }
    private static void ask(MinecraftClient mc){
        if(!CombatClient.combatInput()||!ClientPlayNetworking.canSend(StarPlatinumAttack.INPUT))return;
        if(lastAsk==ticks)return;
        BotandCamera.prepareInteraction();
        // the way it goes is the way the player faces now: the turn goes to the server before the press
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
        ClientPlayNetworking.send(StarPlatinumAttack.INPUT,PacketByteBufs.empty());
        lastAsk=ticks;
    }
    /** This player's punches heard, oldest first (none if none). */
    public static List<Seen> punches(UUID id){var u=USERS.get(id);return u==null?List.of():List.copyOf(u.punches);}
    /** The world tick this player's attack is ready again after its rest (0 if it has not rested). */
    public static long ready(UUID id){var u=USERS.get(id);return u==null?0:u.ready;}
    /** Everyone whose punches still show, with theirs. */
    public static Map<UUID,List<Seen>> all(){
        var out=new HashMap<UUID,List<Seen>>();
        for(var e:USERS.entrySet())if(!e.getValue().punches.isEmpty())out.put(e.getKey(),List.copyOf(e.getValue().punches));
        return out;
    }
    /**
     * The shape of this player's stand through its punches this frame, {@code back} seconds ago (its afterimages), or
     * null if no punch shows.
     */
    public static StandPunch.Shape shape(UUID id,float delta,float back,StandPunch.Shape into){
        var u=USERS.get(id);if(u==null||u.punches.isEmpty())return null;
        Seen now=u.punches.peekLast(),before=null;
        for(var s:u.punches)if(s!=now)before=s;
        float t=now.age(delta)-back;
        if(t<0){if(before==null)return null;now=before;before=null;t=now.age(delta)-back;for(var s:u.punches)if(s!=now&&s.start<now.start)before=s;}
        if(t>StandPunch.length(now.punch))return null;
        return StandPunch.at(now.punch,t,before==null?null:before.punch,before==null?0:before.age(delta)-back,into);
    }
    /** For the capture tools: the player's last punch, its age (seconds), whether it landed and on how many, and its whole ticks in. */
    public static String describe(PlayerEntity p,float delta){
        var u=USERS.get(p.getUuid());if(u==null||u.punches.isEmpty())return null;var s=u.punches.peekLast();
        return String.format(java.util.Locale.ROOT,"%d,%.3f,%d,%d,%d",s.punch.combo(),s.age(delta),s.landed?1:0,s.struck.length,s.ticksIn());
    }
}
