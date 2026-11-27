package dev.sangwoo.celestialfall.client.stand;

import dev.sangwoo.celestialfall.CelestialFall;
import dev.sangwoo.celestialfall.stand.GoldTreeEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Gold Experience's tree as it grows, stands and withers (2.152.0): its GeckoLib model (tools/stand/make_gold_tree.py),
 * each branch scaled from its foot by how grown it is on the tree's clock (GoldTreeGrowth), stirring in the wind once
 * grown, jolting when a blow on it comes back on its striker, and going back into the ground as it withers, the twigs
 * first and the trunk last. Every frame from the clock: nothing jumps.
 */
public final class GoldTreeModel extends GeoModel<GoldTreeEntity> {
    static final Identifier MODEL=CelestialFall.id("geo/entity/gold_tree.geo.json"),
        TEXTURE=CelestialFall.id("textures/entity/gold_tree.png"),
        ANIMATION=CelestialFall.id("animations/entity/gold_tree.animation.json");
    private final float[] sway=new float[2];

    @Override public Identifier getModelResource(GoldTreeEntity e){return MODEL;}
    @Override public Identifier getTextureResource(GoldTreeEntity e){return TEXTURE;}
    @Override public Identifier getAnimationResource(GoldTreeEntity e){return ANIMATION;}

    @Override public void setCustomAnimations(GoldTreeEntity e,long id,AnimationState<GoldTreeEntity> state){
        float delta=state.getPartialTick();
        double age=e.clientAge(delta),wither=e.witherAt()-e.born();
        double since=GoldTree.jolted(e.getId(),delta);
        var bones=getAnimationProcessor();
        for(var g:GoldTreeGrowth.GROWERS){
            CoreGeoBone bone=bones.getBone(g.bone());if(bone==null)continue;
            var rest=bone.getInitialSnapshot();if(rest==null)continue;
            float s=GoldTreeGrowth.size(age,wither,g.depth(),g.order());
            bone.setScaleX(s);bone.setScaleY(s);bone.setScaleZ(s);
            GoldTreeGrowth.sway(age,g.depth(),g.order(),sway);
            float j=GoldTreeGrowth.jolt(since,g.depth())*(g.order()%2==0?1:-.8f);
            bone.setRotX(rest.getRotX()+(float)Math.toRadians(sway[0]+j));
            bone.setRotZ(rest.getRotZ()+(float)Math.toRadians(sway[1]+.6f*j));
        }
        var roots=bones.getBone("roots");
        if(roots!=null){float r=Math.max(.001f,GoldTreeGrowth.roots(age,wither));roots.setScaleX(r);roots.setScaleZ(r);roots.setScaleY(Math.max(.001f,Math.min(1,r*1.6f)));}
    }
}
