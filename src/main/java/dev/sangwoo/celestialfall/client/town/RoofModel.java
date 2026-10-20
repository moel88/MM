package dev.sangwoo.celestialfall.client.town;

import dev.sangwoo.celestialfall.town.RoofBlock;
import dev.sangwoo.celestialfall.town.TownBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Function;

/**
 * The look of a roof cell at its true pitch (RoofBlock), built in code because a block model's parts can only tilt in
 * steps of 22.5 degrees. The top is the roof's surface over the cell: one quad on a plain slope, else eight triangles
 * meeting at the cell's middle, which follow every crease a ridge or a hip can put across a cell. Under it the plate's
 * underside (the eaves' soffit), or where the cell is filled the space down to its floor, faced like the walls; round
 * its sides the plate's edge (the fascia at the eaves, the verge at the gables); along a ridge a cap of tiles. The tiles'
 * channels and a sheet's ribs run down the slope on each facet. Every face is lit and shaded by the game as any block's,
 * and goes through the plain quad path, so Sodium and shader packs draw it as they draw the vanilla blocks.
 */
public final class RoofModel implements UnbakedModel {
    /** A ridge cap's half width, its height above the ridge, and how far it sinks into the slopes (blocks). */
    static final float CAP_HALF=2.5f/16,CAP_UP=2f/16,CAP_DOWN=1.5f/16;
    private final RoofBlock block;
    private final BlockState state;
    private final String[] look;

    RoofModel(RoofBlock block,BlockState state,String[] look){this.block=block;this.state=state;this.look=look;}

    /** Hands every roof block's states to the game as models of their own (no block state files). */
    public static void register(){
        ModelLoadingPlugin.register(plugin->{
            for(var e:TownBlocks.ROOFS.entrySet()){
                var b=e.getKey();var look=e.getValue();
                plugin.registerBlockStateResolver(b,ctx->{for(var s:b.getStateManager().getStates())ctx.setModel(s,new RoofModel(b,s,look));});
            }
        });
    }

    @Override public Collection<Identifier> getModelDependencies(){return List.of();}
    @Override public void setParents(Function<Identifier,UnbakedModel> loader){}

    @Override public BakedModel bake(Baker baker,Function<SpriteIdentifier,Sprite> textures,ModelBakeSettings settings,Identifier id){
        Sprite top=sprite(textures,look[0]),side=sprite(textures,look[1]),under=sprite(textures,look[2]),cap=sprite(textures,look[3]);
        var quads=new ArrayList<BakedQuad>();
        float t=block.thickness;
        boolean filled=state.get(RoofBlock.FILLED);
        double[][] h=new double[3][3];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)h[i][j]=block.height(state,i*0.5,j*0.5);
        surface(quads,top,h,0,true);
        if(!filled)surface(quads,under,h,-t,false);
        // a filled cell's floor: the ceiling of the room under it, where the roof sits right on the top floor
        else quads.add(quad(under,Direction.DOWN,new double[][]{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},new float[][]{{0,0},{16,0},{16,16},{0,16}}));
        // the four sides: north (v=0), south (v=1), west (u=0), east (u=1)
        edge(quads,h,t,filled,side,under,Direction.NORTH);edge(quads,h,t,filled,side,under,Direction.SOUTH);
        edge(quads,h,t,filled,side,under,Direction.WEST);edge(quads,h,t,filled,side,under,Direction.EAST);
        caps(quads,cap);
        Map<Direction,List<BakedQuad>> faces=new EnumMap<>(Direction.class);
        for(var d:Direction.values())faces.put(d,List.of());
        return new BasicBakedModel(List.copyOf(quads),faces,true,true,false,top,ModelTransformation.NONE,ModelOverrideList.EMPTY);
    }
    private static Sprite sprite(Function<SpriteIdentifier,Sprite> textures,String id){
        return textures.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,new Identifier(id)));
    }

    /** The top (or, lowered by the plate's thickness and facing down, the underside) over the whole cell. */
    private static void surface(List<BakedQuad> out,Sprite sprite,double[][] h,double drop,boolean up){
        boolean planar=Math.abs(h[0][0]+h[2][2]-h[2][0]-h[0][2])<1e-6&&Math.abs(2*h[1][1]-h[0][0]-h[2][2])<1e-6
            &&Math.abs(2*h[1][0]-h[0][0]-h[2][0])<1e-6&&Math.abs(2*h[0][1]-h[0][0]-h[0][2])<1e-6&&Math.abs(2*h[2][1]-h[2][0]-h[2][2])<1e-6&&Math.abs(2*h[1][2]-h[0][2]-h[2][2])<1e-6;
        if(planar){
            facet(out,sprite,up,new double[][]{{0,h[0][0]+drop,0},{0,h[0][2]+drop,1},{1,h[2][2]+drop,1},{1,h[2][0]+drop,0}});
            return;
        }
        // eight triangles, each quarter of the cell cut along the line from its corner to the middle
        for(int ci=0;ci<=2;ci+=2)for(int cj=0;cj<=2;cj+=2){
            double[] c={ci/2.0,h[ci][cj]+drop,cj/2.0},mu={0.5,h[1][cj]+drop,cj/2.0},mv={ci/2.0,h[ci][1]+drop,0.5},o={0.5,h[1][1]+drop,0.5};
            facet(out,sprite,up,new double[][]{c,mu,o,o});
            facet(out,sprite,up,new double[][]{c,o,mv,mv});
        }
    }
    /**
     * One flat piece of a surface (a quad, or a triangle with its last corner repeated), turned to face up or down,
     * its texture laid with the picture's width running down the piece's own slope from its low edge.
     */
    private static void facet(List<BakedQuad> out,Sprite sprite,boolean up,double[][] p){
        boolean tri=p[2]==p[3];
        double[] n=normal(p);
        if((n[1]>0)!=up){
            if(tri){double[] t=p[1];p[1]=p[2];p[2]=t;p[3]=t;}else{double[] t=p[1];p[1]=p[3];p[3]=t;}
            n=normal(p);
        }
        // which way this piece falls, from its plane: height = y0 + gu * u + gv * v
        double gu=Math.abs(n[1])>1e-9?-n[0]/n[1]:0,gv=Math.abs(n[1])>1e-9?-n[2]/n[1]:0;
        boolean alongV=Math.abs(gv)>=Math.abs(gu);
        float[][] uv=new float[4][2];
        for(int k=0;k<4;k++){
            double u=p[k][0],v=p[k][2];
            uv[k][0]=(float)(16*(alongV?(gv>=0?v:1-v):(gu>=0?u:1-u)));
            uv[k][1]=(float)(16*(alongV?u:v));
        }
        out.add(quad(sprite,up?Direction.UP:Direction.DOWN,p,uv));
    }

    /**
     * One side of the cell: the plate's edge from its underside to its top (the side texture's top rows), and where
     * the cell is filled, the wall face below it down to the floor (the under texture, laid as the walls below are).
     */
    private static void edge(List<BakedQuad> out,double[][] h,float t,boolean filled,Sprite side,Sprite under,Direction d){
        // the three heights along this side, from one end to the other, and where they lie
        double[][] at=new double[3][2];double[] top=new double[3];
        for(int k=0;k<3;k++){
            double s=k*0.5;
            double u=switch(d){case WEST->0;case EAST->1;default->s;},v=switch(d){case NORTH->0;case SOUTH->1;default->s;};
            at[k][0]=u;at[k][1]=v;top[k]=h[(int)Math.round(u*2)][(int)Math.round(v*2)];
        }
        boolean straight=Math.abs(2*top[1]-top[0]-top[2])<1e-6;
        int[][] spans=straight?new int[][]{{0,2}}:new int[][]{{0,1},{1,2}};
        for(int[] sp:spans){
            int a=sp[0],b=sp[1];
            double ta=top[a],tb=top[b],ba=ta-t,bb=tb-t;
            band(out,side,d,at[a],at[b],ba,bb,ta,tb,true);
            if(filled&&(ba>1e-6||bb>1e-6))band(out,under,d,at[a],at[b],0,0,Math.max(0,ba),Math.max(0,bb),false);
        }
    }
    /** A vertical strip between two points on a side, from its low to its high heights at each, facing out of the cell. */
    private static void band(List<BakedQuad> out,Sprite sprite,Direction d,double[] a,double[] b,double lowA,double lowB,double highA,double highB,boolean edge){
        double[][] p={{a[0],highA,a[1]},{a[0],lowA,a[1]},{b[0],lowB,b[1]},{b[0],highB,b[1]}};
        boolean z=d.getAxis()==Direction.Axis.Z;
        float ua=(float)(16*(z?a[0]:a[1])),ub=(float)(16*(z?b[0]:b[1]));
        // an edge takes the texture's top rows under its own top; a filled wall is laid as the walls below it are
        float[][] uv=edge?new float[][]{{ua,0},{ua,(float)(16*(highA-lowA))},{ub,(float)(16*(highB-lowB))},{ub,0}}
            :new float[][]{{ua,wall(highA)},{ua,wall(lowA)},{ub,wall(lowB)},{ub,wall(highB)}};
        double[] n=normal(p);
        if(n[0]*d.getOffsetX()+n[2]*d.getOffsetZ()<0){
            double[] t=p[1];p[1]=p[3];p[3]=t;float[] w=uv[1];uv[1]=uv[3];uv[3]=w;
        }
        out.add(quad(sprite,d,p,uv));
    }
    private static float wall(double y){return (float)Math.max(0,Math.min(16,16*(1-y)));}
    /** A quad's normal from its diagonals (sound for triangles and for quads with a corner repeated). */
    static double[] normal(double[][] p){
        double ax=p[2][0]-p[0][0],ay=p[2][1]-p[0][1],az=p[2][2]-p[0][2],bx=p[3][0]-p[1][0],by=p[3][1]-p[1][1],bz=p[3][2]-p[1][2];
        return new double[]{ay*bz-az*by,az*bx-ax*bz,ax*by-ay*bx};
    }

    /** The ridge cap of tiles: along a ridge, from the middle to the end of a ridge, a knob on a peak, half of one on a slope's top edge. */
    private void caps(List<BakedQuad> out,Sprite sprite){
        var shape=state.get(RoofBlock.SHAPE);var f=state.get(RoofBlock.FACING);
        float p=block.pitch,b=state.get(RoofBlock.BASE)/4f;
        double[] box=switch(shape){
            case RIDGE->new double[]{0,0.5-CAP_HALF,1,0.5+CAP_HALF,b+p*0.5};
            case RIDGE_END->new double[]{0.5-CAP_HALF,0.5-CAP_HALF,0.5+CAP_HALF,1,b+p*0.5};
            case PEAK->new double[]{0.5-CAP_HALF,0.5-CAP_HALF,0.5+CAP_HALF,0.5+CAP_HALF,b+p*0.5};
            case SLOPE->state.get(RoofBlock.CAP)?new double[]{0,1-CAP_HALF,1,1,b+p}:null;
            default->null;
        };
        if(box==null)return;
        // turn the box from the shape's own frame (facing north) to its facing
        double[] c0=turn(box[0],box[1],f),c1=turn(box[2],box[3],f);
        double x0=Math.min(c0[0],c1[0]),x1=Math.max(c0[0],c1[0]),z0=Math.min(c0[1],c1[1]),z1=Math.max(c0[1],c1[1]);
        double y0=box[4]-CAP_DOWN,y1=box[4]+CAP_UP;
        box(out,sprite,x0,y0,z0,x1,y1,z1);
    }
    private static double[] turn(double cu,double cv,Direction f){
        return switch(f){
            case EAST->new double[]{1-cv,cu};
            case SOUTH->new double[]{1-cu,1-cv};
            case WEST->new double[]{cv,1-cu};
            default->new double[]{cu,cv};
        };
    }
    /** A box's five faces that can be seen (not its floor), each with the texture laid over its own extent. */
    private static void box(List<BakedQuad> out,Sprite s,double x0,double y0,double z0,double x1,double y1,double z1){
        float u0=(float)(x0*16),u1=(float)(x1*16),w0=(float)(z0*16),w1=(float)(z1*16),v0=(float)Math.max(0,Math.min(16,(1-y1)*16)),v1=(float)Math.max(0,Math.min(16,(1-y0)*16));
        if(v1-v0<1)v1=v0+1;
        out.add(quad(s,Direction.UP,new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},new float[][]{{u0,w0},{u0,w1},{u1,w1},{u1,w0}}));
        out.add(quad(s,Direction.NORTH,new double[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}},new float[][]{{16-u1,v0},{16-u1,v1},{16-u0,v1},{16-u0,v0}}));
        out.add(quad(s,Direction.SOUTH,new double[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}},new float[][]{{u0,v0},{u0,v1},{u1,v1},{u1,v0}}));
        out.add(quad(s,Direction.WEST,new double[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}},new float[][]{{w0,v0},{w0,v1},{w1,v1},{w1,v0}}));
        out.add(quad(s,Direction.EAST,new double[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}},new float[][]{{16-w1,v0},{16-w1,v1},{16-w0,v1},{16-w0,v0}}));
    }

    /** A baked quad in the block format: position, white, the sprite's coordinates, no light (the game lights it), its normal. */
    static BakedQuad quad(Sprite sprite,Direction face,double[][] p,float[][] uv){
        int[] data=new int[32];
        double[] n=normal(p);
        double nx=n[0],ny=n[1],nz=n[2],l=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(l<1e-12){nx=face.getOffsetX();ny=face.getOffsetY();nz=face.getOffsetZ();l=1;}
        int normal=((int)Math.round(nx/l*127)&0xFF)|(((int)Math.round(ny/l*127)&0xFF)<<8)|(((int)Math.round(nz/l*127)&0xFF)<<16);
        for(int k=0;k<4;k++){
            int o=k*8;
            data[o]=Float.floatToRawIntBits((float)p[k][0]);data[o+1]=Float.floatToRawIntBits((float)p[k][1]);data[o+2]=Float.floatToRawIntBits((float)p[k][2]);
            data[o+3]=-1;
            data[o+4]=Float.floatToRawIntBits(sprite.getFrameU(Math.max(0,Math.min(16,uv[k][0]))));
            data[o+5]=Float.floatToRawIntBits(sprite.getFrameV(Math.max(0,Math.min(16,uv[k][1]))));
            data[o+6]=0;data[o+7]=normal;
        }
        return new BakedQuad(data,-1,face,sprite,true);
    }
}
