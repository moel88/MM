package dev.sangwoo.celestialfall;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import java.util.List;

public final class AstralSigilItem extends Item {
    public AstralSigilItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if(!dev.sangwoo.celestialfall.map.MapGate.require(user))return TypedActionResult.fail(stack);
        if (world.isClient) return TypedActionResult.success(stack);
        return SpellManager.cast((ServerPlayerEntity) user) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
    }
    @Override public boolean hasGlint(ItemStack stack) { return true; }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable("tooltip.celestialfall.sigil.1").formatted(Formatting.LIGHT_PURPLE));
        lines.add(Text.translatable("tooltip.celestialfall.sigil.2").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.celestialfall.sigil.3").formatted(Formatting.DARK_GRAY));
    }
}
