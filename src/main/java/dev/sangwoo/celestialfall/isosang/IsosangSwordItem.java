package dev.sangwoo.celestialfall.isosang;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.item.Item;
import net.minecraft.util.Rarity;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Lee So-sang's sword (2.76.0, user request: "일단 검은 이렇게 생겼어", after the art the user sent, and "검 모델링은 GeckoLib로
 * 뽑아줘"): drawn with its GeckoLib model (tools/isosang/make_sword.py), its lightning glowing. Lee So-sang's player holds
 * it, drawn in their hand whatever the hand holds (StandClient.sword); nobody has it in an inventory, and it does nothing
 * of itself.
 */
public final class IsosangSwordItem extends Item implements GeoItem {
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.isosang_sword.idle");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    /** Only ever made on a client (GeckoLib hands a dedicated server an empty one). */
    private final Supplier<Object> renderProvider=GeoItem.makeRenderer(this);
    public IsosangSwordItem(){super(new Settings().maxCount(1).rarity(Rarity.EPIC).fireproof());}
    @Override public void createRenderer(Consumer<Object> consumer){
        consumer.accept(new RenderProvider(){
            private net.minecraft.client.render.item.BuiltinModelItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer(){
                if(renderer==null)renderer=new dev.sangwoo.celestialfall.client.isosang.IsosangSwordRenderer();
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
