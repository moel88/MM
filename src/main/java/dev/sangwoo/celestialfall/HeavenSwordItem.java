package dev.sangwoo.celestialfall;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;

public final class HeavenSwordItem extends SwordItem {
    public HeavenSwordItem() { super(ToolMaterials.NETHERITE,5,-2.4f,new Item.Settings().fireproof()); }
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity user,Hand hand) {
        var stack=user.getStackInHand(hand);
        if(hand!=Hand.MAIN_HAND)return TypedActionResult.fail(stack);
        if(!dev.sangwoo.celestialfall.map.MapGate.require(user))return TypedActionResult.fail(stack);
        if(world.isClient)return TypedActionResult.success(stack);
        return HeavenManager.cast((ServerPlayerEntity)user)?TypedActionResult.success(stack):TypedActionResult.fail(stack);
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context) {
        lines.add(Text.translatable("tooltip.celestialfall.heaven.1").formatted(Formatting.GOLD));
        lines.add(Text.translatable("tooltip.celestialfall.heaven.2").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.celestialfall.heaven.3").formatted(Formatting.DARK_GRAY));
    }
}
