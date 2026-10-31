package dev.sangwoo.celestialfall.client.isosang;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.SpiritSword.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The giant sword of the empowered fourth: its own model as the user outlined it on the frames of their videos (2.80.2,
 * "검 모델링 제대로 만들어 영상이랑 똑같은 검 모델로"), half the size and sharp (2.80.3, "검도 더 날카롭게 만들어 주고", "검 크기도
 * 2배 더 줄이고"): 3.5 blocks long in the proportions of their outline, a broad straight blade narrowing to a sharp point
 * with a dark groove down its middle, a guard sweeping out to a sharp point either side, a long narrow grip and a pointed
 * pommel; every face turned outward; and as it shows beside them, its pommel clear of the ground and its point where the
 * server's way begins.
 */
class SpiritSwordTest {
    @Test void itIsHalfTheSevenBlocksInTheUsersProportions(){
        assertEquals(3.5,LENGTH,1e-9,"half the 7 blocks of 2.80.2 (the user's choice)");
        // (the user's outline from above: the blade 0.14 of the sword's length across, the guard's points 0.31, the grip 0.05)
        assertEquals(.137,2*BLADE/LENGTH,.02,"a broad blade");
        assertEquals(.306,2*GUARD/LENGTH,.03,"its guard sweeping out to points well past the blade");
        assertEquals(.36,GRIP/BLADE,.05,"a narrow grip, about a third of the blade");
        assertTrue(TIP-BLADE_BASE>.55*LENGTH,"a long blade: "+(TIP-BLADE_BASE));
        assertTrue(GRIP_HIGH-GRIP_LOW>.2*LENGTH,"a long grip");
        assertTrue(GUARD_POINT<GRIP_HIGH&&GUARD_POINT>GRIP_LOW,"its points swept back past the guard's foot, toward the grip");
    }
    @Test void itsBladeNarrowsToASharpPoint(){
        // (2.80.3, user request: "검도 더 날카롭게": 2.80.2's end was round)
        double last=BLADE+1e-9;
        for(double y=BLADE_BASE;y<=TIP;y+=.01){double h=half(y);assertTrue(h<=last+1e-9,"narrowing to its point at "+y);last=h;}
        assertTrue(half(TIP-POINT-.01)>.8*BLADE,"broad until its point");
        assertTrue(half(TIP-.03)<.08*BLADE,"a sharp point, not round: "+half(TIP-.03));
        double slope=(half(TIP-.06)-half(TIP-.03))/.03;
        assertTrue(slope>.3,"its edges meet at an angle, still closing steeply at the very point: "+slope);
        assertTrue(half(TIP-POINT*.5)>.4*BLADE,"a full point, not a needle");
        assertEquals(0,half(BLADE_BASE-.01),1e-9);assertEquals(0,half(TIP+.01),1e-9);
        assertTrue(GROOVE<FLAT&&GROOVE_LOW>BLADE_BASE&&GROOVE_HIGH<TIP-POINT,"its groove down the middle of its flat, short of the point");
        assertTrue(EDGE_Z<.2*FLAT_Z,"keen edges");
    }
    @Test void everyFaceIsTurnedOutward(){
        assertTrue(FACES.size()>150,"a full model: "+FACES.size());
        double guardMid=(GRIP_HIGH+BLADE_BASE)*.5;
        for(var f:FACES){
            Vec3d c=Vec3d.ZERO;for(var v:f.v())c=c.add(v);c=c.multiply(1.0/f.v().length);
            assertEquals(1,f.n().length(),1e-6);
            // outward from its own axis (the grip and the pommel turn about it; the blade faces out of its flat; the guard
            // and its gem out from the guard's middle)
            Vec3d from=switch(f.role()){case GRIP,BAND,POMMEL->new Vec3d(0,c.y,0);case GUARD,GEM->new Vec3d(0,guardMid,0);default->new Vec3d(c.x*.5,c.y,0);};
            if(f.role()==Role.POMMEL&&Math.abs(c.x)+Math.abs(c.z)<1e-3)continue;
            assertTrue(f.n().dotProduct(c.subtract(from))>=-1e-6,"face "+f.role()+" at "+c+" turned in: "+f.n());
        }
    }
    @Test void asItShowsBesideThemItsPommelIsClearOfTheGroundAndItsPointWhereTheServersWayBegins(){
        // (in their frame: x ahead, y up, z to their right)
        for(double most:new double[]{dev.sangwoo.celestialfall.isosang.IsosangRules.GIANT_RANGE,3.2}){
            Vec3d grip=new Vec3d(IsosangEmpower.hoverFw(most),IsosangEmpower.HOVER_UP,IsosangEmpower.HOVER_RT);
            Vec3d dir=new Vec3d(most,IsosangEmpower.AIM_UP,0).subtract(grip).normalize();
            double pommel=grip.y+POMMEL*dir.y;
            assertTrue(pommel>.5,"its pommel well above the ground: "+pommel);
            assertEquals(dev.sangwoo.celestialfall.isosang.IsosangRules.GIANT_START,grip.add(dir.multiply(TIP)).x,1e-6,"its point where the server's way begins, for a way of "+most);
            assertTrue(grip.z+GUARD<-.3,"its guard clear of their body at their left: "+(grip.z+GUARD));
        }
    }
}
