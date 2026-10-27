package dev.sangwoo.celestialfall.meteor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;
/** The pickaxe that mines fallen meteors (2.49.0), sold by the item seller. It never wears out. */
public final class MeteorPickaxeItem extends Item {
    public MeteorPickaxeItem(){super(new Settings().maxCount(1).rarity(Rarity.RARE));}
    /** It mines meteors only: the map's own blocks stay as they are. */
    @Override public boolean canMine(net.minecraft.block.BlockState state,World world,net.minecraft.util.math.BlockPos pos,net.minecraft.entity.player.PlayerEntity miner){return false;}
    @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,TooltipContext context){
        lines.add(Text.literal("떨어진 운석을 캐는 곡괭이입니다.").formatted(Formatting.AQUA));
        lines.add(Text.literal("손에 들고 운석을 좌클릭 · "+MeteorRules.HITS_PER_FRAGMENT+"번에 조각 1개").formatted(Formatting.GRAY));
        lines.add(Text.literal("닳지 않아요.").formatted(Formatting.DARK_GRAY));
    }
}
