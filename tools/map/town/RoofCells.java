package dev.sangwoo.botandtown;

import dev.sangwoo.celestialfall.town.RoofBlock;
import net.minecraft.util.math.Direction;

/**
 * Which roof block shape covers a cell: the roof's heights at the cell's corners, the middles of its sides and its
 * middle are compared with each shape the roof block knows (in each facing), measured from the cell's lowest point in
 * steps of the pitch. Every cell of a rectangular gable, hip or shed roof is one of them.
 */
final class RoofCells {
    private RoofCells(){}
    record Fit(RoofBlock.Shape shape,Direction facing){}
    static Fit match(double[][] h,double lo,float pitch){
        for(var shape:RoofBlock.Shape.values())for(var f:Direction.Type.HORIZONTAL){
            boolean ok=true;
            for(int i=0;i<3&&ok;i++)for(int j=0;j<3&&ok;j++)
                ok=Math.abs((h[i][j]-lo)-RoofBlock.height(shape,f,0,pitch,i*0.5,j*0.5))<1e-6;
            if(ok)return new Fit(shape,f);
        }
        Town.log("roof cell with no shape: "+java.util.Arrays.deepToString(h));
        return new Fit(RoofBlock.Shape.SLOPE,Direction.NORTH);
    }
}
