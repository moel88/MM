package dev.sangwoo.celestialfall.client.kageno;

import java.util.ArrayDeque;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Speed afterimages: which frame each copy shows, and which parts leave one. */
class BodyGhostTest {
    static BodyGhost.Shot shot(float time,Vec3d pos){
        float[][] parts=new float[10][];for(int i=0;i<6;i++)parts[i]=new float[]{0,0,0,0,0,0};for(int i=6;i<10;i++)parts[i]=new float[]{0};
        Matrix4f body=new Matrix4f();
        return new BodyGhost.Shot(time,pos,body,parts,null,null,null,BodyGhost.points(pos,body,parts,null));
    }

    @Test void eachCopyShowsTheFrameNearestItsTimeOrNoneAtAll(){
        var shots=new ArrayDeque<BodyGhost.Shot>();
        for(int i=0;i<=30;i++)shots.addLast(shot(i/60f,Vec3d.ZERO));
        assertEquals(27/60f,BodyGhost.nearest(shots,.45f).time(),1e-6);
        assertEquals(30/60f,BodyGhost.nearest(shots,.5f-.004f).time(),1e-6);
        // A gap in the frames (the body hidden, first person) leaves the copy out instead of showing an old frame.
        var gap=new ArrayDeque<BodyGhost.Shot>();gap.addLast(shot(0,Vec3d.ZERO));gap.addLast(shot(.5f,Vec3d.ZERO));
        assertNull(BodyGhost.nearest(gap,.4f));
    }
    @Test void onlyPartsMovingFastAndAwayFromTheBodyLeaveACopy(){
        assertEquals(0,BodyGhost.strength(4.3,1),1e-6,"walking leaves nothing");
        assertEquals(1,BodyGhost.strength(30,1),1e-6,"a dash or a quick cut leaves a full copy");
        assertEquals(0,BodyGhost.strength(30,.05),1e-6,"a copy lying on the body would only tint it");
        for(double v=0;v<40;v+=.5)assertTrue(BodyGhost.strength(v+.5,1)>=BodyGhost.strength(v,1),"fades in steadily with speed");
    }
    @Test void aDashMovesEveryPartTheSameWay(){
        var a=shot(0,Vec3d.ZERO);var b=shot(.03f,new Vec3d(0,0,3));
        for(int part=0;part<6;part++)assertEquals(3,BodyGhost.moved(a,b,part),1e-6);
        assertEquals(0,BodyGhost.moved(a,b,6),1e-6,"no sword drawn, no sword copy");
    }
}
