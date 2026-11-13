package dev.sangwoo.celestialfall.xchara;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.combat.Stagger;
import dev.sangwoo.celestialfall.stand.KagenoManager;
import dev.sangwoo.celestialfall.stand.StandManager;
import dev.sangwoo.celestialfall.stand.StarPlatinumTimeStop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import static dev.sangwoo.celestialfall.xchara.XCharaCombo.*;

/**
 * X Chara's basic attack, their knife's four swings (2.114.0; XCharaCombo for the timing and the reach). The attack key
 * on their client asks for a hit (xchara_attack_v1, no payload; a vanilla attack is taken the same way) and the server
 * decides, as for Lee So-sang's sword (IsosangAttack): the next hit of the combo starts now, or when it may if that is
 * near (a press made a little early is kept), or not at all while the attack rests. The swings and their rules are the
 * same with their hack on or off (2.118.0); a hit is told as one begun with the hack on (Q, XCharaHack: XCharaCombo's
 * hits 1 to 4, which the clients draw with a purple cut and what it leaves on whom it strikes) or with it off (hits 5
 * to 8, 2.117.0: the body and the knife only), and the combo goes on through either. Each hit goes the way its user
 * faces as it starts and holds them where they stand a moment (XCharaCombo.HOLD: no walking, guard, dash or other
 * move, CombatManager.busy); at its blow it strikes everyone in its way (XCharaCombo.lane, in sight) without knockback
 * and staggers them until the next blow could land (Stagger.xchara). The fourth, the lunge, takes no guard and sends
 * whom it strikes flying the way the hit went. Every viewer is told when a hit starts, whom its blow struck, and when
 * the attack rests or the hit stops short (xchara_attack_event_v1); the clients draw the body's motion and the cuts
 * (client/xchara/KnifeClient, KnifeCombo). A hit stops short when its user is struck, staggered, incapacitated, held in a
 * cutscene (their own G too), or is X Chara's no more; the attack then rests as after a broken combo.
 * <p>
 * Its sound is the clients' (KnifeClient: the one sound of every swing, the user's own, 2.118.0); the server plays none.
 */
public final class XCharaAttack {
    /** The attack key (no payload); and every viewer's news. */
    public static final Identifier INPUT=CelestialFall.id("xchara_attack_v1"),EVENT=CelestialFall.id("xchara_attack_event_v1");
    /** A hit starts; one of its blows lands (whom it struck); the attack rests until it is ready again; the hit in flight stops short. */
    public static final int HIT=0,LANDED=1,RESTS=2,STOPPED=3;
    /** (2.128.0) The slam's dive begins: where it comes down is settled (told with every event from then). */
    public static final int AIMED=4;
    /** At most this many presses a second are heard. */
    static final int RATE=40;
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        /** The hit in flight or the last one (1 to 8: the four begun with the hack on, the plain four; 0 for none), its id, when it began, the way it goes (degrees), and how many of its blows have landed. */
        int combo;UUID id=new UUID(0,0);long start=-1000;float yaw;int blows;
        /** A press kept for the next hit, and the tick the attack is ready again after a rest (0 for none). */
        boolean queued;long ready;
        /** (2.121.0) The tick the cleave (R with the hack on) is ready again (0 for at once). */
        long cleaveReady;
        /** (2.124.0) The tick the jab (R with the hack off) is ready again (0 for at once). */
        long jabReady;
        /**
         * (2.128.0) The slam (G with the hack off): the tick it is ready again (0 for at once); and of the one in flight,
         * where its user stood as it began, how high they rise over it (blocks), where it comes down (null until its dive
         * begins), and the last their client told of where the middle of its screen points (and the tick it did).
         */
        long slamReady;Vec3d from;double rise;Vec3d land,aim;long aimAt;
        /** (2.128.4) How far forward of where they stood the top of its rise is ({x,z}: the way they faced, as far as they go). */
        double aheadX,aheadZ;
        /**
         * (2.129.0) The three cuts (E with the hack off): the tick they are ready again (0 for at once); and of one going
         * forward first, where it ends (null: it cuts where they stand) and whether they have been taken there.
         */
        long cutterReady;Vec3d to;boolean arrived;
        int window,presses;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private XCharaAttack(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->{
            if(buf.readableBytes()!=0)return;
            server.execute(()->{
                var s=state(player);int second=server.getTicks()/20;
                if(s.window!=second){s.window=second;s.presses=0;}
                if(++s.presses>RATE)return;
                request(player);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(XCharaAttack::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STATES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
    }
    /** Whether this player's attack is X Chara's knife: theirs, alive, on the map. */
    public static boolean attacks(PlayerEntity p){return p!=null&&StandManager.xChara(p);}
    /** Free to start a hit: nothing else holds them (a blow, a guard, a dash, a skill, a cutscene); a hit's own hold aside. */
    public static boolean able(ServerPlayerEntity p){return attacks(p)&&!p.isSleeping()&&!p.hasVehicle()&&!CombatManager.busyOtherwise(p)&&!CombatManager.cinematic(p)&&!XCharaHeal.busy(p);}
    /** Whether one of this player's hits holds them now (XCharaCombo.HOLD): CombatManager.busy and actionLocked count it. */
    public static boolean busy(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&holding(s.combo,s.start,p.getWorld().getTime());
    }
    /** This player's hit as a boss reads it: which, begun at {@code start}, the way {@code yaw}, {@code blows} of its blows landed. */
    public record Hit(int combo,long start,float yaw,int blows){}
    /** This player's hit now (null if they have swung none). */
    public static Hit hit(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?null:new Hit(s.combo,s.start,s.yaw,s.blows);}
    /** The tick this player's attack is ready again after its rest (0 if it is not resting). */
    public static long ready(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.ready;}
    /** (2.121.0) The tick this player's cleave is ready again (0 if it is). */
    public static long cleaveReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.cleaveReady;}
    /** (2.121.0) Whether this player's cleave (or, 2.124.0, their jab) is in flight now (its motion not over): their hack's key is not heard through it. */
    public static boolean cleaving(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.world==p.getWorld()&&skill(s.combo)&&p.getWorld().getTime()<s.start+LENGTH[s.combo];
    }
    /** This player is X Chara's no more: the attack's rest is gone, so it is ready if they are again (StandManager.forgetOthers). */
    public static void forget(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.cleaveReady=0;s.jabReady=0;s.slamReady=0;s.cutterReady=0;}}
    /** The attack's rest is gone (a player the cooldowns are off for, /botand nocooldown). */
    public static void cool(PlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null){s.ready=0;s.cleaveReady=0;s.jabReady=0;s.slamReady=0;s.cutterReady=0;}}
    /** (2.124.0) The tick this player's jab is ready again (0 if it is). */
    public static long jabReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.jabReady;}
    /** (2.129.0) The tick this player's three cuts are ready again (0 if they are). */
    public static long cutterReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.cutterReady;}
    /** (2.129.0) Where this player's three cuts in flight are struck from: where their going forward ends until they are there, then where they stand. */
    public static Vec3d cutsFrom(PlayerEntity p){
        var s=p==null?null:STATES.get(p.getUuid());
        return s!=null&&s.combo==CUTTER_DASH&&s.to!=null&&!s.arrived?s.to:p==null?null:p.getPos();
    }
    /** (2.129.0) For the multiplayer check: this player's three cuts in flight as {start, kind, where the going ends or null, blows landed}, or null. */
    public static Object[] describeCutter(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||!XCharaCombo.cutter(s.combo)?null:new Object[]{s.start,s.combo,s.to,s.blows};}
    /** (2.128.0) The tick this player's slam is ready again (0 if it is). */
    public static long slamReady(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null?0:s.slamReady;}
    /** (2.128.0) Whether this one is in the air of their slam now (drawn up there, standing where they began): nothing harms them, and no one's skill takes them. */
    public static boolean airborne(LivingEntity e){
        var s=e==null||e.getWorld().isClient?null:STATES.get(e.getUuid());
        return s!=null&&s.combo==SLAM&&s.world==e.getWorld()&&SlamRules.airborne(e.getWorld().getTime()-s.start);
    }
    /** (2.128.0) Where this player's slam in flight comes down, once its dive has begun (null before, or with none). */
    public static Vec3d landing(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||s.combo!=SLAM?null:s.land;}
    /** (2.128.0) For the multiplayer check: this player's slam in flight as {start, rise, where it comes down or null, blows landed}, or null. */
    public static Object[] describeSlam(PlayerEntity p){var s=p==null?null:STATES.get(p.getUuid());return s==null||s.combo!=SLAM?null:new Object[]{s.start,s.rise,s.land,s.blows};}
    private static State state(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new State(p);STATES.put(p.getUuid(),s);}
        return s;
    }

    /** The player asks for a hit. Returns whether one starts now or is kept for when it may. */
    public static boolean request(ServerPlayerEntity p){
        if(!able(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(now<s.ready&&!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))return false;
        long may=may(s);
        if(now<may){
            // (the fourth hit's motion runs on after its blows; the next combo waits for its rest, set as it began)
            if(!last(s.combo)&&!skill(s.combo)){if(may-now<=BUFFER)s.queued=true;return s.queued;}
            return false;
        }
        begin(p,s,now);
        return true;
    }
    /**
     * R with the hack on, the cleave (2.121.0, XCharaCombo.CLEAVE). Returns whether it began: with the hack on, nothing
     * holding them (a hit of the knife's own holds too: it waits for no hit), and its cooldown over. It ends the combo
     * (the next hit is the first) and adds no rest of its own.
     */
    public static boolean cleave(ServerPlayerEntity p){
        if(!able(p)||!XCharaHack.on(p)||XCharaHack.held(p)||CombatManager.actionLocked(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(valid(s.combo)&&now<(last(s.combo)||skill(s.combo)?s.start+LENGTH[s.combo]:s.start+HOLD[s.combo]))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(now<s.cleaveReady&&!free)return false;
        s.combo=CLEAVE;s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        s.cleaveReady=free?0:XCharaCombo.cleaveReady(now);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
        return true;
    }
    /**
     * R with the hack off, the jab (2.124.0, XCharaCombo.JAB). Returns whether it began: with the hack off, nothing
     * holding them (a hit of the knife's own holds too), and its cooldown over. As the cleave, it ends the combo and
     * adds no rest of its own.
     */
    public static boolean jab(ServerPlayerEntity p){
        if(!able(p)||XCharaHack.on(p)||XCharaHack.held(p)||CombatManager.actionLocked(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(valid(s.combo)&&now<(last(s.combo)||skill(s.combo)?s.start+LENGTH[s.combo]:s.start+HOLD[s.combo]))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(now<s.jabReady&&!free)return false;
        s.combo=JAB;s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        s.jabReady=free?0:XCharaCombo.jabReady(now);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
        return true;
    }
    /**
     * G with the hack off, the slam (2.128.0, XCharaCombo.SLAM, SlamRules). Returns whether it began: with the hack off,
     * standing on the ground, nothing holding them (a hit of the knife's own holds too), and its cooldown over. As the
     * cleave and the jab, it ends the combo and adds no rest of its own. How high they rise is settled now: SlamRules.RISE,
     * or as far as nothing is over their head.
     */
    public static boolean slam(ServerPlayerEntity p){
        if(!able(p)||XCharaHack.on(p)||XCharaHack.held(p)||CombatManager.actionLocked(p)||!p.isOnGround())return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(valid(s.combo)&&now<(last(s.combo)||skill(s.combo)?s.start+LENGTH[s.combo]:s.start+HOLD[s.combo]))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(now<s.slamReady&&!free)return false;
        s.combo=SLAM;s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        s.from=p.getPos();s.rise=SlamGround.headroom(p.getWorld(),p);s.land=null;s.aim=null;s.aimAt=0;
        Vec3d way=Vec3d.fromPolar(0,s.yaw);double far=SlamGround.forward(p.getWorld(),p,s.rise,way);s.aheadX=way.x*far;s.aheadZ=way.z*far;
        s.slamReady=free?0:SlamRules.ready(now);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
        return true;
    }
    /**
     * E with the hack off, the three cuts (2.129.0, XCharaCombo.CUTTER, CutterRules). Returns whether they began: with the
     * hack off, nothing holding them (a hit of the knife's own holds too), and their cooldown over. Whether they go
     * forward first is settled now: not with someone right ahead (CutterRules.CLOSE); else as far as CutterRules.dash
     * lets them, to a floor they can stand on. As the other skills, it ends the combo and adds no rest of its own.
     */
    public static boolean cutter(ServerPlayerEntity p){
        if(!able(p)||XCharaHack.on(p)||XCharaHack.held(p)||CombatManager.actionLocked(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        settle(p,s,now);
        if(valid(s.combo)&&now<(last(s.combo)||skill(s.combo)?s.start+LENGTH[s.combo]:s.start+HOLD[s.combo]))return false;
        boolean free=dev.sangwoo.celestialfall.NoCooldownState.enabled(p);
        if(now<s.cutterReady&&!free)return false;
        float yaw=MathHelper.wrapDegrees(p.getYaw());
        s.from=p.getPos();s.to=goes(p,yaw);s.arrived=false;
        s.combo=s.to==null?CUTTER:CUTTER_DASH;s.id=UUID.randomUUID();s.start=now;s.yaw=yaw;s.queued=false;s.blows=0;
        s.cutterReady=free?0:CutterRules.ready(s.combo,now);
        p.clearActiveItem();p.setSprinting(false);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
        return true;
    }
    /** Whom {@code p}'s knife may strike at all: alive, to be struck, no merchant, and (a player) one they may harm. */
    private static boolean foe(ServerPlayerEntity p,LivingEntity e){
        return e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()&&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)
            &&!(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other));
    }
    /**
     * Where the three cuts' going forward ends for {@code p} facing {@code yaw}, or null if they cut where they stand:
     * the near side of the first one in their way (in sight) decides how far (CutterRules.dash), less where a block
     * stands in the way; the floor there, a low step up or a drop they can take (CutterRules.UP, DOWN); none, nearer.
     */
    private static Vec3d goes(ServerPlayerEntity p,float yaw){
        var world=p.getServerWorld();Vec3d f=Vec3d.fromPolar(0,yaw),right=new Vec3d(-f.z,0,f.x),at=p.getPos();
        double ahead=Double.NaN,most=CutterRules.DASH+CutterRules.REACH[1];
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(most+3),e->foe(p,e))){
            Vec3d d=e.getPos().subtract(at);double half=e.getWidth()*.5,fw=d.dotProduct(f)-half;
            if(fw<-2*half||fw>most||Math.abs(d.dotProduct(right))>CutterRules.PATH+half||d.y<LOW||d.y>HIGH||!p.canSee(e))continue;
            if(Double.isNaN(ahead)||fw<ahead)ahead=Math.max(0,fw);
        }
        double clear=CutterRules.DASH;
        for(double up:new double[]{CutterRules.UP+.05,1.0,p.getHeight()-.1}){
            Vec3d a=at.add(0,up,0);
            var hit=world.raycast(new net.minecraft.world.RaycastContext(a,a.add(f.multiply(CutterRules.DASH+.5)),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,p));
            if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)clear=Math.min(clear,Math.max(0,hit.getPos().distanceTo(a)-.5));
        }
        for(double far=CutterRules.dash(ahead,clear);far>=CutterRules.LEAST;far-=.5){
            double x=at.x+f.x*far,z=at.z+f.z*far;
            double y=SlamGround.floor(world,p,x,z,at.y+CutterRules.UP,at.y-CutterRules.DOWN);
            if(!Double.isNaN(y))return new Vec3d(x,y,z);
            // (off the ground as they begin, with no floor under where it ends: as high as they are, if they fit there)
            if(!p.isOnGround()&&world.isSpaceEmpty(p,p.getDimensions(net.minecraft.entity.EntityPose.STANDING).getBoxAt(x,at.y,z).contract(1e-4,0,1e-4)))return new Vec3d(x,at.y,z);
        }
        return null;
    }
    /** The three cuts' going forward is over: their user is where it ends, from now (looking as they look: the turn is their client's). */
    private static void arrive(ServerPlayerEntity p,State s){
        s.arrived=true;Vec3d to=s.to;
        p.networkHandler.requestTeleport(to.x,to.y,to.z,0,0,java.util.EnumSet.of(net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT,net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT));
        p.setVelocity(Vec3d.ZERO);p.fallDistance=0;p.velocityModified=true;
    }
    /**
     * (2.128.0) Their client's word of where the middle of its screen points (xchara_skill_v1's AIM), up in the air of
     * their slam before its dive: kept, the last of them, for when the dive begins.
     */
    public static void aim(ServerPlayerEntity p,Vec3d at){
        var s=STATES.get(p.getUuid());if(s==null||s.combo!=SLAM||s.land!=null||s.world!=p.getWorld())return;
        long age=p.getWorld().getTime()-s.start;
        if(age<SlamRules.AIM_FROM-4||age>SlamRules.DIVE||!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z))return;
        s.aim=at;s.aimAt=p.getWorld().getTime();
    }
    /**
     * The slam's dive begins: where it comes down. The ground the middle of their screen points at (their client's
     * word, if it told within the last ticks; else where their look from up there meets the world; the sky, as far out
     * the way they look as the circle goes), brought within the circle, on the floor found there (the first one down
     * from there they can stand on; none, where they stood).
     */
    private static void dive(ServerPlayerEntity p,State s,long now){
        var world=p.getServerWorld();Vec3d from=s.from;
        Vec3d want=s.aim!=null&&now-s.aimAt<=8?s.aim
            :SlamGround.aimed(world,p,from,new Vec3d(from.x+s.aheadX,from.y+s.rise+p.getStandingEyeHeight(),from.z+s.aheadZ),p.getRotationVec(1));
        s.land=SlamGround.landing(world,p,from,s.rise,want);
        send(p,s,AIMED,-1,null);
    }
    /** The slam lands: its user is where it came down, from now. */
    private static void touchDown(ServerPlayerEntity p,State s){
        Vec3d to=s.land==null?s.from:s.land;
        if(p.hasVehicle())p.stopRiding();
        // (they face the way they dived, as every client drew them turning to it)
        Vec3d d=to.subtract(s.from);float yaw=d.horizontalLengthSquared()<.25?p.getYaw():(float)Math.toDegrees(Math.atan2(-d.x,d.z));
        p.networkHandler.requestTeleport(to.x,to.y,to.z,yaw,p.getPitch());p.setHeadYaw(yaw);p.setBodyYaw(yaw);
        p.setVelocity(Vec3d.ZERO);p.fallDistance=0;p.velocityModified=true;
    }
    /** (2.125.0) Another skill of theirs began (the double, XCharaClone): the combo is over (the next hit is the first), no rest added. */
    public static void skillUsed(ServerPlayerEntity p){
        var s=STATES.get(p.getUuid());if(s==null||s.world!=p.getServerWorld())return;
        settle(p,s,p.getWorld().getTime());
        if(valid(s.combo)&&!last(s.combo)&&!skill(s.combo)){s.combo=0;s.queued=false;}
    }
    /** When the next hit may begin: at once with none in flight; the next of the combo its STEP after the hit before; the next combo once the fourth's motion is over (and its rest, s.ready). */
    private static long may(State s){
        if(!valid(s.combo))return Long.MIN_VALUE;
        return last(s.combo)||skill(s.combo)?s.start+LENGTH[s.combo]:nextAt(s.combo,s.start);
    }
    /** The combo broke (the next hit is late): the attack rests and starts again from the first hit; a rest over ends the combo. */
    private static void settle(ServerPlayerEntity p,State s,long now){
        long rest=broken(s.combo,s.start,now);
        if(rest>=0){
            s.combo=0;s.queued=false;
            // (none for a player the cooldowns are off for, /botand nocooldown: the list shows no rest for them)
            if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,rest);
            send(p,s,RESTS,-1,null);
        }
        if(last(s.combo)&&now>=s.ready&&now>=s.start+LENGTH[s.combo])s.combo=0;
        // (the cleave over: no combo is open after it)
        if(skill(s.combo)&&now>=s.start+LENGTH[s.combo])s.combo=0;
    }
    private static void begin(ServerPlayerEntity p,State s,long now){
        // the hack off as it begins, a plain swing (2.117.0); on, the same swing drawn with its purple cut (2.118.0): the
        // combo goes on from the one to the other
        s.combo=next(s.combo,!XCharaHack.on(p));s.id=UUID.randomUUID();s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.queued=false;s.blows=0;
        // (the rest after the fourth hit: none for a player the cooldowns are off for, /botand nocooldown)
        if(last(s.combo))s.ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?now:rested(s.combo,now);
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        send(p,s,HIT,-1,null);
    }
    /** Whatever ends the hit in flight: its user struck, held, or no longer able to fight with the knife. */
    private static boolean held(ServerPlayerEntity p){
        return !attacks(p)||p.isSleeping()||p.hasVehicle()||HitStun.stunned(p)||CombatManager.incapacitated(p)||CombatManager.cinematic(p)||KagenoManager.frozen(p);
    }
    /** The user was struck (staggered or incapacitated, CombatManager): the hit in flight stops short. */
    public static void struck(ServerPlayerEntity p){var s=STATES.get(p.getUuid());if(s!=null)stop(p,s,p.getWorld().getTime());}
    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(STATES.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){STATES.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            // (the cleave is the hack's: the hack gone before its blow, it stops short)
            if(held(p)||s.combo==CLEAVE&&s.blows==0&&!XCharaHack.on(p)){stop(p,s,now);continue;}
            settle(p,s,now);
            if(s.queued&&now>=may(s)){if(able(p))begin(p,s,now);else s.queued=false;}
            if(!valid(s.combo))continue;
            // (2.128.0) the slam's dive begins: where it comes down is settled, and told
            if(s.combo==SLAM&&s.land==null&&s.blows==0&&now>=s.start+SlamRules.DIVE)dive(p,s,now);
            // (2.129.0) the three cuts' going forward is over: they are there
            if(s.combo==CUTTER_DASH&&s.to!=null&&!s.arrived&&now>=s.start+CutterRules.DASH_TO)arrive(p,s);
            while(s.blows<blows(s.combo)&&now>=s.start+blowAt(s.combo,s.blows))blow(p,s,now);
        }
    }
    /** The hit in flight stops short (or the combo is cut off between hits): the attack rests from now. */
    private static void stop(ServerPlayerEntity p,State s,long now){
        boolean moving=valid(s.combo)&&now-s.start<LENGTH[s.combo],open=valid(s.combo)&&!last(s.combo)&&!skill(s.combo)&&broken(s.combo,s.start,now)<0;
        if(skill(s.combo)){
            // the cleave cut short: before its blow its cooldown runs from now (as every skill cut short); after it, it keeps
            // the one set as it began. No rest of the basic attack's.
            if(!moving)return;
            if(s.blows==0){
                long ready=dev.sangwoo.celestialfall.NoCooldownState.enabled(p)?0:now+cooldown(s.combo);
                if(XCharaCombo.cutter(s.combo))s.cutterReady=ready;else if(s.combo==SLAM)s.slamReady=ready;else if(s.combo==JAB)s.jabReady=ready;else s.cleaveReady=ready;
            }
            s.queued=false;s.combo=0;s.blows=0;
            send(p,s,STOPPED,-1,null);
            return;
        }
        if(!moving&&!open&&!s.queued)return;
        s.queued=false;s.combo=0;s.blows=0;
        if(!dev.sangwoo.celestialfall.NoCooldownState.enabled(p))s.ready=Math.max(s.ready,now+REST);
        send(p,s,STOPPED,-1,null);
    }
    /**
     * The hit's blow lands: everyone in its way in front of its user, in sight, is struck without knockback and staggered
     * until the next blow could land; the fourth takes no guard and sends them flying.
     */
    private static void blow(ServerPlayerEntity p,State s,long now){
        int combo=s.combo,i=s.blows++;
        // (2.128.0) the slam's blow is its landing: its user is where it came down as it strikes round them
        if(combo==SLAM){if(s.land==null)dive(p,s,now);touchDown(p,s);}
        var world=p.getServerWorld();var source=world.getDamageSources().playerAttack(p);float amount=damage(combo,i);
        Vec3d f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x),at=p.getPos();
        List<LivingEntity> struck=new ArrayList<>();
        var inWay=world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(MAX_REACH+3),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity));
        // (2.124.0) the jab is a thrust at one: the nearest of them only
        if(single(combo))inWay.sort(java.util.Comparator.comparingDouble(e->e.squaredDistanceTo(p)));
        for(var e:inWay){
            if(single(combo)&&!struck.isEmpty())break;
            Vec3d d=e.getPos().subtract(at);
            // (the slam strikes round where it landed, whichever way they are: seen from there or not)
            if(!lane(combo,i,d.dotProduct(f),d.dotProduct(right),d.y,e.getWidth()*.5)||combo!=SLAM&&!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(unblockable(combo))CombatManager.interrupt(e);
            e.timeUntilRegen=0;
            if(!CombatManager.damageWithoutKnockback(e,source,amount))continue;
            struck.add(e);
            // every blow staggers whom it strikes a moment (2.68.0's rule for basic attacks); one stopped in Star Platinum's
            // stopped time is staggered as time moves again
            if(combo==SLAM){
                // (2.128.0) the slam: whom it strikes can do nothing for a moment (the video's 0.45 s)
                CombatManager.incapacitate(e,SlamRules.STUN,SlamRules.STUN,SlamRules.SLOW);
            }
            else if(combo==JAB){
                // (2.124.0) the jab: whom it strikes stands there and can do nothing for eight seconds
                CombatManager.incapacitate(e,JAB_STUN,JAB_STUN,JAB_SLOW);
            }
            else if(skill(combo)&&!XCharaCombo.cutter(combo)){
                // the cleave: whom it strikes cannot move or act for a second (their legs stopped wholly; one stopped in a
                // time stop takes it as time moves again, CombatManager)
                CombatManager.incapacitate(e,CLEAVE_STUN,CLEAVE_STUN,CLEAVE_SLOW);
            }
            // (2.129.0: the three cuts' blows stagger as these do, drawn as a first strike's flinch and a last's)
            else if(!KagenoManager.frozen(e)||StarPlatinumTimeStop.stopped(e))HitStun.stagger(p,e,XCharaCombo.cutter(combo)?(i==2?4:1):combo,Stagger.xchara(combo,i,s.start,now));
            // the finisher's last cut throws them the way it went, told to a struck player's client once, through the
            // tracker; one stopped in a time stop is thrown as time moves again
            if(launches(combo,i)){
                Vec3d fly=f.multiply(FLY).add(0,FLY_UP,0);
                if(!StarPlatinumTimeStop.deferLaunch(e,fly)){e.setVelocity(fly);e.velocityModified=true;}
            }
            // the user's own hit feedback (the damage number and the blood), as the other characters'
            if(!StarPlatinumTimeStop.deferFeedback(p,e,amount,s.yaw)&&ServerPlayNetworking.canSend(p,KagenoManager.DAMAGE)){
                var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat(amount);b.writeFloat(s.yaw);ServerPlayNetworking.send(p,KagenoManager.DAMAGE,b);
            }
        }
        if(!struck.isEmpty()){
            p.onAttacking(struck.get(0));p.addExhaustion(.1f);
        }
        send(p,s,LANDED,i,struck);
    }
    /**
     * Tells every viewer near the user (and the user) of the event: whose, the kind, the hit's id, which hit and which
     * blow, when the hit began and the server's time now, where its user stands and the way it goes, when the attack is
     * ready again, and whom the blow struck (each with where they stand).
     */
    private static void send(ServerPlayerEntity p,State s,int type,int blow,List<LivingEntity> struck){
        var world=p.getServerWorld();long now=world.getTime();
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());b.writeByte(type);b.writeUuid(s.id);b.writeByte(s.combo);b.writeByte(blow);
            b.writeLong(s.start);b.writeLong(now);b.writeDouble(p.getX());b.writeDouble(p.getY());b.writeDouble(p.getZ());b.writeFloat(s.yaw);b.writeLong(s.ready);
            b.writeVarInt(struck==null?0:struck.size());
            if(struck!=null)for(var e:struck){b.writeUuid(e.getUuid());b.writeInt(e.getId());b.writeDouble(e.getX());b.writeDouble(e.getY());b.writeDouble(e.getZ());}
            // (2.121.0) when the cleave is ready again (a 2.120 client stops reading before it)
            b.writeLong(s.cleaveReady);
            // (2.124.0) and the jab
            b.writeLong(s.jabReady);
            // (2.128.0) and the slam; and of one in flight how high it rises and, once its dive began, where it comes down
            b.writeLong(s.slamReady);
            boolean slam=s.combo==SLAM;
            b.writeFloat(slam?(float)s.rise:0);
            // (2.128.4) and how far forward the top of its rise is
            b.writeFloat(slam?(float)s.aheadX:0);b.writeFloat(slam?(float)s.aheadZ:0);
            // (2.129.0) of the three cuts going forward first, where that ends (in the slam's place: a hit is one or the other)
            Vec3d land=slam?s.land:s.combo==CUTTER_DASH?s.to:null;
            b.writeBoolean(land!=null);
            if(land!=null){b.writeDouble(land.x);b.writeDouble(land.y);b.writeDouble(land.z);}
            // (2.129.0) when the three cuts are ready again (a 2.128 client stops reading before it)
            b.writeLong(s.cutterReady);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
}
