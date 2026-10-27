package dev.sangwoo.celestialfall.mixin;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Permit the 2000-health training dummy; ordinary attribute values remain unchanged. */
@Mixin(ClampedEntityAttribute.class)
public interface HealthAttributeAccess {
    @Mutable @Accessor("maxValue") void botand$setMaxValue(double value);
}
