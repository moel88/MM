package dev.sangwoo.celestialfall.client.town;

import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The code-built town models (2.48.0) are given facing north and turned to their block's facing: every side must land
 * where its face direction says, the turn must not mirror, and a window's west member must lie on the side its block
 * joins as LEFT (TownBlocks.Window.left: the facing turned anticlockwise).
 */
class MeshTurnTest {
    private static final double[][] SIDE_MIDDLES={{8,8,0},{8,8,16},{0,8,8},{16,8,8}};
    private static final Direction[] SIDES={Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};

    @Test void everySideLandsWhereItsDirectionSays(){
        for(var facing:Direction.Type.HORIZONTAL)for(int i=0;i<4;i++){
            double[] p=Mesh.turn(facing,SIDE_MIDDLES[i]);
            Direction d=Mesh.turn(facing,SIDES[i]);
            assertEquals(0.5+0.5*d.getOffsetX(),p[0],1e-9,facing+" "+SIDES[i]);
            assertEquals(0.5,p[1],1e-9);
            assertEquals(0.5+0.5*d.getOffsetZ(),p[2],1e-9,facing+" "+SIDES[i]);
        }
    }
    @Test void theFrontFacesTheBlocksFacing(){
        for(var facing:Direction.Type.HORIZONTAL)assertEquals(facing,Mesh.turn(facing,Direction.NORTH));
    }
    @Test void turningKeepsHandedness(){
        // x cross z of the turned axes keeps pointing down (-y) as it does unturned, so nothing is mirrored
        for(var facing:Direction.Type.HORIZONTAL){
            double[] o=Mesh.turn(facing,new double[]{0,0,0}),x=Mesh.turn(facing,new double[]{16,0,0}),z=Mesh.turn(facing,new double[]{0,0,16});
            double ax=x[0]-o[0],az=x[2]-o[2],bx=z[0]-o[0],bz=z[2]-o[2];
            assertEquals(1.0,ax*bz-az*bx,1e-9,facing.toString());
        }
    }
    @Test void aWindowsWestMemberIsOnTheSideItJoinsAsLeft(){
        for(var facing:Direction.Type.HORIZONTAL)assertEquals(facing.rotateYCounterclockwise(),Mesh.turn(facing,Direction.WEST));
    }
    @Test void upAndDownStayPut(){
        for(var facing:Direction.Type.HORIZONTAL){assertEquals(Direction.UP,Mesh.turn(facing,Direction.UP));assertEquals(Direction.DOWN,Mesh.turn(facing,Direction.DOWN));}
    }
}
