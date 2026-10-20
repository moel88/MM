package dev.sangwoo.botandtown;

import net.minecraft.util.math.Direction;

/** A plot for one building: its rectangle, the ground level it is levelled to, and the side facing its street. */
final class Lot {
    final int x0,z0,x1,z1;
    int y;
    final Direction front;
    /** Where it lies (which decides what may be built on it) and what was built. */
    final String zone;String use="";
    Lot(int x0,int z0,int x1,int z1,int y,Direction front,String zone){
        this.x0=Math.min(x0,x1);this.z0=Math.min(z0,z1);this.x1=Math.max(x0,x1);this.z1=Math.max(z0,z1);
        this.y=y;this.front=front;this.zone=zone;
    }
    int frontage(){return front.getAxis()==Direction.Axis.Z?x1-x0+1:z1-z0+1;}
    int depth(){return front.getAxis()==Direction.Axis.Z?z1-z0+1:x1-x0+1;}
    boolean contains(int x,int z){return x>=x0&&x<=x1&&z>=z0&&z<=z1;}
    int cx(){return (x0+x1)/2;}
    int cz(){return (z0+z1)/2;}
    @Override public String toString(){return use+"@"+x0+","+z0+".."+x1+","+z1+" y"+y+" "+front;}
}
