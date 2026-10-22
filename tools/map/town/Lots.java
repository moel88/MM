package dev.sangwoo.botandtown;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import java.util.*;

/**
 * Plots along the hourglass island's curved roads and the town's canal (map v3.0): a plot a few blocks back from the
 * road's edge with its front to the road (the plain direction nearest to the way back to it, since a building's frame
 * faces north, south, east or west), kept only where all of it is open land of its zone, clear of the roads, the water,
 * the rift and every other plot. Plots stand apart with open ground between them, so the town stays open to fight
 * through (map v2.0's rule).
 */
final class Lots {
    private final Townscape t;private final Canvas c;
    Lots(Townscape t){this.t=t;c=t.c;}

    /** Whether a rectangle is buildable: land of the zone, inside the shore by {@code shore}, off the roads by {@code road}, dry, not white, unclaimed. */
    boolean fits(int x0,int z0,int x1,int z1,int road,int shore,int zone){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(!Canvas.inPlan(x,z))return false;
            int i=Canvas.idx(x,z);
            if(t.terrain.kind[i]!=Terrain.LAND||t.terrain.pale[i]||c.owner[i]!=0)return false;
            if(zone>=0&&Plan.zone(x,z)!=zone)return false;
            if(Plan.shore(x,z)>-shore)return false;
        }
        for(int x=x0-road;x<=x1+road;x++)for(int z=z0-road;z<=z1+road;z++){
            if(t.roads.on(x,z))return false;
            if(Canvas.inPlan(x,z)&&t.terrain.water[Canvas.idx(x,z)]!=0)return false;
        }
        return true;
    }
    /** The highest and lowest ground under a rectangle. */
    int[] range(int x0,int z0,int x1,int z1){
        int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){int g=c.ground(x,z);lo=Math.min(lo,g);hi=Math.max(hi,g);}
        return new int[]{lo,hi};
    }

    /**
     * Plots along a line (points every half block: x, z, the tangent's x and z), on its left ({@code side} 1, seen
     * along the line) or right (-1): every plot {@code w} wide and {@code d} deep, its front {@code back} blocks from the
     * line, then {@code gap} blocks of open ground to the next. Each plot is levelled to the ground at its front (where
     * it meets the road's sidewalk) and claimed with {@code code}.
     */
    List<Lot> along(double[][] line,int side,double back,int w,int d,int gap,int road,int shore,int zone,String kind,int code){
        return along(line,side,back,w,d,gap,road,shore,zone,kind,code,Integer.MAX_VALUE);
    }
    /** As above, taking at most {@code max} plots. */
    List<Lot> along(double[][] line,int side,double back,int w,int d,int gap,int road,int shore,int zone,String kind,int code,int max){
        List<Lot> out=new ArrayList<>();
        int k=0;
        while(k<line.length&&out.size()<max){
            double[] p=line[k];
            double nx=-p[3]*side,nz=p[2]*side;
            double fx=p[0]+nx*back,fz=p[1]+nz*back;
            // the plot faces the way back to the line
            Direction front=Math.abs(nx)>=Math.abs(nz)?(nx>0?Direction.WEST:Direction.EAST):(nz>0?Direction.NORTH:Direction.SOUTH);
            int x0,z0,x1,z1,cx=(int)Math.round(fx),cz=(int)Math.round(fz);
            switch(front){
                case NORTH->{x0=cx-w/2;x1=x0+w-1;z0=cz;z1=cz+d-1;}
                case SOUTH->{x0=cx-w/2;x1=x0+w-1;z1=cz;z0=cz-d+1;}
                case WEST->{z0=cz-w/2;z1=z0+w-1;x0=cx;x1=cx+d-1;}
                default->{z0=cz-w/2;z1=z0+w-1;x1=cx;x0=cx-d+1;}
            }
            if(fits(x0,z0,x1,z1,road,shore,zone)){
                int[] r=range(x0,z0,x1,z1);
                if(r[1]-r[0]<=4){
                    int y=c.ground(cx-(int)Math.round(nx*2),cz-(int)Math.round(nz*2));
                    y=Math.max(r[0],Math.min(r[1],y));
                    var l=new Lot(x0,z0,x1,z1,y,front,kind);
                    level(l);c.claim(x0-1,z0-1,x1+1,z1+1,code);
                    out.add(l);
                    k+=2*(w+gap);
                    continue;
                }
            }
            k+=4;
        }
        return out;
    }

    /**
     * Levels a plot to its ground level: earth under it where the ground is lower, cut away where it is higher, and
     * the ground round it eased toward the plot's level over three blocks so it does not stand on a step.
     */
    void level(Lot l){
        for(int x=l.x0;x<=l.x1;x++)for(int z=l.z0;z<=l.z1;z++)setGround(x,z,l.y);
        for(int ring=1;ring<=3;ring++){
            for(int x=l.x0-ring;x<=l.x1+ring;x++)for(int z=l.z0-ring;z<=l.z1+ring;z++){
                if(x>l.x0-ring&&x<l.x1+ring&&z>l.z0-ring&&z<l.z1+ring)continue;
                if(!Canvas.inPlan(x,z))continue;
                int i=Canvas.idx(x,z);
                if(c.owner[i]!=0||t.roads.on(x,z)||t.terrain.kind[i]!=Terrain.LAND||t.terrain.water[i]!=0)continue;
                int g=c.ground(x,z),want=(int)Math.round(l.y+(g-l.y)*ring/4.0);
                if(want!=g)setGround(x,z,want);
            }
        }
    }
    /** Raises or lowers a column of natural ground to y: grass on earth, cut or filled. */
    void setGround(int x,int z,int y){
        int g=c.ground(x,z);
        var top=c.get(x,g,z);
        if(y>g){for(int yy=g;yy<y;yy++)c.set(x,yy,z,Blocks.DIRT.getDefaultState());c.set(x,y,z,top.isOf(Blocks.GRASS_BLOCK)||top.isAir()?Blocks.GRASS_BLOCK.getDefaultState():top);}
        else if(y<g){c.clear(x,y+1,z,x,g,z);c.set(x,y,z,top.isAir()?Blocks.GRASS_BLOCK.getDefaultState():top);}
        c.clear(x,y+1,z,x,y+3,z);
        c.ground(x,z,y);
    }

    /**
     * A free spot of open ground beside a line, as near as there is to {@code zWant} along it: {@code back} blocks out
     * on the given side, in the zone, off the roads and every plot, with room to stand on it. Null if none.
     */
    int[] freeNear(double[][] line,int side,double back,int zWant,int zone){
        int best=0;double bd=1e9;
        for(int k=0;k<line.length;k++){double dd=Math.abs(line[k][1]-zWant);if(dd<bd){bd=dd;best=k;}}
        for(int o=0;o<line.length;o++)for(int sgn=-1;sgn<=1;sgn+=2){
            int k=best+sgn*o;if(k<0||k>=line.length)continue;
            double[] p=line[k];double nx=-p[3]*side,nz=p[2]*side;
            int x=(int)Math.floor(p[0]+nx*back),z=(int)Math.floor(p[1]+nz*back);
            if(!Canvas.inPlan(x,z)||Plan.zone(x,z)!=zone||t.roads.on(x,z)||c.owner(x,z)!=0||t.terrain.kind[Canvas.idx(x,z)]!=Terrain.LAND)continue;
            int g=c.ground(x,z);
            if(c.air(x,g+1,z)&&c.air(x,g+2,z)&&c.air(x,g+3,z)&&c.air(x+1,g+1,z)&&c.air(x,g+1,z+1))return new int[]{x,z};
        }
        return null;
    }
    /** The points of a line from a to b every half block, in the form {@link Roads#lines} keeps. */
    static double[][] line(double x0,double z0,double x1,double z1){
        double len=Math.hypot(x1-x0,z1-z0);int n=Math.max(1,(int)(len*2));double[][] p=new double[n+1][];
        for(int k=0;k<=n;k++){double f=k/(double)n;p[k]=new double[]{x0+(x1-x0)*f,z0+(z1-z0)*f,(x1-x0)/len,(z1-z0)/len};}
        return p;
    }
}
