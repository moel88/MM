package dev.sangwoo.celestialfall;

import dev.sangwoo.celestialfall.town.RoofShape;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.util.function.DoubleBinaryOperator;
import static org.junit.jupiter.api.Assertions.*;

/** The true-pitch roof blocks: every cell of a rectangular roof is one of their shapes, and neighbouring cells meet. */
class RoofShapeTest {
    static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};

    @Test void aSlopeFallsTowardItsFacing(){
        assertEquals(0,RoofShape.height(RoofShape.SLOPE,Direction.NORTH,0,0.5f,0.5,0),1e-9);
        assertEquals(0.5,RoofShape.height(RoofShape.SLOPE,Direction.NORTH,0,0.5f,0.5,1),1e-9);
        assertEquals(0,RoofShape.height(RoofShape.SLOPE,Direction.EAST,0,0.5f,1,0.5),1e-9);
        assertEquals(0.75,RoofShape.height(RoofShape.SLOPE,Direction.WEST,2,0.25f,1,0.5),1e-9);
        // a hip facing north falls toward north and east: the north-east corner of a roof, its inner corner highest
        assertEquals(0.5,RoofShape.height(RoofShape.HIP,Direction.NORTH,0,0.5f,0,1),1e-9);
        assertEquals(0,RoofShape.height(RoofShape.HIP,Direction.NORTH,0,0.5f,1,1),1e-9);
        // a ridge peaks across its middle, a peak in its middle
        assertEquals(0.25,RoofShape.height(RoofShape.RIDGE,Direction.SOUTH,0,0.5f,0.3,0.5),1e-9);
        assertEquals(0.25,RoofShape.height(RoofShape.PEAK,Direction.NORTH,0,0.5f,0.5,0.5),1e-9);
    }

    /** Gable, hip and shed roofs of many sizes and both pitches: each cell matches a shape, and edges agree. */
    @Test void everyCellOfARectangularRoofIsAShapeAndMeetsItsNeighbours(){
        for(float pitch:new float[]{0.5f,0.25f})for(int type=0;type<3;type++)for(boolean alongX:new boolean[]{true,false})
            for(int w=3;w<=12;w++)for(int d=3;d<=12;d++)check(pitch,type,alongX,w,d);
    }
    private static void check(float pitch,int type,boolean alongX,int w,int d){
        int x0=0,x1=w-1,z0=0,z1=d-1;
        DoubleBinaryOperator run=(x,z)->{
            double dx=Math.min(x-x0,x1+1-x),dz=Math.min(z-z0,z1+1-z);
            return switch(type){case 0->alongX?dz:dx;case 1->Math.min(dx,dz);default->alongX?z1+1-z:z-z0;};
        };
        double[][][] top=new double[w][d][];
        for(int x=0;x<w;x++)for(int z=0;z<d;z++){
            double[][] h=new double[3][3];double lo=Double.MAX_VALUE;
            for(int i=0;i<3;i++)for(int j=0;j<3;j++){h[i][j]=pitch*run.applyAsDouble(x+i*0.5,z+j*0.5);lo=Math.min(lo,h[i][j]);}
            int y=(int)Math.floor(lo+1e-6),base=(int)Math.round((lo-y)*4);
            if(base==4){y++;base=0;}
            String where=String.format("pitch %s type %d alongX %s %dx%d cell %d,%d",pitch,type,alongX,w,d,x,z);
            RoofShape shape=null;Direction facing=null;
            search:for(var s:RoofShape.values())for(var f:FACINGS){
                boolean ok=true;
                for(int i=0;i<3&&ok;i++)for(int j=0;j<3&&ok;j++)ok=Math.abs(h[i][j]-lo-RoofShape.height(s,f,0,pitch,i*0.5,j*0.5))<1e-6;
                if(ok){shape=s;facing=f;break search;}
            }
            assertNotNull(shape,where+" has no shape");
            // the block's own heights, at its level and base, along its edges
            double[] e=new double[5];
            for(int k=0;k<5;k++)e[k]=y+RoofShape.height(shape,facing,base,pitch,k/4.0,0);
            top[x][z]=new double[]{y,base,shape.ordinal(),facing.getHorizontal()};
            for(int k=0;k<5;k++)assertEquals(pitch*run.applyAsDouble(x+k/4.0,z),e[k],1e-6,where+" north edge");
        }
        // neighbours along x share their edge's heights exactly
        for(int x=0;x+1<w;x++)for(int z=0;z<d;z++)for(int k=0;k<=4;k++){
            double v=k/4.0;
            double a=top[x][z][0]+RoofShape.height(RoofShape.values()[(int)top[x][z][2]],Direction.fromHorizontal((int)top[x][z][3]),(int)top[x][z][1],pitch,1,v);
            double b=top[x+1][z][0]+RoofShape.height(RoofShape.values()[(int)top[x+1][z][2]],Direction.fromHorizontal((int)top[x+1][z][3]),(int)top[x+1][z][1],pitch,0,v);
            assertEquals(a,b,1e-6,"seam between "+x+","+z+" and "+(x+1)+","+z);
        }
    }
}
