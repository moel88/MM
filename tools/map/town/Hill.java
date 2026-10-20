package dev.sangwoo.botandtown;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;

/**
 * The ways up the hill: the main stairway straight up the middle from the end of the main street to the shrine (the
 * view back down it runs along the main street and the bridge out to the island), and a ramp for cars between each
 * pair of levels, cut into the slope between stone walls.
 */
final class Hill {
    private final Townscape t;private final Canvas c;
    Hill(Townscape t){this.t=t;c=t.c;}

    /** Level (top block y) below terrace i's wall, i=0 the hill foot. */
    static int below(int i){return i==0?Plan.ST_Y[3]:Plan.TERRACE_Y[i-1];}

    /** Marks the stairway's paths and the ramps in the street plan (before the streets are laid). */
    void plan(Roads roads){
        int b=Plan.bend(0),n=Plan.TERRACE_Y.length;
        for(int i=0;i<n;i++){
            int wall=Plan.TERRACE_Z0[i]+b,top=wall-(Plan.TERRACE_Y[i]-below(i));
            int next=i<n-1?Plan.TERRACE_Z0[i+1]+b:Plan.TERRACE_Z1[n-1]+b-1;
            roads.markIfFree(Plan.STAIR_X0+1,next+1,Plan.STAIR_X1-1,top,Roads.PATH);
        }
        for(int[] r:Plan.RAMPS)roads.mark(r[0],rampEnd(r),r[1],rampStart(r),Roads.RAMP);
    }

    void build(){
        int b=Plan.bend(0),n=Plan.TERRACE_Y.length;
        for(int i=0;i<n;i++)flight(Plan.TERRACE_Z0[i]+b,below(i),Plan.TERRACE_Y[i],Plan.STAIR_X0+1,Plan.STAIR_X1-1);
        // the shrine's stone steps from the top terrace, and the approach on to the shrine's square
        int start=Plan.TERRACE_Z1[n-1]+b-1,topY=Plan.TERRACE_Y[n-1];
        flight(start,topY,Plan.SHRINE_Y,Plan.STAIR_X0+1,Plan.STAIR_X1-1);
        for(int z=start-(Plan.SHRINE_Y-topY)-1;z>Plan.SHRINE_Z0;z--)for(int x=Plan.STAIR_X0;x<=Plan.STAIR_X1;x++){
            c.fill(x,Plan.SHRINE_Y-4,z,x,Plan.SHRINE_Y-1,z,Blocks.STONE.getDefaultState());
            c.set(x,Plan.SHRINE_Y,z,x==Plan.STAIR_X0||x==Plan.STAIR_X1?t.terrain.masonry(x,Plan.SHRINE_Y,z):Blocks.STONE_BRICKS.getDefaultState());
            c.clear(x,Plan.SHRINE_Y+1,z,x,Plan.SHRINE_Y+8,z);c.ground(x,z,Plan.SHRINE_Y);
        }
        for(int[] r:Plan.RAMPS)ramp(r);
        decorate();
    }

    /**
     * Beside the stairway's paths across each terrace: stone lanterns in pairs and pines between them, a pair of cherry
     * trees on the last terrace below the shrine.
     */
    void decorate(){
        int b=Plan.bend(0),n=Plan.TERRACE_Y.length;
        for(int i=0;i<n;i++){
            int wall=Plan.TERRACE_Z0[i]+b,top=wall-(Plan.TERRACE_Y[i]-below(i));
            int next=i<n-1?Plan.TERRACE_Z0[i+1]+b:Plan.TERRACE_Z1[n-1]+b-1;
            int y=Plan.TERRACE_Y[i];
            int k=0;
            for(int z=top-2;z>next+2;z-=6,k++)for(int side=-1;side<=1;side+=2){
                int x=side*5;
                if(c.ground(x,z)!=y||!c.air(x,y+1,z))continue;
                if(k%2==0){c.set(x,y+1,z,Blocks.STONE_BRICKS.getDefaultState());c.set(x,y+2,z,Blocks.STONE_BRICK_WALL.getDefaultState());
                    c.set(x,y+3,z,Blocks.LANTERN.getDefaultState());c.set(x,y+4,z,Canvas.slab(Blocks.STONE_BRICK_SLAB,false));}
                else if(i==n-1&&k==1)t.nature.cherry(side*6,y+1,z,5);
                else t.nature.pine(side*6,y+1,z,4);
            }
        }
    }

    /**
     * A flight of stone steps rising one block a block from {@code low} at the wall line into the terrace, between
     * stone walls topped by a low parapet; a landing at the top.
     */
    void flight(int wall,int low,int high,int x0,int x1){
        int n=high-low;
        for(int k=0;k<n;k++){
            int z=wall-k,y=low+1+k;
            for(int x=x0;x<=x1;x++){
                c.fill(x,low-3,z,x,y-1,z,Blocks.STONE.getDefaultState());
                c.set(x,y,z,Canvas.stairs(Blocks.STONE_BRICK_STAIRS,Direction.NORTH,false));
                c.clear(x,y+1,z,x,high+8,z);
                c.ground(x,z,y);
            }
            for(int x:new int[]{x0-1,x1+1}){
                for(int yy=low+1;yy<=high;yy++)c.set(x,yy,z,t.terrain.masonry(x,yy,z));
                c.set(x,high+1,z,Blocks.ANDESITE_WALL.getDefaultState());
                c.clear(x,high+2,z,x,high+8,z);
                c.ground(x,z,high);
            }
        }
        // the landing at the top: flat stone the width of the flight
        int z=wall-n;
        for(int x=x0-1;x<=x1+1;x++){c.set(x,high,z,Blocks.STONE_BRICKS.getDefaultState());c.clear(x,high+1,z,x,high+8,z);c.ground(x,z,high);}
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
        int lowY=r[2]<0?Plan.ST_Y[3]:Plan.TERRACE_Y[r[2]],highY=Plan.TERRACE_Y[r[3]];
        for(int k=0;k<=len;k++){
            int z=z0-k;
            int lv=(int)Math.round(2*lowY+2.0*(highY-lowY)*k/len);
            boolean slab=(lv&1)==1;int y=lv>>1;
            for(int x=r[0];x<=r[1];x++){
                boolean edge=x==r[0]||x==r[1];
                c.fill(x,Math.min(lowY,y)-3,z,x,y-1,z,Blocks.STONE.getDefaultState());
                if(slab){c.set(x,y,z,TB.ASPHALT);c.set(x,y+1,z,edge?Canvas.slab(Blocks.SMOOTH_STONE_SLAB,false):TB.asphaltSlab());}
                else c.set(x,y,z,edge?Blocks.SMOOTH_STONE.getDefaultState():TB.ASPHALT);
                c.clear(x,y+(slab?2:1),z,x,highY+8,z);
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
