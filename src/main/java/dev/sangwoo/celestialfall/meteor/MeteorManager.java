package dev.sangwoo.celestialfall.meteor;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.commerce.MerchantEntity;
import dev.sangwoo.celestialfall.loot.LootPositions;
import dev.sangwoo.celestialfall.map.MapGate;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import java.util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Drops meteors on the Botand map and lets players mine them (2.49.0). The server rolls the empty places every minute,
 * finds each new meteor a landing place on the map's open ground (never by the spawn, a merchant, a supply spot or
 * another meteor) and a way in from the sky that no building or wall stands in, and tells every player in the world
 * of the fall: each client draws it coming in on its own clock (the streak is seen from far beyond the range the
 * rock itself is shown at). When it lands, its rock is put on the ground wherever its chunk is loaded; a hit with the
 * meteor pickaxe chips it, every fifth hit breaks a crystal off and gives the miner a fragment, and after the first and
 * the second the rock may shatter there and then ({@link MeteorRules#holds}); the third leaves nothing. Five minutes
 * after landing whatever is left crumbles away.
 */
public final class MeteorManager {
    public static final Identifier FALL=CelestialFall.id("meteor_fall_v1"),MINE=CelestialFall.id("meteor_mine_v1");
    private static final Map<UUID,Long> LAST_HIT=new HashMap<>();
    private MeteorManager(){}

    public static void initialize(){
        ServerTickEvents.END_WORLD_TICK.register(MeteorManager::tick);
        ServerPlayNetworking.registerGlobalReceiver(MINE,(server,p,handler,b,response)->{
            if(b.readableBytes()!=4)return;int target=b.readInt();
            server.execute(()->mine(p,target));
        });
        // whoever comes in while one is falling (or has only just landed) sees the rest of it, once their client has
        // said it takes the fall's packet (it says so just after joining). Queued, not run at once: on a world opened in
        // the game the client says so while its connection is still being set up, and asking then whether it takes a
        // packet fails (2.73.0: a world saved just as one fell could not be opened again)
        S2CPlayChannelEvents.REGISTER.register((h,sender,server,channels)->{
            if(!channels.contains(FALL))return;
            server.send(new net.minecraft.server.ServerTask(server.getTicks(),()->{
                if(h.player.isDisconnected())return;
                var w=h.player.getServerWorld();if(!enabled(w))return;
                long now=w.getTime();
                for(var m:MeteorState.get(w).all())if(now-m.start()<MeteorRules.FALL_TICKS+60)send(h.player,m);
            }));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST_HIT.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST_HIT.clear());
    }
    /** Meteors fall only on a Botand map that is on, in the overworld. */
    public static boolean enabled(ServerWorld w){return w.getRegistryKey().equals(World.OVERWORLD)&&MapGate.isBotandMap(w.getServer())&&MapGate.enabled(w);}

    private static void tick(ServerWorld w){
        if(!enabled(w))return;
        var state=MeteorState.get(w);long now=w.getTime();
        if(state.nextRoll()<0||state.nextRoll()>now+MeteorRules.ROLL_TICKS)state.nextRoll(now+MeteorRules.ROLL_TICKS);
        if(now>=state.nextRoll()){
            state.nextRoll(now+MeteorRules.ROLL_TICKS);
            for(int i=0;i<MeteorRules.SLOTS;i++)if(state.slot(i)==null&&MeteorRules.falls(w.random.nextInt(100)))launch(w,i,null);
        }
        for(var m:state.all()){
            if(m.expired(now)){remove(w,m,MeteorEntity.EXPIRED);continue;}
            if(!m.landed(now))continue;
            int cx=MathHelper.floor(m.x())>>4,cz=MathHelper.floor(m.z())>>4;
            if(!w.isChunkLoaded(cx,cz)||w.getEntity(m.uuid())!=null)continue;
            var rock=MeteorRegistry.METEOR.create(w);if(rock==null)continue;
            rock.setUuid(m.uuid());rock.configure(m);rock.refreshPositionAndAngles(m.x(),m.y(),m.z(),0,0);w.spawnEntity(rock);
        }
    }

    /**
     * A new meteor in place {@code slot}: on {@code at} (its ground) if given, else on a landing place found on the map.
     * Null when there is no place for it this time.
     */
    public static MeteorState.Meteor launch(ServerWorld w,int slot,Vec3d at){return launch(w,slot,at,Double.NaN);}
    /**
     * The same, coming in from {@code fromDegrees} (a number: the yaw a player would face looking back up the way it
     * came, 0 south, 90 west; NaN: any way that nothing stands in).
     */
    public static MeteorState.Meteor launch(ServerWorld w,int slot,Vec3d at,double fromDegrees){
        var state=MeteorState.get(w);
        if(slot<0||slot>=MeteorRules.SLOTS||state.slot(slot)!=null)return null;
        Vec3d site=at!=null?ground(w,at):site(w,state);
        if(site==null)return null;
        var random=w.random;double from=0,angle=50;double[] start=null;
        // the first way in from the sky that nothing stands in (the last one tried if none is clear)
        for(int i=0;i<10;i++){
            from=Double.isNaN(fromDegrees)?random.nextDouble()*Math.PI*2:Math.toRadians(fromDegrees);angle=MeteorRules.FALL_ANGLE_MIN+random.nextDouble()*(MeteorRules.FALL_ANGLE_MAX-MeteorRules.FALL_ANGLE_MIN);
            start=MeteorRules.start(site.x,site.y,site.z,from,angle);
            if(clear(w,site,new Vec3d(start[0],start[1],start[2])))break;
        }
        var m=new MeteorState.Meteor(slot,state.nextCycle(),site.x,site.y,site.z,start[0],start[1],start[2],w.getTime(),random.nextInt(),MeteorRules.FRAGMENTS,0);
        state.put(m);
        for(var p:w.getPlayers())send(p,m);
        CelestialFall.LOGGER.info("Meteor {} falls at {} {} {}",slot,Math.round(site.x),Math.round(site.y),Math.round(site.z));
        return m;
    }
    private static void send(ServerPlayerEntity p,MeteorState.Meteor m){
        if(!ServerPlayNetworking.canSend(p,FALL))return;
        var b=PacketByteBufs.create();
        b.writeVarInt(m.slot());b.writeLong(m.cycle());b.writeInt(m.seed());
        b.writeDouble(m.fromX());b.writeDouble(m.fromY());b.writeDouble(m.fromZ());
        b.writeDouble(m.x());b.writeDouble(m.y());b.writeDouble(m.z());
        b.writeLong(m.start());b.writeVarInt(MeteorRules.FALL_TICKS);
        ServerPlayNetworking.send(p,FALL,b);
    }

    /** The ground under (or over) a point: the top of the column there. */
    private static Vec3d ground(ServerWorld w,Vec3d at){
        int x=MathHelper.floor(at.x),z=MathHelper.floor(at.z);
        int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
        return top<=w.getBottomY()?null:new Vec3d(x+.5,top,z+.5);
    }
    /**
     * A landing place: open ground at the level of the spawn's (the map's streets and fields, not a roof, a wall top,
     * a tree or the sea), flat round it, clear of the spawn, the merchants, the supply spots and the other meteors.
     * It looks over a square round the spawn reaching past the farthest of those by 64 blocks.
     */
    private static Vec3d site(ServerWorld w,MeteorState state){
        var server=w.getServer();var spawn=w.getSpawnPos();int level=spawn.getY()-1;
        var keep=new ArrayList<Vec3d>();
        for(int role=0;role<3;role++)keep.add(MerchantEntity.homeOf(server,role));
        for(var spot:LootPositions.current())keep.add(Vec3d.ofBottomCenter(spot));
        double reach=64;for(var k:keep)reach=Math.max(reach,Math.hypot(k.x-spawn.getX(),k.z-spawn.getZ())+64);
        reach=Math.min(reach,600);
        var random=w.random;
        for(int tries=0;tries<40;tries++){
            int x=spawn.getX()+(int)Math.round((random.nextDouble()*2-1)*reach),z=spawn.getZ()+(int)Math.round((random.nextDouble()*2-1)*reach);
            if(Math.hypot(x-spawn.getX(),z-spawn.getZ())<MeteorRules.CLEAR_SPAWN)continue;
            boolean near=false;
            for(var k:keep)if(Math.hypot(k.x-x-.5,k.z-z-.5)<MeteorRules.CLEAR_SPOT){near=true;break;}
            for(var m:state.all())if(Math.hypot(m.x()-x-.5,m.z()-z-.5)<MeteorRules.CLEAR_METEOR){near=true;break;}
            if(near||!w.getWorldBorder().contains(x,z))continue;
            // the column's top under the sky, leaves counted (a rock does not land in a crown)
            int top=w.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z);
            if(Math.abs(top-1-level)>2)continue;
            var pos=new BlockPos(x,top-1,z);var ground=w.getBlockState(pos);
            if(!ground.getFluidState().isEmpty()||!ground.isSolidBlock(w,pos))continue;
            // flat round it: the ground two blocks out on every side at the same height (a step at most)
            boolean flat=true;
            for(int dx=-2;dx<=2&&flat;dx+=2)for(int dz=-2;dz<=2;dz+=2)if(Math.abs(w.getTopY(Heightmap.Type.MOTION_BLOCKING,x+dx,z+dz)-top)>1){flat=false;break;}
            if(!flat)continue;
            return new Vec3d(x+.5,top,z+.5);
        }
        return null;
    }
    /** Whether the way in from the sky is free of blocks (only where they are loaded: an unloaded one has no viewer near). */
    private static boolean clear(ServerWorld w,Vec3d site,Vec3d from){
        Vec3d d=from.subtract(site);double length=d.length();Vec3d step=d.multiply(1/length);
        var pos=new BlockPos.Mutable();
        for(double t=3;t<length;t+=1.5){
            Vec3d p=site.add(step.multiply(t));
            if(p.y>=w.getTopY())break;
            pos.set(p.x,p.y,p.z);
            if(!w.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))continue;
            if(!w.getBlockState(pos).getCollisionShape(w,pos).isEmpty())return false;
        }
        return true;
    }

    /**
     * A hit on a rock with the pickaxe: the client sends it as its swing (the two-handed chop every client sees, from the
     * swing it sent) lands on the rock.
     */
    public static void mine(ServerPlayerEntity p,int target){
        var w=p.getServerWorld();if(!enabled(w)||!p.isAlive()||p.isSpectator()||p.hasVehicle())return;
        long now=w.getTime();
        // the client strikes once every HIT_GAP ticks; two of its blows can reach the server a little closer together
        Long last=LAST_HIT.get(p.getUuid());if(last!=null&&now-last<MeteorRules.HIT_GAP-2&&now>=last)return;
        if(!(w.getEntityById(target) instanceof MeteorEntity rock)||!rock.isAlive())return;
        var state=MeteorState.get(w);var m=state.slot(rock.slot());
        if(m==null||m.cycle()!=rock.cycle()||!m.landed(now)||m.fragments()<=0)return;
        if(rock.getBoundingBox().squaredMagnitude(p.getEyePos())>MeteorRules.REACH*MeteorRules.REACH||!p.canSee(rock))return;
        if(!p.getMainHandStack().isOf(MeteorRegistry.PICKAXE)){
            p.sendMessage(Text.literal("운석 곡괭이가 필요해요. 아이템 판매 · 로카에게서 살 수 있어요."),true);return;
        }
        LAST_HIT.put(p.getUuid(),now);
        var before=m.rock();var after=before.hit();
        if(after.brokeFrom(before)){
            p.getInventory().offerOrDrop(new ItemStack(MeteorRegistry.FRAGMENT));
            p.currentScreenHandler.sendContentUpdates();
            // after the first and the second crystal the rock may shatter, and what is left in it is lost
            boolean holds=MeteorRules.holds(after,w.random.nextInt(100));
            w.sendEntityStatus(rock,holds?MeteorEntity.BREAK:MeteorEntity.MINED);
            w.playSound(null,rock.getX(),rock.getY()+.8,rock.getZ(),holds?MeteorRegistry.BREAK_SOUND:MeteorRegistry.CRUMBLE_SOUND,SoundCategory.BLOCKS,1f,.9f+w.random.nextFloat()*.2f);
            p.sendMessage(Text.literal(holds?"운석 조각 +1":after.depleted()?"운석 조각 +1 · 운석을 다 캤어요":"운석 조각 +1 · 운석이 부서졌어요"),true);
            if(!holds){state.clear(m.slot());rock.discard();return;}
        }else{
            w.sendEntityStatus(rock,MeteorEntity.HIT);
            w.playSound(null,rock.getX(),rock.getY()+.8,rock.getZ(),MeteorRegistry.HIT_SOUND,SoundCategory.BLOCKS,.9f,.85f+w.random.nextFloat()*.3f);
        }
        var mined=m.with(after);state.put(mined);rock.configure(mined);
    }
    /** A meteor goes: its place empties and its rock, if it is out, shows how it went (mined out or crumbled) and leaves. */
    private static void remove(ServerWorld w,MeteorState.Meteor m,byte how){
        MeteorState.get(w).clear(m.slot());
        if(w.getEntity(m.uuid()) instanceof MeteorEntity rock){
            w.sendEntityStatus(rock,how);
            w.playSound(null,rock.getX(),rock.getY()+.6,rock.getZ(),MeteorRegistry.CRUMBLE_SOUND,SoundCategory.BLOCKS,.8f,.8f);
            rock.discard();
        }
    }

    /** /botand meteor: spawn [x y z] · roll · clear · list (operators). */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands(){
        return literal("meteor").requires(s->s.hasPermissionLevel(2))
            .then(literal("spawn")
                .executes(c->spawn(c.getSource(),null))
                .then(argument("pos",BlockPosArgumentType.blockPos()).executes(c->spawn(c.getSource(),Vec3d.ofBottomCenter(BlockPosArgumentType.getLoadedBlockPos(c,"pos"))))))
            .then(literal("roll").executes(c->{var w=c.getSource().getWorld();MeteorState.get(w).nextRoll(w.getTime());c.getSource().sendFeedback(()->Text.literal("다음 틱에 빈 자리마다 운석을 굴려요."),false);return 1;}))
            .then(literal("clear").executes(c->{
                var w=c.getSource().getWorld();int n=0;for(var m:MeteorState.get(w).all()){remove(w,m,MeteorEntity.EXPIRED);n++;}
                int cleared=n;c.getSource().sendFeedback(()->Text.literal("운석 "+cleared+"개를 지웠어요."),true);return cleared;
            }))
            .then(literal("list").executes(c->{
                var w=c.getSource().getWorld();long now=w.getTime();var all=MeteorState.get(w).all();
                for(var m:all)c.getSource().sendFeedback(()->Text.literal(String.format(Locale.ROOT,"#%d (%d, %d, %d) %s · 결정 %d개 · %d초 남음",m.slot(),Math.round(m.x()),Math.round(m.y()),Math.round(m.z()),
                    m.landed(now)?"떨어짐":"떨어지는 중",m.fragments(),Math.max(0,(m.expiry()-now)/20))),false);
                c.getSource().sendFeedback(()->Text.literal("운석 "+all.size()+"/"+MeteorRules.SLOTS+"개"),false);return all.size();
            }));
    }
    private static int spawn(ServerCommandSource source,Vec3d at){
        var w=source.getWorld();
        if(!enabled(w)){source.sendError(Text.literal("보탠드 맵에서만 운석이 떨어져요."));return 0;}
        int slot=MeteorState.get(w).firstEmpty();
        if(slot<0){source.sendError(Text.literal("운석은 최대 "+MeteorRules.SLOTS+"개까지예요."));return 0;}
        var m=launch(w,slot,at);
        if(m==null){source.sendError(Text.literal("운석이 떨어질 자리를 찾지 못했어요."));return 0;}
        source.sendFeedback(()->Text.literal(String.format(Locale.ROOT,"운석 #%d이 (%d, %d, %d)에 떨어져요.",m.slot(),Math.round(m.x()),Math.round(m.y()),Math.round(m.z()))),true);
        return 1;
    }
}
