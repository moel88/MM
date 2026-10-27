package dev.sangwoo.celestialfall.meteor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;
/**
 * The stand arrow (2.49.0): found in treasure chests and boxes or made at the crafter. Since 2.70.0 it is used: one with
 * no stand drives it into their own chest, and it kills them or awakens a stand (StandArrow, StandArrowRules).
 */
public final class StandArrowItem extends Item {
    public StandArrowItem(){super(new Settings().maxCount(16).rarity(Rarity.EPIC).fireproof());}
    /** R on the map: the server decides (StandArrow.use). The hand never swings for it: the stab is its own motion. */
    @Override public TypedActionResult<ItemStack> use(World w,PlayerEntity p,Hand hand){
        var stack=p.getStackInHand(hand);
        if(p instanceof ServerPlayerEntity s)return dev.sangwoo.celestialfall.stand.StandArrow.use(s,hand)?TypedActionResult.consume(stack):TypedActionResult.fail(stack);
        return TypedActionResult.consume(stack);
    }
    /** A share of the arrow's table as a percentage ("20%", "2.5%"). */
    private static String percent(int share){
        double p=share*100.0/dev.sangwoo.celestialfall.stand.StandArrowRules.ODDS;
        return (p==Math.rint(p)?String.valueOf((long)p):String.format(java.util.Locale.ROOT,"%.1f",p))+"%";
    }
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("운석 조각으로 벼린 화살촉이 달린 화살입니다.").formatted(Formatting.LIGHT_PURPLE));
        lines.add(Text.literal("몸에 꽂으면 화살이 고릅니다: 스탠드에 각성하거나, 죽거나.").formatted(Formatting.GRAY));
        var odds=new StringBuilder("사망 "+percent(dev.sangwoo.celestialfall.stand.StandArrowRules.DEATH));
        for(var a:dev.sangwoo.celestialfall.stand.StandArrowRules.STANDS)odds.append(" · ").append(dev.sangwoo.celestialfall.progression.StandStats.get(a.stand()).korean()).append(' ').append(percent(a.weight()));
        lines.add(Text.literal(odds.toString()).formatted(Formatting.GOLD));
        lines.add(Text.literal("스탠드가 없을 때 손에 들고 아이템 사용 키(R)").formatted(Formatting.DARK_GRAY));
        lines.add(Text.literal("보물상자 5% · 박스 0.1% · 제작소에서 제작").formatted(Formatting.DARK_GRAY));
    }
}
