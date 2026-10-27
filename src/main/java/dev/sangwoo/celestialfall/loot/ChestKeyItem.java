package dev.sangwoo.celestialfall.loot;
import net.minecraft.item.*;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;
public final class ChestKeyItem extends Item {
    public ChestKeyItem(){super(new Settings().maxCount(64).rarity(Rarity.UNCOMMON));}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("금색 보물상자를 여는 열쇠입니다.").formatted(Formatting.GOLD));
        lines.add(Text.literal("가까이서 E 길게 누르기 · 열쇠 1개 소모").formatted(Formatting.GRAY));
        lines.add(Text.literal("손에 들지 않아도 인벤토리에서 사용됩니다.").formatted(Formatting.DARK_GRAY));
    }
}
