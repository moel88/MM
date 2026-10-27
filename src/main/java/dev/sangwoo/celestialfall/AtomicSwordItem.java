package dev.sangwoo.celestialfall;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;

public final class AtomicSwordItem extends SwordItem {
    public AtomicSwordItem() { super(ToolMaterials.NETHERITE,5,-2.4f,new Item.Settings().fireproof()); }
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity user,Hand hand) {
        var stack=user.getStackInHand(hand);
        if(hand!=Hand.MAIN_HAND)return TypedActionResult.fail(stack);
        if(!dev.sangwoo.celestialfall.map.MapGate.require(user))return TypedActionResult.fail(stack);
        if(world.isClient)return TypedActionResult.success(stack);
        return AtomicManager.cast((ServerPlayerEntity)user)?TypedActionResult.success(stack):TypedActionResult.fail(stack);
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context) {
        lines.add(Text.translatable("tooltip.celestialfall.atomic.1").formatted(Formatting.LIGHT_PURPLE));
        lines.add(Text.translatable("tooltip.celestialfall.atomic.2").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.celestialfall.atomic.3").formatted(Formatting.DARK_GRAY));
    }
}
