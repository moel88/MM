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
import static net.minecraft.server.command.CommandManager.literal;

public final class TrainingDummies {
    public static final Map<DummyKind,EntityType<TrainingDummyEntity>> TYPES=new EnumMap<>(DummyKind.class);
    public static final Map<DummyKind,Item> EGGS=new EnumMap<>(DummyKind.class);
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
    private static int nearby(ServerCommandSource source,boolean remove) {
        var list=source.getWorld().getEntitiesByClass(TrainingDummyEntity.class,new Box(source.getPosition(),source.getPosition()).expand(32),
            d->d.isAlive()&&d.squaredDistanceTo(source.getPosition())<=32*32);
        for(var dummy:list){if(remove)dummy.discard();else dummy.resetTraining();}
        source.sendFeedback(()->Text.translatable(remove?"message.botand.dummy_cleared":"message.botand.dummy_reset",list.size()),false);
        return list.size();
    }
}
