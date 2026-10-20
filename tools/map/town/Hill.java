package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;

/**
 * The ways up the hill: the main stairway straight up the middle from the end of the main street to the shrine (the
 * view back down it ends in the sea), and four ramps for cars, one between each pair of levels, cut into the slope
 * between stone walls.
 */
final class Hill {
    private final Townscape t;private final Canvas c;
    Hill(Townscape t){this.t=t;c=t.c;}

    /** Level (top block y) below terrace i's wall, i=0 the hill foot. */
    static int below(int i){return i==0?Plan.GROUND:Plan.TERRACE_Y[i-1];}

    /** Marks the stairway's landings and paths in the street plan (before the streets are laid). */
    void plan(Roads roads){
        int b=Plan.bend(0);
        for(int i=0;i<4;i++){
            int wall=Plan.TERRACE_Z0[i]+b,top=wall-5;
            int next=i<3?Plan.TERRACE_Z0[i+1]+b:Plan.TERRACE_Z1[3]+b;
            // the flight itself is laid by stairs(); the path runs on to the next wall
            roads.markIfFree(Plan.STAIR_X0+1,next+1,Plan.STAIR_X1-1,top-1,Roads.PATH);
        }
        for(int[] r:Plan.RAMPS)roads.mark(r[0],rampEnd(r),r[1],rampStart(r),Roads.RAMP);
    }

    void build(){
        int b=Plan.bend(0);
        for(int i=0;i<4;i++)flight(Plan.TERRACE_Z0[i]+b,below(i),Plan.TERRACE_Y[i],Plan.STAIR_X0+1,Plan.STAIR_X1-1);
        // the shrine's stone steps from the top terrace
        int start=Plan.TERRACE_Z1[3]+b-1;
        flight(start,Plan.TERRACE_Y[3],Plan.SHRINE_Y,Plan.STAIR_X0+1,Plan.STAIR_X1-1);
        for(int z=start-(Plan.SHRINE_Y-Plan.TERRACE_Y[3]);z>Plan.SHRINE_Z0;z--)for(int x=Plan.STAIR_X0+1;x<=Plan.STAIR_X1-1;x++){
            c.set(x,Plan.SHRINE_Y,z,Blocks.STONE_BRICKS.getDefaultState());c.clear(x,Plan.SHRINE_Y+1,z,x,Plan.SHRINE_Y+6,z);c.ground(x,z,Plan.SHRINE_Y);
        }
        for(int[] r:Plan.RAMPS)ramp(r);
    }

    /**
     * A flight of stone steps rising one block a block from {@code low} at the wall line into the terrace, with
     * stone walls on both sides topped by a low parapet, and a handrail down the middle of the wider flights.
     */
    void flight(int wall,int low,int high,int x0,int x1){
        int n=high-low;
        for(int k=0;k<n;k++){
            int z=wall-k,y=low+1+k;
            for(int x=x0;x<=x1;x++){
                c.fill(x,low-2,z,x,y-1,z,Blocks.STONE.getDefaultState());
                c.set(x,y,z,Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.NORTH,false));
                c.clear(x,y+1,z,x,high+6,z);
                c.ground(x,z,y);
            }
            for(int x:new int[]{x0-1,x1+1}){
                for(int y=low+1;y<=high;y++)c.set(x,y,z,t.terrain.masonry(x,y,z));
                c.set(x,high+1,z,Blocks.ANDESITE_WALL.getDefaultState());
                c.ground(x,z,high);
            }
        }
        // the landing at the top: flat stone the width of the flight
        for(int x=x0-1;x<=x1+1;x++){int z=wall-n;c.set(x,high,z,Blocks.STONE_BRICKS.getDefaultState());c.clear(x,high+1,z,x,high+6,z);}
    }

    static int rampStart(int[] r){
        int mid=(r[0]+r[1])/2;
        return r[2]<0?Plan.TERRACE_Z0[0]+Plan.bend(mid):Plan.terraceStreetZ1(r[2],mid)-1;
    }
    static int rampEnd(int[] r){return Plan.terraceStreetZ0(r[3],(r[0]+r[1])/2)+1;}

    /**
     * A ramp for cars from one level's street to the next terrace's street: asphalt rising evenly in half blocks,
     * kerbs at both edges, a stone wall where the ground beside is higher and a wall with a fence where it is lower.
     */
    void ramp(int[] r){
        int z0=rampStart(r),z1=rampEnd(r),len=z0-z1;
        int lowY=r[2]<0?Plan.GROUND:Plan.TERRACE_Y[r[2]],highY=Plan.TERRACE_Y[r[3]];
        for(int k=0;k<=len;k++){
            int z=z0-k;
            double s=lowY+1+(highY-lowY)*(k/(double)len);
            int half=(int)Math.round(s*2);
            boolean slab=(half&1)==1;int y=slab?(half-1)/2:half/2-1;
            for(int x=r[0];x<=r[1];x++){
                boolean edge=x==r[0]||x==r[1];
                c.fill(x,Math.min(lowY,y)-3,z,x,y-1,z,Blocks.STONE.getDefaultState());
                if(slab){c.set(x,y,z,Blocks.STONE.getDefaultState());c.set(x,y+1,z,edge?Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false):Pal.ramp(true));}
                else c.set(x,y,z,edge?Blocks.SMOOTH_STONE.getDefaultState():Pal.ramp(false));
                c.clear(x,y+(slab?2:1),z,x,highY+6,z);
                c.ground(x,z,slab?y+1:y);
            }
            // the sides
            for(int x:new int[]{r[0]-1,r[1]+1}){
                int g=c.ground(x,z);
                int ry=slab?y+1:y;
                if(g>ry)for(int yy=ry+1;yy<=g;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));
                else if(g<ry){
                    for(int yy=g+1;yy<=ry;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));
                    c.set(x,ry+1,z,Blocks.SPRUCE_FENCE.getDefaultState());c.ground(x,z,ry);
                }
            }
        }
    }
}
