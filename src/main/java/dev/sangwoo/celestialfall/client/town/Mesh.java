package dev.sangwoo.celestialfall.client.town;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Builds a block's model in code (2.48.0), where a block model file could not: boxes with a texture rect of their own
 * on each face, flat cards seen from both sides (leaves, blossom, glass), many-sided cylinders and discs, all given in
 * sixteenths of a block for a thing facing north and turned to its block's facing. A face lying on a side of the block
 * is kept under that side, so the game drops it where a neighbour covers it; everything else is always drawn. The
 * quads go through the game's own block path (BakedQuad), so Sodium and shader packs draw them as any block's.
 */
final class Mesh {
    /** A box in sixteenths, each face with a sprite (none drops the face) and, where given, the rect of it to lay over the face. */
    static final class Box {
        final double x0,y0,z0,x1,y1,z1;
        final Sprite[] sprites=new Sprite[6];
        final float[][] rects=new float[6][];
        Box(double x0,double y0,double z0,double x1,double y1,double z1){this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;}
        Box all(Sprite s){Arrays.fill(sprites,s);return this;}
        Box face(Direction d,Sprite s){sprites[d.ordinal()]=s;rects[d.ordinal()]=null;return this;}
        /** One face's sprite and the rect of it over the face: left, top, right, bottom in sixteenths, seen from outside. */
        Box face(Direction d,Sprite s,double u0,double v0,double u1,double v1){sprites[d.ordinal()]=s;rects[d.ordinal()]=new float[]{(float)u0,(float)v0,(float)u1,(float)v1};return this;}
        Box skip(Direction... ds){for(var d:ds)sprites[d.ordinal()]=null;return this;}
    }

    final List<BakedQuad> loose=new ArrayList<>();
    final Map<Direction,List<BakedQuad>> sides=new EnumMap<>(Direction.class);
    private final Direction facing;
    private boolean shade=true;
    private UnaryOperator<double[]> warp;

    /** A mesh for a block facing the given way: everything is given facing north and turned. */
    Mesh(Direction facing){this.facing=facing;for(var d:Direction.values())sides.put(d,new ArrayList<>());}
    Mesh(){this(Direction.NORTH);}

    /** A block sprite by name: the town's own ({@code leaf_mass}) or a vanilla block's ({@code minecraft:cherry_log}). */
    static Sprite sprite(Function<SpriteIdentifier,Sprite> textures,String name){
        int c=name.indexOf(':');
        Identifier id=c>=0?new Identifier(name.substring(0,c),"block/"+name.substring(c+1)):new Identifier("celestialfall","block/town/"+name);
        return textures.apply(new SpriteIdentifier(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,id));
    }

    /** Whether the quads added from now on are darkened by the way they look (a card of leaves is not). */
    Mesh shade(boolean on){shade=on;return this;}
    /** A bend applied to every point added from now on (in sixteenths, before the turn to the facing), or none. */
    Mesh warp(UnaryOperator<double[]> f){warp=f;return this;}

    private double[] turn(double[] p){
        if(warp!=null)p=warp.apply(p);
        double x=p[0]/16,y=p[1]/16,z=p[2]/16;
        return switch(facing){
            case EAST->new double[]{1-z,y,x};
            case SOUTH->new double[]{1-x,y,1-z};
            case WEST->new double[]{z,y,1-x};
            default->new double[]{x,y,z};
        };
    }
    private Direction turn(Direction d){
        if(d.getAxis()==Direction.Axis.Y)return d;
        return switch(facing){case EAST->d.rotateYClockwise();case SOUTH->d.getOpposite();case WEST->d.rotateYCounterclockwise();default->d;};
    }

    /**
     * A quad: four corners in sixteenths, anticlockwise seen from its front, each with its texture coordinates in
     * sixteenths of the sprite. {@code face} is the way it looks (null: taken from the corners); {@code onSide} true
     * where it lies on that side of the block and may be dropped against a neighbour.
     */
    void quad(Sprite s,Direction face,double[][] p,float[][] uv,boolean onSide){
        double[][] q=new double[4][];
        for(int k=0;k<4;k++)q[k]=turn(p[k]);
        Direction f;
        if(face!=null)f=turn(face);
        else{double[] n=RoofModel.normal(q);f=Direction.getFacing((float)n[0],(float)n[1],(float)n[2]);}
        BakedQuad b=bake(s,f,q,uv,shade);
        if(onSide)sides.get(f).add(b);else loose.add(b);
    }
    /** A flat piece seen from both sides (its texture mirrored on the back). */
    void card(Sprite s,double[][] p,float[][] uv){
        quad(s,null,p,uv,false);
        quad(s,null,new double[][]{p[3],p[2],p[1],p[0]},new float[][]{uv[3],uv[2],uv[1],uv[0]},false);
    }
    /** A square card of the given size at a centre, looking along {@code n}, rolled by {@code roll}, the whole sprite over it (mirrored when asked). */
    void card(Sprite s,double[] c,double[] n,double roll,double size,boolean mirror){
        double l=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
        double nx=n[0]/l,ny=n[1]/l,nz=n[2]/l;
        // a direction across the card: anything not along n, then rolled round n
        double ax=Math.abs(ny)<0.9?0:1,ay=Math.abs(ny)<0.9?1:0,az=0;
        double ux=ay*nz-az*ny,uy=az*nx-ax*nz,uz=ax*ny-ay*nx;
        double ul=Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=ul;uy/=ul;uz/=ul;
        double wx=ny*uz-nz*uy,wy=nz*ux-nx*uz,wz=nx*uy-ny*ux;
        double cr=Math.cos(roll),sr=Math.sin(roll);
        double rx=ux*cr+wx*sr,ry=uy*cr+wy*sr,rz=uz*cr+wz*sr;
        double tx=wx*cr-ux*sr,ty=wy*cr-uy*sr,tz=wz*cr-uz*sr;
        // across = r, up = t; the front (r x t) looks along n
        double h=size/2;
        double[][] p={{c[0]-h*rx+h*tx,c[1]-h*ry+h*ty,c[2]-h*rz+h*tz},{c[0]-h*rx-h*tx,c[1]-h*ry-h*ty,c[2]-h*rz-h*tz},
            {c[0]+h*rx-h*tx,c[1]+h*ry-h*ty,c[2]+h*rz-h*tz},{c[0]+h*rx+h*tx,c[1]+h*ry+h*ty,c[2]+h*rz+h*tz}};
        float a=mirror?16:0,b=mirror?0:16;
        card(s,p,new float[][]{{a,0},{a,16},{b,16},{b,0}});
    }

    /** A box, each face with its own sprite over its own extent (as a block model lays it) unless a rect was given. */
    void box(Box b){
        for(var d:Direction.values()){
            Sprite s=b.sprites[d.ordinal()];if(s==null)continue;
            float[] r=b.rects[d.ordinal()];if(r==null)r=rect(d,b);
            boolean onSide=switch(d){case DOWN->b.y0<=0;case UP->b.y1>=16;case NORTH->b.z0<=0;case SOUTH->b.z1>=16;case WEST->b.x0<=0;case EAST->b.x1>=16;};
            quad(s,d,corners(d,b),new float[][]{{r[0],r[1]},{r[0],r[3]},{r[2],r[3]},{r[2],r[1]}},onSide);
        }
    }
    /** A box with one sprite on every face. */
    void box(Sprite s,double x0,double y0,double z0,double x1,double y1,double z1){box(new Box(x0,y0,z0,x1,y1,z1).all(s));}
    /** One face of a box on its own: a flat piece looking the given way, laid like that face of the box. */
    void face(Sprite s,Direction d,Box b){
        float[] r=b.rects[d.ordinal()];if(r==null)r=rect(d,b);
        quad(s,d,corners(d,b),new float[][]{{r[0],r[1]},{r[0],r[3]},{r[2],r[3]},{r[2],r[1]}},false);
    }
    /** The rect a block model lays over a face: the face's own extent of the sprite. */
    static float[] rect(Direction d,Box b){
        return switch(d){
            case DOWN->new float[]{(float)b.x0,(float)(16-b.z1),(float)b.x1,(float)(16-b.z0)};
            case UP->new float[]{(float)b.x0,(float)b.z0,(float)b.x1,(float)b.z1};
            case NORTH->new float[]{(float)(16-b.x1),(float)(16-b.y1),(float)(16-b.x0),(float)(16-b.y0)};
            case SOUTH->new float[]{(float)b.x0,(float)(16-b.y1),(float)b.x1,(float)(16-b.y0)};
            case WEST->new float[]{(float)b.z0,(float)(16-b.y1),(float)b.z1,(float)(16-b.y0)};
            case EAST->new float[]{(float)(16-b.z1),(float)(16-b.y1),(float)(16-b.z0),(float)(16-b.y0)};
        };
    }
    /** A face's corners, top left first seen from outside, anticlockwise (the game's own order). */
    static double[][] corners(Direction d,Box b){
        return switch(d){
            case DOWN->new double[][]{{b.x0,b.y0,b.z1},{b.x0,b.y0,b.z0},{b.x1,b.y0,b.z0},{b.x1,b.y0,b.z1}};
            case UP->new double[][]{{b.x0,b.y1,b.z0},{b.x0,b.y1,b.z1},{b.x1,b.y1,b.z1},{b.x1,b.y1,b.z0}};
            case NORTH->new double[][]{{b.x1,b.y1,b.z0},{b.x1,b.y0,b.z0},{b.x0,b.y0,b.z0},{b.x0,b.y1,b.z0}};
            case SOUTH->new double[][]{{b.x0,b.y1,b.z1},{b.x0,b.y0,b.z1},{b.x1,b.y0,b.z1},{b.x1,b.y1,b.z1}};
            case WEST->new double[][]{{b.x0,b.y1,b.z0},{b.x0,b.y0,b.z0},{b.x0,b.y0,b.z1},{b.x0,b.y1,b.z1}};
            case EAST->new double[][]{{b.x1,b.y1,b.z1},{b.x1,b.y0,b.z1},{b.x1,b.y0,b.z0},{b.x1,b.y1,b.z0}};
        };
    }

    /**
     * An n-sided cylinder round an axis through (a, b) (the other two coordinates) from {@code from} to {@code to}
     * along it, the side sprite wrapped once round, its ends capped with the end sprite where asked.
     */
    void cylinder(Sprite side,Sprite end,Direction.Axis axis,double a,double b,double r,double from,double to,int n,boolean capFrom,boolean capTo){
        for(int k=0;k<n;k++){
            double a0=k*Math.PI*2/n,a1=(k+1)*Math.PI*2/n,mid=(a0+a1)/2;
            double p0=a+r*Math.cos(a0),q0=b+r*Math.sin(a0),p1=a+r*Math.cos(a1),q1=b+r*Math.sin(a1);
            float u0=16f*k/n,u1=16f*(k+1)/n;
            // the side looks out of the cylinder, the length running down the sprite
            oriented(side,new double[][]{at(axis,p1,q1,to),at(axis,p1,q1,from),at(axis,p0,q0,from),at(axis,p0,q0,to)},new float[][]{{u1,0},{u1,16},{u0,16},{u0,0}},at(axis,Math.cos(mid),Math.sin(mid),0));
            float e0u=(float)(8+8*Math.cos(a0)),e0v=(float)(8+8*Math.sin(a0)),e1u=(float)(8+8*Math.cos(a1)),e1v=(float)(8+8*Math.sin(a1));
            if(capTo)oriented(end,new double[][]{at(axis,a,b,to),at(axis,p1,q1,to),at(axis,p0,q0,to),at(axis,p0,q0,to)},new float[][]{{8,8},{e1u,e1v},{e0u,e0v},{e0u,e0v}},at(axis,0,0,1));
            if(capFrom)oriented(end,new double[][]{at(axis,a,b,from),at(axis,p1,q1,from),at(axis,p0,q0,from),at(axis,p0,q0,from)},new float[][]{{8,8},{e1u,e1v},{e0u,e0v},{e0u,e0v}},at(axis,0,0,-1));
        }
    }
    /** A quad made to look the given way: its corners reversed when they run the other way round. */
    private void oriented(Sprite s,double[][] p,float[][] uv,double[] look){
        double[] nn=RoofModel.normal(p);
        if(nn[0]*look[0]+nn[1]*look[1]+nn[2]*look[2]<0){
            if(p[2]==p[3]){double[] t=p[1];p[1]=p[2];p[2]=t;p[3]=t;float[] w=uv[1];uv[1]=uv[2];uv[2]=w;uv[3]=w;}
            else{double[] t=p[1];p[1]=p[3];p[3]=t;float[] w=uv[1];uv[1]=uv[3];uv[3]=w;}
        }
        quad(s,null,p,uv,false);
    }
    /** A point from its two coordinates across an axis and one along it: y round (x, z), x round (y, z), z round (x, y). */
    private static double[] at(Direction.Axis axis,double a,double b,double along){
        return switch(axis){case X->new double[]{along,a,b};case Y->new double[]{a,along,b};case Z->new double[]{a,b,along};};
    }
    /**
     * A flat n-sided disc in a plane across an axis, at {@code along} on it, round (a, b), looking the positive way
     * along the axis or the negative; the sprite's middle at its middle, its edge at the sprite's edge.
     */
    void disc(Sprite s,Direction.Axis axis,double a,double b,double r,double along,int n,boolean positive){
        for(int k=0;k<n;k++){
            double a0=k*Math.PI*2/n,a1=(k+1)*Math.PI*2/n;
            double p0=a+r*Math.cos(a0),q0=b+r*Math.sin(a0),p1=a+r*Math.cos(a1),q1=b+r*Math.sin(a1);
            float e0u=(float)(8+8*Math.cos(a0)),e0v=(float)(8+8*Math.sin(a0)),e1u=(float)(8+8*Math.cos(a1)),e1v=(float)(8+8*Math.sin(a1));
            oriented(s,new double[][]{at(axis,a,b,along),at(axis,p1,q1,along),at(axis,p0,q0,along),at(axis,p0,q0,along)},new float[][]{{8,8},{e1u,e1v},{e0u,e0v},{e0u,e0v}},at(axis,0,0,positive?1:-1));
        }
    }

    /** The model: its loose quads always drawn, those on a side dropped where a neighbour covers it. */
    BakedModel bake(Sprite particle,boolean ambientOcclusion){
        Map<Direction,List<BakedQuad>> fixed=new EnumMap<>(Direction.class);
        for(var e:sides.entrySet())fixed.put(e.getKey(),List.copyOf(e.getValue()));
        return new BasicBakedModel(List.copyOf(loose),fixed,ambientOcclusion,true,false,particle,ModelTransformation.NONE,ModelOverrideList.EMPTY);
    }

    /** A baked quad in the block format: position (blocks), white, the sprite's coordinates, no light (the game lights it), its normal. */
    static BakedQuad bake(Sprite sprite,Direction face,double[][] p,float[][] uv,boolean shade){
        int[] data=new int[32];
        double[] n=RoofModel.normal(p);
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
        return new BakedQuad(data,-1,face,sprite,shade);
    }
}
