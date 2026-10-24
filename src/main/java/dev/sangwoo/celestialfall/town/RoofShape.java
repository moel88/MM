package dev.sangwoo.celestialfall.town;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

/**
 * The part of a roof over one cell (RoofBlock): one slope, a hip (two slopes meeting across the cell's diagonal), a
 * ridge across its middle, the end of a ridge, or the peak of a pyramid. Each is the lowest of up to four planes rising
 * at the roof's pitch from the cell's sides, so neighbouring cells always meet edge to edge.
 */
public enum RoofShape implements StringIdentifiable {
    SLOPE,HIP,RIDGE,RIDGE_END,PEAK;
    @Override public String asString(){return name().toLowerCase(java.util.Locale.ROOT);}

    /**
     * The roof's top over a cell, above its floor (blocks), at u (toward east) and v (toward south), 0..1. A shape
     * facing north has its slope falling toward north; a hip facing north falls toward north and east (the north-east
     * corner of a roof); a ridge facing north runs east-west; the end of a ridge facing north is where a ridge running
     * north-south ends in the slope falling north. Other facings turn the shape clockwise. {@code base} is the lowest
     * edge's height in quarters of a block, {@code pitch} the rise per block of run.
     */
    public static double height(RoofShape shape,Direction facing,int base,float pitch,double u,double v){
        double cu,cv;
        switch(facing){
            case EAST->{cu=v;cv=1-u;}
            case SOUTH->{cu=1-u;cv=1-v;}
            case WEST->{cu=1-v;cv=u;}
            default->{cu=u;cv=v;}
        }
        double b=base/4.0;
        double n=b+pitch*cv,s=b+pitch*(1-cv),e=b+pitch*(1-cu),w=b+pitch*cu;
        return switch(shape){
            case SLOPE->n;
            case HIP->Math.min(n,e);
            case RIDGE->Math.min(n,s);
            case RIDGE_END->Math.min(n,Math.min(e,w));
            case PEAK->Math.min(Math.min(n,s),Math.min(e,w));
        };
    }
}
