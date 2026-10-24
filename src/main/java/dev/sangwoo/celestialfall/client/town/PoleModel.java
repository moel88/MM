package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Function;

/**
 * Round posts (2.47.0): the concrete utility pole with its climbing bolts, its yellow-and-black sleeve, the white pipe
 * posts, and the pole through a crossarm, built in code as cylinders of sixteen sides, where block models could only
 * make them square. The texture wraps once round each; the ends are capped and hidden where a post stands on another.
 * A crossarm's steel arm and insulators are boxes along its axis.
 */
public final class PoleModel implements UnbakedModel {
    /** A box in sixteenths for a crossarm lying along x (turned for one along z), and its texture. */
    record Box(float x0,float y0,float z0,float x1,float y1,float z1,String texture){}
    static final int SIDES=16;
    private final String texture;
    private final float radius;
    private final boolean bolts;
    private final List<Box> boxes;
    private final boolean alongZ;

    PoleModel(String texture,float radius,boolean bolts,List<Box> boxes,boolean alongZ){
        this.texture=texture;this.radius=radius;this.bolts=bolts;this.boxes=boxes;this.alongZ=alongZ;
    }

    public static void register(){
        String pole="celestialfall:block/town/concrete_pole",sleeve="celestialfall:block/town/pole_sleeve",white="celestialfall:block/town/white_steel",
            steel="celestialfall:block/town/steel",porcelain="celestialfall:block/town/porcelain";
        ModelLoadingPlugin.register(plugin->{
            one(plugin,TownBlocks.UTILITY_POLE,s->new PoleModel(pole,4.2f,true,List.of(),false));
            one(plugin,TownBlocks.POLE_SLEEVE,s->new PoleModel(sleeve,4.7f,false,List.of(),false));
            one(plugin,TownBlocks.PIPE_POST,s->new PoleModel(white,1.15f,false,List.of(),false));
            one(plugin,TownBlocks.MIRROR_POST,s->new PoleModel("celestialfall:block/town/mirror_orange",0.9f,false,List.of(),false));
            // the steel arm through the pole and its two insulators
            var arm=List.of(new Box(0,11,7,16,13.5f,9,steel),new Box(1.5f,13.5f,7.25f,3.5f,16,8.75f,porcelain),new Box(12.5f,13.5f,7.25f,14.5f,16,8.75f,porcelain));
            one(plugin,TownBlocks.POLE_CROSSARM,s->new PoleModel(pole,4.2f,false,arm,s.get(Properties.HORIZONTAL_AXIS)==Direction.Axis.Z));
        });
    }
    private static void one(ModelLoadingPlugin.Context plugin,Block b,Function<BlockState,PoleModel> make){
        plugin.registerBlockStateResolver(b,ctx->{for(var s:b.getStateManager().getStates())ctx.setModel(s,make.apply(s));});
    }

    @Override public Collection<Identifier> getModelDependencies(){return List.of();}
    @Override public void setParents(Function<Identifier,UnbakedModel> loader){}

    @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> textures,ModelBakeSettings settings,Identifier id){
        Sprite side=sprite(textures,texture);
        List<BakedQuad> quads=new ArrayList<>();
        Map<Direction,List<BakedQuad>> faces=new EnumMap<>(Direction.class);
        for(var d:Direction.values())faces.put(d,new ArrayList<>());
        double r=radius/16.0;
        for(int k=0;k<SIDES;k++){
            double a0=k*Math.PI*2/SIDES,a1=(k+1)*Math.PI*2/SIDES;
            double x0=0.5+r*Math.cos(a0),z0=0.5+r*Math.sin(a0),x1=0.5+r*Math.cos(a1),z1=0.5+r*Math.sin(a1);
            double mid=(a0+a1)/2;
            var face=Direction.getFacing((float)Math.cos(mid),0,(float)Math.sin(mid));
            float u0=16f*k/SIDES,u1=16f*(k+1)/SIDES;
            // listed to face out of the post (anticlockwise seen from outside)
            quads.add(RoofModel.quad(side,face,new double[][]{{x1,1,z1},{x1,0,z1},{x0,0,z0},{x0,1,z0}},new float[][]{{u1,0},{u1,16},{u0,16},{u0,0}}));
            // the ends: a fan from the middle, dropped where another post stands on this one
            faces.get(Direction.UP).add(RoofModel.quad(side,Direction.UP,new double[][]{{0.5,1,0.5},{x1,1,z1},{x0,1,z0},{x0,1,z0}},
                new float[][]{{8,8},{(float)(8+8*r*2*Math.cos(a1)),(float)(8+8*r*2*Math.sin(a1))},{(float)(8+8*r*2*Math.cos(a0)),(float)(8+8*r*2*Math.sin(a0))},{(float)(8+8*r*2*Math.cos(a0)),(float)(8+8*r*2*Math.sin(a0))}}));
            faces.get(Direction.DOWN).add(RoofModel.quad(side,Direction.DOWN,new double[][]{{0.5,0,0.5},{x0,0,z0},{x1,0,z1},{x1,0,z1}},
                new float[][]{{8,8},{(float)(8+8*r*2*Math.cos(a0)),(float)(8+8*r*2*Math.sin(a0))},{(float)(8+8*r*2*Math.cos(a1)),(float)(8+8*r*2*Math.sin(a1))},{(float)(8+8*r*2*Math.cos(a1)),(float)(8+8*r*2*Math.sin(a1))}}));
        }
        Sprite steel=sprite(textures,"celestialfall:block/town/steel");
        if(bolts){
            // climbing bolts, one a side at different heights, east and west
            box(quads,steel,0.5+r-0.02,0.22,7.5/16,0.5+r+3/16.0,0.28,8.5/16);
            box(quads,steel,0.5-r-3/16.0,0.72,7.5/16,0.5-r+0.02,0.78,8.5/16);
        }
        for(var b:boxes){
            Sprite s=sprite(textures,b.texture());
            if(alongZ)box(quads,s,b.z0()/16.0,b.y0()/16.0,b.x0()/16.0,b.z1()/16.0,b.y1()/16.0,b.x1()/16.0);
            else box(quads,s,b.x0()/16.0,b.y0()/16.0,b.z0()/16.0,b.x1()/16.0,b.y1()/16.0,b.z1()/16.0);
        }
        Map<Direction,List<BakedQuad>> fixed=new EnumMap<>(Direction.class);
        for(var e:faces.entrySet())fixed.put(e.getKey(),List.copyOf(e.getValue()));
        return new BasicBakedModel(List.copyOf(quads),fixed,true,true,false,side,ModelTransformation.NONE,ModelOverrideList.EMPTY);
    }
    private static Sprite sprite(Function<SpriteIdentifier,Sprite> textures,String id){
        return textures.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,new Identifier(id)));
    }
    /** All six faces of a box (blocks), the texture laid over each face's own extent. */
    private static void box(List<BakedQuad> out,Sprite s,double x0,double y0,double z0,double x1,double y1,double z1){
        float a=(float)(x0*16),b=(float)(x1*16),c=(float)(z0*16),d=(float)(z1*16),v0=clamp((1-y1)*16),v1=clamp((1-y0)*16);
        if(v1-v0<0.5f)v1=v0+0.5f;
        out.add(RoofModel.quad(s,Direction.UP,new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},new float[][]{{a,c},{a,d},{b,d},{b,c}}));
        out.add(RoofModel.quad(s,Direction.DOWN,new double[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},new float[][]{{a,d},{a,c},{b,c},{b,d}}));
        out.add(RoofModel.quad(s,Direction.NORTH,new double[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}},new float[][]{{16-b,v0},{16-b,v1},{16-a,v1},{16-a,v0}}));
        out.add(RoofModel.quad(s,Direction.SOUTH,new double[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}},new float[][]{{a,v0},{a,v1},{b,v1},{b,v0}}));
        out.add(RoofModel.quad(s,Direction.WEST,new double[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}},new float[][]{{c,v0},{c,v1},{d,v1},{d,v0}}));
        out.add(RoofModel.quad(s,Direction.EAST,new double[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}},new float[][]{{16-d,v0},{16-d,v1},{16-c,v1},{16-c,v0}}));
    }
    private static float clamp(double v){return (float)Math.max(0,Math.min(16,v));}
}
