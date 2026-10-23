package dev.sangwoo.celestialfall.meteor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;
/** A fragment mined out of a fallen meteor (2.49.0). */
public final class MeteorFragmentItem extends Item {
    public MeteorFragmentItem(){super(new Settings().maxCount(64).rarity(Rarity.UNCOMMON).fireproof());}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("떨어진 운석에서 캐낸 조각입니다. 아직 따뜻해요.").formatted(Formatting.GOLD));
        lines.add(Text.literal("제작소: 조각 "+MeteorRules.ARROW_FRAGMENTS+"개 + 막대기 "+MeteorRules.ARROW_STICKS+"개 → 스탠드 화살").formatted(Formatting.GRAY));
    }
}
