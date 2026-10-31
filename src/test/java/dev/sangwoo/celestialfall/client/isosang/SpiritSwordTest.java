package dev.sangwoo.celestialfall.client.isosang;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.SpiritSword.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The giant sword of the empowered fourth (2.80.2): its own model as the user outlined it on the frames of their videos
 * ("검 모델링 제대로 만들어 영상이랑 똑같은 검 모델로", "키의 4배 (약 7블록)"): some 7 blocks long, a broad straight blade
 * with a rounded end and a dark groove down its middle, a guard flaring out to a point either side, a long narrow grip and
 * a round pommel; every face turned outward; and as it shows beside them, its pommel clear of the ground.
 */
class SpiritSwordTest {
    @Test void itIsSevenBlocksLongInTheUsersProportions(){
        assertEquals(7,LENGTH,1e-9,"four times their height (the user's choice)");
        // (the user's outline from above: the blade 0.14 of the sword's length across, the guard's points 0.31, the grip 0.05)
        assertEquals(.137,2*BLADE/LENGTH,.02,"a broad blade");
        assertEquals(.306,2*GUARD/LENGTH,.03,"its guard flaring out to points well past the blade");
        assertEquals(.36,GRIP/BLADE,.05,"a narrow grip, about a third of the blade");
        assertTrue(TIP-BLADE_BASE>.55*LENGTH,"a long blade: "+(TIP-BLADE_BASE));
        assertTrue(GRIP_HIGH-GRIP_LOW>.2*LENGTH,"a long grip");
        assertTrue(GUARD_POINT>GRIP_HIGH&&GUARD_POINT<BLADE_BASE,"its points between the grip and the blade");
    }
    @Test void itsBladeIsStraightThenRoundedAtItsEnd(){
        for(double y=BLADE_BASE;y<=TIP-END;y+=.1)assertEquals(BLADE,half(y),1e-9,"straight at "+y);
        double last=BLADE;
        for(double y=TIP-END;y<=TIP;y+=.01){double h=half(y);assertTrue(h<=last+1e-9,"narrowing to its end at "+y);last=h;}
        assertTrue(half(TIP-.02)<.4*BLADE,"rounded, not cut square");assertTrue(half(TIP-END*.5)>.8*BLADE,"round, not pointed");
        assertEquals(0,half(BLADE_BASE-.01),1e-9);assertEquals(0,half(TIP+.01),1e-9);
        assertTrue(GROOVE<FLAT&&GROOVE_LOW>BLADE_BASE&&GROOVE_HIGH<TIP-END,"its groove down the middle of its flat");
    }
    @Test void everyFaceIsTurnedOutward(){
        assertTrue(FACES.size()>150,"a full model: "+FACES.size());
        for(var f:FACES){
            Vec3d c=Vec3d.ZERO;for(var v:f.v())c=c.add(v);c=c.multiply(1.0/f.v().length);
            assertEquals(1,f.n().length(),1e-6);
            // outward from its own axis (the grip and the pommel turn about it; the blade and the guard face out of their flat)
            Vec3d from=switch(f.role()){case GRIP,BAND,POMMEL->new Vec3d(0,c.y,0);default->new Vec3d(c.x*.5,c.y,0);};
            if(f.role()==Role.POMMEL&&Math.abs(c.x)+Math.abs(c.z)<1e-3)continue;
            assertTrue(f.n().dotProduct(c.subtract(from))>=-1e-6,"face "+f.role()+" at "+c+" turned in: "+f.n());
        }
    }
    @Test void asItShowsBesideThemItsPommelIsClearOfTheGroundAndItsEndWhereTheServersWayBegins(){
        Vec3d dir=IsosangEmpower.tilted(0);
        double pommel=IsosangEmpower.HOVER_UP+POMMEL*dir.y;
        assertTrue(pommel>.1,"its pommel above the ground: "+pommel);
        double end=IsosangEmpower.HOVER_FW+TIP*dir.horizontalLength();
        assertEquals(dev.sangwoo.celestialfall.isosang.IsosangRules.GIANT_START,end,1e-9,"its end where the server's way begins");
        assertTrue(IsosangEmpower.HOVER_RT+GUARD<-.25,"its guard clear of their body at their left");
    }
}
