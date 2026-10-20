package dev.sangwoo.celestialfall.map;

import dev.sangwoo.celestialfall.*;
import dev.sangwoo.celestialfall.combat.CombatManager;
import dev.sangwoo.celestialfall.progression.ProgressManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.Predicate;
import java.security.MessageDigest;
import static net.minecraft.server.command.CommandManager.literal;

/** Server owns map authorization; receiving the mod's packet channels alone never enables it. */
public final class MapGate {
    public static final Identifier STATE=CelestialFall.id("map_state_v1");
    private static final Map<MinecraftServer,Boolean> CITY=new IdentityHashMap<>();
    private static final Map<MinecraftServer,MapDefinition> DEFINED=new IdentityHashMap<>();
    private record Sent(Identifier dimension,boolean enabled,int entityId) {}
    private static final Map<UUID,Sent> SENT=new HashMap<>();
    private static Predicate<World> client=w->false;
    private MapGate() {}
    public static void setClientCheck(Predicate<World> check){client=check;}
    public static boolean enabled(World world) {
        if(world instanceof ServerWorld w) {
            Boolean choice=MapState.get(w.getServer()).override(w.getRegistryKey().getValue().toString());
            return choice!=null?choice:w.getRegistryKey().equals(World.OVERWORLD)&&isBotandMap(w.getServer());
        }
        return world!=null&&world.isClient&&client.test(world);
    }
    /** The delivered one-third city, recognised by its bundled functions (its own fixes apply only to it). */
    public static boolean isCity(MinecraftServer server){return CITY.getOrDefault(server,false);}
    /** A Botand map: the delivered city, or a world whose data pack describes it (map/definition.json). */
    public static boolean isBotandMap(MinecraftServer server){return isCity(server)||DEFINED.containsKey(server);}
    public static Optional<MapDefinition> definition(MinecraftServer server){return Optional.ofNullable(DEFINED.get(server));}
    public static boolean require(PlayerEntity p) {
        if(enabled(p.getWorld()))return true;
        if(!p.getWorld().isClient)p.sendMessage(Text.translatable("message.botand.map_only"),true);return false;
    }
    public static boolean botandItem(ItemStack stack) {
        var item=stack.getItem();return item==CelestialFall.ASTRAL_SIGIL||item==CelestialFall.ATOMIC_SWORD||item==CelestialFall.HEAVEN_SWORD
            ||item==dev.sangwoo.celestialfall.loot.LootRegistry.CHEST_KEY||item==dev.sangwoo.celestialfall.commerce.CommerceRegistry.ROKAKAKA||dev.sangwoo.celestialfall.training.TrainingDummies.EGGS.containsValue(item);
    }
    public static void initialize() {
        MapWires.initialize();
        MapDecoration.initialize();
        FloorRepair.initialize();
        VenueRenovation.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(MapGate::refresh);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,manager,success)->{if(success)refresh(server);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sync(handler.player,true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SENT.remove(handler.player.getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p,origin,destination)->{sync(p,true);CombatManager.leave(p);ProgressManager.joined(p);});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->sync(p,true));
        ServerTickEvents.START_SERVER_TICK.register(server->{for(var p:server.getPlayerManager().getPlayerList())sync(p,false);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{CITY.remove(server);DEFINED.remove(server);SENT.clear();dev.sangwoo.celestialfall.loot.LootPositions.use(null);});
    }
    public static void refresh(MinecraftServer server) {
        CITY.put(server,recognizeCity(server));
        var defined=CITY.get(server)?Optional.<MapDefinition>empty():MapDefinition.load(server);
        if(defined.isPresent()){DEFINED.put(server,defined.get());CelestialFall.LOGGER.info("Botand map: {} ({})",defined.get().name(),defined.get().id());}
        else DEFINED.remove(server);
        dev.sangwoo.celestialfall.loot.LootPositions.use(defined.map(MapDefinition::loot).orElse(null));
        if(CITY.get(server)){FloorRepair.offer(server);VenueRenovation.offer(server);}
        for(var p:server.getPlayerManager().getPlayerList()){sync(p,true);ProgressManager.joined(p);if(!enabled(p.getWorld()))CombatManager.leave(p);}
        MapWires.sendAll(server);
    }
    /** Exact bundled teleport functions identify the previously delivered one-third city, regardless of folder name. */
    static boolean recognizeCity(MinecraftServer server) {
        try {
            var hash=MessageDigest.getInstance("SHA-256");
            for(String name:new String[]{"arena","forest","spawn"}) {
                var resource=server.getResourceManager().getResource(new Identifier("cliffcity","functions/"+name+".mcfunction"));
                if(resource.isEmpty())return false;
                hash.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                try(var in=resource.get().getInputStream()){hash.update(in.readAllBytes());}
            }
            return HexFormat.of().formatHex(hash.digest()).equals("eadea44b7b260a3800b556db73ea57548807f64957db705f19f600338eb93acf");
        } catch(Exception e){CelestialFall.LOGGER.warn("Could not identify Botand city datapack",e);return false;}
    }
    public static void designate(ServerWorld world,Boolean enabled) {
        MapState.get(world.getServer()).designate(world.getRegistryKey().getValue().toString(),enabled);
        refresh(world.getServer());
    }
    private static void sync(ServerPlayerEntity p,boolean force) {
        Sent current=new Sent(p.getWorld().getRegistryKey().getValue(),enabled(p.getWorld()),p.getId());
        if(!force&&current.equals(SENT.get(p.getUuid())))return;
        SENT.put(p.getUuid(),current);
        if(ServerPlayNetworking.canSend(p,STATE)) {
            var b=PacketByteBufs.create();b.writeIdentifier(current.dimension);b.writeBoolean(current.enabled);ServerPlayNetworking.send(p,STATE,b);
        }
        if(!current.enabled)CombatManager.leave(p);
        ProgressManager.applyHealth(p);
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands() {
        var root=literal("map").then(literal("status").executes(c->{
            boolean on=enabled(c.getSource().getWorld());c.getSource().sendFeedback(()->Text.translatable(on?"message.botand.map_on":"message.botand.map_off"),false);return on?1:0;
        }));
        for(String mode:new String[]{"enable","disable","auto"})root.then(literal(mode).requires(s->s.hasPermissionLevel(2)).executes(c->{
            designate(c.getSource().getWorld(),mode.equals("auto")?null:mode.equals("enable"));
            c.getSource().sendFeedback(()->Text.translatable(enabled(c.getSource().getWorld())?"message.botand.map_on":"message.botand.map_off"),true);return 1;
        }));
        root.then(literal("decorate").requires(s->s.hasPermissionLevel(2))
            .executes(c->MapDecoration.start(c.getSource()))
            .then(literal("status").executes(c->MapDecoration.status(c.getSource())))
            .then(literal("cancel").executes(c->MapDecoration.cancel(c.getSource()))));
        root.then(literal("fixfloor").requires(s->s.hasPermissionLevel(2)).executes(c->{
            boolean started=FloorRepair.restart(c.getSource().getWorld());
            c.getSource().sendFeedback(()->Text.literal(started?"바닥 석영 수정 시작! 해당 구역이 로딩되면 자동으로 회색 바닥으로 고쳐요.":"기존 보탠드 도시 맵에서만 바닥을 수정할 수 있어요."),false);
            return started?1:0;
        }));
        root.then(literal("venues").requires(s->s.hasPermissionLevel(2)).executes(c->{
            boolean ok=VenueRenovation.restart(c.getSource().getWorld());
            c.getSource().sendFeedback(()->Text.literal(ok?"상점·카페 정비 시작! 두 구역이 로딩되면 출입구와 장식을 적용해요.":"기존 보탠드 도시 맵에서만 적용할 수 있어요."),false);
            return ok?1:0;
        }).then(literal("status").executes(c->{
            int left=VenueRenovation.remaining(c.getSource().getServer());
            c.getSource().sendFeedback(()->Text.literal(left<0?"정비 대상 맵이 아니에요.":"상점·카페 정비 남은 블록: "+left+" (미로딩/캐릭터가 있는 위치는 대기)"),false);return Math.max(0,left);
        })));
        return root;
    }
}
