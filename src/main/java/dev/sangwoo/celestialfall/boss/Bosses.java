package dev.sangwoo.celestialfall.boss;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.combat.HitStun;
import dev.sangwoo.celestialfall.loot.FieldLootEntity;
import dev.sangwoo.celestialfall.loot.LootRegistry;
import dev.sangwoo.celestialfall.loot.LootRules;
import dev.sangwoo.celestialfall.map.MapGate;
import dev.sangwoo.celestialfall.mixin.PlayerManagerAccess;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import dev.sangwoo.celestialfall.progression.ProgressProfile;
import dev.sangwoo.celestialfall.stand.StandHolder;
import dev.sangwoo.celestialfall.stand.StarPlatinumInhale;
import dev.sangwoo.celestialfall.stand.StarPlatinumRush;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * The bosses on the map (2.69.0): it brings them in (when their time comes, or by an operator's word), keeps them, and
 * takes them off; it tells every client which players are bosses (boss_list_v1: their names for the boss bar, their
 * range, whether they are angry), gives what a fallen boss leaves (BossKind: its reward to whoever felled it, its boxes
 * in a ring where it fell), and keeps the world's record of them (BossState) across restarts.
 */
public final class Bosses {
    public static final Identifier LIST=CelestialFall.id("boss_list_v1");
    /** The bosses in play, by their bodies' ids. */
    private static final Map<UUID,BossPlayer> LIVE=new LinkedHashMap<>();
    /** Kinds whose missing spot was already told to the log. */
    private static final EnumSet<BossKind> UNPLACED=EnumSet.noneOf(BossKind.class);
    private Bosses(){}

    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(Bosses::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sendList(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p,origin,destination)->sendList(p));
        ServerLifecycleEvents.SERVER_STARTED.register(Bosses::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(Bosses::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{LIVE.clear();UNPLACED.clear();});
    }

    // ---- who is a boss

    /** The boss with this body id, if one is in play (PlayerManager finds them through it, BossPlayerManagerMixin). */
    public static BossPlayer get(UUID id){return id==null?null:LIVE.get(id);}
    public static Collection<BossPlayer> all(){return Collections.unmodifiableCollection(LIVE.values());}
    public static BossPlayer live(BossKind kind){for(var b:LIVE.values())if(b.kind==kind)return b;return null;}
    /** The server's players and the bosses: those whose combat state the server runs every tick. */
    public static List<ServerPlayerEntity> withBosses(List<ServerPlayerEntity> players){
        if(LIVE.isEmpty())return players;
        var all=new ArrayList<ServerPlayerEntity>(players.size()+LIVE.size());all.addAll(players);all.addAll(LIVE.values());return all;
    }
    /** {@code p}'s cooldown for {@code skill}: a player's {@code base}, a boss's own where its kind has one (BossKind.cooldown). */
    public static int cooldown(PlayerEntity p,String skill,int base){return p instanceof BossPlayer b?b.kind.cooldown(skill,base):base;}
    /** Whether a stagger slides off {@code e} now: a boss held down long enough lately (BossRules.Poise). */
    public static boolean shrugsStagger(LivingEntity e){return e instanceof BossPlayer b&&b.brain.poise.shrugs(e.getWorld().getTime());}
    /** {@code by} struck {@code target} with something that is not a blow (an inhale catching it, a time stop): a boss is angry with them. */
    public static void provoke(LivingEntity target,LivingEntity by){
        if(target instanceof BossPlayer b&&by instanceof ServerPlayerEntity p&&!(p instanceof BossPlayer))b.brain.provoked(p);
    }

    // ---- bringing them in and taking them off

    /**
     * Brings a boss of {@code kind} in at {@code at}, facing {@code yaw}, with {@code health} (its whole health if 0 or
     * more): every client first learns of its body (a client draws no player it has no entry for), then it is put in
     * the world. One of its kind already in play goes first.
     */
    public static BossPlayer spawn(ServerWorld world,BossKind kind,Vec3d at,float yaw,float health){
        var old=live(kind);if(old!=null)remove(old);
        var server=world.getServer();
        var boss=new BossPlayer(world,kind,UUID.randomUUID());
        boss.refreshPositionAndAngles(at.x,at.y,at.z,yaw,0);boss.setHeadYaw(yaw);boss.setBodyYaw(yaw);
        boss.home=at;boss.homeYaw=yaw;
        ((StandHolder)(Object)boss).botand$setStand(kind.stand);
        var max=boss.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);if(max!=null)max.setBaseValue(kind.health);
        boss.setHealth(health>0?Math.min(health,kind.health):kind.health);
        boss.joinInvulnerabilityTicks=0;
        server.getPlayerManager().sendToAll(entry(List.of(boss)));
        LIVE.put(boss.getUuid(),boss);
        if(!world.spawnEntity(boss)){LIVE.remove(boss.getUuid());server.getPlayerManager().sendToAll(new PlayerRemoveS2CPacket(List.of(boss.getUuid())));return null;}
        var state=BossState.get(server);var e=state.entry(kind);e.up=true;e.health=boss.getHealth();state.changed();
        sendListAll(server);
        announce(world,Text.literal("[보스] ").formatted(Formatting.RED,Formatting.BOLD).append(Text.literal(kind.title+"이(가) 나타났습니다! ").formatted(Formatting.GOLD))
            .append(Text.literal(String.format(Locale.ROOT,"(%d, %d, %d)",Math.round(at.x),Math.round(at.y),Math.round(at.z))).formatted(Formatting.GRAY)));
        CelestialFall.LOGGER.info("Boss {} came at ({}, {}, {}) with {} health",kind.id,at.x,at.y,at.z,boss.getHealth());
        return boss;
    }
    /** Takes a boss off the map (fallen, sent away, the server stopping): its body goes, and every client forgets it. */
    public static void remove(BossPlayer boss){
        if(LIVE.remove(boss.getUuid())==null)return;
        var server=boss.getServer();
        // its skills let go of whom they hold; its own state goes as a player's does when they leave
        StarPlatinumRush.release(boss);StarPlatinumInhale.release(boss);
        boss.brain.forget();
        CombatManager.leave(boss);HitStun.disconnect(boss.getUuid());
        if(!boss.isRemoved())boss.discard();
        var access=(PlayerManagerAccess)server.getPlayerManager();
        access.botand$statistics().remove(boss.getUuid());access.botand$advancements().remove(boss.getUuid());
        server.getPlayerManager().sendToAll(new PlayerRemoveS2CPacket(List.of(boss.getUuid())));
        sendListAll(server);
    }
    /**
     * A boss fell: whoever felled it (the blow's player, or the one it last took harm from) gets its reward, everyone on
     * the map is told, and its boxes come out in a ring where it fell. Its body goes down and is taken off a second on.
     */
    static void defeated(BossPlayer boss,DamageSource source){
        var world=boss.getServerWorld();var server=world.getServer();
        ServerPlayerEntity killer=null;
        if(source!=null&&source.getAttacker() instanceof ServerPlayerEntity p&&!(p instanceof BossPlayer))killer=p;
        else if(boss.getPrimeAdversary() instanceof ServerPlayerEntity p&&!(p instanceof BossPlayer))killer=p;
        if(killer==null)killer=boss.brain.lastStriker();
        var kind=boss.kind;
        if(killer!=null&&!killer.isRemoved()&&MapGate.enabled(killer.getWorld())){
            var before=ProgressManager.profile(killer);
            ProgressManager.set(killer,before.withMoney(Math.min(ProgressProfile.MAX_MONEY,before.money()+kind.reward)));
            killer.sendMessage(Text.literal(kind.title+" 처치! +"+String.format(Locale.ROOT,"%,d",kind.reward)+"원").formatted(Formatting.GOLD,Formatting.BOLD),true);
            announce(world,Text.literal("[보스] ").formatted(Formatting.RED,Formatting.BOLD).append(Text.literal(killer.getName().getString()+"님이 "+kind.title+"을(를) 쓰러뜨렸습니다! ").formatted(Formatting.GOLD))
                .append(Text.literal("+"+String.format(Locale.ROOT,"%,d",kind.reward)+"원").formatted(Formatting.YELLOW)));
        }else announce(world,Text.literal("[보스] ").formatted(Formatting.RED,Formatting.BOLD).append(Text.literal(kind.title+"이(가) 쓰러졌습니다!").formatted(Formatting.GOLD)));
        int boxes=dropBoxes(boss);
        CelestialFall.LOGGER.info("Boss {} fell at ({}, {}, {}), felled by {}, {} boxes",kind.id,boss.getX(),boss.getY(),boss.getZ(),killer==null?"nobody":killer.getName().getString(),boxes);
        var state=BossState.get(server);var e=state.entry(kind);e.up=false;e.health=kind.health;state.changed();
        sendListAll(server);
    }
    /** Its boxes, evenly on a ring round where it fell, each on the ground there (nearer in if there is no room out there). */
    static int dropBoxes(BossPlayer boss){
        var world=boss.getServerWorld();var at=boss.getPos();int n=0;
        var spots=BossRules.ring(at.x,at.z,boss.kind.boxes,BossRules.BOX_RING,boss.getYaw());
        for(var spot:spots){
            Vec3d pos=null;
            for(double k:new double[]{1,.7,.45}){pos=ground(world,at.x+(spot[0]-at.x)*k,at.y,at.z+(spot[1]-at.z)*k);if(pos!=null)break;}
            if(pos==null)pos=at;
            var box=LootRegistry.FIELD_LOOT.create(world);if(box==null)continue;
            int drops=LootRules.drops(false,world.random.nextInt(100),world.random.nextInt(100))|LootRules.extras(false,world.random.nextInt(1000),world.random.nextInt(100));
            box.makeLoose(drops,world.getTime());
            box.refreshPositionAndAngles(pos.x,pos.y,pos.z,world.random.nextFloat()*360,0);
            if(world.spawnEntity(box)){
                n++;
                world.spawnParticles(ParticleTypes.CLOUD,pos.x,pos.y+.5,pos.z,8,.35,.25,.35,.02);
                world.spawnParticles(ParticleTypes.END_ROD,pos.x,pos.y+.8,pos.z,6,.25,.35,.25,.03);
            }
        }
        return n;
    }
    /** The top of the ground under ({@code x}, {@code z}) near {@code y}, with room for a box on it; null if there is none. */
    private static Vec3d ground(ServerWorld world,double x,double y,double z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z),by=(int)Math.floor(y);
        for(int dy=3;dy>=-6;dy--){
            var pos=new BlockPos(bx,by+dy,bz);
            if(!world.getBlockState(pos).getCollisionShape(world,pos).isEmpty()||!world.getBlockState(pos.up()).getCollisionShape(world,pos.up()).isEmpty())continue;
            var below=pos.down();
            if(world.getBlockState(below).isSideSolidFullSquare(world,below,Direction.UP))return new Vec3d(x,pos.getY(),z);
        }
        return null;
    }

    // ---- the clients told

    /** The player list's entry for each boss: its body's profile and its game mode, unlisted (it shows in no list). */
    static PlayerListS2CPacket entry(List<ServerPlayerEntity> bosses){
        return new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER,PlayerListS2CPacket.Action.UPDATE_GAME_MODE),bosses);
    }
    /** A player joins: their client learns of the bosses' bodies before the world shows them (BossPlayerManagerMixin). */
    public static void introduce(ServerPlayerEntity p){
        if(LIVE.isEmpty())return;
        p.networkHandler.sendPacket(entry(new ArrayList<>(LIVE.values())));
    }
    /** Tells this player's client which bodies in its world are bosses, their names, their range and their anger. */
    public static void sendList(ServerPlayerEntity p){
        if(p instanceof BossPlayer||!ServerPlayNetworking.canSend(p,LIST))return;
        var b=PacketByteBufs.create();
        var here=new ArrayList<BossPlayer>();for(var boss:LIVE.values())if(boss.getWorld()==p.getWorld())here.add(boss);
        b.writeVarInt(here.size());
        for(var boss:here){
            b.writeUuid(boss.getUuid());b.writeString(boss.kind.id,32);b.writeString(boss.kind.title,64);
            b.writeFloat((float)BossRules.RANGE);b.writeBoolean(boss.brain.angry());
        }
        ServerPlayNetworking.send(p,LIST,b);
    }
    static void sendListAll(MinecraftServer server){for(var p:server.getPlayerManager().getPlayerList())sendList(p);}
    private static void announce(ServerWorld world,Text text){for(var p:world.getPlayers())if(!(p instanceof BossPlayer))p.sendMessage(text,false);}

    // ---- time

    private static void tick(MinecraftServer server){
        // a body gone (fallen and down a second, or removed by a command): off the list
        for(var b:new ArrayList<>(LIVE.values()))if(b.isRemoved())remove(b);
        var world=server.getOverworld();
        if(!MapGate.enabled(world))return;
        long now=world.getTime();var state=BossState.get(server);
        for(var kind:BossKind.values()){
            var e=state.entry(kind);
            // the first hour and a half counts from when the map first ran with the mod
            if(e.next<=0){e.next=now+kind.every;state.changed();continue;}
            var up=live(kind);
            if(up!=null&&now%100==0&&up.isAlive()){e.health=up.getHealth();state.changed();}
            if(now<e.next)continue;
            e.next=BossRules.nextAfter(e.next,kind.every,now);state.changed();
            // its time came: it comes, unless it is still up (one of each at a time)
            if(up!=null)continue;
            var spot=spot(server,kind);
            if(spot==null){if(UNPLACED.add(kind))CelestialFall.LOGGER.warn("Boss {} has no spot on this map: /botand boss {} setspawn",kind.id,kind.id);continue;}
            spawn(world,kind,new Vec3d(spot[0],spot[1],spot[2]),(float)spot[3],kind.health);
        }
    }
    /** Back up as it was, if it was up when the server stopped. */
    private static void started(MinecraftServer server){
        var world=server.getOverworld();if(!MapGate.enabled(world))return;
        var state=BossState.get(server);
        for(var kind:BossKind.values()){
            var e=state.entry(kind);if(!e.up)continue;
            var spot=spot(server,kind);if(spot==null)continue;
            spawn(world,kind,new Vec3d(spot[0],spot[1],spot[2]),(float)spot[3],e.health);
        }
    }
    /** The server stops: every boss is kept as it is (up, with its health) and taken off before the players are sent away. */
    private static void stopping(MinecraftServer server){
        if(LIVE.isEmpty())return;
        var state=BossState.get(server);
        for(var b:new ArrayList<>(LIVE.values())){
            var e=state.entry(b.kind);e.up=b.isAlive();e.health=b.isAlive()?b.getHealth():b.kind.health;state.changed();
            remove(b);
        }
    }
    /**
     * Where a boss of {@code kind} comes: the spot an operator set, or the map's own (for now the open island's, out on
     * the grass north of the spawn, user request: "위치는 나중에 지정해줄게"); null if there is none.
     */
    public static double[] spot(MinecraftServer server,BossKind kind){
        var e=BossState.get(server).entry(kind);
        if(e.placed())return new double[]{e.x,e.y,e.z,e.yaw};
        var id=MapGate.definition(server).map(d->d.id()).orElse("");
        if(kind==BossKind.JOTARO&&id.equals("botand:expanse"))return new double[]{17.5,81,-77.5,0};
        return null;
    }

    // ---- the operators' word

    /**
     * "/botand boss": the bosses in play and when each comes next; "/botand boss jotaro spawn [here]" brings him in at
     * his spot (or where you stand); "setspawn" makes where you stand his spot; "remove" takes him off (no reward, no
     * boxes); "timer [minutes]" says (or sets) how long until he comes next.
     */
    public static LiteralArgumentBuilder<ServerCommandSource> commands(){
        var root=literal("boss").requires(s->s.hasPermissionLevel(2)).executes(c->status(c.getSource()));
        for(var kind:BossKind.values()){
            root.then(literal(kind.id)
                .then(literal("spawn").executes(c->spawnCommand(c.getSource(),kind,false)).then(literal("here").executes(c->spawnCommand(c.getSource(),kind,true))))
                .then(literal("setspawn").executes(c->{
                    var s=c.getSource();var e=BossState.get(s.getServer()).entry(kind);var pos=s.getPosition();
                    e.x=pos.x;e.y=pos.y;e.z=pos.z;e.yaw=s.getRotation().y;BossState.get(s.getServer()).changed();UNPLACED.remove(kind);
                    s.sendFeedback(()->Text.literal(String.format(Locale.ROOT,"%s의 위치를 (%.1f, %.1f, %.1f)로 정했어요.",kind.title,pos.x,pos.y,pos.z)),true);return 1;
                }))
                .then(literal("remove").executes(c->{
                    var b=live(kind);if(b==null){c.getSource().sendError(Text.literal(kind.title+"은(는) 지금 없어요."));return 0;}
                    var state=BossState.get(c.getSource().getServer());state.entry(kind).up=false;state.changed();
                    remove(b);c.getSource().sendFeedback(()->Text.literal(kind.title+"을(를) 없앴어요."),true);return 1;
                }))
                .then(literal("timer").executes(c->{
                    var s=c.getSource();long left=BossState.get(s.getServer()).entry(kind).next-s.getServer().getOverworld().getTime();
                    s.sendFeedback(()->Text.literal(kind.title+" 다음 등장까지 "+minutes(left)),false);return 1;
                }).then(argument("minutes",IntegerArgumentType.integer(0,24*60)).executes(c->{
                    var s=c.getSource();int m=IntegerArgumentType.getInteger(c,"minutes");var state=BossState.get(s.getServer());
                    state.entry(kind).next=s.getServer().getOverworld().getTime()+Math.max(1,m*1200L);state.changed();
                    s.sendFeedback(()->Text.literal(kind.title+" 다음 등장까지 "+m+"분"),true);return 1;
                }))));
        }
        return root;
    }
    private static int spawnCommand(ServerCommandSource source,BossKind kind,boolean here){
        var world=source.getServer().getOverworld();
        if(!MapGate.enabled(world)){source.sendError(Text.literal("보탠드 맵에서만 보스가 나와요."));return 0;}
        double[] spot=here?null:spot(source.getServer(),kind);
        Vec3d at=spot!=null?new Vec3d(spot[0],spot[1],spot[2]):source.getPosition();float yaw=spot!=null?(float)spot[3]:source.getRotation().y;
        if(spot==null&&source.getWorld()!=world){source.sendError(Text.literal("보스는 오버월드 맵에만 나와요."));return 0;}
        var b=spawn(world,kind,at,yaw,kind.health);
        if(b==null){source.sendError(Text.literal(kind.title+"을(를) 불러오지 못했어요."));return 0;}
        source.sendFeedback(()->Text.literal(String.format(Locale.ROOT,"%s 등장 (%.1f, %.1f, %.1f)",kind.title,at.x,at.y,at.z)),true);
        return 1;
    }
    private static int status(ServerCommandSource source){
        var server=source.getServer();long now=server.getOverworld().getTime();var state=BossState.get(server);
        for(var kind:BossKind.values()){
            var b=live(kind);var e=state.entry(kind);var spot=spot(server,kind);
            String where=spot==null?"위치 없음":String.format(Locale.ROOT,"(%.0f, %.0f, %.0f)",spot[0],spot[1],spot[2]);
            String up=b==null?"없음":String.format(Locale.ROOT,"체력 %.0f/%.0f, %s",b.getHealth(),b.getMaxHealth(),b.brain.describe());
            source.sendFeedback(()->Text.literal(kind.title+": "+up+" · 위치 "+where+" · 다음 등장까지 "+minutes(e.next-now)),false);
        }
        return LIVE.size();
    }
    private static String minutes(long ticks){
        if(ticks<=0)return "곧";
        long s=(ticks+19)/20;return s>=60?(s/60)+"분 "+(s%60)+"초":s+"초";
    }
}
