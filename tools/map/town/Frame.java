package dev.sangwoo.botandtown;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

/**
 * A building's own coordinates. Every building is drawn facing north, its front along bz=0 and its depth running
 * toward +bz, bx from its left to its right as seen from the street, by=0 the ground it stands on; the frame turns it
 * to face its street and turns every directional block (stairs, doors, signs, beds, trapdoors) with it.
 */
final class Frame {
    final Canvas c;final int ox,oz,y0,W,D;final Direction front;final BlockRotation rot;
    /**
     * @param x0,z0,x1,z1 the plot (inclusive) in world coordinates
     * @param front the side the building faces (toward its street)
     * @param y0 the ground level (top block) the building stands on
     */
    Frame(Canvas c,int x0,int z0,int x1,int z1,Direction front,int y0){
        this.c=c;this.front=front;this.y0=y0;
        boolean ns=front.getAxis()==Direction.Axis.Z;
        W=ns?x1-x0+1:z1-z0+1;D=ns?z1-z0+1:x1-x0+1;
        switch(front){
            case NORTH->{ox=x0;oz=z0;rot=BlockRotation.NONE;}
            case SOUTH->{ox=x1;oz=z1;rot=BlockRotation.CLOCKWISE_180;}
            case EAST->{ox=x1;oz=z0;rot=BlockRotation.CLOCKWISE_90;}
            default->{ox=x0;oz=z1;rot=BlockRotation.COUNTERCLOCKWISE_90;}
        }
    }
    static Frame of(Canvas c,Lot l){return new Frame(c,l.x0,l.z0,l.x1,l.z1,l.front,l.y);}
    /** A frame inset into a lot by the given margins (front, back, left, right). */
    Frame inset(int f,int b,int l,int r){
        int bx0=l,bx1=W-1-r,bz0=f,bz1=D-1-b;
        int ax=wx(bx0,bz0),az=wz(bx0,bz0),cx=wx(bx1,bz1),cz=wz(bx1,bz1);
        return new Frame(c,Math.min(ax,cx),Math.min(az,cz),Math.max(ax,cx),Math.max(az,cz),front,y0);
    }

    int wx(int bx,int bz){return switch(front){case NORTH->ox+bx;case SOUTH->ox-bx;case EAST->ox-bz;default->ox+bz;};}
    int wz(int bx,int bz){return switch(front){case NORTH->oz+bz;case SOUTH->oz-bz;case EAST->oz+bx;default->oz-bx;};}
    /** A direction in the building's coordinates (NORTH = toward the street) turned to the world's. */
    Direction dir(Direction d){return d.getAxis().isVertical()?d:rot.rotate(d);}

    void set(int bx,int by,int bz,BlockState s){c.set(wx(bx,bz),y0+by,wz(bx,bz),s.rotate(rot));}
    void set(int bx,int by,int bz,Block b){set(bx,by,bz,b.getDefaultState());}
    BlockState get(int bx,int by,int bz){return c.get(wx(bx,bz),y0+by,wz(bx,bz));}
    boolean air(int bx,int by,int bz){return c.air(wx(bx,bz),y0+by,wz(bx,bz));}
    void fill(int bx0,int by0,int bz0,int bx1,int by1,int bz1,BlockState s){
        for(int x=Math.min(bx0,bx1);x<=Math.max(bx0,bx1);x++)for(int y=Math.min(by0,by1);y<=Math.max(by0,by1);y++)for(int z=Math.min(bz0,bz1);z<=Math.max(bz0,bz1);z++)set(x,y,z,s);
    }
    void fill(int bx0,int by0,int bz0,int bx1,int by1,int bz1,Block b){fill(bx0,by0,bz0,bx1,by1,bz1,b.getDefaultState());}
    void clear(int bx0,int by0,int bz0,int bx1,int by1,int bz1){fill(bx0,by0,bz0,bx1,by1,bz1,Blocks.AIR.getDefaultState());}
    /** The ground's real height under a point (it may differ from the frame's base on a slope). */
    int ground(int bx,int bz){return c.ground(wx(bx,bz),wz(bx,bz))-y0;}
    void door(int bx,int by,int bz,Block b,Direction facing,boolean rightHinge){
        int x=wx(bx,bz),z=wz(bx,bz);c.door(x,y0+by,z,b,dir(facing),rightHinge);
    }
    void wallSign(int bx,int by,int bz,Block sign,Direction facing,DyeColor color,boolean glow,String... lines){
        c.wallSign(wx(bx,bz),y0+by,wz(bx,bz),sign,dir(facing),color,glow,lines);
    }
    void standingSign(int bx,int by,int bz,Block sign,Direction facing,DyeColor color,boolean glow,String... lines){
        Direction d=dir(facing);
        int rotation=switch(d){case SOUTH->0;case WEST->4;case NORTH->8;default->12;};
        c.standingSign(wx(bx,bz),y0+by,wz(bx,bz),sign,rotation,color,glow,lines);
    }
    void bed(int bx,int by,int bz,Block b,Direction head){
        Direction d=dir(head);c.bed(wx(bx,bz),y0+by,wz(bx,bz),b,d);
    }
    void tallPlant(int bx,int by,int bz,Block b){c.tallPlant(wx(bx,bz),y0+by,wz(bx,bz),b);}
    void hangingLantern(int bx,int by,int bz,boolean soul){c.hangingLantern(wx(bx,bz),y0+by,wz(bx,bz),soul);}
}
