package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.NoCooldownState;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.CutsceneGlow;
import dev.sangwoo.celestialfall.combat.HitStun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.stand.GoldExperienceRules.*;

/**
 * Gold Experience's G, the beatdown: the seven-page muda (2.150.0; GoldExperienceRules has the user's words, the
 * answers and every tick). With the stand out, G (gold_beatdown_v1, no payload) makes its user glow first as every
 * skill with a cutscene does (CutsceneGlow), and as the glow ends the stand throws its opening punch, which lands
 * BEAT_CUT ticks on on the first one in its way, the way its user faced: a guard up breaks (StarPlatinumRules.
 * HEAVY_BREAK, as Star Platinum's beatdown's blow breaks one) and nothing more comes of it; no one there, nothing.
 * Landing on one with no guard up it begins the cutscene: its user and the one struck are held where they are all
 * through it (KagenoManager.frozen, by {@link #involved}) and nothing harms them but its own blows; the rush's blows
 * and the last deal its damage (GoldExperienceRules.beatdownDamage, worked out as it begins) between them, none taking
 * the one struck below one health; as they come down (BEAT_LAND) they are put where the last blow sent them and the
 * fall deals what is left, killing them there if it was more than they had. Where they are shown in between, the
 * stand, the camera and the sound are the clients' (client/stand/GoldBeatdown), from the beatdown's clock and what its
 * start told them: where they are held, how high there is room to hold them and to drive them up, how far its blows
 * drive them on, and where they come down. Its cooldown runs from its end (the punch's, if no cutscene came of it; cut
 * short, from then). Star Platinum's beatdown's ways, with this one's script.
 */
public final class GoldExperienceBeatdown {
    public static final Identifier INPUT=CelestialFall.id("gold_beatdown_v1"),EVENT=CelestialFall.id("gold_beatdown_event_v1");
    /** The event's kinds: the opening punch begins; it lands (on no one, or on a guard it breaks); it is stopped short (or only its cooldown is told); the cutscene starts; it ends (done, or cut short). */
    public static final int OPEN=0,LANDED=1,STOPPED=2,START=3,END=4;
    private static final int RATE=20;
    /** A player's opening punch and G's cooldown. */
    private static final class Open {
        final ServerWorld world;long start=Long.MIN_VALUE/4,ready;float yaw;boolean landed=true;int window,presses;
        Open(ServerPlayerEntity p){world=p.getServerWorld();}
        boolean flying(long now){return now-start<BEAT_OPENER_LENGTH;}
    }
    /** A cutscene: its user and the one struck, when the punch began, its way, where everyone is, how high and how far on they are driven, where they come down, its damage and what it has dealt. */
    private static final class Beat {
        final ServerPlayerEntity user;final LivingEntity victim;final ServerWorld world;final long start;final float yaw;
        final Vec3d from,victimFrom,hold,land;final double drift,lift,high;final float damage;float dealt;
        boolean down,killed;
        Beat(ServerPlayerEntity user,LivingEntity victim,long start,float yaw,Vec3d hold,double drift,double lift,double high,Vec3d land,float damage){
            this.user=user;this.victim=victim;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=user.getPos();victimFrom=victim.getPos();
            this.hold=hold;this.drift=drift;this.lift=lift;this.high=high;this.land=land;this.damage=damage;
        }
    }
    private static final Map<UUID,Open> OPENS=new HashMap<>();
    private static final Map<UUID,Beat> BY_USER=new HashMap<>(),BY_VICTIM=new HashMap<>();
    /** Set while a cutscene's own blow is dealt (to its victim): the only harm that reaches them. */
    private static final ThreadLocal<LivingEntity> BLOW=new ThreadLocal<>();
    private GoldExperienceBeatdown(){}

    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buf,response)->server.execute(()->{
            var s=state(player);int second=server.getTicks()/20;
            if(s.window!=second){s.window=second;s.presses=0;}
            if(++s.presses>RATE)return;
            request(player);
        }));
        ServerTickEvents.END_SERVER_TICK.register(GoldExperienceBeatdown::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var b=BY_USER.get(p.getUuid());if(b==null)b=BY_VICTIM.get(p.getUuid());
            if(b!=null)end(b,b.world.getTime(),true);
            OPENS.remove(p.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{OPENS.clear();BY_USER.clear();BY_VICTIM.clear();});
    }
    private static Open state(ServerPlayerEntity p){
        var s=OPENS.get(p.getUuid());
        if(s==null||s.world!=p.getServerWorld()){s=new Open(p);OPENS.put(p.getUuid(),s);}
        return s;
    }
    /** Whether this one is in a beatdown's cutscene now (its user, or the one struck). */
    public static boolean involved(LivingEntity e){return e!=null&&!e.getWorld().isClient&&(BY_USER.containsKey(e.getUuid())||BY_VICTIM.containsKey(e.getUuid()));}
    /** Whether this player is a beatdown's user, its cutscene going on now. */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether harm from {@code source} is kept from {@code e}: it is in a cutscene and this is not its own blow (what bypasses invulnerability still reaches them). */
    public static boolean shielded(LivingEntity e,DamageSource source){
        return involved(e)&&BLOW.get()!=e&&!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
    /** Whether this player's stand is throwing the opening punch now. */
    public static boolean opening(PlayerEntity p){var s=p==null?null:OPENS.get(p.getUuid());return s!=null&&s.world==p.getWorld()&&s.flying(p.getWorld().getTime());}
    /** The opening punch in flight for the bosses' reading of it: {start, the tick it lands, yaw as bits}, or null (none, or landed). */
    public static long[] coming(PlayerEntity p){
        var s=p==null?null:OPENS.get(p.getUuid());
        return s==null||s.world!=p.getWorld()||s.landed||!s.flying(p.getWorld().getTime())?null:new long[]{s.start,s.start+BEAT_CUT,Float.floatToIntBits(s.yaw)};
    }
    /** The tick this player's beatdown is ready again (0 if they have not used it). */
    public static long ready(PlayerEntity p){var s=OPENS.get(p.getUuid());return s==null?0:s.ready;}
    /** This player's stand is not Gold Experience (StandManager): its cooldown is gone. */
    public static void forget(PlayerEntity p){var s=OPENS.get(p.getUuid());if(s!=null)s.ready=0;}

    /** G: its user glows now if it may be begun (CutsceneGlow), and its opening punch is thrown as the glow ends ({@link #glowed}). Returns whether the glow began. */
    public static boolean request(ServerPlayerEntity p){return may(p)&&CutsceneGlow.begin(p,CutsceneGlow.GOLD_BEATDOWN);}
    /** Whether the beatdown may be begun now. */
    private static boolean may(ServerPlayerEntity p){
        if(!GoldExperienceOverdrive.punches(p)||p.isSleeping()||p.hasVehicle()||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        if(GoldExperienceHeal.active(p)||GoldExperienceOverdrive.punching(p)||involved(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        return !s.flying(now)&&(now>=s.ready||NoCooldownState.enabled(p));
    }
    /** The glow is over (CutsceneGlow): the opening punch is thrown now, the way its user faces. Returns whether it was. */
    public static boolean glowed(ServerPlayerEntity p){
        if(!may(p))return false;
        var s=state(p);long now=p.getWorld().getTime();
        s.start=now;s.yaw=MathHelper.wrapDegrees(p.getYaw());s.landed=false;
        s.ready=NoCooldownState.enabled(p)?now+BEAT_OPENER_LENGTH:beatReady(now+BEAT_OPENER_LENGTH);
        send(p,s,OPEN,null,false);
        return true;
    }
    /** The glow was cut short (CutsceneGlow: a blow landed on its user as they glowed): its cooldown runs in full from now. */
    public static void glowCut(ServerPlayerEntity p){
        var s=state(p);long now=p.getWorld().getTime();
        s.ready=NoCooldownState.enabled(p)?now:beatReady(now);
        send(p,s,STOPPED,null,false);
    }
    /** A blow landed on this player: their stand's opening punch not yet landed is stopped short, its cooldown running from now (HitStun.attackerDamaged). */
    public static void struck(ServerPlayerEntity p){
        var s=OPENS.get(p.getUuid());long now=p.getWorld().getTime();
        if(s==null||s.world!=p.getServerWorld()||s.landed||!s.flying(now))return;
        stop(p,s,now);
    }
    private static void stop(ServerPlayerEntity p,Open s,long now){
        s.start=Long.MIN_VALUE/4;s.landed=true;s.ready=NoCooldownState.enabled(p)?now:beatReady(now);
        send(p,s,STOPPED,null,false);
    }

    private static void tick(MinecraftServer server){
        for(var e:new ArrayList<>(OPENS.entrySet())){
            var p=server.getPlayerManager().getPlayer(e.getKey());var s=e.getValue();
            if(p==null||p.getServerWorld()!=s.world){OPENS.remove(e.getKey());continue;}
            long now=p.getWorld().getTime();
            // (its cutscene holds its user: its punch has landed, and nothing more comes of it until the cutscene is over)
            if(!s.flying(now)||casting(p))continue;
            if(!GoldExperienceOverdrive.punches(p)||HitStun.stunned(p)||CombatManager.actionLocked(p)||StarPlatinumRush.rushing(p)){stop(p,s,now);continue;}
            if(!s.landed&&now>=s.start+BEAT_CUT)strike(p,s,now);
        }
        if(BY_USER.isEmpty())return;
        for(var b:new ArrayList<>(BY_USER.values())){
            long now=b.world.getTime(),age=now-b.start;
            var u=b.user;var v=b.victim;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=b.world||u.isSpectator()||!GoldExperienceOverdrive.punches(u);
            boolean victimGone=!b.killed&&(v.isRemoved()||!v.isAlive()||v.getWorld()!=b.world||v.isSpectator());
            if(userGone||victimGone){end(b,now,true);continue;}
            KagenoManager.freeze(u);if(!b.killed){KagenoManager.freeze(v);v.fallDistance=0;}
            float share=beatShare(age);
            if(share>0)blow(b,age,share);
            if(age>=BEAT_LAND&&!b.down)land(b);
            if(age>=BEAT_END)end(b,now,false);
        }
    }
    /** The first one in the punch's way (the nearest, to its near side), in sight, in no cutscene and not stopped in a time stop; or null. */
    private static LivingEntity first(ServerPlayerEntity p,Open s){
        var world=p.getServerWorld();Vec3d at=p.getPos(),f=Vec3d.fromPolar(0,s.yaw),right=new Vec3d(-f.z,0,f.x);
        LivingEntity first=null;double best=Double.MAX_VALUE;
        for(var e:world.getEntitiesByClass(LivingEntity.class,new Box(at,at).expand(StarPlatinumRules.REACH_BIG+2),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity))){
            Vec3d d=e.getPos().subtract(at);double ahead=d.dotProduct(f),half=e.getWidth()*.5;
            if(!overLane(ahead,d.dotProduct(right),d.y,half)||!p.canSee(e))continue;
            if(e instanceof PlayerEntity other&&!p.shouldDamagePlayer(other))continue;
            if(StarPlatinumBeatdown.involved(e)||StarPlatinumTimeStop.stopped(e)||e instanceof ServerPlayerEntity sp&&CombatManager.cinematic(sp))continue;
            if(ahead-half<best){best=ahead-half;first=e;}
        }
        return first;
    }
    /** The opening punch lands on the first one in its way: a guard up breaks and nothing more comes of it; none up, the cutscene begins. */
    private static void strike(ServerPlayerEntity p,Open s,long now){
        s.landed=true;
        var world=p.getServerWorld();var e=first(p,s);
        if(e==null){send(p,s,LANDED,null,false);return;}
        if(CombatManager.breakGuard(e,StarPlatinumRules.HEAVY_BREAK)){send(p,s,LANDED,e,true);return;}
        var source=world.getDamageSources().playerAttack(p);
        // (Lee So-sang's H's dodge keeps its blow from her, as any other's: no cutscene)
        if(e.isInvulnerableTo(source)||dev.sangwoo.celestialfall.isosang.IsosangAttack.dodges(e,source)||!begin(p,e,s.start,s.yaw)){send(p,s,LANDED,e,false);return;}
        p.onAttacking(e);
        // it lands without harm (the cutscene's blows deal it): a hit all the same, and the count is held open to its landing
        dev.sangwoo.celestialfall.combat.HitCounter.struck(p,e);
        dev.sangwoo.celestialfall.combat.HitCounter.hold(p,BEAT_LAND-BEAT_CUT);
        world.playSound(null,e.getX(),e.getY(),e.getZ(),net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK,net.minecraft.sound.SoundCategory.PLAYERS,.9f,.8f);
    }

    /** The opening punch struck {@code victim} (no guard up): the cutscene begins. Returns whether it did (it does not for someone already in one). */
    private static boolean begin(ServerPlayerEntity user,LivingEntity victim,long start,float yaw){
        if(StarPlatinumBeatdown.involved(user)||StarPlatinumBeatdown.involved(victim))return false;
        dev.sangwoo.celestialfall.boss.Bosses.provoke(victim,user);
        var world=user.getServerWorld();long now=world.getTime();
        Vec3d f=Vec3d.fromPolar(0,yaw),at=user.getPos();
        // held on the blow's line, knocked back a little from where they stood (room for the stand between)
        Vec3d to=at.add(f.multiply(beatHold(victim.getPos().subtract(at).dotProduct(f)))).withAxis(net.minecraft.util.math.Direction.Axis.Y,victim.getY());
        Vec3d hold=victim.getPos();
        for(int k=8;k>=0;k--){Vec3d p=victim.getPos().lerp(to,k/8.0);if(fits(world,victim,p)){hold=p;break;}}
        // up in the air through the rush, as high as there is room; driven on along the line by its blows; then up
        // again on the "WRYYY", as high as there is room
        double lift=0;
        for(double d=.25;d<=BEAT_LIFT+1e-6;d+=.25){if(!fits(world,victim,hold.add(0,d,0)))break;lift=d;}
        double drift=0;
        for(double d=.25;d<=BEAT_DRIFT+1e-6;d+=.25){if(!fits(world,victim,hold.add(f.multiply(d)).add(0,lift,0)))break;drift=d;}
        Vec3d on=hold.add(f.multiply(drift));
        double high=lift;
        for(double d=lift+.25;d<=BEAT_HIGH+1e-6;d+=.25){if(!fits(world,victim,on.add(0,d,0)))break;high=d;}
        Vec3d launch=on.add(0,high,0);
        double reach=0;
        for(double d=.25;d<=BEAT_FLY+1e-6;d+=.25){if(!fits(world,victim,launch.add(f.multiply(d))))break;reach=d;}
        Vec3d over=launch.add(f.multiply(Math.max(0,reach-.3)));
        var ground=world.raycast(new RaycastContext(over,over.add(0,-40,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,victim));
        Vec3d land=ground.getType()==HitResult.Type.MISS?over.add(0,-40,0):ground.getPos();
        if(!fits(world,victim,land))land=over;
        float damage=beatdownDamage(victim.getHealth(),victim.getMaxHealth());
        var b=new Beat(user,victim,start,yaw,hold,drift,lift,high,land,damage);
        BY_USER.put(user.getUuid(),b);BY_VICTIM.put(victim.getUuid(),b);
        // whatever either was doing stops: they are held (KagenoManager.frozen) and the one struck faces its user
        CombatManager.interrupt(victim);HitStun.release(victim.getUuid());
        if(victim instanceof ServerPlayerEntity sp){
            StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);StarPlatinumTimeStop.struck(sp);
            GoldExperienceOverdrive.struck(sp);GoldExperienceHeal.struck(sp);struck(sp);
            KagenoManager.disable(sp);sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();
        }
        if(victim instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        float back=yaw+180;victim.setYaw(back);victim.setHeadYaw(back);victim.setBodyYaw(back);
        user.setYaw(yaw);user.setHeadYaw(yaw);user.setBodyYaw(yaw);
        user.clearActiveItem();user.setSprinting(false);if(user.currentScreenHandler!=user.playerScreenHandler)user.closeHandledScreen();
        KagenoManager.freeze(user);KagenoManager.freeze(victim);
        tell(b,START,now,false);
        return true;
    }
    private static boolean fits(ServerWorld world,LivingEntity e,Vec3d p){
        double half=e.getWidth()*.5;
        return world.isSpaceEmpty(e,new Box(p.x-half,p.y+.01,p.z-half,p.x+half,p.y+e.getHeight(),p.z+half));
    }
    /**
     * One of the cutscene's blows: its share of the damage (the last, all that is left of its share), none taking the
     * one struck below one health: what is more than they had is the fall's ({@link #land}). The rush's land quietly
     * (the recording carries them), the last with a cry. A blow that finds them at one health still shows as one.
     */
    private static void blow(Beat b,long age,float share){
        var v=b.victim;boolean last=age==BEAT_FINAL;
        float factor=Math.max(.01f,dev.sangwoo.celestialfall.xchara.XCharaWeaken.factor(b.user,v));
        float amount=Math.min(last?b.damage-b.dealt:b.damage*share,(v.getHealth()-1)/factor);
        if(amount>0)deal(b,amount,!last);
        else if(v.isAlive()){
            b.world.sendEntityDamage(v,b.world.getDamageSources().playerAttack(b.user));
            dev.sangwoo.celestialfall.combat.HitCounter.struck(b.user,v);
        }
        if(last){
            b.user.onAttacking(v);
            // its user's own hit feedback: the whole beatdown's damage, once
            if(ServerPlayNetworking.canSend(b.user,KagenoManager.DAMAGE)){var buf=PacketByteBufs.create();buf.writeVarInt(v.getId());buf.writeFloat(b.damage);buf.writeFloat(b.yaw);ServerPlayNetworking.send(b.user,KagenoManager.DAMAGE,buf);}
        }
    }
    private static void deal(Beat b,float amount,boolean quietly){
        var v=b.victim;var source=b.world.getDamageSources().playerAttack(b.user);
        LivingEntity previous=BLOW.get();BLOW.set(v);
        try{
            v.timeUntilRegen=0;
            boolean landed=quietly?CombatManager.damageQuietly(v,source,amount):CombatManager.damageWithoutKnockback(v,source,amount);
            if(landed)b.dealt+=amount;
        }finally{if(previous==null)BLOW.remove();else BLOW.set(previous);}
    }
    /** The one struck comes down where the last blow sent them: put there, the fall deals what is left of the damage (killing them there if it was more than they had). */
    private static void land(Beat b){
        b.down=true;put(b);
        float rest=b.damage-b.dealt;
        if(rest>.001f)deal(b,rest,false);
        if(!b.victim.isAlive()){b.killed=true;BY_VICTIM.remove(b.victim.getUuid());}
    }
    private static void put(Beat b){
        var v=b.victim;float back=b.yaw+180;
        if(v instanceof ServerPlayerEntity sp)sp.networkHandler.requestTeleport(b.land.x,b.land.y,b.land.z,back,0);
        else{v.refreshPositionAndAngles(b.land.x,b.land.y,b.land.z,back,0);v.setHeadYaw(back);v.setBodyYaw(back);}
        v.setVelocity(Vec3d.ZERO);v.fallDistance=0;v.velocityModified=true;
    }
    /** The cutscene ends: done (the one struck down where they came down) or cut short. Its cooldown runs from now. */
    private static void end(Beat b,long now,boolean cut){
        BY_USER.remove(b.user.getUuid());if(BY_VICTIM.get(b.victim.getUuid())==b)BY_VICTIM.remove(b.victim.getUuid());
        if(cut)dev.sangwoo.celestialfall.combat.HitCounter.release(b.user);
        var v=b.victim;
        if(!cut&&!b.down&&v.isAlive()&&!v.isRemoved()&&v.getWorld()==b.world)put(b);
        var s=OPENS.get(b.user.getUuid());
        if(s!=null){s.start=Long.MIN_VALUE/4;s.landed=true;s.ready=NoCooldownState.enabled(b.user)?now:beatReady(now);}
        tell(b,END,now,cut);
    }

    /** Tells every player near the user (and the user, and whoever it met) of the opening punch. */
    private static void send(ServerPlayerEntity p,Open s,int type,LivingEntity whom,boolean broke){
        var world=p.getServerWorld();long now=world.getTime();
        float aim=Float.NaN;
        if(type==OPEN){var e=first(p,s);if(e!=null)aim=(float)(e.getPos().subtract(p.getPos()).dotProduct(Vec3d.fromPolar(0,s.yaw))-e.getWidth()*.5);}
        for(var viewer:world.getPlayers()){
            if(viewer!=p&&viewer!=whom&&viewer.squaredDistanceTo(p)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var b=PacketByteBufs.create();
            b.writeByte(type);b.writeUuid(p.getUuid());b.writeIdentifier(world.getRegistryKey().getValue());
            b.writeLong(s.start);b.writeLong(now);b.writeFloat(s.yaw);b.writeLong(s.ready);
            b.writeFloat(aim);b.writeInt(whom==null?-1:whom.getId());b.writeBoolean(broke);
            ServerPlayNetworking.send(viewer,EVENT,b);
        }
    }
    /** Tells every player near the user (and the user, and the one struck) of the cutscene: it starts, it ends. */
    private static void tell(Beat b,int type,long now,boolean cut){
        var u=b.user;var s=OPENS.get(u.getUuid());
        for(var viewer:b.world.getPlayers()){
            if(viewer!=u&&viewer!=b.victim&&viewer.squaredDistanceTo(u)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeByte(type);buf.writeUuid(u.getUuid());buf.writeIdentifier(b.world.getRegistryKey().getValue());
            buf.writeLong(b.start);buf.writeLong(now);buf.writeFloat(b.yaw);buf.writeLong(s==null?0:s.ready);
            buf.writeInt(b.victim.getId());buf.writeUuid(b.victim.getUuid());
            vec(buf,b.from);vec(buf,b.victimFrom);vec(buf,b.hold);buf.writeDouble(b.drift);buf.writeDouble(b.lift);buf.writeDouble(b.high);vec(buf,b.land);
            buf.writeFloat(b.damage);buf.writeBoolean(cut);
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf buf,Vec3d v){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);}
}
