package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.WeightedBakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.state.property.Properties;
import net.minecraft.util.collection.Weighted;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Function;

/**
 * Leaves as the photos show them (2.48.0): a clipped hedge is a block whose six faces carry the dark mass of leaves,
 * with a dozen cards of leaf clusters stuck through it every way; a tree's crown and a cherry's blossom are only cards,
 * sixteen to a block, reaching past its edges so neighbouring blocks grow into one another and a crown has no cubic
 * faces or outline, its depth showing through its gaps. The cards are seen from both sides and take the light evenly
 * (not darkened by the way they look), as the game's own plants are. Three arrangements are made and the game picks
 * one by position, so a hedge does not repeat. Ivy is cards of leaves lying against the wall behind it; a cherry's limb
 * is a thin round branch, its trunk a thick one.
 */
final class FoliageModel {
    static final int VARIANTS=3;
    private static final String[] LEAF={"leaf_cards_0","leaf_cards_1","leaf_cards_2"},BLOSSOM={"blossom_cards_0","blossom_cards_1","blossom_cards_2"},IVY={"ivy_cards_0","ivy_cards_1"};

    static void register(){
        ModelLoadingPlugin.register(plugin->{
            CodeModel.resolve(plugin,TownBlocks.HEDGE,s->sp->mass(sp,"leaf_mass",LEAF,true,12,14,20,0x48ed6eL));
            CodeModel.resolve(plugin,TownBlocks.CANOPY,s->sp->mass(sp,"leaf_mass",LEAF,false,16,15,24,0x5a11fL));
            CodeModel.resolve(plugin,TownBlocks.CHERRY_BLOSSOM,s->sp->mass(sp,"blossom_mass",BLOSSOM,false,16,16,26,0x7b10c4L));
            CodeModel.resolve(plugin,TownBlocks.IVY,s->sp->ivy(sp,s.get(Properties.HORIZONTAL_FACING)));
            CodeModel.resolve(plugin,TownBlocks.CHERRY_BRANCH,s->sp->branch(sp,s.get(Properties.AXIS),s.get(TownBlocks.Branch.THICK)));
        });
    }

    /** A block of leaves: the mass on its faces where asked and cards of clusters through it, in three arrangements. */
    static BakedModel mass(Function<String,Sprite> sp,String massName,String[] cardNames,boolean faces,int cards,double sizeMin,double sizeMax,long seed){
        Sprite mass=sp.apply(massName);
        Sprite[] card=new Sprite[cardNames.length];
        for(int i=0;i<card.length;i++)card[i]=sp.apply(cardNames[i]);
        var r=new Random(seed);
        List<Weighted.Present<BakedModel>> variants=new ArrayList<>();
        for(int v=0;v<VARIANTS;v++){
            Mesh m=new Mesh();
            if(faces)m.box(new Mesh.Box(0,0,0,16,16,16).all(mass));
            m.shade(false);
            for(int k=0;k<cards;k++){
                double[] c={8+(r.nextDouble()-0.5)*9,8+(r.nextDouble()-0.5)*9,8+(r.nextDouble()-0.5)*9};
                m.card(card[r.nextInt(card.length)],c,unit(r),r.nextDouble()*Math.PI*2,sizeMin+r.nextDouble()*(sizeMax-sizeMin),r.nextBoolean());
            }
            variants.add(Weighted.of(m.bake(mass,true),1));
        }
        return new WeightedBakedModel(variants);
    }
    /** Ivy: cards of leaves hanging against the wall behind (the model's south), tilted a little every way. */
    static BakedModel ivy(Function<String,Sprite> sp,Direction facing){
        Sprite[] card={sp.apply(IVY[0]),sp.apply(IVY[1])};
        var r=new Random(0x1f3a7L);
        List<Weighted.Present<BakedModel>> variants=new ArrayList<>();
        for(int v=0;v<VARIANTS;v++){
            Mesh m=new Mesh(facing);
            m.shade(false);
            for(int k=0;k<8;k++){
                double[] c={8+(r.nextDouble()-0.5)*10,8+(r.nextDouble()-0.5)*10,14.2-r.nextDouble()*3};
                double[] n={(r.nextDouble()-0.5)*0.7,(r.nextDouble()-0.5)*0.7,-1};
                m.card(card[r.nextInt(card.length)],c,n,(r.nextDouble()-0.5)*0.7,13+r.nextDouble()*7,r.nextBoolean());
            }
            variants.add(Weighted.of(m.bake(card[0],false),1));
        }
        return new WeightedBakedModel(variants);
    }
    /** A cherry's limb: a round branch five sixteenths across along its axis (the trunk eleven), in the cherry log's bark. */
    static BakedModel branch(Function<String,Sprite> sp,Direction.Axis axis,boolean thick){
        Mesh m=new Mesh();
        Sprite bark=sp.apply("minecraft:cherry_log");
        m.cylinder(bark,sp.apply("minecraft:cherry_log_top"),axis,8,8,thick?5.5:2.5,0,16,thick?12:8,true,true);
        return m.bake(bark,true);
    }
    private static double[] unit(Random r){
        double z=r.nextDouble()*2-1,a=r.nextDouble()*Math.PI*2,s=Math.sqrt(1-z*z);
        return new double[]{s*Math.cos(a),z,s*Math.sin(a)};
    }
}
