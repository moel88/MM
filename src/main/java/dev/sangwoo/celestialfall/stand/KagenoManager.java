package dev.sangwoo.celestialfall.stand;

import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import java.util.*;
import java.util.function.Predicate;
import static dev.sangwoo.celestialfall.stand.KagenoRules.*;

/** Server owns casts, hold duration, target membership, damage and all movement locks. */
public final class KagenoManager {
    public static final Identifier INPUT=CelestialFall.id("kageno_input_v1"),CAST=CelestialFall.id("kageno_cast_v1"),STOP=CelestialFall.id("kageno_stop_v1"),STATUS=CelestialFall.id("kageno_status_v1"),HITS=CelestialFall.id("kageno_hits_v1"),DAMAGE=CelestialFall.id("kageno_damage_v1");
    public static final class Cast {
        public final UUID id=UUID.randomUUID();public final ServerPlayerEntity player;public final ServerWorld world;
        public final Vec3d origin;public final float yaw,pitch;public final int skill,combo;public final long start;
        public final Map<LivingEntity,Vec3d> targets=new LinkedHashMap<>();public final Set<UUID> viewers=new HashSet<>();
        /**
         * T+: the first one a fire slash struck (where the dash goes) and where it was held, how far up the line each
         * slash got (it ends on the first one it reaches), and the dash's planned spot.
         */
        LivingEntity mark;Vec3d markAt;final double[] slashEnd={RUSH_RANGE,RUSH_RANGE,RUSH_RANGE};Vec3d dash;
        Cast(ServerPlayerEntity p,int skill,int combo){this.player=p;world=p.getServerWorld();origin=p.getPos();yaw=p.getYaw();pitch=p.getPitch();this.skill=skill;this.combo=combo;start=world.getTime();}
        public int age(){return (int)(world.getTime()-start);}
    }
    private static final class State {
        final ServerWorld world;State(ServerPlayerEntity p){world=p.getServerWorld();}
        final long[] ready=new long[9];long press=-1,pulse=-1,lastCombo=-100,overUntil,lastHit=-1000,poseReady;int combo,window,packets,hits;float boost;
    }
    private record Mark(ServerPlayerEntity owner,LivingEntity target,long until){}
    private static final Map<ServerPlayerEntity,State> STATES=new HashMap<>();
    private static final Map<UUID,Cast> CASTS=new LinkedHashMap<>();
    private static final Map<UUID,Mark> MARKS=new HashMap<>();
    private static final UUID R_SLOW=UUID.fromString("126f3481-4544-4737-ae86-94f61a1e6230");
    private static Predicate<LivingEntity> clientFrozen=e->false;
    private KagenoManager(){}
    public static Collection<Cast> casts(){return Collections.unmodifiableCollection(CASTS.values());}
    public static void setClientFrozen(Predicate<LivingEntity> value){clientFrozen=value;}
    public static void initialize(){
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,p,h,b,r)->{
            if(b.readableBytes()!=1)return;int action=b.readUnsignedByte();if(action>POSE_CANCEL)return;
            server.execute(()->{
                var s=STATES.computeIfAbsent(p,k->new State(k));int win=server.getTicks()/20;
                if(s.window!=win){s.window=win;s.packets=0;}if(++s.packets>50)return;
                input(p,action);
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(KagenoManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->leave(h.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{CASTS.clear();STATES.clear();MARKS.clear();});
    }
    private static boolean valid(ServerPlayerEntity p){return MapGate.enabled(p.getWorld())&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&!p.isSleeping()&&!p.hasVehicle()&&ProgressManager.canUseKageno(p);}
    public static boolean busy(PlayerEntity p){
        if(!MapGate.enabled(p.getWorld()))return false;
        if(frozen(p))return true;
        for(var c:CASTS.values())if(c.player==p&&c.age()<inputEnd(c.skill))return true;
        return false;
    }
    public static boolean frozen(LivingEntity e){
        if(!MapGate.enabled(e.getWorld())||!e.isAlive()||e.isRemoved())return false;
        if(e.getWorld().isClient)return clientFrozen.test(e);
        if(e instanceof PlayerEntity p&&AtomicManager.immobilized(p))return true;
        for(var c:CASTS.values())if(c.world==e.getWorld()&&(KagenoRules.frozen(c.skill,c.age())&&((c.player==e&&c.skill!=R)||c.targets.containsKey(e))||KagenoRules.trapped(c.skill,c.age())&&c.targets.containsKey(e)))return true;
        return false;
    }
    public static void freeze(LivingEntity e){e.setVelocity(Vec3d.ZERO);e.fallDistance=0;e.setSprinting(false);e.clearActiveItem();}
    public static boolean input(ServerPlayerEntity p,int action){
        var s=STATES.computeIfAbsent(p,k->new State(k));long now=p.getServerWorld().getTime();
        if(action==CANCEL){s.press=-1;return true;}
        // Movement may release an emote, but must never toggle a new emote on.
        if(action==POSE_CANCEL){
            for(var c:new ArrayList<>(CASTS.values()))if(c.player==p&&c.skill==P)stop(c);
            return true;
        }
        if(action==PULSE){if(s.press>=0)s.pulse=now;return s.press>=0;}
        if(action==T_UP){
            if(s.press<0)return false;long held=now-s.press;s.press=-1;
            return begin(p,held>=HOLD?RUSH:T);
        }
        // P is an emote: pressing it again releases the pose; any other skill input releases it first.
        if(action==POSE){
            for(var c:new ArrayList<>(CASTS.values()))if(c.player==p&&c.skill==P){stop(c);return true;}
            if(!valid(p)||busy(p)||CombatManager.busy(p)||CombatManager.cinematic(p)||now<s.poseReady)return false;
            var c=new Cast(p,P,0);CASTS.put(c.id,c);s.poseReady=now+COOLDOWN[P];
            p.clearActiveItem();p.setSprinting(false);p.setVelocity(0,p.getVelocity().y,0);sync(c);return true;
        }
        if(action!=Q)for(var c:new ArrayList<>(CASTS.values()))if(c.player==p&&c.skill==P)stop(c);
        if(!valid(p)||busy(p)||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        if(action==T_DOWN){if(s.press<0&&ready(p,s,T,now)){s.press=s.pulse=now;return true;}return false;}
        if(action==G)return AtomicManager.cast(p);
        if(action==Q){
            if(s.overUntil>now||!ready(p,s,Q,now))return false;
            s.overUntil=now+600;s.boost=.05f;s.ready[Q]=now+COOLDOWN[Q];status(p,s);return true;
        }
        if(action<0||action>H)return false;
        s.press=-1;return begin(p,action);
    }
    private static boolean ready(ServerPlayerEntity p,State s,int skill,long now){return NoCooldownState.enabled(p)||now>=s.ready[skill==RUSH?T:skill];}
    private static boolean begin(ServerPlayerEntity p,int skill){
        if(!valid(p)||busy(p)||CombatManager.busy(p)||CombatManager.cinematic(p))return false;
        var s=STATES.computeIfAbsent(p,k->new State(k));long now=p.getWorld().getTime();
        if(!ready(p,s,skill,now))return false;
        if(skill==M1){s.combo=now-s.lastCombo>36||s.combo>=4?1:s.combo+1;s.lastCombo=now;}
        var c=new Cast(p,skill,s.combo);
        if(skill==R||skill==H){
            double radius=skill==R?STOP_RADIUS:HIGHEST_RADIUS;
            for(var e:near(c.world,c.origin,radius,p,false)){
                c.targets.put(e,e.getPos());CombatManager.interrupt(e);if(e instanceof PlayerEntity target)cancelCaster(target);
            }
        }
        CASTS.put(c.id,c);if(skill==R)rSlow(p,true);s.ready[skill==RUSH?T:skill]=now+(skill==M1?(s.combo==4?24:8):COOLDOWN[skill]);
        p.clearActiveItem();p.setSprinting(false);p.swingHand(Hand.MAIN_HAND,true);CombatManager.interrupt(p);
        sync(c);status(p,s);
        return true;
    }
    private static List<LivingEntity> near(ServerWorld world,Vec3d origin,double radius,ServerPlayerEntity p,boolean sight){
        return world.getEntitiesByClass(LivingEntity.class,new Box(origin,origin).expand(radius+1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&e.canHit()
            &&!(e instanceof dev.sangwoo.celestialfall.commerce.MerchantEntity)&&inside(e.getPos().squaredDistanceTo(origin),radius)&&(!sight||p.canSee(e)));
    }
    public static List<LivingEntity> radiusTargets(ServerPlayerEntity p,double radius){return near(p.getServerWorld(),p.getPos(),radius,p,false);}
    /** Point in the cast frame: forward, lateral (character's right) and height relative to the origin. */
    private static Vec3d local(Cast c,Vec3d p){Vec3d f=Vec3d.fromPolar(0,c.yaw),right=new Vec3d(-f.z,0,f.x),d=p.subtract(c.origin);return new Vec3d(d.dotProduct(f),d.dotProduct(right),d.y);}
    private static List<LivingEntity> strikeTargets(Cast c){
        int age=c.age();
        // R's release: the time-stopped targets and whoever stands in the band it explodes along.
        if(c.skill==R&&age==R_RELEASE){
            var out=new LinkedHashSet<>(c.targets.keySet());
            out.addAll(near(c.world,c.origin.add(Vec3d.fromPolar(0,c.yaw).multiply(3.5)),12,c.player,false).stream().filter(e->{var l=local(c,e.getPos());return rBand(l.x,l.y,l.z);}).toList());
            return new ArrayList<>(out);
        }
        if(c.skill==R||c.skill==H)return new ArrayList<>(c.targets.keySet());
        // T: the ≈6×6 character-height fire field, biased forward and to the sword side.
        if(c.skill==T)return near(c.world,c.origin.add(Vec3d.fromPolar(0,c.yaw).multiply(3)),9,c.player,false).stream().filter(e->{var l=local(c,e.getPos());return l.x>=-2.6&&l.x<=8.6&&l.y>=-4.6&&l.y<=6.4&&l.z>=-2&&l.z<=7;}).toList();
        // T+: the arrival strike and the fire burst after it take whoever stands at the caster and the one the dash went
        // to; the spin cut everyone round him.
        if(c.skill==RUSH){
            double r=age==RUSH_FINISH?RUSH_SPIN_RADIUS:3;Vec3d at=c.player.getPos();
            // Right after the dash the server still sees the caster a tick or two short of where his client took him:
            // the arrival and the burst strike at the end of the planned dash (unless something stopped him well short).
            if(age<RUSH_FINISH&&c.dash!=null&&c.dash.squaredDistanceTo(at)<6*6)at=c.dash;
            var out=new LinkedHashSet<>(near(c.world,at,r,c.player,true));
            if(c.mark!=null&&c.mark.isAlive()&&c.mark.getWorld()==c.world&&c.mark.squaredDistanceTo(at)<=(r+1.5)*(r+1.5))out.add(c.mark);
            return new ArrayList<>(out);
        }
        boolean field=c.skill==T||c.skill==Y;Vec3d center=c.skill==T?c.origin:c.player.getPos();double r=field?4:c.skill==RUSH?3.2:3.4;
        Vec3d facing=Vec3d.fromPolar(0,c.yaw);
        if(c.skill==E){Vec3d right=new Vec3d(facing.z,0,-facing.x);return near(c.world,center,r,c.player,true).stream().filter(e->{Vec3d offset=e.getPos().subtract(center);return thrustLane(offset.dotProduct(facing),offset.dotProduct(right),offset.y,e.getWidth()*.5);}).toList();}
        return near(c.world,center,r,c.player,true).stream().filter(e->field||e.getPos().subtract(center).multiply(1,0,1).normalize().dotProduct(facing)>-.05).toList();
    }
    private static void tick(MinecraftServer server){
        for(var p:new ArrayList<>(STATES.keySet()))if(!valid(p)||server.getPlayerManager().getPlayer(p.getUuid())!=p||STATES.get(p).world!=p.getWorld())leave(p);
        for(var entry:new ArrayList<>(STATES.entrySet())){
            var p=entry.getKey();var s=entry.getValue();long now=p.getWorld().getTime();
            if(s.press>=0){if(now-s.pulse>6||busy(p)){s.press=-1;}else if(now-s.press>=HOLD){s.press=-1;begin(p,RUSH);}}
            if(s.overUntil>0&&now>=s.overUntil){s.overUntil=0;s.boost=0;status(p,s);}
            // Passive 1 (초재생): 1 HP per second, applied as 0.5 every half second like the review's counter.
            if(now%10==0&&!frozen(p))p.heal(.5f);
            if(now%10==0)status(p,s);
        }
        // Owners with no casts still regenerate, and are initialized after equip/join.
        for(var p:server.getPlayerManager().getPlayerList())if(valid(p))STATES.computeIfAbsent(p,k->new State(k));
        MARKS.values().removeIf(m->!valid(m.owner)||!m.target.isAlive()||m.owner.getWorld()!=m.target.getWorld()||m.until<=m.target.getWorld().getTime());
        for(var c:new ArrayList<>(CASTS.values())){
            if(!CASTS.containsKey(c.id))continue;
            if(!valid(c.player)||c.player.getWorld()!=c.world||c.age()>=DURATION[c.skill]||c.age()<0){stop(c);continue;}
            // T+'s mark stays first among the targets, alive or not: the caster's client dashes to where it was held.
            c.targets.keySet().removeIf(e->e!=c.mark&&(!e.isAlive()||e.isRemoved()||e.getWorld()!=c.world||e instanceof ServerPlayerEntity p&&server.getPlayerManager().getPlayer(p.getUuid())!=p));
            int age=c.age();if(c.skill==R){rSlow(c.player,true);c.player.setSprinting(false);}
            if(KagenoRules.frozen(c.skill,age)){
                if(c.skill!=R)hold(c.player,c.origin,c.yaw,c.pitch);
                for(var e:c.targets.entrySet())hold(e.getKey(),e.getValue(),e.getKey().getYaw(),e.getKey().getPitch());
            }
            // T+: the fire slashes fly up the line, and each ends on the first one it reaches (user request: it does not
            // cut through to whoever stands behind) and strikes them; the first one struck is the mark, where the dash goes.
            if(c.skill==RUSH&&age>=RUSH_SLASHES[0]&&age<=RUSH_ARRIVE){
                Vec3d f=Vec3d.fromPolar(0,c.yaw);boolean struck=false;
                for(int i=0;i<RUSH_SLASHES.length;i++){
                    double from=Math.max(0,rushSlash(i,age-1)),to=Math.min(c.slashEnd[i],rushSlash(i,age));if(to<0||age>RUSH_SLASHES[i]&&to<=from)continue;
                    LivingEntity first=null;double reach=0;
                    for(var e:near(c.world,c.origin.add(f.multiply((from+to)/2)),(to-from)/2+4,c.player,false)){var l=local(c,e.getPos());if(rushPath(l.x,l.y,l.z,from-.5,to+.5)&&(first==null||l.x<reach)){first=e;reach=l.x;}}
                    if(first==null)continue;
                    c.slashEnd[i]=Math.max(1,reach);hit(c,first,rushSlashDamage(i),age);
                    if(c.targets.containsKey(first)){struck=true;if(c.mark==null){c.mark=first;c.markAt=c.targets.get(first);}}
                }
                if(struck)sync(c);
            }
            // T+ dash: the caster's own client moves him (velocity packets sent every tick get merged when several reach
            // a client in one frame, and the dash fell short in multiplayer). The server plans the same path, from where
            // he stood to just short of the mark (where it was held) or to the end of the range, aimed again every tick,
            // for where the arrival strikes.
            if(c.skill==RUSH&&age>=RUSH_START&&age<RUSH_ARRIVE){
                int k=age-RUSH_START;if(c.dash==null)c.dash=c.player.getPos();
                if(c.mark!=null&&c.mark.isAlive()&&c.mark.getWorld()==c.world&&c.targets.containsKey(c.mark))c.markAt=c.targets.get(c.mark);
                Vec3d dest=rushDestination(c.origin,Vec3d.fromPolar(0,c.yaw),c.dash,c.markAt);
                c.dash=c.dash.add(dest.subtract(c.dash).multiply(1,0,1).multiply(rushStep(k)));
            }
            // Y: targets struck by the flash stand still until the launch, then rise straight up.
            if(c.skill==Y&&age==Y_FLASH)c.targets.clear();
            if(c.skill==Y&&age>=Y_LAUNCH&&age<Y_LAUNCH+16){
                for(var t:c.targets.keySet()){
                    int k=age-Y_LAUNCH;if(k>1&&t.isOnGround()||frozen(t))continue;
                    t.setVelocity(0,yLift(k),0);t.fallDistance=0;t.velocityModified=true;
                    if(t instanceof ServerPlayerEntity p)p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            }
            float amount=damage(c.skill,age,c.combo);
            if(amount>0){
                for(var target:strikeTargets(c))hit(c,target,amount,age);
            }
            // Hit 4's struck targets go out with the blow, so every viewer's trail follows the ones that fly.
            if(age%10==0||c.skill==Y&&age==Y_FLASH||c.skill==M1&&c.combo==4&&age==6&&!c.targets.isEmpty())sync(c);
        }
    }
    private static void hold(LivingEntity e,Vec3d pos,float yaw,float pitch){
        freeze(e);e.setPosition(pos);e.setYaw(yaw);e.setPitch(pitch);
        e.prevX=pos.x;e.prevY=pos.y;e.prevZ=pos.z;
        if(e instanceof ServerPlayerEntity p&&p.age%5==0){p.velocityModified=true;p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));}
    }
    private static void hit(Cast c,LivingEntity e,float amount,int age){
        if(!e.isAlive()||e.getWorld()!=c.world||e.isSpectator())return;
        if(e instanceof PlayerEntity other&&(!c.world.getServer().isPvpEnabled()||!c.player.shouldDamagePlayer(other)))return;
        var s=STATES.get(c.player);boolean over=s!=null&&s.overUntil>c.world.getTime();
        double multiplier=multiplier(c.world.isNight(),over?s.boost:0,false);
        var source=c.world.getDamageSources().playerAttack(c.player);
        boolean unblock=c.skill==R||c.skill==H||c.skill==Y||c.skill==E&&age==E_FINISH||c.skill==M1&&c.combo==4||c.skill==RUSH&&age==RUSH_FINISH;
        if(unblock)CombatManager.interrupt(e);
        e.timeUntilRegen=0;
        if(CombatManager.damageWithoutKnockback(e,source,(float)(amount*multiplier))){
            if(s!=null){long now=c.world.getTime();int n=KagenoRules.hits(c.skill,age);s.hits=now-s.lastHit>50?n:s.hits+n;s.lastHit=now;
                if(ServerPlayNetworking.canSend(c.player,HITS)){var b=PacketByteBufs.create();b.writeVarInt(s.hits);ServerPlayNetworking.send(c.player,HITS,b);}}
            // Attacker-side hit feedback (red cumulative number and blood), as in the review's dummy demos.
            if(ServerPlayNetworking.canSend(c.player,DAMAGE)){var b=PacketByteBufs.create();b.writeVarInt(e.getId());b.writeFloat((float)(amount*multiplier));b.writeFloat(c.yaw);ServerPlayNetworking.send(c.player,DAMAGE,b);}
            if((c.skill==T||c.skill==RUSH&&age<RUSH_FINISH)&&c.targets.putIfAbsent(e,e.getPos())==null)CombatManager.interrupt(e);
            // Overdrive: +0.01 damage multiplier per skill hit, a little more time, and the shadow mark
            // (lasting as long as the overdrive itself).
            if(over){long now=c.world.getTime();s.boost=Math.min(1,s.boost+.01f*KagenoRules.hits(c.skill,age));s.overUntil=Math.min(now+1800,s.overUntil+4);MARKS.put(e.getUuid(),new Mark(c.player,e,Math.max(now+200,s.overUntil)));}
            if(c.skill==Y&&(age==Y_STOMP||age==Y_FLASH)&&!frozen(e))c.targets.putIfAbsent(e,e.getPos());
            // M1 hit 4: the thrust sends whoever it strikes flying along it (2.37.0: no hold and no rising cut).
            if(c.skill==M1&&c.combo==4&&!frozen(e)){
                c.targets.put(e,e.getPos());
                e.setVelocity(Vec3d.fromPolar(0,c.yaw).multiply(M1_FLY).add(0,M1_FLY_UP,0));e.velocityModified=true;
                if(e instanceof ServerPlayerEntity p)p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }
            // R strikes without knockback (user request, 2.37.0): its targets stay where they stand when the release hits.
            // T+'s spin cut throws everyone it strikes out away from the caster.
            if(c.skill==RUSH&&age==RUSH_FINISH&&!frozen(e)){
                Vec3d away=e.getPos().subtract(c.player.getPos()).multiply(1,0,1);if(away.lengthSquared()<1e-4)away=Vec3d.fromPolar(0,c.yaw);
                e.setVelocity(away.normalize().multiply(.75).add(0,.32,0));e.velocityModified=true;
                if(e instanceof ServerPlayerEntity p)p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            }
        }
    }
    public static float outgoingDamage(ServerPlayerEntity p,float base){var s=STATES.get(p);float boost=s!=null&&s.overUntil>p.getWorld().getTime()?s.boost:0;return (float)(base*multiplier(p.getWorld().isNight(),boost,false));}
    /** Shadow marks amplify incoming damage once, including attacks from other players. */
    public static float incomingDamage(LivingEntity target,float amount){
        if(target.getWorld().isClient||!MapGate.enabled(target.getWorld()))return amount;
        var mark=MARKS.get(target.getUuid());
        return mark!=null&&mark.target==target&&mark.until>target.getWorld().getTime()&&valid(mark.owner)&&mark.owner.getWorld()==target.getWorld()?amount*1.15f:amount;
    }
    /** Call from other stand time-stop implementations; Kageno's own R captures targets but permits the caster to walk at half speed. */
    public static boolean ignoresTimeStop(PlayerEntity p,String source){return StandManager.kageno(p)&&KagenoRules.ignoresForeignTimeStop(source);}
    private static void sound(Cast c,SoundEvent event,float volume,float pitch){c.world.playSound(null,c.player.getBlockPos(),event,SoundCategory.PLAYERS,volume,pitch);}
    private static void sync(Cast c){
        for(var viewer:c.world.getPlayers())if(viewer==c.player||c.targets.containsKey(viewer)||viewer.squaredDistanceTo(c.origin)<96*96){
            if(!ServerPlayNetworking.canSend(viewer,CAST))continue;
            var b=PacketByteBufs.create();b.writeUuid(c.id);b.writeUuid(c.player.getUuid());b.writeIdentifier(c.world.getRegistryKey().getValue());b.writeByte(c.skill);b.writeByte(c.combo);b.writeLong(c.start);b.writeLong(c.world.getTime());vec(b,c.origin);b.writeFloat(c.yaw);b.writeVarInt(c.targets.size());
            for(var t:c.targets.entrySet()){b.writeUuid(t.getKey().getUuid());b.writeInt(t.getKey().getId());vec(b,t.getValue());}
            ServerPlayNetworking.send(viewer,CAST,b);c.viewers.add(viewer.getUuid());
        }
    }
    private static void vec(net.minecraft.network.PacketByteBuf b,Vec3d v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static void status(ServerPlayerEntity p,State s){
        long now=p.getWorld().getTime();
        for(var viewer:p.getServerWorld().getPlayers())if(viewer==p||viewer.squaredDistanceTo(p)<96*96){
            if(!ServerPlayNetworking.canSend(viewer,STATUS))continue;
            var b=PacketByteBufs.create();b.writeUuid(p.getUuid());b.writeIdentifier(p.getWorld().getRegistryKey().getValue());b.writeLong(s.overUntil);b.writeFloat(s.boost);
            for(long value:s.ready)b.writeLong(value);
            var marks=MARKS.values().stream().filter(m->m.owner==p&&m.until>now).toList();b.writeVarInt(marks.size());
            for(var m:marks){b.writeUuid(m.target.getUuid());b.writeInt(m.target.getId());b.writeLong(m.until);}
            ServerPlayNetworking.send(viewer,STATUS,b);
        }
    }
    private static void rSlow(ServerPlayerEntity p,boolean active){
        var attr=p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        if(!active){attr.removeModifier(R_SLOW);return;}
        if(attr.getModifier(R_SLOW)==null)attr.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(R_SLOW,"Kageno R half walk speed",-.5,net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    private static void stop(Cast c){
        CASTS.remove(c.id);if(c.skill==R)rSlow(c.player,false);
        for(var id:c.viewers){var p=c.world.getServer().getPlayerManager().getPlayer(id);if(p!=null&&ServerPlayNetworking.canSend(p,STOP)){var b=PacketByteBufs.create();b.writeUuid(c.id);ServerPlayNetworking.send(p,STOP,b);}}
        if(c.player.isAlive())c.player.setVelocity(0,0,0);
    }
    private static void cancelCaster(PlayerEntity p){for(var c:new ArrayList<>(CASTS.values()))if(c.player==p)stop(c);}
    public static void leave(ServerPlayerEntity p){cancelCaster(p);rSlow(p,false);var s=STATES.remove(p);if(s!=null){s.overUntil=0;s.boost=0;status(p,s);}MARKS.values().removeIf(m->m.owner==p||m.target==p);for(var c:CASTS.values())c.targets.remove(p);}
    public static void clear(MinecraftServer server){for(var c:new ArrayList<>(CASTS.values()))stop(c);for(var p:new ArrayList<>(STATES.keySet()))leave(p);}
    public static void resetCooldown(ServerPlayerEntity p){var s=STATES.get(p);if(s!=null)Arrays.fill(s.ready,0);}
}
