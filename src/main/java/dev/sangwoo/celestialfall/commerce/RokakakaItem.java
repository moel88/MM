package dev.sangwoo.celestialfall.commerce;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import java.util.List;
public final class RokakakaItem extends Item {
    public RokakakaItem(){super(new Settings().maxCount(64).rarity(Rarity.UNCOMMON));}
    @Override public TypedActionResult<ItemStack> use(World w,PlayerEntity p,Hand hand){
        if(!dev.sangwoo.celestialfall.map.MapGate.require(p))return TypedActionResult.fail(p.getStackInHand(hand));
        if(p instanceof ServerPlayerEntity s)CommerceServer.askReset(s,hand);
        return TypedActionResult.success(p.getStackInHand(hand));
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("사용 확인 후 현재 스탠드를 초기화합니다.").formatted(Formatting.LIGHT_PURPLE));
        lines.add(Text.literal("레벨 · 환생 · 초월 · 돈은 유지됩니다.").formatted(Formatting.GRAY));
        lines.add(Text.literal("손에 들고 클릭 또는 아이템 사용 키(R)").formatted(Formatting.DARK_GRAY));
    }
}
