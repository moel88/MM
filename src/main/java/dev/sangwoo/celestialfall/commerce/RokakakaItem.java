package dev.sangwoo.celestialfall.commerce;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
/** The Rokakaka: resets the stand after a confirmation. Drawn in 3D with its GeckoLib model (2.41.0, user request). */
public final class RokakakaItem extends Item implements GeoItem {
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.rokakaka.idle");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    /** Only ever made on a client (GeckoLib hands a dedicated server an empty one). */
    private final Supplier<Object> renderProvider=GeoItem.makeRenderer(this);
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
    @Override public void createRenderer(Consumer<Object> consumer){
        consumer.accept(new RenderProvider(){
            private net.minecraft.client.render.item.BuiltinModelItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer(){
                if(renderer==null)renderer=new dev.sangwoo.celestialfall.client.commerce.RokakakaRenderer();
                return renderer;
            }
        });
    }
    @Override public Supplier<Object> getRenderProvider(){return renderProvider;}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"idle",0,state->state.setAndContinue(IDLE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
