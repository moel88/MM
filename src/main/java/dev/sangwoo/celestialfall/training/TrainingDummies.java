package dev.sangwoo.celestialfall.training;

import dev.sangwoo.celestialfall.CelestialFall;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.command.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TrainingDummies {
    public static final Map<DummyKind,EntityType<TrainingDummyEntity>> TYPES=new EnumMap<>(DummyKind.class);
    public static final Map<DummyKind,Item> EGGS=new EnumMap<>(DummyKind.class);
    /** The Atomic dummy's tag. It is saved with the dummy, so one left over from a server stopped mid-cast goes as soon as it ticks. */
    public static final String ATOMIC_TAG="botand_atomic_dummy";
    private TrainingDummies() {}
    public static void initialize() {
        for(var kind:DummyKind.values()) {
            var type=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id(kind.id+"_dummy"),
                FabricEntityTypeBuilder.<TrainingDummyEntity>create(SpawnGroup.CREATURE,(t,w)->new TrainingDummyEntity(t,w,kind))
                    .dimensions(EntityDimensions.fixed(.6f,1.8f)).trackRangeBlocks(80).trackedUpdateRate(2).build());
            TYPES.put(kind,type);FabricDefaultAttributeRegistry.register(type,TrainingDummyEntity.attributes());
            var egg=Registry.register(Registries.ITEM,CelestialFall.id(kind.id+"_dummy_spawn_egg"),new MapDummyEgg(type));
            EGGS.put(kind,egg);
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries->{for(var kind:DummyKind.values())entries.add(EGGS.get(kind));});
    }
    public static LiteralArgumentBuilder<ServerCommandSource> commands() {
        var root=literal("dummy");
        for(var kind:DummyKind.values())root.then(literal(kind.id).executes(c->spawn(c.getSource(),kind)));
        // A dummy that casts Atomic ahead of you (user request, 2.38.2: to watch it from far away), optionally this far.
        root.then(literal("atomic").executes(c->atomic(c.getSource(),atomicDistance()))
            .then(argument("distance",com.mojang.brigadier.arguments.IntegerArgumentType.integer(4,256))
                .executes(c->atomic(c.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"distance")))));
        root.then(literal("reset").executes(c->nearby(c.getSource(),false)));
        root.then(literal("clear").executes(c->nearby(c.getSource(),true)));
        return root;
    }
    private static int spawn(ServerCommandSource source,DummyKind kind) {
        var world=source.getWorld();if(!dev.sangwoo.celestialfall.map.MapGate.enabled(world)){source.sendError(Text.translatable("message.botand.map_only"));return 0;}var dummy=TYPES.get(kind).create(world);if(dummy==null)return 0;
        Vec3d position=source.getPosition().add(Vec3d.fromPolar(0,source.getRotation().y).multiply(2.5));
        dummy.refreshPositionAndAngles(position.x,position.y,position.z,source.getRotation().y+180,0);
        dummy.setHeadYaw(dummy.getYaw());dummy.setBodyYaw(dummy.getYaw());
        if(!world.isSpaceEmpty(dummy)||!world.getWorldBorder().contains(dummy.getBoundingBox())) {
            source.sendError(Text.translatable("message.botand.dummy_no_space"));return 0;
        }
        if(!world.spawnEntity(dummy))return 0;
        source.sendFeedback(()->Text.translatable("message.botand.dummy_spawned",dummy.getName()),false);return 1;
    }
    /**
     * How far ahead the Atomic dummy stands unless told (blocks): outside the column it opens and the shaking by its
     * wall, so you see it from outside, and near enough to see its body (an entity this small is drawn to 64 blocks).
     */
    static int atomicDistance(){return (int)Math.min(256,Math.max(60,Math.ceil(CelestialFall.CONFIG.atomicVisualRadius)+24));}
    /**
     * Summons a standing dummy {@code distance} blocks ahead on the ground, facing you, and it casts Atomic at once; it
     * goes when the cast ends. It cannot be hurt by the cast and never hurts or holds a player (the cast has no owner).
     */
    private static int atomic(ServerCommandSource source,int distance) {
        var world=source.getWorld();if(!dev.sangwoo.celestialfall.map.MapGate.enabled(world)){source.sendError(Text.translatable("message.botand.map_only"));return 0;}
        if(dev.sangwoo.celestialfall.AtomicManager.hasActive()||dev.sangwoo.celestialfall.SpellManager.hasActive()||dev.sangwoo.celestialfall.HeavenManager.hasActive()){
            source.sendError(Text.translatable("message.celestialfall.busy"));return 0;
        }
        Vec3d at=source.getPosition().add(Vec3d.fromPolar(0,source.getRotation().y).multiply(distance));var column=BlockPos.ofFloored(at);
        // Never load or generate ground for it.
        if(!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(column.getX()),ChunkSectionPos.getSectionCoord(column.getZ()))){
            source.sendError(Text.translatable("message.botand.dummy_atomic_far"));return 0;
        }
        int ground=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
        var dummy=TYPES.get(DummyKind.IDLE).create(world);if(dummy==null)return 0;
        dummy.refreshPositionAndAngles(at.x,ground,at.z,source.getRotation().y+180,0);
        dummy.setHeadYaw(dummy.getYaw());dummy.setBodyYaw(dummy.getYaw());
        if(!world.isSpaceEmpty(dummy)||!world.getWorldBorder().contains(dummy.getBoundingBox())) {
            source.sendError(Text.translatable("message.botand.dummy_no_space"));return 0;
        }
        dummy.setInvulnerable(true);dummy.addCommandTag(ATOMIC_TAG);dummy.setCustomName(Text.translatable("entity.celestialfall.atomic_dummy"));
        if(!world.spawnEntity(dummy))return 0;
        if(!dev.sangwoo.celestialfall.AtomicManager.castBy(dummy)){dummy.discard();source.sendError(Text.translatable("message.celestialfall.busy"));return 0;}
        source.sendFeedback(()->Text.translatable("message.botand.dummy_atomic",distance),false);
        if(distance<=CelestialFall.CONFIG.atomicVisualRadius+8)source.sendFeedback(()->Text.translatable("message.botand.dummy_atomic_close"),false);
        return 1;
    }
    private static int nearby(ServerCommandSource source,boolean remove) {
        var list=source.getWorld().getEntitiesByClass(TrainingDummyEntity.class,new Box(source.getPosition(),source.getPosition()).expand(32),
            d->d.isAlive()&&d.squaredDistanceTo(source.getPosition())<=32*32);
        for(var dummy:list){if(remove)dummy.discard();else dummy.resetTraining();}
        source.sendFeedback(()->Text.translatable(remove?"message.botand.dummy_cleared":"message.botand.dummy_reset",list.size()),false);
        return list.size();
    }
}
