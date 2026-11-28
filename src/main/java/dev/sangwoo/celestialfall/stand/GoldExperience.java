package dev.sangwoo.celestialfall.stand;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.sangwoo.celestialfall.CelestialFall;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.literal;

/** Gold Experience's entity and its OP command (2.145.0): the figure to look at the model in the game. */
public final class GoldExperience {
    public static EntityType<GoldExperienceEntity> TYPE;
    private GoldExperience(){}
    public static void initialize(){
        TYPE=Registry.register(Registries.ENTITY_TYPE,CelestialFall.id("gold_experience"),FabricEntityTypeBuilder.<GoldExperienceEntity>create(SpawnGroup.MISC,GoldExperienceEntity::new)
            .dimensions(EntityDimensions.fixed(.95f,2.35f)).trackRangeBlocks(96).trackedUpdateRate(20).build());
        FabricDefaultAttributeRegistry.register(TYPE,GoldExperienceEntity.attributes());
    }
    /**
     * "/botand gold" summons the figure three blocks ahead, facing you, floating at rest; "/botand gold hero" in the pose
     * of the picture, "/botand gold stand" standing straight; "/botand gold clear" removes the figures within 32 blocks.
     * (2.156.0) A skin after the stance draws the figure in it: "/botand gold hero hq" (StandSkins; "original" its own look).
     */
    public static LiteralArgumentBuilder<ServerCommandSource> commands(){
        var root=literal("gold").requires(source->source.hasPermissionLevel(2)).executes(c->summon(c.getSource(),0,0));
        var skins=StandSkins.of("gold_experience");
        for(int i=0;i<GoldExperienceStance.NAMES.size();i++){
            int stance=i;var node=literal(GoldExperienceStance.NAMES.get(i)).executes(c->summon(c.getSource(),stance,0));
            for(int k=0;k<skins.size();k++){int skin=k;node.then(literal(skins.get(k)).executes(c->summon(c.getSource(),stance,skin)));}
            root.then(node);
        }
        root.then(literal("clear").executes(c->clear(c.getSource())));
        return root;
    }
    private static int summon(ServerCommandSource source,int stance,int skin){
        var world=source.getWorld();var figure=TYPE.create(world);if(figure==null)return 0;
        Vec3d at=source.getPosition().add(Vec3d.fromPolar(0,source.getRotation().y).multiply(3));float yaw=source.getRotation().y+180;
        figure.refreshPositionAndAngles(at.x,at.y,at.z,yaw,0);figure.setHeadYaw(yaw);figure.setBodyYaw(yaw);figure.setStance(stance);figure.setSkin(skin);
        if(!world.spawnEntity(figure))return 0;
        source.sendFeedback(()->Text.translatable("message.botand.gold_spawned",figure.getName()),true);return 1;
    }
    private static int clear(ServerCommandSource source){
        var figures=source.getWorld().getEntitiesByClass(GoldExperienceEntity.class,new Box(source.getPosition(),source.getPosition()).expand(32),e->true);
        figures.forEach(Entity::discard);
        source.sendFeedback(()->Text.translatable("message.botand.gold_cleared",figures.size()),true);return figures.size();
    }
}
