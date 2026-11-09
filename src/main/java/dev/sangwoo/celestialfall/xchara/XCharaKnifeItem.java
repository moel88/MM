package dev.sangwoo.celestialfall.xchara;

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
 * X Chara's knife (2.111.0, after the photo the user sent: "검 모델링부터 보내줄게 대충 이걸 참고해"): a black blade, its spine
 * straight to the point and its belly curving up to it, on a thin black rod (tools/xchara/make_knife.py, GeckoLib), the
 * model 1.5 blocks in all (the photo's proportions) and a hand holding it twice that, 3 blocks, its fist round the rod's
 * middle ("검 크기 ㅈㄴ 키워야할듯 그리고 손잡이 가운데 부분 잡아야하고", the size the user's choice among in-game sizes; its
 * display, models/item/x_chara_knife.json). X Chara's player holds it, drawn in their hand whatever the hand holds
 * (StandClient.sword); nobody has it in an inventory, and it does nothing of itself.
 */
public final class XCharaKnifeItem extends Item implements GeoItem {
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.x_chara_knife.idle");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    /** Only ever made on a client (GeckoLib hands a dedicated server an empty one). */
    private final Supplier<Object> renderProvider=GeoItem.makeRenderer(this);
    public XCharaKnifeItem(){super(new Settings().maxCount(1).rarity(Rarity.EPIC).fireproof());}
    @Override public void createRenderer(Consumer<Object> consumer){
        consumer.accept(new RenderProvider(){
            private net.minecraft.client.render.item.BuiltinModelItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer(){
                if(renderer==null)renderer=new dev.sangwoo.celestialfall.client.xchara.XCharaKnifeRenderer();
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
