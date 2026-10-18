package dev.sangwoo.celestialfall.training;
import dev.sangwoo.celestialfall.map.MapGate;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
public final class MapDummyEgg extends SpawnEggItem {
    public MapDummyEgg(EntityType<? extends MobEntity> type){super(type,0x91969D,0x31363D,new Item.Settings());}
    @Override public ActionResult useOnBlock(ItemUsageContext c){return !MapGate.enabled(c.getWorld())?ActionResult.FAIL:super.useOnBlock(c);}
    @Override public TypedActionResult<ItemStack> use(World w,PlayerEntity p,Hand h){return !MapGate.require(p)?TypedActionResult.fail(p.getStackInHand(h)):super.use(w,p,h);}
}
