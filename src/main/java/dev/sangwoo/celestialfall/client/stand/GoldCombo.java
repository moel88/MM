package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.client.BotandCamera;
import dev.sangwoo.celestialfall.client.CombatClient;
import dev.sangwoo.celestialfall.client.MapClient;
import dev.sangwoo.celestialfall.stand.GoldExperienceAttack;
import dev.sangwoo.celestialfall.stand.GoldExperienceLook;
import dev.sangwoo.celestialfall.stand.GoldExperienceRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.stand.GoldExperienceStance.*;

/**
 * Gold Experience's punches as this client sees and hears them (2.154.0, its basic attack: GoldExperienceRules has the
 * user's words). The attack key of a user whose stand is out asks the server for a punch (held, it keeps asking); the
 * server says when each punch of every user nearby starts, whom it struck and whose guard took it, and when the attack
 * rests or stops (GoldExperienceAttack). Each punch runs on its own clock from when the client heard of it (the server's
 * age of it then, this client's ticks since, the frame's part of a tick), as Star Platinum's do (StandAttack).
 * <p>
 * The stand moves through them as Star Platinum does through its own (StandPunch), on this figure's stances and a little
 * slower: it comes round to the front of its user, and from its guard each punch loads (coiled away from the blow),
 * holds the load a moment, strikes faster and faster to land on the server's tick, freezes there a few frames (the
 * hit-stop), holds the blow and goes back to the guard; a new punch takes over from the last over a few frames, so
 * nothing jumps, and the stand dashes on into each blow as far as it takes to meet whoever stands there. 1 a left jab,
 * 2 a right cross, 3 a left hook, 4 a right with the whole body behind it. Faint golden afterimages follow each strike.
 * Once the punches stop it stays a moment in front and goes back to its user's shoulder. Everything is a function of
 * the punches' clocks.
 * <p>
 * Its sounds (made from recordings, tools/stand/make_gold_sounds.py: nothing synthesized, as its T's and H's): each punch
 * whooshes through the air, loudest as it lands (SWING_PEAK into its file); whoever it lands on is struck with a blow
 * of its own, played as the server's word comes (the punch's clock at its contact: the word and the punch are late the
 * same); the fourth carries the overdrive's shout, "mu-DA!", its "DA" on the blow (the user's choice: "주먹 소리 +
 * 4타에 '무-다!'"). As the stands' other sounds they are loud for its user and whom it strikes and fade to 40 blocks.
 */
public final class GoldCombo {
    // ───────────────────────────── the keys ─────────────────────────────

    static final int SMOOTH=0,HOLD=1,STRIKE=2;
    record Key(float at,int stance,int ease){}
    static Key k(float at,int stance,int ease){return new Key(at,stance,ease);}
    /** When each punch lands (seconds after it starts: the server's contact) and how long it then stands still (the hit-stop). */
    public static final float[] HIT=new float[5],FREEZE={0,.05f,.05f,.08f,.13f};
    static{for(int c=1;c<=4;c++)HIT[c]=GoldExperienceRules.M1_CONTACT[c]/20f;}
    /**
     * Each punch's keys on its own clock with the hit-stop taken out (Star Platinum's, StandPunch.KEYS, a tenth longer
     * where the server's blows are): the jab and the cross load in 0.08 s and land at 0.2 s; the hook loads to 0.27 s,
     * holds it and lands at 0.5 s; the fourth coils up and back to 0.29 s, holds it, and lands at 0.55 s, held out long
     * while whom it struck flies.
     */
    static final Key[][] KEYS={null,
        {k(0,GUARD,SMOOTH),k(.08f,JAB_WIND,SMOOTH),k(.1f,JAB_WIND,HOLD),k(HIT[1],JAB,STRIKE),k(.25f,JAB,HOLD),k(.48f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.08f,CROSS_WIND,SMOOTH),k(.1f,CROSS_WIND,HOLD),k(HIT[2],CROSS,STRIKE),k(.25f,CROSS,HOLD),k(.48f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.27f,HOOK_WIND,SMOOTH),k(.37f,HOOK_WIND,HOLD),k(HIT[3],HOOK,STRIKE),k(.55f,HOOK,HOLD),k(.84f,GUARD,SMOOTH)},
        {k(0,GUARD,SMOOTH),k(.29f,FINISH_WIND,SMOOTH),k(.42f,FINISH_WIND,HOLD),k(HIT[4],FINISH,STRIKE),k(.79f,FINISH,HOLD),k(1.1f,GUARD,SMOOTH)}};
    /** The stance each punch loads in, the one it strikes in, and the fist it strikes with (true: the right). */
    static final int[] WINDS={0,JAB_WIND,CROSS_WIND,HOOK_WIND,FINISH_WIND},STRIKES={0,JAB,CROSS,HOOK,FINISH};
    static final boolean[] RIGHT={false,false,true,false,true};
    /** How far the stand dashes on into each punch as it lands (blocks): for its fist to sink BITE into its target, within these (it stays right in front of its user). */
    static final float[] LUNGE_MIN={0,.3f,.35f,.4f,.6f},LUNGE_MAX={0,.85f,.9f,.95f,1.3f};
    /** How far each blow shoves the stand on through its target as it lands (blocks, in over two frames and soon gone). */
    static final float[] DRIVE={0,.12f,.12f,.18f,.3f};
    /**
     * Where it stands to punch: right in front of its user, AHEAD blocks on along the punch, RAISE higher than at its
     * shoulder (its rush's and its overdrive's: its fists land on a body's face and chest).
     */
    static final float AHEAD=.95f,BITE=.15f,RAISE=GoldRush.RAISE;
    /** Seconds: the first punch brings it round to the front (as its rush does); it turns to a new way. */
    static final float RISE=StandRush.RISE,TURN=.12f;
    /** Seconds a new punch takes to take over from the last (Star Platinum's: the jab and the cross snap in, the hook and the fourth take longer). */
    static final float[] BLEND={0,.1f,.12f,.2f,.2f};
    /** Seconds its stances take to take over from the pose it had (beside its user, or another skill's). */
    static final float TAKE=.1f;
    /** It stays in front this long once a punch is over (seconds; less once one is stopped short), then goes back over RETURN. */
    static final float LINGER=.45f,LINGER_STOPPED=.15f,RETURN=.4f;
    /** Seconds a punch stopped short takes to turn back to its guard (StarPlatinumRules.STOP_RETURN). */
    static final float BACK=dev.sangwoo.celestialfall.stand.StarPlatinumRules.STOP_RETURN/20f;
    /** The afterimages of a strike: how far behind each is (seconds) and how strongly it shows. */
    static final float[] GHOST_BACK={.02f,.04f,.065f},GHOST_ALPHA={.34f,.22f,.12f};

    // ───────────────────────────── the sounds ─────────────────────────────

    /** Where in each punch's whoosh its loudest moment is (seconds into its file: tools/stand/make_gold_sounds.py lays it there), so when it is played: that moment as the fist lands. */
    static final float[] SWING_PEAK={0,.07f,.07f,.1f,.12f};
    /** The fourth's shout: its "DA" (GoldPunch.DA_AT into its file) as the fist lands. */
    static final float SHOUT_AT=HIT[4]-GoldPunch.DA_AT;
    static GoldSounds.Cue[] cues(int combo){
        var swing=new GoldSounds.Cue(HIT[combo]-SWING_PEAK[combo],"gold_punch_swing_"+combo,1);
        return combo==4?new GoldSounds.Cue[]{swing,new GoldSounds.Cue(SHOUT_AT,"gold_experience_overdrive",1)}:new GoldSounds.Cue[]{swing};
    }
    /** The blow on whoever a punch lands on (one for each punch, the fourth the heaviest). */
    static String blow(int combo){return "gold_punch_hit_"+combo;}

    // ───────────────────────────── the punches heard ─────────────────────────────

    /** A punch as heard: when it began, the server's age of it when it was sent and this client's tick then, which, its way, how far ahead its target stood. */
    public static final class Punch {
        public final long start;final long serverAge,received;public final int combo;final float yaw,aim;
        /** Its clock (seconds) when it was stopped short (NaN: it was not) and when it landed (NaN: not yet). */
        float stopped=Float.NaN,landedAt=Float.NaN;
        /** Whom it struck and where they stood (their chest), whose guard took it. */
        int[] struck=new int[0],guarded=new int[0];Vec3d[] struckAt=new Vec3d[0],guardedAt=new Vec3d[0];
        final GoldSounds.Run sound;
        /** For the checks: how many blows were heard. */
        int blows;
        /** The server's word that it landed came before this client's clock of it reached its contact: its blow waits for that. */
        boolean blowDue;
        Punch(long start,long serverAge,long received,int combo,float yaw,float aim){
            this.start=start;this.serverAge=serverAge;this.received=received;this.combo=combo;this.yaw=yaw;this.aim=aim;sound=new GoldSounds.Run(cues(combo));
        }
        /** Seconds on its clock this frame. */
        public float age(float delta){return (serverAge+StandRush.ticks-received+Math.max(0,Math.min(1,delta)))/20f;}
        public boolean landed(){return !Float.isNaN(landedAt);}
        public float stopped(){return stopped;}
        public float yaw(){return yaw;}
        public int seed(){return (int)(start*31+combo*7919)&0x7fff;}
    }
    static final class User {final ArrayDeque<Punch> punches=new ArrayDeque<>();long ready;}
    private static final Map<UUID,User> USERS=new HashMap<>();
    private static Object world;
    private static long lastAsk=-100;
    private GoldCombo(){}

    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(GoldExperienceAttack.EVENT,(mc,handler,b,response)->{
            UUID user=b.readUuid();Identifier dimension=b.readIdentifier();int type=b.readUnsignedByte(),combo=b.readUnsignedByte();
            long start=b.readLong(),serverNow=b.readLong();float yaw=b.readFloat(),aim=b.readFloat();long ready=b.readLong();
            int n=b.readVarInt();if(n<0||n>256)return;
            int[] ids=new int[n];Vec3d[] at=new Vec3d[n];
            for(int i=0;i<n;i++){ids[i]=b.readInt();at[i]=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
            int m=b.readVarInt();if(m<0||m>256)return;
            int[] gids=new int[m];Vec3d[] gat=new Vec3d[m];
            for(int i=0;i<m;i++){gids[i]=b.readInt();gat[i]=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());}
            if(!Float.isFinite(yaw)||combo>4)return;
            mc.execute(()->{
                if(!MapClient.enabled()||mc.world==null||!mc.world.getRegistryKey().getValue().equals(dimension))return;
                if(world!=mc.world){clear(mc);world=mc.world;}
                event(mc,user,type,combo,start,serverNow,yaw,aim,ready,ids,at,gids,gat);
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(GoldCombo::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->mc.execute(()->clear(mc)));
    }
    private static void clear(MinecraftClient mc){for(var u:USERS.values())for(var p:u.punches)p.sound.stop(mc);USERS.clear();world=null;}
    private static void event(MinecraftClient mc,UUID id,int type,int combo,long start,long serverNow,float yaw,float aim,long ready,int[] ids,Vec3d[] at,int[] gids,Vec3d[] gat){
        var user=USERS.computeIfAbsent(id,k->new User());user.ready=ready;
        switch(type){
            case GoldExperienceAttack.PUNCH->{
                if(combo<1)return;
                user.punches.addLast(new Punch(start,serverNow-start,StandRush.ticks,combo,yaw,Float.isFinite(aim)?aim:Float.NaN));
                while(user.punches.size()>3)user.punches.removeFirst();
            }
            case GoldExperienceAttack.LANDED->{
                var p=find(user,start);if(p==null)return;
                p.landedAt=(serverNow-start)/20f;p.struck=ids;p.struckAt=at;p.guarded=gids;p.guardedAt=gat;
                // the blow on whom it struck: now, as the fist lands on the punch's clock; but if the word came before this
                // client's clock of the punch is there (its start heard late, the first multiplayer check: the blow 0.1 s
                // before the fist), when it is (tick)
                if(ids.length>0&&!p.sound.wasCut()){if(p.age(0)+1e-4f>=HIT[p.combo])blow(mc,id,p);else p.blowDue=true;}
            }
            case GoldExperienceAttack.STOPPED->{
                var p=find(user,start);if(p==null||!Float.isNaN(p.stopped))return;
                p.stopped=Math.max(0,p.age(0));
                // stopped before it landed: its whoosh (and the fourth's shout) fade out with it
                if(!p.landed())p.sound.cut();
            }
            default->{}
        }
    }
    private static Punch find(User u,long start){for(var p:u.punches)if(p.start==start)return p;return null;}
    /** The blow of punch {@code p} of user {@code id} on whom it struck: loud for its user and them, from where they were. */
    private static void blow(MinecraftClient mc,UUID id,Punch p){
        p.blowDue=false;
        var u=mc.world.getPlayerByUuid(id);boolean ours=mc.player!=null&&mc.player.getUuid().equals(id);
        for(int e:p.struck)if(mc.player!=null&&mc.player.getId()==e)ours=true;
        Vec3d[] from=new Vec3d[p.struckAt.length+1];System.arraycopy(p.struckAt,0,from,0,p.struckAt.length);from[p.struckAt.length]=u==null?null:u.getPos();
        if(GoldSounds.play(mc,blow(p.combo),GoldSounds.volume(mc,ours,from),1)!=null)p.blows++;
    }
    private static void tick(MinecraftClient mc){
        if(mc.world==null||!MapClient.enabled()){if(!USERS.isEmpty()||world!=null)clear(mc);return;}
        if(world!=mc.world){clear(mc);world=mc.world;}
        if(mc.isPaused())return;
        for(var e:USERS.entrySet()){
            var user=mc.world.getPlayerByUuid(e.getKey());
            boolean mine=mc.player!=null&&mc.player.getUuid().equals(e.getKey());
            for(var p:e.getValue().punches){
                float t=p.age(0);if(t>length(p)+1)continue;
                if(p.blowDue&&t+1e-4f>=HIT[p.combo]){if(p.sound.wasCut())p.blowDue=false;else blow(mc,e.getKey(),p);}
                boolean ours=mine;for(int id:p.struck)if(mc.player!=null&&mc.player.getId()==id)ours=true;
                p.sound.tick(mc,t,GoldSounds.volume(mc,ours,user==null?null:user.getPos()));
            }
        }
        // a punch long over is let go (its user gone from sight, or simply done)
        USERS.entrySet().removeIf(e->{
            e.getValue().punches.removeIf(p->p.age(0)>length(p)+1.5f);
            return e.getValue().punches.isEmpty()&&e.getValue().ready<=mc.world.getTime()&&mc.world.getPlayerByUuid(e.getKey())==null;
        });
        // held, the attack keeps asking: the server keeps a press made a little before the next punch may start
        if(mc.player!=null&&mc.options.attackKey.isPressed()&&StandRush.ticks-lastAsk>=2&&mine(mc)&&!dev.sangwoo.celestialfall.client.meteor.MeteorClient.pickaxeInHand()&&!dev.sangwoo.celestialfall.client.commerce.CommerceClient.fruitInHand())ask(mc);
    }

    // ───────────────────────────── the key ─────────────────────────────

    /** Whether the local player's attack is their Gold Experience's punches now. */
    static boolean mine(MinecraftClient mc){return mc.player!=null&&GoldPresence.out(mc.player);}
    /** The local player's attack is their stand's punches: the attack key breaks no blocks. */
    public static boolean punching(){return mine(MinecraftClient.getInstance());}
    /** The attack key, pressed: Gold Experience punches if it is out (whatever the hand holds). Returns whether it took the press. */
    public static boolean punch(){
        var mc=MinecraftClient.getInstance();
        if(!mine(mc))return false;
        ask(mc);
        return true;
    }
    private static void ask(MinecraftClient mc){
        if(!CombatClient.combatInput()||!ClientPlayNetworking.canSend(GoldExperienceAttack.INPUT))return;
        if(lastAsk==StandRush.ticks)return;
        BotandCamera.prepareInteraction();
        // the way it goes is the way the player faces now: the turn goes to the server before the press
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(),mc.player.getPitch(),mc.player.isOnGround()));
        ClientPlayNetworking.send(GoldExperienceAttack.INPUT,PacketByteBufs.empty());
        lastAsk=StandRush.ticks;
    }

    // ───────────────────────────── the pose ─────────────────────────────

    static float clamp(float v){return v<0?0:v>1?1:v;}
    static float smooth(float v){v=clamp(v);return v*v*(3-2*v);}
    /** Ease into a key: smoothly, holding the stance, or striking (faster and faster, at full speed as it lands). */
    static float ease(int ease,float x){x=clamp(x);return ease==STRIKE?(float)Math.pow(x,1.6):smooth(x);}
    /** The punch's own clock as its body moves on it: time stands still for the hit-stop. */
    static float warp(int combo,float t){float hit=HIT[combo];return t<hit?t:t<hit+FREEZE[combo]?hit:t-FREEZE[combo];}
    /** When a punch is over (seconds on its clock): its stand back in its guard, or stopped short. */
    static float end(Punch p){return Float.isNaN(p.stopped)?GoldExperienceRules.M1_LENGTH[p.combo]/20f:p.stopped;}
    static float linger(Punch p){return Float.isNaN(p.stopped)?LINGER:LINGER_STOPPED;}
    /** How long a punch shows (seconds): its keys and its stand in front, then its way back. */
    static float length(Punch p){return end(p)+linger(p)+RETURN+.2f;}
    /** Adds the stances of punch {@code combo} at {@code u} seconds on its clock with the hit-stop taken out into {@code w}, {@code share} of them. */
    static void stances(int combo,float u,float[] w,float share){
        var keys=KEYS[combo];
        if(u<=keys[0].at){w[keys[0].stance]+=share;return;}
        for(int i=1;i<keys.length;i++){
            if(u>keys[i].at)continue;
            float s=ease(keys[i].ease,(u-keys[i-1].at)/(keys[i].at-keys[i-1].at));
            w[keys[i-1].stance]+=share*(1-s);w[keys[i].stance]+=share*s;return;
        }
        w[keys[keys.length-1].stance]+=share;
    }
    /** Adds the stances of punch {@code p} at {@code t} seconds on its clock: stopped short, from where it was back to the guard. */
    static void stances(Punch p,float t,float[] w,float share){
        if(!Float.isNaN(p.stopped)&&t>p.stopped){
            float back=smooth((t-p.stopped)/BACK);
            stances(p.combo,warp(p.combo,p.stopped),w,share*(1-back));w[GUARD]+=share*back;return;
        }
        stances(p.combo,warp(p.combo,Math.max(0,t)),w,share);
    }
    /** Off fast and settling in (StandPunch.burst). */
    static float burst(float u){return StandPunch.burst(u);}
    /** How far the stand is out in front for this punch: brought round as it starts, sent back once it is over. */
    static float envelope(Punch p,float t){return burst(t/RISE)*(1-smooth((t-end(p)-linger(p))/RETURN));}
    /** How far its stances have taken over from the pose it had (beside its user, or another skill's). */
    static float takeOver(Punch p,float t){return smooth(t/TAKE)*(1-smooth((t-end(p)-linger(p))/RETURN));}
    /** How strongly this punch strikes now (its striking stance's share): what the dash goes by. */
    static float striking(Punch p,float t){float[] w=new float[NAMES.size()];stances(p,t,w,1);return w[STRIKES[p.combo]];}
    /** How far the stand dashes on into this punch as it lands (blocks): for its fist to sink BITE into its target's near side. */
    static float lunge(Punch p){
        float min=LUNGE_MIN[p.combo],max=LUNGE_MAX[p.combo];
        if(Float.isNaN(p.aim))return min;
        float need=p.aim+BITE-(AHEAD+GoldRush.FISTS[STRIKES[p.combo]][RIGHT[p.combo]?1:0][0]);
        return Math.max(min,Math.min(max,need));
    }
    /** The shove of a blow as it lands (blocks): in over two frames, gone in a few more (none for a punch stopped before it landed). */
    static float drive(Punch p,float t){
        if(!Float.isNaN(p.stopped)&&p.stopped<HIT[p.combo])return 0;
        return DRIVE[p.combo]*StandPunch.kick(t,HIT[p.combo],.07f);
    }
    /** When the punch's strike starts on its clock (seconds): the key before its striking one. */
    static float strikeFrom(int combo){var keys=KEYS[combo];for(int i=1;i<keys.length;i++)if(keys[i].ease==STRIKE)return keys[i-1].at;return HIT[combo];}
    /** The afterimages: from the moment the strike starts, through the blow and its hit-stop, gone soon after (not for a punch stopped before it landed). */
    static float ghost(Punch p,float t){
        float hit=HIT[p.combo];
        if(!Float.isNaN(p.stopped)&&p.stopped<hit)return 0;
        return smooth((t-strikeFrom(p.combo))/.03f)*(1-smooth((t-hit-FREEZE[p.combo])/.1f));
    }
    /** How much light has gathered in the fourth's drawn-back fist {@code t} seconds in (0 to 1; none for the others): up as it coils, gone as it strikes. */
    static float charge(Punch p,float t){
        if(p.combo!=4)return 0;
        float c=smooth((t-.05f)/.3f)*(1-smooth((t-strikeFrom(4))/(HIT[4]-strikeFrom(4))));
        return Float.isNaN(p.stopped)?c:c*(1-smooth((t-p.stopped)/BACK));
    }
    static float wrap(float degrees){degrees%=360;if(degrees>=180)degrees-=360;if(degrees<-180)degrees+=360;return degrees;}

    /** The newest punch of this player's and the one before it that still shows ({@code back} seconds ago): {now, before} (either may be null). */
    private static Punch[] pair(UUID id,float delta,float back){
        var u=USERS.get(id);if(u==null||u.punches.isEmpty())return null;
        Punch now=null,before=null;
        for(var p:u.punches){if(p.age(delta)-back<0)continue;before=now;now=p;}
        if(now==null)return null;
        if(now.age(delta)-back>length(now))return null;
        if(before!=null&&before.age(delta)-back>length(before))before=null;
        return new Punch[]{now,before};
    }
    /**
     * The punches this frame ({@code back} seconds ago: an afterimage) on a look that already holds the stand's pose
     * (beside its user, or another skill's): the newest punch's stances, and the last one's while the newest takes over
     * from it, take over from the look's as it comes round, and {@code shape} says where it stands. Returns whether
     * they show.
     */
    static boolean pose(UUID id,float delta,float back,GoldExperienceLook look,GoldRush.Shape shape,float[] scratch){
        var pair=pair(id,delta,back);if(pair==null)return false;
        Punch now=pair[0],before=pair[1];
        float t=now.age(delta)-back,tb=before==null?0:before.age(delta)-back;
        Arrays.fill(scratch,0);
        float take=before==null?1:smooth(t/BLEND[now.combo]);
        stances(now,t,scratch,take);
        if(take<1)stances(before,tb,scratch,1-take);
        float in=Math.max(takeOver(now,t),before==null?0:takeOver(before,tb));
        for(int i=0;i<scratch.length;i++)look.weights[i]=look.weights[i]*(1-in)+scratch[i]*in;
        look.armL=look.armL*(1-in)+in;look.armR=look.armR*(1-in)+in;
        shape.engage=Math.max(envelope(now,t),before==null?0:envelope(before,tb));
        float turn=before==null?1:smooth(t/TURN);
        shape.yaw=before==null?now.yaw:before.yaw+wrap(now.yaw-before.yaw)*turn;
        shape.ahead=AHEAD+lunge(now)*striking(now,t)*take+(before==null?0:lunge(before)*striking(before,tb)*(1-take))+drive(now,t)+(before==null?0:drive(before,tb));
        shape.raise=RAISE;shape.side=0;shape.inward=0;
        return true;
    }
    /** How strongly this player's strike afterimages show this frame (0 when they do not). */
    static float ghost(UUID id,float delta){
        var pair=pair(id,delta,0);if(pair==null)return 0;
        float g=ghost(pair[0],pair[0].age(delta));
        if(pair[1]!=null)g=Math.max(g,ghost(pair[1],pair[1].age(delta)));
        return g;
    }
    /** How much light gathers in the fourth's fist this frame (its aura burns brighter with it). */
    static float charge(UUID id,float delta){var pair=pair(id,delta,0);return pair==null?0:charge(pair[0],pair[0].age(delta));}
    /** The age of this player's newest punch this frame (seconds), or NaN if none shows: the stand's skills are laid in the order they began. */
    static float newest(UUID id,float delta){var pair=pair(id,delta,0);return pair==null?Float.NaN:pair[0].age(delta);}
    /** Where punch {@code p}'s striking fist's knuckles are {@code t} seconds in (blocks ahead, to its right and up from the stand's feet, at its size 1). */
    static float[] fist(Punch p,float t){
        float[] w=new float[NAMES.size()];stances(p,t,w,1);float[] out=new float[3];int side=RIGHT[p.combo]?1:0;
        for(int s=0;s<w.length;s++){
            if(w[s]<=0)continue;
            float[] f=GoldRush.FISTS[s][side];
            for(int i=0;i<3;i++)out[i]+=f[i]*w[s];
        }
        return out;
    }

    // ───────────────────────────── what others ask ─────────────────────────────

    /** Everyone whose punches still show, with theirs (oldest first). */
    static Map<UUID,List<Punch>> all(){
        var out=new HashMap<UUID,List<Punch>>();
        for(var e:USERS.entrySet())if(!e.getValue().punches.isEmpty())out.put(e.getKey(),new ArrayList<>(e.getValue().punches));
        return out;
    }
    /** The world tick this player's attack is ready again after its rest (0 if it has not rested). */
    public static long ready(UUID id){var u=USERS.get(id);return u==null?0:u.ready;}
    /** This player's stand is not Gold Experience: its rest is gone, as the server forgets it. */
    public static void forget(UUID id){var u=USERS.get(id);if(u!=null)u.ready=0;}
    /** For the checks: this player's newest punch as "combo,age,landed,struck,guarded,stopped,sounds,blows", or null. */
    public static String describe(UUID id,float delta){
        var u=USERS.get(id);if(u==null||u.punches.isEmpty())return null;var p=u.punches.peekLast();
        return String.format(java.util.Locale.ROOT,"%d,%.2f,%d,%d,%d,%d,%d,%d",p.combo,p.age(delta),p.landed()?1:0,p.struck.length,p.guarded.length,Float.isNaN(p.stopped)?0:1,p.sound.heard(),p.blows);
    }
    /** For the tests: a punch heard {@code serverAge} ticks after it began, at this client's tick {@code received}. */
    static Punch heard(int combo,long received,float yaw,float aim){return new Punch(0,0,received,combo,yaw,aim);}
    /** For the tests: this player's punches as heard. */
    static void put(UUID id,Punch... punches){var u=USERS.computeIfAbsent(id,k->new User());u.punches.clear();u.punches.addAll(Arrays.asList(punches));}
}
