package dev.sangwoo.celestialfall.mixin;

import dev.sangwoo.celestialfall.xchara.EraseCubes;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (2.133.0) X Chara's delete slash's cube of ground (EraseCubes): every body moving near it meets it as it meets a
 * block: its shape joins the shapes a move is kept out of (on the server and on each client alike), so they stand on
 * its top and cannot walk into its sides. It is no block: the map is not changed. If another mod has taken this place
 * the cube is only a picture (require 0).
 */
@Mixin(Entity.class)
public abstract class EraseCubeMixin {
    @ModifyVariable(method="adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",at=@At("STORE"),ordinal=0,require=0)
    private List<VoxelShape> botand$eraseCubes(List<VoxelShape> shapes){
        Entity self=(Entity)(Object)this;
        if(!(self instanceof LivingEntity)||!EraseCubes.any(self.getWorld()))return shapes;
        return EraseCubes.with(self.getWorld(),self.getBoundingBox().expand(3),shapes);
    }
}
