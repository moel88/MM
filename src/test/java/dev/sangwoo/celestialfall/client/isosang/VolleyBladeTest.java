package dev.sangwoo.celestialfall.client.isosang;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import static dev.sangwoo.celestialfall.client.isosang.VolleyBlade.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The swords of H's basic attack, 회풍만검 (2.95.1, "검이 하늘색이여야 하고 지금 검 모델링이 너무 안 좋아 영상이랑 똑같이
 * 만들어봐 6검을"): their own model as the user's video has them: a long straight blade closing to a sharp point, a small
 * cross-guard whose arms end in points swept a little toward the point, a short grip narrower than the blade and a pointed
 * pommel; sky blue; every face turned outward; and their flat toward the way they are told.
 */
class VolleyBladeTest {
    @Test void itIsTheVideosSwordInItsProportions(){
        assertEquals(1.6,TIP-POMMEL,1e-9,"1.6 blocks, pommel to point");
        assertEquals(TIP,SwordVolley.LEN,1e-9,"the swords' way is reckoned from its grip to its point");
        // (the video at five times its size, and the sword its interface shows: a blade about a twentieth of its length
        // across, the hilt about a fifth of it, the guard's points well past the blade, the grip narrower than the blade)
        assertEquals(.055,2*BLADE/LENGTH,.012,"a long narrow blade");
        double hilt=(GUARD_LOW+GUARD_HIGH)*.5-POMMEL;
        assertEquals(.22,hilt/LENGTH,.04,"a short hilt: "+hilt/LENGTH);
        assertTrue(GUARD>2.2*BLADE&&GUARD<3.4*BLADE,"the guard's points well past the blade, a small guard: "+GUARD/BLADE);
        assertTrue(GUARD_POINT>(GUARD_LOW+GUARD_HIGH)*.5,"its points swept toward the blade's point");
        assertTrue(GRIP<BLADE&&GRIP>.4*BLADE,"a grip narrower than the blade");
        assertTrue(POMMEL_HALF>GRIP&&POMMEL_WIDE>POMMEL&&POMMEL_WIDE<GRIP_LOW,"a diamond pommel, wider than the grip");
    }
    @Test void itsBladeIsStraightAndClosesToASharpPoint(){
        for(double y=BLADE_BASE;y<=TIP-POINT;y+=.01)assertEquals(BLADE,half(y),1e-9,"parallel edges at "+y);
        double last=BLADE+1e-9;
        for(double y=TIP-POINT;y<=TIP;y+=.005){double h=half(y);assertTrue(h<=last+1e-9,"closing at "+y);last=h;}
        assertTrue(half(TIP-.01)<.12*BLADE,"a sharp point: "+half(TIP-.01));
        assertEquals(0,half(TIP+.01),1e-9);assertEquals(0,half(BLADE_BASE-.01),1e-9);
        assertTrue(EDGE_Z<.3*RIDGE_Z,"keen edges either side of its ridge");
    }
    @Test void everyFaceIsTurnedOutward(){
        assertTrue(FACES.size()>60,"a whole model: "+FACES.size());
        double guardMid=(GUARD_LOW+GUARD_HIGH)*.5;
        for(var f:FACES){
            Vec3d c=Vec3d.ZERO;for(var v:f.v())c=c.add(v);c=c.multiply(1.0/f.v().length);
            assertEquals(1,f.n().length(),1e-6);
            Vec3d from=switch(f.role()){case GRIP,POMMEL->new Vec3d(0,c.y,0);case GUARD->new Vec3d(0,guardMid,0);default->new Vec3d(c.x*.5,c.y,0);};
            if(f.role()==Role.BLADE&&Math.abs(f.n().y)>.99)from=new Vec3d(0,BLADE_BASE+.01,0);
            if(Math.abs(c.x)+Math.abs(c.z)<1e-3&&f.role()!=Role.BLADE)continue;
            assertTrue(f.n().dotProduct(c.subtract(from))>=-1e-6,"face "+f.role()+" at "+c+" turned in: "+f.n());
        }
    }
    @Test void itsFlatFacesTheWayItIsTold(){
        // (the fan behind her: its flat away from her back, toward the camera behind her, for any way it points)
        Vec3d back=new Vec3d(0,0,-1);
        for(double a=-80;a<=80;a+=20){
            Vec3d dir=new Vec3d(Math.sin(Math.toRadians(a)),Math.cos(Math.toRadians(a)),-.3).normalize();
            Vec3d[] f=frame(dir,back);
            assertEquals(1,f[1].dotProduct(dir),1e-9);
            assertEquals(0,f[0].dotProduct(f[1]),1e-9);assertEquals(0,f[2].dotProduct(f[1]),1e-9);assertEquals(0,f[0].dotProduct(f[2]),1e-9);
            assertTrue(f[2].dotProduct(back)>.9,"its flat toward her back at "+a+": "+f[2].dotProduct(back));
            assertEquals(1,f[0].crossProduct(f[1]).dotProduct(f[2]),1e-9,"a right-handed frame");
        }
        // straight back along the way it is told: still a frame
        Vec3d[] f=frame(back,back);assertEquals(1,f[0].crossProduct(f[1]).dotProduct(f[2]),1e-9);
    }
    @Test void itIsSkyBlue(){
        for(var r:Role.values()){int c=rgb(r),red=c>>16&255,green=c>>8&255,blue=c&255;assertTrue(blue>=green&&green>red&&blue>200,"sky blue: "+r+" "+Integer.toHexString(c));}
    }
}
