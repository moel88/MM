package dev.sangwoo.celestialfall.client.atomic;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Walkable surface heights around a cast point, so decals and slabs sit on uneven terrain. */
final class GroundCache {
    private final Vec3d origin;
    private final int radius;
    private final float[] top;
    private long built;private boolean ready;
    GroundCache(Vec3d origin,int radius) {
        this.origin=origin;this.radius=radius;top=new float[(2*radius+1)*(2*radius+1)];
    }
    /** Re-reads the terrain at most once a second. */
    void refresh(ClientWorld world) {
        long now=world.getTime();if(ready&&now-built<20)return;built=now;ready=true;
        int ox=(int)Math.floor(origin.x),oz=(int)Math.floor(origin.z);
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
            top[(dx+radius)*(2*radius+1)+dz+radius]=surface(world,ox+dx,oz+dz,origin.y);
    }
    /** Surface height of the block column at world (x,z), or NaN if none near the cast height. */
    float at(int x,int z) {
        int dx=x-(int)Math.floor(origin.x),dz=z-(int)Math.floor(origin.z);
        if(Math.abs(dx)>radius||Math.abs(dz)>radius)return Float.NaN;
        return top[(dx+radius)*(2*radius+1)+dz+radius];
    }
    int radius() { return radius; }
    /** The solid top face closest to {@code y} within 6 blocks below and 3 above. */
    static float surface(ClientWorld world,int x,int z,double y) {
        var pos=new BlockPos.Mutable();float best=Float.NaN;double bestGap=1e9;
        int y0=(int)Math.floor(y);
        for(int by=y0+3;by>=y0-6;by--){
            pos.set(x,by,z);var state=world.getBlockState(pos);
            var shape=state.getCollisionShape(world,pos);if(shape.isEmpty())continue;
            pos.set(x,by+1,z);var above=world.getBlockState(pos);
            var aboveShape=above.getCollisionShape(world,pos);
            if(!aboveShape.isEmpty()&&aboveShape.getMin(Direction.Axis.Y)<=0)continue;
            float h=(float)(by+shape.getMax(Direction.Axis.Y));double gap=Math.abs(h-y);
            if(gap<bestGap){bestGap=gap;best=h;}
        }
        return best;
    }
}
