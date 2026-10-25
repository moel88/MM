package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.StarPlatinumAttack;
import dev.sangwoo.celestialfall.stand.StarPlatinumRules;
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
 * <p>
 * R is the heavy punch (2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야"), a fifth punch: R
 * asks the server for it (stand_heavy_v1) and it comes back as a punch like the others. Its sound is the user's two
 * recordings (tools/stand/cut_heavy.py), started HEAVY_LEAD ticks into it so the blow's crack comes as the fist lands:
 * the voice, and the blow on it, in at once as the fist lands if it strikes someone (this client's own judgement a
 * tick before, the server's word after) and faded out if it struck no one, so that striking someone it is the
 * recording with the blow and striking no one the voice alone.
 */
public final class StandAttack {
    /** Ticks into a punch its shout starts: a sound held back a tick starts on a client tick, the grid this clock counts on. */
    public static final int LEAD=1;
    /** The shouts are a little louder than the game's other attack sounds (about -9 LUFS against -11 to -15). */
    static final float VOICE=.8f;
    /** Ticks a shout takes to fade out when the next one comes, or when its punch stops before it lands. */
    static final int FADE=3;
    /** Ticks into the heavy punch its two sounds start: the blow's crack is two ticks into them (tools/stand/cut_heavy.py), on its contact. */
    public static final int HEAVY_LEAD=StarPlatinumRules.CONTACT[StarPlatinumRules.HEAVY]-2;
    /** The heavy punch's recordings are at their own level (the one with the blow peaks 1 dB under full scale), as the rush's. */
    static final float HEAVY_VOLUME=1f;
    /**
     * The heavy punch's sound (2.59.0): its voice, or the blow on it, as loud as the punch strikes someone (in at once,
     * as the crack comes where the fist lands; out over FADE ticks once it is known to have struck no one). Told to fade
     * before it began (the punch stopped short before its sound), it never plays.
     */
    public static final class Heavy extends net.minecraft.client.sound.AbstractSoundInstance implements net.minecraft.client.sound.TickableSoundInstance {
        private final boolean blow;private final float full;float level,target;private int fading=-1;private boolean done,begun;
        Heavy(Identifier id,boolean blow,float volume){
            super(id,SoundCategory.PLAYERS,net.minecraft.util.math.random.Random.create());
            this.blow=blow;full=volume;level=target=blow?0:1;this.volume=full*level;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        /** The blow sounds (it struck someone) or not. */
        void strike(boolean on){target=on?1:0;}
        void fade(){if(fading<0)fading=0;}
        /** How loud it is now (before the game's own volume settings). */
        public float loudness(){return volume;}
        /** Told to fade before it began, it never plays; playing, it fades out (the game stops a playing sound at once on a tick it cannot play). */
        @Override public boolean canPlay(){return !done&&(fading<0||begun);}
        /** The blow starts silent (not known yet whether it strikes): the game skips a sound that starts at no loudness unless it must play. */
        @Override public boolean shouldAlwaysPlay(){return blow;}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            begun=true;
            level=target>level?target:Math.max(target,level-1f/FADE);
            float v=full*level;
            if(fading>=0){fading++;v*=Math.max(0,1-fading/(float)FADE);if(fading>=FADE)done=true;}
            volume=v;
        }
    }
    /**
     * A shout: played like any other sound until it is told to fade, then quieter tick by tick to nothing (the game
     * sets a playing sound's loudness once a tick, so a fade has no click where a stop would).
     */
    public static final class Shout extends net.minecraft.client.sound.AbstractSoundInstance implements net.minecraft.client.sound.TickableSoundInstance {
        private final float full;private int fading=-1;private boolean done;
        Shout(Identifier id,float volume){
            super(id,SoundCategory.PLAYERS,net.minecraft.util.math.random.Random.create());
            this.volume=full=volume;pitch=1;attenuationType=SoundInstance.AttenuationType.NONE;relative=true;x=y=z=0;
        }
        /** Fades it out over FADE ticks (if it is not already fading). */
        void fade(){if(fading<0)fading=0;}
        /** How loud it is now (before the game's own volume settings). */
        float loudness(){return volume;}
        public boolean fading(){return fading>=0;}
        @Override public boolean isDone(){return done;}
        @Override public void tick(){
            if(fading<0)return;
            fading++;volume=full*Math.max(0,1-fading/(float)FADE);
            if(fading>=FADE)done=true;
        }
    }
    /** A punch as heard: the server's age of it when it was sent and this client's tick then, whom it struck (entity ids and where they stood) once it landed. */
    public static final class Seen {
        public final long start;public final StandPunch.Punch punch;final long serverAge,received;
        public int[] struck=new int[0];public Vec3d[] struckAt=new Vec3d[0];public boolean landed;
        /** Whose guard it broke (2.59.0: the heavy punch's, from the server's word as it lands). */
        public int[] broke=new int[0];
        Shout voice;
        /** The heavy punch's two sounds (2.59.0), and whether this client has judged yet whether it strikes. */
        Heavy heavyVoice,heavyBlow;boolean judged;
        Seen(long start,StandPunch.Punch punch,long serverAge,long received){this.start=start;this.punch=punch;this.serverAge=serverAge;this.received=received;}
        /** Seconds on the punch's clock this frame (less than nought for a heavy punch whose start is a few ticks on, 2.59.0). */
        public float age(float delta){return (serverAge+ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        /** Whole ticks on the punch's clock since it started (the frames after this tick show this much and the frame's part of a tick). */
        public long ticksIn(){return Math.max(0,serverAge+ticks-received);}
        /** Its shout, once asked for (null if it has none: the jab's and the cross's, or too far, or heard too late). */
        public SoundInstance voice(){return voice;}
        /** The heavy punch's voice and its blow (2.59.0), once asked for (null for the other punches, or too far, or heard too late). */
        public Heavy heavyVoice(){return heavyVoice;}
        public Heavy heavyBlow(){return heavyBlow;}
    }
    static final class User {final ArrayDeque<Seen> punches=new ArrayDeque<>();long ready,heavyReady;Seen stopped;}
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
            // 2.59.0: when the heavy punch is ready again, and whose guard it broke as it landed
            long heavyReady=b.readLong();int m=b.readVarInt();if(m<0||m>256)return;
            int[] broke=new int[m];for(int i=0;i<m;i++)broke[i]=b.readInt();
            if(!Float.isFinite(yaw)||combo>StarPlatinumRules.HEAVY)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){USERS.clear();world=mc.world;}
                event(mc,user,type,combo,start,serverNow,yaw,aim,ready,ids,at,heavyReady,broke);
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(StandAttack::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(StandAttack::clear));
    }
    static void clear(){
        var mc=MinecraftClient.getInstance();FADING.clear();
        for(var u:USERS.values())for(var s:u.punches){
            if(s.voice!=null&&!s.voice.isDone())mc.getSoundManager().stop(s.voice);
            if(s.heavyVoice!=null&&!s.heavyVoice.isDone())mc.getSoundManager().stop(s.heavyVoice);
            if(s.heavyBlow!=null&&!s.heavyBlow.isDone())mc.getSoundManager().stop(s.heavyBlow);
        }
        USERS.clear();world=null;
    }
    private static void event(MinecraftClient mc,UUID id,int type,int combo,long start,long serverNow,float yaw,float aim,long ready,int[] ids,Vec3d[] at,long heavyReady,int[] broke){
        var user=USERS.computeIfAbsent(id,k->new User());user.ready=ready;user.heavyReady=heavyReady;
        switch(type){
            case StarPlatinumAttack.PUNCH->{
                if(combo<1)return;
                // (the heavy punch's start may be a few ticks on: a punch it stopped short turns back to the guard first)
                var seen=new Seen(start,new StandPunch.Punch(combo,yaw,Float.isFinite(aim)?aim:Float.NaN,Float.NaN),serverNow-start,ticks);
                var before=user.punches.peekLast();
                user.punches.addLast(seen);while(user.punches.size()>3)user.punches.removeFirst();
                if(combo==StarPlatinumRules.HEAVY)heavySound(mc,id,seen,before);else shout(mc,id,seen,before);
            }
            case StarPlatinumAttack.LANDED->{
                var seen=find(user,start);if(seen==null)return;
                seen.landed=true;seen.struck=ids;seen.struckAt=at;seen.broke=broke;
                // the server's word on the heavy punch's blow: it struck someone or no one
                if(seen.heavyBlow!=null){seen.heavyBlow.strike(ids.length>0);seen.judged=true;}
            }
            case StarPlatinumAttack.STOPPED->{
                var seen=find(user,start);if(seen==null||!Float.isNaN(seen.punch.stopped()))return;
                float age=Math.max(0,seen.age(0));
                var stopped=new Seen(seen.start,seen.punch.stop(age),seen.serverAge,seen.received);
                stopped.struck=seen.struck;stopped.struckAt=seen.struckAt;stopped.landed=seen.landed;stopped.voice=seen.voice;
                stopped.broke=seen.broke;stopped.heavyVoice=seen.heavyVoice;stopped.heavyBlow=seen.heavyBlow;stopped.judged=seen.judged;
                replace(user,seen,stopped);
                // a punch stopped before it landed takes its shout with it (the heavy punch its voice and its blow)
                if(!seen.landed&&seen.voice!=null)seen.voice.fade();
                if(!seen.landed&&seen.heavyVoice!=null){seen.heavyVoice.fade();seen.heavyBlow.fade();}
            }
            default->{}
        }
    }
    private static Seen find(User u,long start){for(var s:u.punches)if(s.start==start)return s;return null;}
    private static void replace(User u,Seen old,Seen now){var list=new ArrayList<>(u.punches);list.set(list.indexOf(old),now);u.punches.clear();u.punches.addAll(list);}
    /**
     * The punch's shout, LEAD ticks into it (the hook's and the finisher's; 2.56.0: the jab and the cross have none):
     * loud for its user and those near, fading out to 40 blocks. The one its user shouted before fades out as it starts.
     */
    private static void shout(MinecraftClient mc,UUID id,Seen seen,Seen before){
        if(!StarPlatinumRules.shouts(seen.punch.combo())||mc.player==null||seen.serverAge>LEAD)return;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        float volume=(float)(CelestialFall.CONFIG.effectVolume*VOICE*Math.max(0,1-far/40));if(volume<=.01f)return;
        var sound=new Shout(CelestialFall.id("star_platinum_ora_"+seen.punch.combo()),volume);
        seen.voice=sound;
        int delay=(int)(LEAD-seen.serverAge);
        if(delay>0)mc.getSoundManager().play(sound,delay);else mc.getSoundManager().play(sound);
        if(before!=null&&before.voice!=null)FADING.add(new Object[]{before.voice,ticks+Math.max(0,delay)});
    }
    /**
     * The heavy punch's two sounds, HEAVY_LEAD ticks into it (loud for its user and those near, fading out to 40
     * blocks; none if it was heard too late): the voice, and the blow on it, silent until this client judges, a tick
     * before the fist lands, whether it strikes someone. The shout its user shouted before fades out as they start.
     */
    private static void heavySound(MinecraftClient mc,UUID id,Seen seen,Seen before){
        if(mc.player==null||seen.serverAge>HEAVY_LEAD)return;
        var user=mc.world.getPlayerByUuid(id);
        double far=user==null||user==mc.player?0:user.getPos().distanceTo(mc.player.getPos());
        float volume=(float)(CelestialFall.CONFIG.effectVolume*HEAVY_VOLUME*Math.max(0,1-far/40));if(volume<=.01f)return;
        seen.heavyVoice=new Heavy(CelestialFall.id("star_platinum_heavy"),false,volume);
        seen.heavyBlow=new Heavy(CelestialFall.id("star_platinum_heavy_hit"),true,volume);
        int delay=(int)(HEAVY_LEAD-seen.serverAge);
        if(delay>0){mc.getSoundManager().play(seen.heavyVoice,delay);mc.getSoundManager().play(seen.heavyBlow,delay);}
        else{mc.getSoundManager().play(seen.heavyVoice);mc.getSoundManager().play(seen.heavyBlow);}
        if(before!=null&&before.voice!=null)FADING.add(new Object[]{before.voice,ticks+Math.max(0,delay)});
    }
    /**
     * Whether whoever is in this punch's way now would be struck (as the server judges it as the punch lands,
     * StarPlatinumRules.lane, in sight): what the heavy punch's blow sounds by until the server's word comes.
     */
    static boolean anyoneInWay(MinecraftClient mc,UUID id,StandPunch.Punch p){
        var user=mc.world.getPlayerByUuid(id);if(user==null)return false;
        Vec3d at=user.getPos(),f=Vec3d.fromPolar(0,p.yaw()),r=new Vec3d(-f.z,0,f.x);
        return !mc.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,new net.minecraft.util.math.Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->{
            if(e==user||!e.isAlive()||e.isSpectator()||!e.canHit()||e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)return false;
            Vec3d d=e.getPos().subtract(at);
            return StarPlatinumRules.lane(p.combo(),d.dotProduct(f),d.dotProduct(r),d.y,e.getWidth()*.5)&&user.canSee(e);
        }).isEmpty();
    }
    /** Shouts to fade out once the next one starts: {the shout, the tick it fades from}. */
    private static final java.util.List<Object[]> FADING=new java.util.ArrayList<>();
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!USERS.isEmpty()||world!=null)clear();return;}
        if(world!=mc.world){clear();world=mc.world;}
        if(mc.isPaused())return;
        ticks++;
        // the shout before fades as the next one starts (the next starts this tick or later, held back to its punch's tick)
        FADING.removeIf(f->{if(ticks<(long)f[1])return false;((Shout)f[0]).fade();return true;});
        // the heavy punch's blow, a tick before the fist lands: this client's own judgement of whether it strikes
        for(var e:USERS.entrySet())for(var s:e.getValue().punches)
            if(s.heavyBlow!=null&&!s.judged&&Float.isNaN(s.punch.stopped())&&s.ticksIn()>=StarPlatinumRules.CONTACT[StarPlatinumRules.HEAVY]-1){
                s.heavyBlow.strike(anyoneInWay(mc,e.getKey(),s.punch));s.judged=true;
            }
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
    /** This player's newest punch heard (null if none shows). */
    public static Seen newest(UUID id){var u=USERS.get(id);return u==null?null:u.punches.peekLast();}
    /** The world tick this player's attack is ready again after its rest (0 if it has not rested). */
    public static long ready(UUID id){var u=USERS.get(id);return u==null?0:u.ready;}
    /** The world tick this player's heavy punch is ready again (2.59.0; 0 if they have not thrown one). */
    public static long heavyReady(UUID id){var u=USERS.get(id);return u==null?0:u.heavyReady;}
    private static boolean heldR;
    /**
     * R for Star Platinum's user on the map (2.59.0): asks for the heavy punch if the stand is out. R is the map's use
     * key there (MapControls), and it is always the heavy punch's for Star Platinum's user, as it is a skill's for
     * Kageno's. Returns whether it took the key.
     */
    public static boolean heavyKey(long window,int key,int action,int modifiers){
        var mc=MinecraftClient.getInstance();if(key!=org.lwjgl.glfw.GLFW.GLFW_KEY_R||window!=mc.getWindow().getHandle())return false;
        if(action==org.lwjgl.glfw.GLFW.GLFW_RELEASE){if(!heldR)return false;heldR=false;return true;}
        if(heldR)return true;
        if(action!=org.lwjgl.glfw.GLFW.GLFW_PRESS||mc.currentScreen!=null||!MapClient.enabled()||!dev.sangwoo.celestialfall.stand.StandManager.starPlatinum(mc.player))return false;
        heldR=true;
        if(mine(mc)&&CombatClient.combatInput()&&ClientPlayNetworking.canSend(StarPlatinumAttack.HEAVY_INPUT)){
            BotandCamera.prepareInteraction();
            // the way it goes is the way the player faces now: the turn goes to the server before the press
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
            ClientPlayNetworking.send(StarPlatinumAttack.HEAVY_INPUT,PacketByteBufs.empty());
        }
        return true;
    }
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
