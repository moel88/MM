package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
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
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import static dev.sangwoo.celestialfall.stand.StarPlatinumRules.*;

/**
 * Star Platinum's beatdown's cutscene (2.64.0; StarPlatinumRules for its timing): begun when the beatdown's blow, G,
 * strikes someone with no guard up (StarPlatinumAttack). Its user and the one struck are held where they are all
 * through it (KagenoManager.frozen: neither moves, nor acts) and nothing harms them but its own blows; it deals its
 * damage (StarPlatinumRules.beatdownDamage, worked out from their health as it begins) at its blows' ticks, none of
 * which takes them below one health. As they come down (BEAT_LAND) they are put where its last blow sent them (worked
 * out as it begins: as far along the blow as there is room, down to the ground there), and the fall deals what is
 * left of its damage: if it was more than they had, it is the fall that kills them, where their body lies (the
 * cutscene runs on to its end for its user). Everything in between, where the one struck is shown, the stand, the
 * camera, the bars, the sound, is the clients' to draw from the beatdown's clock and what the start told them
 * (client/stand/StandBeatdown). Every viewer within 128 blocks (and the one struck) is told when it starts and when
 * it ends (stand_beatdown_event_v1); it ends early if its user or the one struck dies (but by its fall), leaves or is
 * gone, or its user can no longer have the stand out. Its cooldown runs from its end.
 */
public final class StarPlatinumBeatdown {
    public static final Identifier EVENT=CelestialFall.id("stand_beatdown_event_v1");
    /** It starts; it ends (done, or cut short). */
    public static final int START=0,END=1;
    /**
     * A cutscene: its user and the one struck, when the beatdown began (G), the way the blow went, where its user
     * stands, where the one struck stood, where they are held, how far its blows drive them on before the last, where
     * they come down, its damage and what it has dealt.
     */
    private static final class Beat {
        final ServerPlayerEntity user;final LivingEntity victim;final ServerWorld world;final long start;final float yaw;
        final Vec3d from,victimFrom,hold,land;final double drift;final float damage;float dealt;
        /** Whether they have come down where they land, and whether the fall killed them. */
        boolean down,killed;
        Beat(ServerPlayerEntity user,LivingEntity victim,long start,float yaw,Vec3d hold,double drift,Vec3d land,float damage){
            this.user=user;this.victim=victim;world=user.getServerWorld();this.start=start;this.yaw=yaw;from=user.getPos();victimFrom=victim.getPos();
            this.hold=hold;this.drift=drift;this.land=land;this.damage=damage;
        }
    }
    private static final Map<UUID,Beat> BY_USER=new HashMap<>(),BY_VICTIM=new HashMap<>();
    /** Set while a cutscene's own blow is dealt (to its victim): the only harm that reaches them. */
    private static final ThreadLocal<LivingEntity> BLOW=new ThreadLocal<>();
    private StarPlatinumBeatdown(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(StarPlatinumBeatdown::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var p=handler.player;var b=BY_USER.get(p.getUuid());if(b==null)b=BY_VICTIM.get(p.getUuid());
            if(b!=null)end(b,b.world.getTime(),true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BY_USER.clear();BY_VICTIM.clear();});
    }
    /** Whether this one is in a beatdown's cutscene now (its user, or the one struck). */
    public static boolean involved(LivingEntity e){return e!=null&&!e.getWorld().isClient&&(BY_USER.containsKey(e.getUuid())||BY_VICTIM.containsKey(e.getUuid()));}
    /** Whether this player is a beatdown's user, its cutscene going on now. */
    public static boolean casting(LivingEntity e){return e!=null&&!e.getWorld().isClient&&BY_USER.containsKey(e.getUuid());}
    /** Whether harm from {@code source} is kept from {@code e}: it is in a cutscene and this is not its own blow (what bypasses invulnerability, /kill or the void, still reaches them). */
    public static boolean shielded(LivingEntity e,DamageSource source){
        return involved(e)&&BLOW.get()!=e&&!source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * The beatdown's blow struck {@code victim} (no guard up), {@code start} the tick G began it, {@code yaw} its way:
     * the cutscene begins. Returns whether it did (it does not for someone already in one).
     */
    public static boolean begin(ServerPlayerEntity user,LivingEntity victim,long start,float yaw){
        if(involved(user)||involved(victim))return false;
        var world=user.getServerWorld();long now=world.getTime();
        Vec3d f=Vec3d.fromPolar(0,yaw),at=user.getPos();
        // held on the blow's line, knocked back a little from where they stood (room for the stand between): as far
        // out as there is room for them
        Vec3d to=at.add(f.multiply(beatHold(victim.getPos().subtract(at).dotProduct(f)))).withAxis(net.minecraft.util.math.Direction.Axis.Y,victim.getY());
        Vec3d hold=victim.getPos();
        for(int k=8;k>=0;k--){Vec3d p=victim.getPos().lerp(to,k/8.0);if(fits(world,victim,p)){hold=p;break;}}
        // driven on by its blows, up in the air (less if something is in the way), then sent flying from there
        double drift=0;
        for(double d=.25;d<=BEAT_DRIFT+1e-6;d+=.25){if(!fits(world,victim,hold.add(f.multiply(d)).add(0,BEAT_LIFT,0)))break;drift=d;}
        Vec3d launch=hold.add(f.multiply(drift)).add(0,BEAT_LIFT,0);
        double reach=0;
        for(double d=.25;d<=BEAT_FLY+1e-6;d+=.25){if(!fits(world,victim,launch.add(f.multiply(d))))break;reach=d;}
        Vec3d over=launch.add(f.multiply(Math.max(0,reach-.3)));
        // down to the ground there (as far as 24 blocks down: further, they fall the rest once it is over)
        var ground=world.raycast(new RaycastContext(over,over.add(0,-24,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,victim));
        Vec3d land=ground.getType()==HitResult.Type.MISS?over.add(0,-24,0):ground.getPos();
        if(!fits(world,victim,land))land=over;
        float damage=beatdownDamage(victim.getHealth(),victim.getMaxHealth());
        var b=new Beat(user,victim,start,yaw,hold,drift,land,damage);
        BY_USER.put(user.getUuid(),b);BY_VICTIM.put(victim.getUuid(),b);
        // whatever either was doing stops: they are held (KagenoManager.frozen) and the one struck faces its user
        CombatManager.interrupt(victim);HitStun.release(victim.getUuid());
        StarPlatinumRush.struck(user);StarPlatinumInhale.struck(user);
        if(victim instanceof ServerPlayerEntity sp){StarPlatinumAttack.struck(sp);StarPlatinumRush.struck(sp);StarPlatinumInhale.struck(sp);StarPlatinumTimeStop.struck(sp);KagenoManager.disable(sp);sp.clearActiveItem();sp.setSprinting(false);if(sp.currentScreenHandler!=sp.playerScreenHandler)sp.closeHandledScreen();}
        if(victim instanceof MobEntity mob){mob.getNavigation().stop();mob.setTarget(null);}
        float back=yaw+180;victim.setYaw(back);victim.setHeadYaw(back);victim.setBodyYaw(back);
        user.setYaw(yaw);user.setHeadYaw(yaw);user.setBodyYaw(yaw);
        user.clearActiveItem();user.setSprinting(false);if(user.currentScreenHandler!=user.playerScreenHandler)user.closeHandledScreen();
        KagenoManager.freeze(user);KagenoManager.freeze(victim);
        send(b,START,now,false);
        return true;
    }
    /** Whether {@code e}'s body fits at {@code p} (its feet). */
    private static boolean fits(ServerWorld world,LivingEntity e,Vec3d p){
        double half=e.getWidth()*.5;
        return world.isSpaceEmpty(e,new Box(p.x-half,p.y+.01,p.z-half,p.x+half,p.y+e.getHeight(),p.z+half));
    }
    private static void tick(MinecraftServer server){
        if(BY_USER.isEmpty())return;
        for(var b:new ArrayList<>(BY_USER.values())){
            long now=b.world.getTime(),age=now-b.start;
            var u=b.user;var v=b.victim;
            boolean userGone=u.isRemoved()||!u.isAlive()||u.getServerWorld()!=b.world||u.isSpectator()||!StarPlatinumAttack.punches(u);
            boolean victimGone=!b.killed&&(v.isRemoved()||!v.isAlive()||v.getWorld()!=b.world||v.isSpectator());
            if(userGone||victimGone){end(b,now,true);continue;}
            KagenoManager.freeze(u);if(!b.killed){KagenoManager.freeze(v);v.fallDistance=0;}
            float share=beatShare(age);
            if(share>0)blow(b,age,share);
            if(age>=BEAT_LAND&&!b.down)land(b);
            if(age>=BEAT_END)end(b,now,false);
        }
    }
    /**
     * One of the cutscene's blows: its share of the damage (the last, all that is left of it), none taking the one
     * struck below one health: what is more than they had is the fall's (land). The rush's land quietly (its sound is
     * in the recording), the others with a cry. A blow that finds them at one health still shows as one (they flash
     * red as every blow before made them: in 2.64.0's multiplayer check, one struck at 50 health stopped flashing at the
     * end of the rush, through the blows after it).
     */
    private static void blow(Beat b,long age,float share){
        var v=b.victim;boolean last=age==BEAT_FINAL;
        float amount=Math.min(last?b.damage-b.dealt:b.damage*share,v.getHealth()-1);
        boolean rush=age>=BEAT_RUSH_FIRST&&age<=BEAT_RUSH_LAST;
        if(amount>0)deal(b,amount,rush||!last&&age!=BEAT_UPPERCUT);
        else if(v.isAlive())b.world.sendEntityDamage(v,b.world.getDamageSources().playerAttack(b.user));
        if(last){
            b.user.onAttacking(v);
            // its user's own hit feedback: the whole beatdown's damage, once
            if(ServerPlayNetworking.canSend(b.user,KagenoManager.DAMAGE)){var buf=PacketByteBufs.create();buf.writeVarInt(v.getId());buf.writeFloat(b.damage);buf.writeFloat(b.yaw);ServerPlayNetworking.send(b.user,KagenoManager.DAMAGE,buf);}
        }
    }
    /** Deals {@code amount} of the beatdown's damage to the one struck, past every guard but its own (quietly: no cry). */
    private static void deal(Beat b,float amount,boolean quietly){
        var v=b.victim;var source=b.world.getDamageSources().playerAttack(b.user);
        LivingEntity previous=BLOW.get();BLOW.set(v);
        try{
            v.timeUntilRegen=0;
            boolean landed=quietly?CombatManager.damageQuietly(v,source,amount):CombatManager.damageWithoutKnockback(v,source,amount);
            if(landed)b.dealt+=amount;
        }finally{if(previous==null)BLOW.remove();else BLOW.set(previous);}
    }
    /**
     * The one struck comes down where the last blow sent them: they are put there (where every client shows them
     * already), and the fall deals what is left of the beatdown's damage, killing them if it was more than they had
     * (there, where their body lies). Killed, they are no longer held: its user's cutscene runs on to its end.
     */
    private static void land(Beat b){
        b.down=true;put(b);
        float rest=b.damage-b.dealt;
        if(rest>.001f)deal(b,rest,false);
        if(!b.victim.isAlive()){b.killed=true;BY_VICTIM.remove(b.victim.getUuid());}
    }
    /** Puts the one struck down where they land, facing its user. */
    private static void put(Beat b){
        var v=b.victim;float back=b.yaw+180;
        if(v instanceof ServerPlayerEntity sp)sp.networkHandler.requestTeleport(b.land.x,b.land.y,b.land.z,back,0);
        else{v.refreshPositionAndAngles(b.land.x,b.land.y,b.land.z,back,0);v.setHeadYaw(back);v.setBodyYaw(back);}
        v.setVelocity(Vec3d.ZERO);v.fallDistance=0;v.velocityModified=true;
    }
    /** The cutscene ends: done (the one struck down where they came down) or cut short. Its cooldown runs from now. */
    private static void end(Beat b,long now,boolean cut){
        BY_USER.remove(b.user.getUuid());if(BY_VICTIM.get(b.victim.getUuid())==b)BY_VICTIM.remove(b.victim.getUuid());
        var v=b.victim;
        if(!cut&&!b.down&&v.isAlive()&&!v.isRemoved()&&v.getWorld()==b.world)put(b);
        StarPlatinumAttack.beatdownOver(b.user,now);
        send(b,END,now,cut);
    }
    /** Tells every player near the user (and the user, and the one struck) of the event. */
    private static void send(Beat b,int type,long now,boolean cut){
        var u=b.user;
        for(var viewer:b.world.getPlayers()){
            if(viewer!=u&&viewer!=b.victim&&viewer.squaredDistanceTo(u)>128*128)continue;
            if(!ServerPlayNetworking.canSend(viewer,EVENT))continue;
            var buf=PacketByteBufs.create();
            buf.writeUuid(u.getUuid());buf.writeIdentifier(b.world.getRegistryKey().getValue());buf.writeByte(type);
            buf.writeLong(b.start);buf.writeLong(now);buf.writeFloat(b.yaw);
            buf.writeInt(b.victim.getId());buf.writeUuid(b.victim.getUuid());
            vec(buf,b.from);vec(buf,b.victimFrom);vec(buf,b.hold);buf.writeDouble(b.drift);vec(buf,b.land);
            buf.writeFloat(b.damage);buf.writeBoolean(cut);
            ServerPlayNetworking.send(viewer,EVENT,buf);
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf buf,Vec3d v){buf.writeDouble(v.x);buf.writeDouble(v.y);buf.writeDouble(v.z);}
}
