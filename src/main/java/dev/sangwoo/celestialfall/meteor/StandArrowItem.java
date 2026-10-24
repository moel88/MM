package dev.sangwoo.celestialfall.meteor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;
/** The stand arrow (2.49.0): found in treasure chests and boxes or made at the crafter. It has no use yet (user's choice). */
public final class StandArrowItem extends Item {
    public StandArrowItem(){super(new Settings().maxCount(16).rarity(Rarity.EPIC).fireproof());}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("운석 조각으로 벼린 화살촉이 달린 화살입니다.").formatted(Formatting.LIGHT_PURPLE));
        lines.add(Text.literal("아직 쓸 곳이 없어요. 모아 두세요.").formatted(Formatting.GRAY));
        lines.add(Text.literal("보물상자 5% · 박스 0.1% · 제작소에서 제작").formatted(Formatting.DARK_GRAY));
    }
}
